@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.BookEntity
import com.xreader.app.data.LibrarySearchRow
import com.xreader.app.data.NeuralTtsModelEntity
import com.xreader.app.data.NeuralTtsModelStatus
import com.xreader.app.data.ReaderTheme
import com.xreader.app.importer.SupportedBookTypes
import com.xreader.app.repository.bookExportFileName
import com.xreader.app.repository.bookExportMimeType
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibraryGroup
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.NeuralTtsGender
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.tts.AudiobookGenerationHardwareReadiness
import com.xreader.app.tts.AudiobookPlaybackUiState
import com.xreader.app.tts.AudiobookGenerationScope
import com.xreader.app.tts.GeneratedAudiobookChapter
import com.xreader.app.tts.NeuralTtsModelCatalog
import com.xreader.app.tts.NeuralTtsModelSpec
import com.xreader.app.tts.NeuralTtsSpeakerSpec
import com.xreader.app.tts.audiobookGenerationProgressLabel
import com.xreader.app.tts.canDeleteGeneratedAudiobook
import com.xreader.app.tts.generationEtaLabel
import com.xreader.app.tts.segmentLimit
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun BookAudiobookDialog(
    book: BookEntity,
    settings: ReaderSettings,
    models: List<NeuralTtsModelEntity>,
    hardwareReadiness: AudiobookGenerationHardwareReadiness,
    bookAudioItems: List<BookAudiobookAudioUiItem>,
    scan: AudiobookScanUiState?,
    playback: AudiobookPlaybackUiState,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onGenerate: (AudiobookGenerationScope) -> Unit,
    onCancelGeneration: (BookAudioEntity) -> Unit,
    onExportAudio: (BookAudioEntity) -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
    onDeleteAllAudio: () -> Unit,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPlayAudioFromSegment: (BookAudioEntity, Int) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onSpeakerSelected: (Int) -> Unit,
    onGenderSelected: (NeuralTtsGender) -> Unit,
    onToneSelected: (NeuralTtsTone) -> Unit,
    onPaceSelected: (NeuralTtsPace) -> Unit,
    onDownload: (String) -> Unit,
    onCancelModelInstall: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    val bookAudio = remember(bookAudioItems) { bookAudioItems.map { it.audio } }
    val modelsById = remember(models) { models.associateBy { it.modelId } }
    val selectedSpec = NeuralTtsModelCatalog.models.firstOrNull { it.modelId == settings.neuralTtsModelId }
        ?: NeuralTtsModelCatalog.models.first()
    val selectedModel = modelsById[selectedSpec.modelId]
    val selectedStatus = selectedModel?.status ?: NeuralTtsModelStatus.NOT_DOWNLOADED
    val selectedSpeaker = selectedSpec.speaker(settings.neuralTtsSpeakerId)
    val narratorOptions = remember(selectedSpec, settings.neuralTtsGender, selectedSpeaker) {
        selectedSpec.speakers
            .filter { settings.neuralTtsGender == NeuralTtsGender.ANY || it.gender == settings.neuralTtsGender }
            .let { filtered ->
                if (selectedSpeaker in filtered) filtered else listOf(selectedSpeaker) + filtered
            }
    }
    val selectedAudioItem = remember(bookAudioItems, settings.neuralTtsModelId, settings.neuralTtsSpeakerId, settings.neuralTtsPace, settings.neuralTtsTone) {
        selectedAudiobookStatusItem(
            items = bookAudioItems,
            modelId = settings.neuralTtsModelId,
            speakerId = selectedSpec.normalizedSpeakerId(settings.neuralTtsSpeakerId),
            speed = settings.neuralTtsPace.speed,
            tone = settings.neuralTtsTone.name
        )
    }
    val selectedProfileSpeakerId = selectedSpec.normalizedSpeakerId(settings.neuralTtsSpeakerId)
    val generatingSelectedAudio = remember(bookAudio, settings.neuralTtsModelId, selectedProfileSpeakerId, settings.neuralTtsPace, settings.neuralTtsTone) {
        bookAudio.any { audio ->
            audio.matchesAudiobookProfile(
                modelId = settings.neuralTtsModelId,
                speakerId = selectedProfileSpeakerId,
                speed = settings.neuralTtsPace.speed,
                tone = settings.neuralTtsTone.name
            ) && audio.status == BookAudioStatus.GENERATING
        }
    }
    val generationBlockedReason = audiobookGenerationBlockedReason(
        status = selectedStatus,
        generatingSelectedAudio = generatingSelectedAudio,
        modelName = selectedSpec.displayName,
        hardwareReadiness = hardwareReadiness
    )
    val generatedAudioItems = remember(bookAudioItems) {
        bookAudioItems
            .filter { item -> item.shouldShowInGlobalAudiobooksScreen() }
            .sortedByDescending { it.audio.generatedAt ?: it.audio.updatedAt }
    }
    var deleteCandidate by remember(book.id) { mutableStateOf<BookAudioEntity?>(null) }
    var deleteAllOpen by remember(book.id) { mutableStateOf(false) }
    var chapterPicker by remember(book.id) { mutableStateOf<BookAudiobookAudioUiItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate audiobook") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = audiobookGenerationHeaderDetail(
                                book = book,
                                scan = scan,
                                selectedSpec = selectedSpec,
                                selectedSpeaker = selectedSpeaker,
                                tone = settings.neuralTtsTone,
                                pace = settings.neuralTtsPace
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                selectedAudioItem?.let { item ->
                    item {
                        AudiobookStatusCard(
                            audio = item.audio,
                            playableSegmentFiles = item.playableSegmentFiles,
                            onCancelGeneration = onCancelGeneration,
                            onExportAudio = onExportAudio,
                            playback = playback,
                            onPlayAudio = onPlayAudio,
                            onPauseAudio = onPauseAudio,
                            onStopAudio = onStopAudio,
                            onDeleteAudio = { deleteCandidate = it },
                        )
                    }
                }
                if (bookAudio.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { deleteAllOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = bookAudio.all { it.canDeleteGeneratedAudiobook() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (bookAudio.any { !it.canDeleteGeneratedAudiobook() }) {
                                    "Stop generation before deleting audio"
                                } else {
                                    "Delete all generated audio for this book"
                                }
                            )
                        }
                    }
                }
                item {
                    AudiobookScanCard(
                        scan = scan,
                        onScan = onScan
                    )
                }
                item {
                    AudiobookRuntimeNote()
                }
                item {
                    AudiobookChipGroup(
                        title = "Voice",
                        options = NeuralTtsGender.entries,
                        selected = settings.neuralTtsGender,
                        label = { it.label },
                        onSelected = onGenderSelected
                    )
                }
                if (selectedSpec.speakers.size > 1) {
                    item {
                        AudiobookChipGroup(
                            title = "Narrator",
                            options = narratorOptions,
                            selected = selectedSpeaker,
                            label = { "${it.label} (${it.gender.label})" },
                            onSelected = { onSpeakerSelected(it.id) }
                        )
                    }
                }
                item {
                    AudiobookChipGroup(
                        title = "Narration style",
                        options = NeuralTtsTone.entries,
                        selected = settings.neuralTtsTone,
                        label = { it.label },
                        onSelected = onToneSelected
                    )
                }
                item {
                    AudiobookChipGroup(
                        title = "Pacing",
                        options = NeuralTtsPace.entries,
                        selected = settings.neuralTtsPace,
                        label = { it.label },
                        onSelected = onPaceSelected
                    )
                }
                item {
                    AudiobookSelectedVoiceModelRow(
                        spec = selectedSpec,
                        status = selectedStatus,
                        statusText = neuralTtsStatusText(
                            status = selectedStatus,
                            downloaded = selectedModel?.downloadedBytes ?: 0,
                            total = selectedModel?.totalBytes ?: selectedSpec.archiveBytes,
                            archiveBytes = selectedSpec.archiveBytes,
                            error = selectedModel?.error
                        ),
                        onDownload = { onDownload(selectedSpec.modelId) },
                        onCancel = { onCancelModelInstall(selectedSpec.modelId) },
                        onPreview = { onPreview(selectedSpec.modelId) }
                    )
                }
                generationBlockedReason?.let { reason ->
                    item {
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (generatedAudioItems.isNotEmpty()) {
                    item {
                        GeneratedAudiobookList(
                            items = generatedAudioItems,
                            selectedAudioId = selectedAudioItem?.audio?.id,
                            playback = playback,
                            onExportAudio = onExportAudio,
                            onPlayAudio = onPlayAudio,
                            onPauseAudio = onPauseAudio,
                            onStopAudio = onStopAudio,
                            onDeleteAudio = { deleteCandidate = it },
                            onShowChapters = { chapterPicker = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onGenerate(AudiobookGenerationScope.SAMPLE) },
                    enabled = generationBlockedReason == null
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AudiobookScopeActionText(AudiobookGenerationScope.SAMPLE, scan)
                }
                OutlinedButton(
                    onClick = { onGenerate(AudiobookGenerationScope.FIRST_CHAPTER) },
                    enabled = generationBlockedReason == null
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    AudiobookScopeActionText(AudiobookGenerationScope.FIRST_CHAPTER, scan)
                }
                Button(
                    onClick = { onGenerate(AudiobookGenerationScope.FULL_BOOK) },
                    enabled = generationBlockedReason == null
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    if (generatingSelectedAudio) {
                        Text("Generating")
                    } else {
                        AudiobookScopeActionText(AudiobookGenerationScope.FULL_BOOK, scan)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
    deleteCandidate?.let { audio ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete generated audio?") },
            text = {
                Text(generatedAudiobookDeleteDetail(audio))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        onDeleteAudio(audio)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (deleteAllOpen) {
        AlertDialog(
            onDismissRequest = { deleteAllOpen = false },
            title = { Text("Delete all audiobook audio?") },
            text = { Text("This removes every generated, partial, and failed audiobook version for ${book.title}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAllOpen = false
                        onDeleteAllAudio()
                    }
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    chapterPicker?.let { item ->
        AudiobookChapterPickerDialog(
            title = item.audio.displayProfileLabel(),
            chapters = item.chapters,
            playback = playback.takeIf { it.audioId == item.audio.id },
            onDismiss = { chapterPicker = null },
            onChapter = { chapter ->
                chapterPicker = null
                onPlayAudioFromSegment(item.audio, chapter.firstSegmentIndex)
            }
        )
    }
}

@Composable
private fun AudiobookScanCard(
    scan: AudiobookScanUiState?,
    onScan: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Full-book audio scan", style = MaterialTheme.typography.labelLarge)
                    Text(
                        audiobookScanSummary(scan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (scan?.scanning == true) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(onClick = onScan) {
                        Text(if (scan?.hasText == true) "Rescan" else "Scan")
                    }
                }
            }
            scan?.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (scan?.hasText == true) {
                val pillLabels = remember(scan) { audiobookScanPillLabels(scan) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pillLabels.forEach { label ->
                        AudiobookScanPill(label)
                    }
                }
                val detectedChapters = remember(scan.chapterTitles) {
                    audiobookDetectedChapterTitleSummary(scan.chapterTitles)
                }
                detectedChapters?.let { detectedText ->
                    Text(
                        text = detectedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookScanPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

internal fun audiobookScanSummary(scan: AudiobookScanUiState?): String =
    when {
        scan?.scanning == true -> "Scanning indexed book text for extractable narration."
        scan?.hasText == true -> audiobookPreparedScanSummary(scan)
        scan?.error != null -> "Scan could not prepare narration for this book."
        else -> "Scan the selected ebook before a long generation job."
    }

private fun audiobookPreparedScanSummary(scan: AudiobookScanUiState): String {
    val sourceText = "${scan.sourceSectionCount} source sections prepared"
    return if (scan.chapterCount > 0) {
        "$sourceText • ${generatedAudiobookChapterCountLabel(scan.chapterCount)} detected"
    } else {
        sourceText
    }
}

internal fun audiobookScanPillLabels(scan: AudiobookScanUiState): List<String> =
    buildList(capacity = 5) {
        add("${scan.wordCount} words")
        add("${scan.segmentCount} segments")
        if (scan.chapterCount > 0) {
            add(generatedAudiobookChapterCountLabel(scan.chapterCount))
        }
        add(audiobookDurationLabel(scan.estimatedAudioMillis))
        add(audiobookStorageLabel(scan.estimatedStorageBytes))
    }

internal fun audiobookDetectedChapterTitleSummary(titles: List<String>): String? {
    var hasTitle = false
    val detail = buildString {
        titles.forEach { rawTitle ->
            val title = rawTitle.trim()
            if (title.isBlank()) return@forEach
            if (hasTitle) append(" • ")
            append(title)
            hasTitle = true
        }
    }
    return detail.takeIf { hasTitle }?.let { "Detected: $it" }
}

internal fun audiobookScopeActionLabel(
    scope: AudiobookGenerationScope,
    scan: AudiobookScanUiState?,
): String {
    val meta = audiobookScopeActionMetaLabel(scope, scan) ?: return audiobookScopeActionTitle(scope)
    return "${audiobookScopeActionTitle(scope)} • $meta"
}

internal fun audiobookScopeActionTitle(scope: AudiobookGenerationScope): String =
    when (scope) {
        AudiobookGenerationScope.SAMPLE -> "Sample"
        AudiobookGenerationScope.FIRST_CHAPTER -> "Chapter"
        AudiobookGenerationScope.FULL_BOOK -> "Full book"
    }

internal fun audiobookScopeActionMetaLabel(
    scope: AudiobookGenerationScope,
    scan: AudiobookScanUiState?,
): String? {
    if (scan?.hasText != true) {
        return null
    }
    val segments = audiobookScopeSegmentEstimate(scope, scan)
    val duration = audiobookScopeDurationEstimateMillis(scope, scan)
    return "$segments seg • ${audiobookDurationLabel(duration)}"
}

@Composable
private fun AudiobookScopeActionText(
    scope: AudiobookGenerationScope,
    scan: AudiobookScanUiState?,
) {
    val meta = audiobookScopeActionMetaLabel(scope, scan)
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(audiobookScopeActionTitle(scope), maxLines = 1)
        if (meta != null) {
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun audiobookScopeSegmentEstimate(
    scope: AudiobookGenerationScope,
    scan: AudiobookScanUiState,
): Int =
    scope.segmentLimit(
        totalSegments = scan.segmentCount,
        firstChapterSegmentCount = scan.firstChapterSegmentCount
    )

private fun audiobookScopeDurationEstimateMillis(
    scope: AudiobookGenerationScope,
    scan: AudiobookScanUiState,
): Long {
    if (scan.segmentCount <= 0 || scan.estimatedAudioMillis <= 0L) return 0L
    val segments = audiobookScopeSegmentEstimate(scope, scan)
    return (scan.estimatedAudioMillis * segments / scan.segmentCount).coerceAtLeast(0L)
}

@Composable
private fun AudiobookRuntimeNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "Generated locally on device with Kokoro v1.0. Full-book jobs require hardware acceleration, keep resumable segment files as they run, and can take time on long books.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AudiobookStatusCard(
    audio: BookAudioEntity,
    playableSegmentFiles: Int,
    onCancelGeneration: (BookAudioEntity) -> Unit,
    onExportAudio: (BookAudioEntity) -> Unit,
    playback: AudiobookPlaybackUiState,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
) {
    val progress = when {
        audio.segmentCount <= 0 -> 0f
        else -> audio.completedSegments.toFloat() / audio.segmentCount.toFloat()
    }.coerceIn(0f, 1f)
    val playableSegments = playableSegmentFiles
    val title = when (audio.status) {
        BookAudioStatus.GENERATED -> if (playableSegments > 0) "${audio.scopeLabel} ready" else "${audio.scopeLabel} files missing"
        BookAudioStatus.GENERATING -> "Generating ${audio.scopeLabel.lowercase(Locale.US)}"
        BookAudioStatus.CANCELED -> "${audio.scopeLabel} generation stopped"
        BookAudioStatus.FAILED -> "${audio.scopeLabel} generation failed"
    }
    val detail = audiobookStatusDetail(audio, playableSegmentFiles = playableSegmentFiles)
    val canPlay = playableSegments > 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = when (audio.status) {
            BookAudioStatus.GENERATED -> MaterialTheme.colorScheme.primaryContainer
            BookAudioStatus.GENERATING -> MaterialTheme.colorScheme.secondaryContainer
            BookAudioStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
            BookAudioStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = when (audio.status) {
                    BookAudioStatus.GENERATED -> MaterialTheme.colorScheme.onPrimaryContainer
                    BookAudioStatus.GENERATING -> MaterialTheme.colorScheme.onSecondaryContainer
                    BookAudioStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                    BookAudioStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                }
            )
            if (audio.status == BookAudioStatus.GENERATING && audio.segmentCount > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { onCancelGeneration(audio) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Stop")
                }
            }
            if ((audio.status == BookAudioStatus.CANCELED || audio.status == BookAudioStatus.FAILED) && !canPlay) {
                TextButton(
                    onClick = { onDeleteAudio(audio) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
            if (canPlay) {
                AudiobookPlaybackActions(
                    audio = audio,
                    playableSegmentFiles = playableSegments,
                    playback = playback,
                    onPlayAudio = onPlayAudio,
                    onPauseAudio = onPauseAudio,
                    onStopAudio = onStopAudio,
                    onExportAudio = onExportAudio,
                    onDeleteAudio = onDeleteAudio,
                    modifier = Modifier.align(Alignment.End)
                )
                playback.error?.takeIf { playback.audioId == audio.id }?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

internal fun audiobookStatusDetail(
    audio: BookAudioEntity,
    playableSegmentFiles: Int,
    nowMillis: Long = System.currentTimeMillis(),
): String =
    when (audio.status) {
        BookAudioStatus.GENERATED -> generatedAudiobookStatusDetail(audio, playableSegmentFiles)
        BookAudioStatus.GENERATING -> generatingAudiobookStatusDetail(audio, nowMillis = nowMillis)
        BookAudioStatus.CANCELED -> canceledAudiobookStatusDetail(audio)
        BookAudioStatus.FAILED -> audio.error ?: "Generation stopped before audio was ready."
    }

private fun generatedAudiobookStatusDetail(
    audio: BookAudioEntity,
    playableSegmentFiles: Int,
): String =
    buildString {
        append(
            when {
                playableSegmentFiles >= audio.segmentCount -> "${audio.segmentCount} segments"
                playableSegmentFiles <= 0 -> "Audio files missing"
                else -> "$playableSegmentFiles playable of ${audio.segmentCount} segments"
            }
        )
        appendAudiobookDetailPart(audio.audiobookPerformanceLabel())
        appendAudiobookDetailPart(audio.estimatedDurationLabel())
        appendAudiobookDetailPart(audio.audiobookResumeLabel(prefix = "resume", playableSegmentFiles = playableSegmentFiles))
        appendAudiobookDetailPart(audio.fileSizeBytes.takeIf { it > 0 }?.compactBytes())
    }

private fun generatingAudiobookStatusDetail(
    audio: BookAudioEntity,
    nowMillis: Long,
): String {
    if (audio.segmentCount <= 0) return "Preparing segments"
    val progress = (audio.completedSegments.toFloat() / audio.segmentCount.toFloat()).coerceIn(0f, 1f)
    return buildString {
        appendAudiobookDetailPart(audio.audiobookGenerationProgressLabel())
        appendAudiobookDetailPart("${(progress * 100).roundToInt()}%")
        appendAudiobookDetailPart(audio.generationEtaLabel(nowMillis = nowMillis))
        appendAudiobookDetailPart(audio.audiobookPerformanceLabel())
        appendAudiobookDetailPart(audio.estimatedDurationLabel())
    }
}

private fun canceledAudiobookStatusDetail(audio: BookAudioEntity): String {
    if (audio.segmentCount <= 0) return "No segments were generated."
    return buildString {
        append(audio.completedSegments.coerceAtMost(audio.segmentCount))
        append(" of ")
        append(audio.segmentCount)
        append(" segments completed")
        appendAudiobookDetailPart(audio.audiobookPerformanceLabel())
        appendAudiobookDetailPart(audio.estimatedDurationLabel())
    }
}

@Composable
private fun AudiobookPlaybackActions(
    audio: BookAudioEntity,
    playableSegmentFiles: Int,
    playback: AudiobookPlaybackUiState,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onExportAudio: (BookAudioEntity) -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = playback.audioId == audio.id && playback.active
    val actions = generatedAudiobookActionState(
        active = active,
        playback = playback,
        audio = audio,
        playableSegmentFiles = playableSegmentFiles
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (active) {
            Text(
                playbackProgressLabel(playback),
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actions.showPlay) {
            TooltipIconButton(
                label = actions.playIconLabel,
                onClick = {
                    if (active && playback.playing) onPauseAudio(audio) else onPlayAudio(audio)
                },
                modifier = Modifier.size(40.dp),
                enabled = actions.canPlay
            ) {
                Icon(if (active && playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
            }
        }
        if (active) {
            TooltipIconButton(
                label = "Stop generated audio",
                onClick = onStopAudio,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
            }
        }
        if (actions.showExport) {
            TooltipIconButton(
                label = "${actions.exportLabel} generated audio",
                onClick = { onExportAudio(audio) },
                modifier = Modifier.size(40.dp),
                enabled = actions.canExport
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
            }
        }
        TooltipIconButton(
            label = "Delete generated audio",
            onClick = { onDeleteAudio(audio) },
            modifier = Modifier.size(40.dp),
            enabled = audio.canDeleteFromAudiobooksScreen()
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
        }
    }
}

internal fun playbackProgressLabel(playback: AudiobookPlaybackUiState): String =
    if (playback.segmentCount > 0) {
        joinPlaybackProgressParts(
            state = audiobookPlaybackStateLabel(playback),
            chapter = playback.chapterTitle,
            time = playback.segmentTimeLabel()
        )
    } else {
        "Ready"
    }

private fun joinPlaybackProgressParts(
    state: String,
    chapter: String?,
    time: String?,
): String {
    val trimmedChapter = chapter?.trim()
    val trimmedTime = time?.trim()
    if (trimmedChapter.isNullOrEmpty() && trimmedTime.isNullOrEmpty()) return state
    return buildString {
        append(state)
        if (!trimmedChapter.isNullOrEmpty()) {
            append(" • ")
            append(trimmedChapter)
        }
        if (!trimmedTime.isNullOrEmpty()) {
            append(" • ")
            append(trimmedTime)
        }
    }
}

@Composable
private fun GeneratedAudiobookList(
    items: List<BookAudiobookAudioUiItem>,
    selectedAudioId: Long?,
    playback: AudiobookPlaybackUiState,
    onExportAudio: (BookAudioEntity) -> Unit,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
    onShowChapters: (BookAudiobookAudioUiItem) -> Unit,
) {
    val listIdentity = generatedAudiobookItemsIdentityKey(items)
    var expanded by remember(listIdentity, selectedAudioId) { mutableStateOf(false) }
    val visibleAudio = remember(items, selectedAudioId, expanded) {
        visibleGeneratedAudiobookItems(items, selectedAudioId, expanded)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Generated audio", style = MaterialTheme.typography.labelLarge)
        visibleAudio.forEach { item ->
            GeneratedAudiobookRow(
                audio = item,
                selected = item.audio.id == selectedAudioId,
                playback = playback.forAudiobooksScreenRow(item.audio.id),
                onExportAudio = onExportAudio,
                onPlayAudio = onPlayAudio,
                onPauseAudio = onPauseAudio,
                onStopAudio = onStopAudio,
                onDeleteAudio = onDeleteAudio,
                onShowChapters = onShowChapters
            )
        }
        if (items.size > COLLAPSED_GENERATED_AUDIO_COUNT) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show fewer versions" else "Show all ${items.size} versions")
            }
        }
    }
}

@Composable
private fun GeneratedAudiobookRow(
    audio: BookAudiobookAudioUiItem,
    selected: Boolean,
    playback: AudiobookPlaybackUiState,
    onExportAudio: (BookAudioEntity) -> Unit,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
    onShowChapters: (BookAudiobookAudioUiItem) -> Unit,
) {
    val audioEntity = audio.audio
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        audioEntity.displayProfileLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        generatedAudiobookRowDetail(audio),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FlowRow(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AudiobookPlaybackActions(
                    audio = audioEntity,
                    playableSegmentFiles = audio.playableSegmentFiles,
                    playback = playback,
                    onPlayAudio = onPlayAudio,
                    onPauseAudio = onPauseAudio,
                    onStopAudio = onStopAudio,
                    onExportAudio = onExportAudio,
                    onDeleteAudio = onDeleteAudio
                )
                if (audio.chapters.size > 1) {
                    TooltipIconButton(
                        label = "Choose chapter",
                        onClick = { onShowChapters(audio) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    }
                }
            }
        }
    }
}

internal fun generatedAudiobookRowDetail(
    audio: BookAudiobookAudioUiItem,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val audioEntity = audio.audio
    return buildString {
        append(audiobookStatusDetail(audioEntity, playableSegmentFiles = audio.playableSegmentFiles))
        if (audio.chapters.isNotEmpty()) {
            appendAudiobookDetailPart(generatedAudiobookChapterCountLabel(audio.chapters.size))
        }
        audioEntity.generatedAt?.let { generatedAt ->
            appendAudiobookDetailPart("generated ${relativeAgeLabel(generatedAt, nowMillis)}")
        }
    }
}

internal fun generatedAudiobookDeleteDetail(audio: BookAudioEntity): String =
    buildString {
        append(audio.displayProfileLabel())
        appendAudiobookDetailPart(audio.estimatedDurationLabel())
        appendAudiobookDetailPart(audio.fileSizeBytes.takeIf { it > 0 }?.compactBytes())
    }

internal fun audiobookGenerationHeaderDetail(
    book: BookEntity,
    scan: AudiobookScanUiState?,
    selectedSpec: NeuralTtsModelSpec,
    selectedSpeaker: NeuralTtsSpeakerSpec,
    tone: NeuralTtsTone,
    pace: NeuralTtsPace,
): String =
    buildString {
        append("Full book")
        appendAudiobookDetailPart(bookLengthLabel(book))
        appendAudiobookDetailPart(scan?.takeIf { it.hasText }?.let { audiobookDurationLabel(it.estimatedAudioMillis) })
        appendAudiobookDetailPart(selectedSpec.displayName)
        appendAudiobookDetailPart(selectedSpeaker.label)
        appendAudiobookDetailPart(tone.label)
        appendAudiobookDetailPart(pace.label)
    }

private fun StringBuilder.appendAudiobookDetailPart(part: String?) {
    if (part.isNullOrBlank()) return
    if (isNotEmpty()) append(" • ")
    append(part)
}

@Composable
private fun <T> AudiobookChipGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun AudiobookSelectedVoiceModelRow(
    spec: NeuralTtsModelSpec,
    status: NeuralTtsModelStatus,
    statusText: String,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onPreview: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Local voice model", style = MaterialTheme.typography.labelLarge)
                Text(
                    spec.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == NeuralTtsModelStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (status) {
                NeuralTtsModelStatus.DOWNLOADING,
                NeuralTtsModelStatus.EXTRACTING -> {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onCancel) { Text("Stop") }
                }
                NeuralTtsModelStatus.INSTALLED -> TextButton(onClick = onPreview) { Text("Preview") }
                NeuralTtsModelStatus.NOT_DOWNLOADED -> TextButton(onClick = onDownload) { Text("Download") }
                NeuralTtsModelStatus.FAILED -> TextButton(onClick = onDownload) { Text("Retry") }
            }
        }
    }
}

internal fun audiobookGenerationBlockedReason(
    status: NeuralTtsModelStatus,
    generatingSelectedAudio: Boolean,
    modelName: String,
    hardwareReadiness: AudiobookGenerationHardwareReadiness = AudiobookGenerationHardwareReadiness(ready = true),
): String? =
    when {
        generatingSelectedAudio -> "This voice is already generating audio. Stop it before starting another scope."
        status == NeuralTtsModelStatus.INSTALLED -> null
        status == NeuralTtsModelStatus.DOWNLOADING -> "$modelName is still downloading. Generation will unlock when the download finishes."
        status == NeuralTtsModelStatus.EXTRACTING -> "$modelName is installing. Generation will unlock when setup finishes."
        status == NeuralTtsModelStatus.FAILED -> "$modelName did not install cleanly. Retry the download before generating audio."
        status == NeuralTtsModelStatus.NOT_DOWNLOADED -> "Download $modelName before generating audiobook audio."
        else -> null
    } ?: hardwareReadiness.reason.takeIf { !hardwareReadiness.ready }

internal fun selectedAudiobookStatusItem(
    items: List<BookAudiobookAudioUiItem>,
    modelId: String,
    speakerId: Int,
    speed: Float,
    tone: String,
): BookAudiobookAudioUiItem? {
    val matching = items.filter { item -> item.audio.matchesAudiobookProfile(modelId, speakerId, speed, tone) }
    return matching.firstOrNull { it.audio.status == BookAudioStatus.GENERATING }
        ?: matching.firstOrNull { it.audio.scope == AudiobookGenerationScope.FULL_BOOK.key }
        ?: matching.maxByOrNull { it.audio.generatedAt ?: it.audio.updatedAt }
}

internal fun BookAudioEntity.matchesAudiobookProfile(
    modelId: String,
    speakerId: Int,
    speed: Float,
    tone: String,
): Boolean =
    this.modelId == modelId &&
        this.speakerId == speakerId &&
        abs(this.speed - speed) < AUDIOBOOK_PROFILE_SPEED_EPSILON &&
        this.tone == tone
