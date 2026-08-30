#!/usr/bin/env python3
"""Prepare a Kokoro ONNX model artifact for ONNX Runtime QNN HTP execution.

This tool intentionally requires representative calibration tensors. QNN HTP
quantization without real calibration can produce bad audio and misleading
benchmarks, so the script fails fast instead of fabricating random inputs.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import platform
import shutil
import sys
import tarfile
import tempfile
from collections import Counter
from pathlib import Path
from typing import Iterator


class MissingDependency(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


STRICT_QNN_BLOCKING_OPS = {
    "ConcatFromSequence",
    "If",
    "Loop",
    "RandomNormal",
    "RandomNormalLike",
    "RandomUniform",
    "RandomUniformLike",
    "Scan",
    "SequenceAt",
    "SequenceConstruct",
    "SequenceEmpty",
    "SequenceErase",
    "SequenceInsert",
    "SequenceLength",
    "SplitToSequence",
}


def import_onnx_tools():
    try:
        import numpy as np  # noqa: F401
        import onnx
        from onnxruntime.quantization import QuantType, quantize
        from onnxruntime.quantization import CalibrationDataReader
        from onnxruntime.quantization.execution_providers.qnn import (
            get_qnn_qdq_config,
            qnn_preprocess_model,
        )
    except Exception as error:  # pragma: no cover - exercised by CLI use
        raise MissingDependency(
            "Install host quantization tools first: "
            "python3 -m pip install onnx onnxruntime numpy"
        ) from error
    return {
        "onnx": onnx,
        "QuantType": QuantType,
        "quantize": quantize,
        "CalibrationDataReader": CalibrationDataReader,
        "get_qnn_qdq_config": get_qnn_qdq_config,
        "qnn_preprocess_model": qnn_preprocess_model,
    }


def extract_source(source: Path, work_dir: Path) -> Path:
    if source.is_dir():
        return source
    if not source.is_file():
        raise FileNotFoundError(f"Missing Kokoro source: {source}")
    if source.suffixes[-2:] != [".tar", ".bz2"]:
        raise ValueError(f"Expected an extracted model directory or .tar.bz2 archive: {source}")

    with tarfile.open(source, "r:bz2") as archive:
        archive.extractall(work_dir, filter="data")
    roots = [child for child in work_dir.iterdir() if child.is_dir()]
    if len(roots) != 1:
        raise ValueError(f"Expected one extracted model root in {source}, found {len(roots)}")
    return roots[0]


def model_value_info(value) -> dict[str, object]:
    tensor = value.type.tensor_type
    shape: list[object] = []
    for dim in tensor.shape.dim:
        if dim.dim_value:
            shape.append(dim.dim_value)
        elif dim.dim_param:
            shape.append(dim.dim_param)
        else:
            shape.append("?")
    return {
        "name": value.name,
        "elem_type": tensor.elem_type,
        "shape": shape,
        "dynamic": any(not isinstance(dim, int) for dim in shape),
    }


def inspect_model(onnx, model_path: Path) -> dict[str, object]:
    model = onnx.load(model_path)
    ops = Counter(node.op_type for node in model.graph.node)
    return {
        "ir_version": model.ir_version,
        "opset_imports": {op.domain or "ai.onnx": op.version for op in model.opset_import},
        "inputs": [model_value_info(value) for value in model.graph.input],
        "outputs": [model_value_info(value) for value in model.graph.output],
        "op_counts": dict(sorted(ops.items())),
    }


def strict_qnn_compatibility(report: dict[str, object]) -> dict[str, object]:
    op_counts = report.get("op_counts", {})
    if not isinstance(op_counts, dict):
        op_counts = {}
    blocking_ops = {
        str(op): int(count)
        for op, count in sorted(op_counts.items())
        if op in STRICT_QNN_BLOCKING_OPS and int(count) > 0
    }
    dynamic_inputs = [
        value.get("name", "")
        for value in report.get("inputs", [])
        if isinstance(value, dict) and value.get("dynamic") is True
    ]
    compatible = not blocking_ops and not dynamic_inputs
    return {
        "strict_qnn_compatible": compatible,
        "blocking_ops": blocking_ops,
        "dynamic_inputs": dynamic_inputs,
        "reason": "compatible"
        if compatible
        else "Model still contains control-flow, sequence, random, or dynamic-input surfaces that must be removed before strict no-CPU QNN generation can be considered real.",
    }


def iter_calibration_npz(calibration_dir: Path) -> Iterator[Path]:
    if not calibration_dir.is_dir():
        raise FileNotFoundError(f"Missing calibration directory: {calibration_dir}")
    files = sorted(calibration_dir.glob("*.npz"))
    if not files:
        raise ValueError(
            f"No calibration .npz files found in {calibration_dir}. "
            "Each file must contain arrays keyed by ONNX input name."
        )
    return iter(files)


def make_data_reader(base_class, calibration_dir: Path):
    import numpy as np

    class NpzCalibrationDataReader(base_class):
        def __init__(self, directory: Path):
            self._items = list(iter_calibration_npz(directory))
            self._iterator = iter(self._items)

        def get_next(self):
            path = next(self._iterator, None)
            if path is None:
                return None
            with np.load(path) as data:
                return {key: data[key] for key in data.files}

        def rewind(self):
            self._iterator = iter(self._items)

    return NpzCalibrationDataReader(calibration_dir)


def copy_support_files(source_root: Path, output_root: Path, model_name: str, output_model_name: str) -> None:
    output_root.mkdir(parents=True, exist_ok=True)
    for child in source_root.iterdir():
        if child.name in {model_name, output_model_name}:
            continue
        target = output_root / child.name
        if child.is_dir():
            if target.exists():
                shutil.rmtree(target)
            shutil.copytree(child, target)
        elif child.is_file():
            shutil.copy2(child, target)


def prepare(args: argparse.Namespace) -> int:
    tools = import_onnx_tools()
    with tempfile.TemporaryDirectory(prefix="xreader-kokoro-qnn-") as temp:
        source_root = extract_source(args.source.resolve(), Path(temp))
        input_model = source_root / args.model_name
        if not input_model.is_file():
            raise FileNotFoundError(f"Missing source ONNX model: {input_model}")

        args.output.mkdir(parents=True, exist_ok=True)
        copy_support_files(source_root, args.output, args.model_name, args.output_model_name)

        preprocessed_model = args.output / "model.qnn.preprocessed.onnx"
        output_model = args.output / args.output_model_name
        manifest_path = args.output / "xreader-qnn-model-manifest.json"

        # onnxruntime 1.27's QNN preprocessor currently assumes metadata_props
        # is directly iterable as key/value pairs. Pass a sanitized ModelProto
        # to avoid that helper bug while keeping metadata in our manifest.
        source_model = tools["onnx"].load(input_model)
        del source_model.metadata_props[:]
        changed = tools["qnn_preprocess_model"](source_model, str(preprocessed_model))
        model_to_quantize = preprocessed_model if changed else input_model
        reader = make_data_reader(tools["CalibrationDataReader"], args.calibration_dir.resolve())
        qnn_config = tools["get_qnn_qdq_config"](
            str(model_to_quantize),
            reader,
            activation_type=tools["QuantType"].QUInt16,
            weight_type=tools["QuantType"].QUInt8,
        )
        tools["quantize"](str(model_to_quantize), str(output_model), qnn_config)

        source_report = inspect_model(tools["onnx"], input_model)
        output_report = inspect_model(tools["onnx"], output_model)
        strict_report = strict_qnn_compatibility(output_report)
        fixed_dimensions = {
            int(dimension)
            for value in output_report.get("inputs", [])
            if isinstance(value, dict)
            for dimension in value.get("shape", [])
            if isinstance(dimension, int)
        }
        if args.token_bucket not in fixed_dimensions:
            raise ValueError(
                f"Prepared model does not expose requested fixed token bucket {args.token_bucket}; "
                f"fixed input dimensions were {sorted(fixed_dimensions)}"
            )
        manifest = {
            "schema_version": 1,
            "artifact_type": "xreader-kokoro-qnn",
            "source_model": {
                "name": input_model.name,
                "sha256": sha256(input_model),
                "revision": args.source_revision,
            },
            "output_model": output_model.name,
            "output_model_sha256": sha256(output_model),
            "output_model_bytes": output_model.stat().st_size,
            "preprocessed_model_changed": bool(changed),
            "calibration_npz_count": len(list(args.calibration_dir.glob("*.npz"))),
            "activation_type": "QUInt16",
            "weight_type": "QUInt8",
            "token_buckets": [args.token_bucket],
            "strict_qnn_compatible": strict_report["strict_qnn_compatible"],
            "blocker_analysis": strict_report,
            "toolchain": {
                "python": platform.python_version(),
                "onnx": importlib.metadata.version("onnx"),
                "onnxruntime": importlib.metadata.version("onnxruntime"),
                "numpy": importlib.metadata.version("numpy"),
                "qairt": args.qairt_version,
            },
            "provenance": {
                "source_url": args.source_url,
                "license": args.model_license,
            },
            "source_report": source_report,
            "output_report": output_report,
        }
        manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        if args.require_strict_qnn_compatible and not strict_report["strict_qnn_compatible"]:
            raise ValueError(
                "Prepared model is not strict-QNN-compatible: "
                f"{json.dumps(strict_report, sort_keys=True)}"
            )
        print(f"Wrote QNN-prepared Kokoro model: {output_model}")
        print(f"Wrote preparation manifest: {manifest_path}")
        return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        required=True,
        help="Extracted kokoro-multi-lang-v1_0 directory or the downloaded .tar.bz2 archive.",
    )
    parser.add_argument(
        "--calibration-dir",
        type=Path,
        required=True,
        help="Directory of representative .npz calibration tensors keyed by ONNX input name.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Output model directory to package or push beside the installed app model.",
    )
    parser.add_argument("--model-name", default="model.onnx")
    parser.add_argument("--output-model-name", default="model.qnn.onnx")
    parser.add_argument("--source-revision", required=True, help="Immutable upstream release tag or commit for the source model.")
    parser.add_argument("--source-url", default="https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models")
    parser.add_argument("--model-license", default="Apache-2.0")
    parser.add_argument("--qairt-version", required=True, help="QAIRT version used to build and validate the QNN runtime.")
    parser.add_argument(
        "--token-bucket",
        type=int,
        required=True,
        help="Fixed token dimension that must be present in this prepared model's input shapes.",
    )
    parser.add_argument(
        "--require-strict-qnn-compatible",
        action="store_true",
        help="Fail if the prepared ONNX graph still has known strict QNN full-graph blockers.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    try:
        return prepare(parse_args(argv))
    except MissingDependency as error:
        print(error, file=sys.stderr)
        return 2
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
