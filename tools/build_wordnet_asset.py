#!/usr/bin/env python3
import io
import pathlib
import sqlite3
import tarfile
import urllib.request

WORDNET_URL = "https://wordnetcode.princeton.edu/3.0/WordNet-3.0.tar.gz"
ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "dictionary"
OUT_DB = OUT_DIR / "wordnet.db"
OUT_LICENSE = OUT_DIR / "LICENSE_WORDNET.txt"

POS_NAMES = {
    "n": "noun",
    "v": "verb",
    "a": "adjective",
    "s": "adjective",
    "r": "adverb",
}


def parse_data_file(content: str):
    for line in content.splitlines():
        if not line or line.startswith("  "):
            continue
        fields, _, gloss = line.partition(" | ")
        if not gloss:
            continue
        tokens = fields.split()
        if len(tokens) < 5:
            continue
        pos = POS_NAMES.get(tokens[2])
        if pos is None:
            continue
        word_count = int(tokens[3], 16)
        words = [tokens[4 + (i * 2)].replace("_", " ") for i in range(word_count)]
        definition = gloss.split(";")[0].strip()
        synonyms = ", ".join(dict.fromkeys(words))
        for word in words:
            lemma = word.lower()
            yield lemma, pos, definition, synonyms


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(WORDNET_URL, timeout=60) as response:
        data = response.read()

    entries = {}
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        for member in archive.getmembers():
            name = pathlib.Path(member.name).name
            if name in {"LICENSE", "README"}:
                extracted = archive.extractfile(member)
                if extracted and name == "LICENSE":
                    OUT_LICENSE.write_text(extracted.read().decode("utf-8", errors="replace"))
            if name in {"data.noun", "data.verb", "data.adj", "data.adv"}:
                extracted = archive.extractfile(member)
                if not extracted:
                    continue
                text = extracted.read().decode("utf-8", errors="replace")
                for lemma, pos, definition, synonyms in parse_data_file(text):
                    key = (lemma, pos, definition)
                    entries[key] = (lemma, pos, definition, synonyms)

    if OUT_DB.exists():
        OUT_DB.unlink()
    conn = sqlite3.connect(OUT_DB)
    try:
        conn.execute("PRAGMA journal_mode=OFF")
        conn.execute("PRAGMA synchronous=OFF")
        conn.execute(
            """
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY,
                lemma TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                definition TEXT NOT NULL,
                synonyms TEXT NOT NULL
            )
            """
        )
        conn.executemany(
            "INSERT INTO entries (lemma, part_of_speech, definition, synonyms) VALUES (?, ?, ?, ?)",
            sorted(entries.values()),
        )
        conn.execute("CREATE INDEX entries_lemma_idx ON entries(lemma)")
        conn.execute("CREATE INDEX entries_lemma_pos_idx ON entries(lemma, part_of_speech)")
        conn.commit()
        conn.execute("VACUUM")
    finally:
        conn.close()

    if not OUT_LICENSE.exists():
        OUT_LICENSE.write_text(
            "WordNet data is distributed by Princeton University. "
            "See https://wordnet.princeton.edu/license-and-commercial-use\n",
            encoding="utf-8",
        )

    print(f"Wrote {OUT_DB} with {len(entries)} entries")


if __name__ == "__main__":
    main()
