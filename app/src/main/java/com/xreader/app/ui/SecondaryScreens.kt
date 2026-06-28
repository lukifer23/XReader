@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.annotations.annotationTagsLabel
import com.xreader.app.annotations.normalizeAnnotationNote
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.BookAudioWithBook
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.analytics.ActivityBucketAnalytics
import com.xreader.app.analytics.ActivityBucketGranularity
import com.xreader.app.analytics.AnalyticsRange
import com.xreader.app.analytics.AnalyticsSummary
import com.xreader.app.analytics.BookAnalytics
import com.xreader.app.analytics.GroupAnalytics
import com.xreader.app.data.NeuralTtsModelEntity
import com.xreader.app.data.NeuralTtsModelStatus
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.MAX_READER_DIM_AMOUNT
import com.xreader.app.settings.MAX_READER_FONT_WEIGHT
import com.xreader.app.settings.MIN_READER_FONT_WEIGHT
import com.xreader.app.settings.NeuralTtsGender
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import com.xreader.app.settings.ReadAloudSleepTimer
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderHighlightColor
import com.xreader.app.settings.ReaderOrientation
import com.xreader.app.settings.ReaderPageDirection
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderPdfScrollAxis
import com.xreader.app.settings.ReaderSpacingPreset
import com.xreader.app.settings.ReaderTapZonePreset
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.settings.spacingPresetOrNull
import com.xreader.app.tts.ReadAloudEngineOption
import com.xreader.app.tts.ReadAloudVoiceOption
import com.xreader.app.tts.NeuralTtsModelCatalog
import com.xreader.app.tts.AudiobookPlaybackUiState
import com.xreader.app.tts.EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE
import com.xreader.app.tts.TtsAccelerationRuntime
import com.xreader.app.tts.audiobookGenerationProgressLabel
import com.xreader.app.tts.canDeleteGeneratedAudiobook
import com.xreader.app.tts.generationEtaLabel
import com.xreader.app.tts.GeneratedAudiobookChapter
import com.xreader.app.tts.playableSegmentCount
import com.xreader.app.tts.playableSegmentFiles
import com.xreader.app.tts.nextChapterStart
import com.xreader.app.tts.previousChapterStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AnalyticsRoute(
    container: AppContainer,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportAnalytics(uri)
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportAnalyticsCsv(uri)
    }
    var exportMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text("Reading stats") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { exportMenuOpen = true },
                            enabled = !state.exporting
                        ) {
                            if (state.exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Export reading stats")
                            }
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("CSV") },
                                onClick = {
                                    exportMenuOpen = false
                                    exportCsvLauncher.launch("xreader-reading-stats.csv")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("JSON") },
                                onClick = {
                                    exportMenuOpen = false
                                    exportLauncher.launch("xreader-reading-stats.json")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val summary = state.summary
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (summary != null) {
                item {
                    AnalyticsRangeSelector(
                        selectedRange = state.selectedRange,
                        onRangeSelected = viewModel::setRange
                    )
                }
                item { AnalyticsSnapshotCard(summary) }
                item {
                    ReadingActivityChart(
                        buckets = summary.activityBuckets,
                        range = summary.range,
                        bestStreakDays = summary.bestStreakDays
                    )
                }
                if (summary.byAuthor.isNotEmpty()) {
                    item { AnalyticsSectionTitle("Authors") }
                    items(summary.byAuthor, key = { it.label }) { row ->
                        GroupAnalyticsRow(row)
                    }
                }
                if (summary.byGenre.isNotEmpty()) {
                    item { AnalyticsSectionTitle("Genres") }
                    items(summary.byGenre, key = { it.label }) { row ->
                        GroupAnalyticsRow(row)
                    }
                }
                if (summary.byReadability.isNotEmpty()) {
                    item { AnalyticsSectionTitle("Reading level") }
                    items(summary.byReadability, key = { it.label }) { row ->
                        GroupAnalyticsRow(row)
                    }
                }
                if (summary.byBook.isNotEmpty()) {
                    item { AnalyticsSectionTitle("Books") }
                }
                items(summary.byBook, key = { it.book.id }) { row ->
                    BookAnalyticsRow(row)
                }
                if (summary.sessions == 0) {
                    item {
                        Text("No reading sessions in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsRangeSelector(
    selectedRange: AnalyticsRange,
    onRangeSelected: (AnalyticsRange) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label) }
            )
        }
    }
}

@Composable
private fun AnalyticsSnapshotCard(summary: AnalyticsSummary) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnalyticsMetric(
                    label = "Time",
                    value = formatDuration(summary.activeMillis),
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMetric(
                    label = "Words",
                    value = analyticsCountLabel(summary.wordsRead),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnalyticsMetric(
                    label = "Pace",
                    value = analyticsPaceValue(summary.averageWpm),
                    supporting = if (summary.averageWpm > 0) "WPM" else "Pending",
                    modifier = Modifier.weight(1f)
                )
                AnalyticsMetric(
                    label = "Streak",
                    value = "${summary.currentStreakDays}d",
                    supporting = "Best ${summary.bestStreakDays}d",
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${analyticsCountLabel(summary.sessions)} ${if (summary.sessions == 1) "session" else "sessions"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${analyticsCountLabel(summary.finishedBooks)} of ${analyticsCountLabel(summary.totalBooks)} finished",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    analyticsPaceDetail(summary.paceSampleSessions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AnalyticsMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        supporting?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AnalyticsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ReadingActivityChart(
    buckets: List<ActivityBucketAnalytics>,
    range: AnalyticsRange,
    bestStreakDays: Int,
) {
    val maxActiveMillis = buckets.maxOfOrNull { it.activeMillis }?.coerceAtLeast(1L) ?: 1L
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(activityTitle(range, buckets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Best streak ${bestStreakDays}d",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEachIndexed { index, bucket ->
                    ActivityBucketBar(
                        bucket = bucket,
                        maxActiveMillis = maxActiveMillis,
                        showLabel = shouldShowActivityLabel(index, buckets),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityBucketBar(
    bucket: ActivityBucketAnalytics,
    maxActiveMillis: Long,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeFraction = (bucket.activeMillis.toFloat() / maxActiveMillis.toFloat()).coerceIn(0f, 1f)
    val visibleFraction = if (bucket.activeMillis > 0L) activeFraction.coerceAtLeast(0.08f) else 0.03f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(visibleFraction),
                shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                color = if (bucket.activeMillis > 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {}
        }
        Text(
            if (showLabel) formatActivityBucket(bucket) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun GroupAnalyticsRow(row: GroupAnalytics) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                analyticsRowDetail(row.sessions, row.activeMillis, row.wordsRead, row.averageWpm),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BookAnalyticsRow(row: BookAnalytics) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                analyticsRowDetail(row.sessions, row.activeMillis, row.wordsRead, row.averageWpm),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            readabilityDetailLabel(row.book)?.let { readability ->
                Text(
                    readability,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Immutable
data class AudiobooksUiState(
    val rows: List<GeneratedAudiobookUiItem> = emptyList(),
    val message: String? = null,
)

@Immutable
data class GeneratedAudiobookUiItem(
    val book: BookEntity,
    val audio: BookAudioEntity,
    val chapters: List<GeneratedAudiobookChapter> = emptyList(),
    val playableSegmentFiles: Int = 0,
    val searchText: String = generatedAudiobookSearchText(book, audio),
)

private data class AudiobookRowsSortKey(
    val activeAudioId: Long?,
    val active: Boolean,
)

internal fun List<GeneratedAudiobookUiItem>.sortedForAudiobooksScreen(
    playback: AudiobookPlaybackUiState,
): List<GeneratedAudiobookUiItem> =
    sortedForAudiobooksScreen(playback.toAudiobookRowsSortKey())

private fun List<GeneratedAudiobookUiItem>.sortedForAudiobooksScreen(
    playback: AudiobookRowsSortKey,
): List<GeneratedAudiobookUiItem> =
    sortedWith(
        compareBy<GeneratedAudiobookUiItem> { it.audiobookScreenPriority(playback) }
            .thenByDescending { it.audio.audiobookScreenSortMillis() }
            .thenBy { it.book.sortTitle }
            .thenBy { it.audio.id }
    )

private fun AudiobookPlaybackUiState.toAudiobookRowsSortKey(): AudiobookRowsSortKey =
    AudiobookRowsSortKey(activeAudioId = audioId, active = active)

private val AUDIOBOOK_SEARCH_SPLIT_REGEX = Regex("\\s+")

internal fun List<GeneratedAudiobookUiItem>.filteredForAudiobooksScreen(
    query: String,
): List<GeneratedAudiobookUiItem> {
    val terms = query
        .lowercase(Locale.US)
        .split(AUDIOBOOK_SEARCH_SPLIT_REGEX)
        .filter { it.isNotBlank() }
    if (terms.isEmpty()) return this
    return filter { item ->
        terms.all { term -> term in item.searchText }
    }
}

private fun generatedAudiobookSearchText(
    book: BookEntity,
    audio: BookAudioEntity,
): String =
    buildString {
        append(book.title)
        append(' ')
        append(book.author)
        append(' ')
        append(audio.scopeLabel)
        append(' ')
        append(audio.modelDisplayName)
        append(' ')
        append(audio.audiobookDisplayProfileLabel(includeScope = false))
        append(' ')
        append(audio.status.name)
        append(' ')
        append(audio.tone)
    }.lowercase(Locale.US)

private fun GeneratedAudiobookUiItem.audiobookScreenPriority(playback: AudiobookRowsSortKey): Int =
    when {
        playback.activeAudioId == audio.id && playback.active -> 0
        audio.status == BookAudioStatus.GENERATING -> 1
        playableSegmentFiles > 0 -> 2
        audio.status == BookAudioStatus.FAILED -> 3
        audio.status == BookAudioStatus.CANCELED -> 4
        else -> 5
    }

private fun BookAudioEntity.audiobookScreenSortMillis(): Long =
    when (status) {
        BookAudioStatus.GENERATING -> generationStartedAt ?: updatedAt
        BookAudioStatus.GENERATED -> generatedAt ?: updatedAt
        BookAudioStatus.CANCELED,
        BookAudioStatus.FAILED -> updatedAt
    }

internal fun List<BookAudioWithBook>.toGeneratedAudiobookUiItems(
    audioItems: List<BookAudiobookAudioUiItem>,
): List<GeneratedAudiobookUiItem> {
    if (size == audioItems.size) {
        return zip(audioItems).mapNotNull { (row, audioItem) ->
            row.toGeneratedAudiobookUiItem(audioItem)
        }
    }

    val audioItemsById = audioItems.associateBy { it.audio.id }
    return mapNotNull { row ->
        row.toGeneratedAudiobookUiItem(audioItemsById[row.audio.id] ?: return@mapNotNull null)
    }
}

private fun BookAudioWithBook.toGeneratedAudiobookUiItem(
    audioItem: BookAudiobookAudioUiItem,
): GeneratedAudiobookUiItem? {
    if (!audioItem.shouldShowInGlobalAudiobooksScreen()) return null
    return GeneratedAudiobookUiItem(
        book = book,
        audio = audioItem.audio,
        chapters = audioItem.chapters,
        playableSegmentFiles = audioItem.playableSegmentFiles,
        searchText = generatedAudiobookSearchText(book, audioItem.audio)
    )
}

internal fun List<BookAudioWithBook>.audiobookRowsInvalidationKey(): List<AudiobookRowInvalidationKey> =
    map { row ->
        AudiobookRowInvalidationKey(
            audioId = row.audio.id,
            book = row.book.audiobookBookInvalidationKey(),
            audio = row.audio.audiobookUiInvalidationKey()
        )
    }

internal fun sameAudiobookScreenRows(
    previous: List<BookAudioWithBook>,
    next: List<BookAudioWithBook>,
): Boolean {
    if (previous.size != next.size) return false
    return previous.indices.all { index ->
        previous[index].sameAudiobookScreenRow(next[index])
    }
}

private fun BookAudioWithBook.sameAudiobookScreenRow(other: BookAudioWithBook): Boolean =
    audio.id == other.audio.id &&
        book.sameAudiobookScreenBook(other.book) &&
        sameAudiobookUiInvalidationRow(audio, other.audio)

private fun BookEntity.sameAudiobookScreenBook(other: BookEntity): Boolean =
    id == other.id &&
        title == other.title &&
        author == other.author &&
        sortTitle == other.sortTitle

internal data class AudiobookRowInvalidationKey(
    val audioId: Long,
    val book: AudiobookBookInvalidationKey,
    val audio: AudiobookUiInvalidationKey,
)

internal data class AudiobookBookInvalidationKey(
    val id: Long,
    val title: String,
    val author: String,
    val sortTitle: String,
)

private fun BookEntity.audiobookBookInvalidationKey(): AudiobookBookInvalidationKey =
    AudiobookBookInvalidationKey(
        id = id,
        title = title,
        author = author,
        sortTitle = sortTitle
    )

class AudiobooksViewModel(private val container: AppContainer) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val playback = container.generatedAudiobookPlayback.state
    val playbackState: StateFlow<AudiobookPlaybackUiState> =
        playback
            .map { it.forAudiobooksScreen() }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE)
    private val audiobookUiItemCache = BookAudiobookAudioUiItemCache()
    private val playbackSortKey =
        playback
            .map { it.toAudiobookRowsSortKey() }
            .distinctUntilChanged()

    private val audiobookRows: StateFlow<List<GeneratedAudiobookUiItem>> =
        container.neuralTtsRepository.observeVisibleAudiobookScreenRows()
            .distinctUntilChanged(::sameAudiobookScreenRows)
            .map { rows ->
                withContext(Dispatchers.IO) {
                    val audioItems = audiobookUiItemCache.toUiItemsForRows(rows)
                    rows.toGeneratedAudiobookUiItems(audioItems)
                }
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sortedAudiobookRows: StateFlow<List<GeneratedAudiobookUiItem>> =
        combine(audiobookRows, playbackSortKey) { rows, sortKey ->
            withContext(Dispatchers.Default) {
                rows.sortedForAudiobooksScreen(sortKey)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<AudiobooksUiState> =
        combine(
            sortedAudiobookRows,
            message
        ) { sortedRows, currentMessage ->
            AudiobooksUiState(
                rows = sortedRows,
                message = currentMessage
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudiobooksUiState())

    fun clearMessage() {
        message.value = null
    }

    fun play(item: GeneratedAudiobookUiItem) {
        if (item.playableSegmentFiles <= 0) {
            message.value = "Generate at least one segment before playing."
            return
        }
        container.generatedAudiobookPlayback.play(item.book.title, item.audio)
    }

    fun playFromSegment(item: GeneratedAudiobookUiItem, segmentIndex: Int) {
        if (item.playableSegmentFiles <= 0) {
            message.value = "Generate at least one segment before playing."
            return
        }
        container.generatedAudiobookPlayback.playFromSegment(item.book.title, item.audio, segmentIndex)
    }

    fun pause(item: GeneratedAudiobookUiItem) {
        if (playback.value.audioId == item.audio.id) container.generatedAudiobookPlayback.pause()
    }

    fun stop() {
        container.generatedAudiobookPlayback.stop()
    }

    fun cancelGeneration(item: GeneratedAudiobookUiItem) {
        container.cancelAudiobookGeneration(item.book.id, item.audio)
        message.value = "Stopping audiobook generation for ${item.book.title}."
    }

    fun skipPrevious() {
        container.generatedAudiobookPlayback.skipPrevious()
    }

    fun skipNext() {
        container.generatedAudiobookPlayback.skipNext()
    }

    fun skipPreviousChapter() {
        container.generatedAudiobookPlayback.skipPreviousChapter()
    }

    fun skipNextChapter() {
        container.generatedAudiobookPlayback.skipNextChapter()
    }

    fun export(audioId: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { container.neuralTtsRepository.exportBookAudio(audioId, uri) }
                .onSuccess { audio -> message.value = audiobookExportSuccessMessage(audio) }
                .onFailure { error -> message.value = error.message ?: "Audiobook export failed." }
        }
    }

    fun delete(item: GeneratedAudiobookUiItem) {
        viewModelScope.launch {
            if (playback.value.audioId == item.audio.id) container.generatedAudiobookPlayback.stop()
            runCatching { container.neuralTtsRepository.deleteBookAudio(item.audio.id) }
                .onSuccess { message.value = "Deleted generated audio for ${item.book.title}." }
                .onFailure { error -> message.value = error.message ?: "Audiobook delete failed." }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AudiobooksViewModel(container) as T
            }
    }
}

@Composable
internal fun AudiobooksRoute(
    container: AppContainer,
    openReaderAt: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: AudiobooksViewModel = viewModel(factory = AudiobooksViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var exportTarget by remember { mutableStateOf<BookAudioEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<GeneratedAudiobookUiItem?>(null) }
    var chapterPicker by remember { mutableStateOf<GeneratedAudiobookUiItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val visibleRows = remember(state.rows, searchQuery) {
        state.rows.filteredForAudiobooksScreen(searchQuery)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) viewModel.export(target.id, uri)
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(title = { Text("Audiobooks") })
        }
    ) { padding ->
        if (state.rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("No generated audiobooks", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Generate a sample, chapter, or full book from any book's Audiobook action. Completed and partial audio will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AudiobookSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        resultCount = visibleRows.size,
                        totalCount = state.rows.size
                    )
                }
                if (visibleRows.isEmpty()) {
                    item {
                        Text(
                            "No generated audiobooks match this search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(visibleRows, key = { "${it.book.id}:${it.audio.id}" }) { item ->
                    val rowPlayback = playback.forAudiobooksScreenRow(item.audio.id)
                    GeneratedAudiobookScreenRow(
                        item = item,
                        chapters = item.chapters,
                        playback = rowPlayback,
                        onOpenBook = { openReaderAt(item.book.id) },
                        onPlay = { viewModel.play(item) },
                        onPause = { viewModel.pause(item) },
                        onStop = viewModel::stop,
                        onCancelGeneration = { viewModel.cancelGeneration(item) },
                        onSkipPrevious = viewModel::skipPrevious,
                        onSkipNext = viewModel::skipNext,
                        onSkipPreviousChapter = viewModel::skipPreviousChapter,
                        onSkipNextChapter = viewModel::skipNextChapter,
                        onShowChapters = { chapterPicker = item },
                        onExport = {
                            exportTarget = item.audio
                            exportLauncher.launch("${item.book.title.fileSafeName()} ${item.audio.audiobookFileSafeProfileName()} audiobook.zip")
                        },
                        onDelete = { deleteCandidate = item }
                    )
                }
            }
        }
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete generated audio?") },
            text = { Text("${item.book.title} • ${item.audio.audiobookDisplayProfileLabel(includeScope = false)}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        viewModel.delete(item)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
    chapterPicker?.let { item ->
        val pickerPlayback = playback.forAudiobooksScreenRow(item.audio.id)
        AudiobookChapterPickerDialog(
            title = item.book.title,
            chapters = item.chapters,
            playback = pickerPlayback.takeIf { it.audioId == item.audio.id },
            onDismiss = { chapterPicker = null },
            onChapter = { chapter ->
                chapterPicker = null
                viewModel.playFromSegment(item, chapter.firstSegmentIndex)
            }
        )
    }
}

@Composable
private fun AudiobookSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    resultCount: Int,
    totalCount: Int,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search audiobooks") },
        supportingText = {
            if (query.isNotBlank()) {
                Text("$resultCount of $totalCount")
            }
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        }
    )
}

@Composable
private fun GeneratedAudiobookScreenRow(
    item: GeneratedAudiobookUiItem,
    chapters: List<GeneratedAudiobookChapter>,
    playback: AudiobookPlaybackUiState,
    onOpenBook: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onCancelGeneration: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPreviousChapter: () -> Unit,
    onSkipNextChapter: () -> Unit,
    onShowChapters: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val audio = item.audio
    val active = playback.audioId == audio.id && playback.active
    val actions = generatedAudiobookActionState(
        active = active,
        playback = playback,
        audio = audio,
        playableSegmentFiles = item.playableSegmentFiles
    )
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onOpenBook) { Text("Open") }
            }
            Text(
                audio.audiobookStatusDetail(
                    activePlayback = active,
                    playback = playback,
                    playableSegmentFiles = item.playableSegmentFiles
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AudiobookInfoPill(audio.scopeLabel)
                AudiobookInfoPill(audio.audiobookDisplayProfileLabel(includeScope = false))
                if (chapters.isNotEmpty()) {
                    AudiobookInfoPill(generatedAudiobookChapterCountLabel(chapters.size))
                }
                audio.estimatedDurationLabel()?.let { AudiobookInfoPill(it) }
                audio.audiobookResumeLabel(playableSegmentFiles = item.playableSegmentFiles)?.let { AudiobookInfoPill(it) }
                audio.generatedAt?.let {
                    AudiobookInfoPill("Generated ${shortDateLabel(it)}")
                }
            }
            if (active) {
                LinearProgressIndicator(
                    progress = { playback.segmentProgress() },
                    modifier = Modifier.fillMaxWidth()
                )
                playback.segmentTimeLabel()?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                playback.chapterLabel()?.let { chapter ->
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = if (active && playback.playing) onPause else onPlay,
                    enabled = actions.canPlay
                ) {
                    Text(actions.playLabel)
                }
                if (active) {
                    TextButton(onClick = onSkipPrevious, enabled = playback.segmentIndex > 0) { Text("Previous") }
                    TextButton(onClick = onSkipNext, enabled = playback.segmentIndex < playback.segmentCount - 1) { Text("Next") }
                    if (playback.chapterCount > 1) {
                        TextButton(onClick = onSkipPreviousChapter, enabled = playback.canSkipPreviousChapter(chapters)) { Text("Prev chapter") }
                        TextButton(onClick = onSkipNextChapter, enabled = playback.canSkipNextChapter(chapters)) { Text("Next chapter") }
                    }
                    TextButton(onClick = onStop) { Text("Stop") }
                }
                if (chapters.size > 1) {
                    TextButton(onClick = onShowChapters) { Text("Chapters") }
                }
                if (audio.canCancelGenerationFromAudiobooksScreen()) {
                    TextButton(onClick = onCancelGeneration) { Text("Stop generation") }
                }
                TextButton(onClick = onExport, enabled = actions.canExport) {
                    Text(actions.exportLabel)
                }
                if (audio.canDeleteFromAudiobooksScreen()) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun AudiobookInfoPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun BookAudioEntity.audiobookStatusDetail(
    activePlayback: Boolean,
    playback: AudiobookPlaybackUiState,
    playableSegmentFiles: Int = playableSegmentCount(),
): String {
    val progress = when {
        segmentCount <= 0 -> null
        status == BookAudioStatus.GENERATING -> audiobookGenerationProgressLabel()
        status == BookAudioStatus.GENERATED && playableSegmentFiles >= segmentCount -> "$segmentCount segments"
        status == BookAudioStatus.GENERATED && playableSegmentFiles <= 0 -> "Audio files missing"
        status == BookAudioStatus.GENERATED -> "$playableSegmentFiles playable of $segmentCount segments"
        playableSegmentFiles > 0 -> "$playableSegmentFiles playable of $segmentCount segments"
        else -> null
    }
    val statusLabel = when (status) {
        BookAudioStatus.GENERATED -> "Ready"
        BookAudioStatus.GENERATING -> listOfNotNull(
            "Generating",
            generationEtaLabel()
        ).joinToString(" • ")
        BookAudioStatus.CANCELED -> "Stopped"
        BookAudioStatus.FAILED -> error ?: "Failed"
    }
    val playbackLabel = if (activePlayback && playback.segmentCount > 0) audiobookPlaybackStateLabel(playback) else null
    val performanceLabel = if (status == BookAudioStatus.GENERATING) null else audiobookPerformanceLabel()
    return listOfNotNull(statusLabel, progress, performanceLabel, playbackLabel).joinToString(" • ")
}

internal fun BookAudioEntity.audiobookPerformanceLabel(): String? {
    val provider = generationProvider?.takeIf { it.isNotBlank() }?.generationProviderLabel()
    val factor = generationAudioTimeFactorLabel(generationAudioMillis, generationComputeMillis)
    return listOfNotNull(provider, factor).takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

internal fun generationAudioTimeFactorLabel(audioMillis: Long, computeMillis: Long): String? {
    if (audioMillis <= 0L || computeMillis <= 0L) return null
    val factor = computeMillis.toDouble() / audioMillis.toDouble()
    if (!factor.isFinite()) return null
    return when {
        factor < 10.0 -> String.format(Locale.US, "%.1fx audio time", factor)
        else -> "${factor.toInt()}x audio time"
    }
}

private fun String.generationProviderLabel(): String =
    when (TtsAccelerationRuntime.providerDisplayKey(this)) {
        "webgpu" -> "WebGPU"
        "qnn-gpu-hybrid" -> "QNN GPU hybrid"
        "qnn-htp-hybrid" -> "QNN NPU hybrid"
        "qnn-gpu" -> "QNN GPU"
        "qnn-htp" -> "QNN NPU"
        "qnn" -> "NPU"
        "nnapi" -> "NNAPI"
        "xnnpack" -> "XNNPACK"
        "cpu" -> "CPU"
        else -> uppercase(Locale.US)
    }

internal data class GeneratedAudiobookActionState(
    val playLabel: String,
    val playIconLabel: String,
    val exportLabel: String,
    val canPlay: Boolean,
    val canExport: Boolean,
)

internal fun generatedAudiobookActionState(
    active: Boolean,
    playback: AudiobookPlaybackUiState,
    audio: BookAudioEntity,
    playableSegmentFiles: Int? = null,
): GeneratedAudiobookActionState {
    val playableFiles = playableSegmentFiles ?: audio.playableSegmentCount()
    val playLabel = when {
        active && playback.preparing -> "Preparing"
        active && playback.playing -> "Pause"
        active && playback.paused -> "Resume"
        audio.hasAudiobookResumePosition(playableFiles) -> "Resume"
        audio.hasPartialGeneratedAudio(playableFiles) -> "Play partial"
        else -> "Play"
    }
    val playIconLabel = when (playLabel) {
        "Preparing" -> "Preparing generated audio"
        "Pause" -> "Pause generated audio"
        "Resume" -> "Resume generated audio"
        "Play partial" -> "Play partial generated audio"
        else -> "Play generated audio"
    }
    val exportLabel = if (audio.hasPartialGeneratedAudio(playableFiles)) "Save partial" else "Save"
    return GeneratedAudiobookActionState(
        playLabel = playLabel,
        playIconLabel = playIconLabel,
        exportLabel = exportLabel,
        canPlay = playableFiles > 0 && !(active && playback.preparing),
        canExport = playableFiles > 0
    )
}

internal fun audiobookPlaybackActionLabel(
    active: Boolean,
    playback: AudiobookPlaybackUiState,
    audio: BookAudioEntity? = null,
    playableSegmentFiles: Int? = null,
): String =
    if (audio == null) {
        when {
            active && playback.preparing -> "Preparing"
            active && playback.playing -> "Pause"
            active && playback.paused -> "Resume"
            else -> "Play"
        }
    } else {
        generatedAudiobookActionState(active, playback, audio, playableSegmentFiles).playLabel
    }

internal fun audiobookPlaybackIconLabel(
    active: Boolean,
    playback: AudiobookPlaybackUiState,
    audio: BookAudioEntity,
    playableSegmentFiles: Int? = null,
): String =
    generatedAudiobookActionState(active, playback, audio, playableSegmentFiles).playIconLabel

internal fun audiobookExportActionLabel(audio: BookAudioEntity, playableSegmentFiles: Int? = null): String =
    generatedAudiobookActionState(
        active = false,
        playback = EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE,
        audio = audio,
        playableSegmentFiles = playableSegmentFiles
    ).exportLabel

internal fun audiobookExportSuccessMessage(audio: BookAudioEntity, playableSegmentFiles: Int? = null): String =
    if (audio.hasPartialGeneratedAudio(playableSegmentFiles)) {
        "Saved partial generated audiobook audio."
    } else {
        "Saved generated audiobook audio."
    }

private fun BookAudioEntity.hasPartialGeneratedAudio(playableSegmentFiles: Int? = null): Boolean =
    status != BookAudioStatus.GENERATED && (playableSegmentFiles ?: playableSegmentCount()) > 0

internal fun canPlayGeneratedAudiobookAction(
    active: Boolean,
    playback: AudiobookPlaybackUiState,
    audio: BookAudioEntity,
): Boolean =
    generatedAudiobookActionState(
        active = active,
        playback = playback,
        audio = audio
    ).canPlay

internal fun canPlayGeneratedAudiobookAction(
    active: Boolean,
    playback: AudiobookPlaybackUiState,
    playableSegmentFiles: Int,
): Boolean =
    playableSegmentFiles > 0 && !(active && playback.preparing)

internal fun canExportGeneratedAudiobookAction(audio: BookAudioEntity): Boolean =
    generatedAudiobookActionState(
        active = false,
        playback = EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE,
        audio = audio
    ).canExport

internal fun canExportGeneratedAudiobookAction(playableSegmentFiles: Int): Boolean =
    playableSegmentFiles > 0

internal fun audiobookPlaybackStateLabel(playback: AudiobookPlaybackUiState): String =
    when {
        playback.preparing -> "preparing ${(playback.segmentIndex + 1).coerceAtMost(playback.segmentCount.coerceAtLeast(1))} / ${playback.segmentCount.coerceAtLeast(1)}"
        playback.playing -> "playing ${(playback.segmentIndex + 1).coerceAtMost(playback.segmentCount)} / ${playback.segmentCount}"
        else -> "ready ${(playback.segmentIndex + 1).coerceAtMost(playback.segmentCount)} / ${playback.segmentCount}"
    }

internal fun BookAudioEntity.audiobookDisplayProfileLabel(includeScope: Boolean = true): String =
    listOf(
        scopeLabel.takeIf { includeScope }?.takeUnless { it.equals("Full book", ignoreCase = true) },
        modelDisplayName,
        "Speaker ${speakerId + 1}".takeIf { speakerId > 0 },
        tone.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) },
        "%.2fx".format(Locale.US, speed)
    ).filterNotNull().joinToString(" ")

internal fun BookAudioEntity.audiobookResumeLabel(
    prefix: String = "Resume",
    playableSegmentFiles: Int? = null,
): String? {
    val playableCount = playableSegmentFiles ?: segmentCount
    if (!hasAudiobookResumePosition(playableCount)) return null
    val segment = playbackSegmentIndex.coerceIn(0, playableCount - 1)
    return "$prefix ${segment + 1} / $playableCount"
}

internal fun BookAudioEntity.hasAudiobookResumePosition(playableSegmentFiles: Int? = null): Boolean {
    val playableCount = playableSegmentFiles ?: segmentCount
    if (playableCount <= 0) return false
    val segment = playbackSegmentIndex.coerceIn(0, playableCount)
    if (segment >= playableCount) return false
    return segment > 0 || playbackPositionMs > 0
}

internal fun BookAudioEntity.canCancelGenerationFromAudiobooksScreen(): Boolean =
    status == BookAudioStatus.GENERATING

internal fun BookAudioEntity.canDeleteFromAudiobooksScreen(): Boolean =
    canDeleteGeneratedAudiobook()

private fun AudiobookPlaybackUiState.segmentProgress(): Float =
    when {
        segmentDurationMs > 0 -> segmentPositionMs.toFloat() / segmentDurationMs.toFloat()
        segmentCount > 0 -> (segmentIndex + 1).toFloat() / segmentCount.toFloat()
        else -> 0f
    }.coerceIn(0f, 1f)

internal fun AudiobookPlaybackUiState.chapterLabel(): String? {
    val title = chapterTitle?.takeIf { it.isNotBlank() } ?: return null
    val position = chapterIndex?.takeIf { chapterCount > 0 }?.let { "${it + 1} / $chapterCount" }
    return listOfNotNull(title, position).joinToString(" • ")
}

internal fun AudiobookPlaybackUiState.canSkipPreviousChapter(): Boolean =
    chapterCount > 1 && segmentIndex > 0

internal fun AudiobookPlaybackUiState.canSkipNextChapter(): Boolean =
    chapterCount > 1 && chapterIndex != null && chapterIndex < chapterCount - 1

internal fun AudiobookPlaybackUiState.canSkipPreviousChapter(chapters: List<GeneratedAudiobookChapter>): Boolean =
    chapters.previousChapterStart(segmentIndex) != null

internal fun AudiobookPlaybackUiState.canSkipNextChapter(chapters: List<GeneratedAudiobookChapter>): Boolean =
    chapters.nextChapterStart(segmentIndex) != null

internal fun AudiobookPlaybackUiState.forAudiobooksScreenRow(audioId: Long): AudiobookPlaybackUiState =
    if (this.audioId == audioId && active) this else EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE

internal fun AudiobookPlaybackUiState.forAudiobooksScreen(): AudiobookPlaybackUiState {
    if (!active) return EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE
    return copy(
        segmentPositionMs = segmentPositionMs.coerceAtLeast(0)
            .roundedDownToAudiobooksScreenPlaybackStep(),
        segmentDurationMs = segmentDurationMs.coerceAtLeast(0)
    )
}

private fun Int.roundedDownToAudiobooksScreenPlaybackStep(): Int =
    if (this <= 0) {
        0
    } else {
        (this / AUDIOBOOKS_SCREEN_PLAYBACK_POSITION_STEP_MS) * AUDIOBOOKS_SCREEN_PLAYBACK_POSITION_STEP_MS
    }

internal fun GeneratedAudiobookChapter.chapterRangeLabel(): String =
    when {
        segmentCount <= 1 -> "Segment ${firstSegmentIndex + 1}"
        else -> "Segments ${firstSegmentIndex + 1}-${lastSegmentIndex + 1}"
    }

internal fun generatedAudiobookChapterCountLabel(chapterCount: Int): String =
    if (chapterCount == 1) "1 chapter" else "$chapterCount chapters"

private const val AUDIOBOOKS_SCREEN_PLAYBACK_POSITION_STEP_MS = 5_000

private fun BookAudioEntity.audiobookFileSafeProfileName(): String =
    audiobookDisplayProfileLabel(includeScope = true)
        .fileSafeName()
        .take(56)

private fun shortDateLabel(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

@Composable
internal fun NotesRoute(
    container: AppContainer,
    onBack: (() -> Unit)? = null,
    openReaderAt: (Long, String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportMarkdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) viewModel.exportMarkdown(uri)
    }
    var editing by remember { mutableStateOf<AnnotationEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<AnnotationEntity?>(null) }
    var tagMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text("Notes and highlights") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { exportMarkdownLauncher.launch("xreader-notes.md") },
                        enabled = !state.exporting
                    ) {
                        if (state.exporting) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Export notes as Markdown")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear notes search")
                        }
                    }
                }
            )
            FlowRow(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.kind == null,
                    onClick = { viewModel.setKind(null) },
                    label = { Text("All") }
                )
                AnnotationKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                        label = { Text(kind.label()) }
                    )
                }
                if (state.tagOptions.isNotEmpty()) {
                    Box {
                        FilterChip(
                            selected = state.selectedTag != null,
                            onClick = { tagMenuOpen = true },
                            label = { Text(state.selectedTag?.let { "Tag: $it" } ?: "Tags") }
                        )
                        DropdownMenu(expanded = tagMenuOpen, onDismissRequest = { tagMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("All tags") },
                                onClick = {
                                    viewModel.setSelectedTag(null)
                                    tagMenuOpen = false
                                }
                            )
                            state.tagOptions.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text("${tag.label} (${tag.count})") },
                                    onClick = {
                                        viewModel.setSelectedTag(tag.label)
                                        tagMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.notes.isEmpty()) {
                    item {
                        Text(
                            if (state.query.isBlank() && state.kind == null && state.selectedTag == null) "No notes yet." else "No matching notes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.notes, key = { it.annotation.id }) { item ->
                    AnnotationRow(
                        item = item,
                        onOpen = { openReaderAt(item.annotation.bookId, item.annotation.locator) },
                        onEdit = { editing = item.annotation },
                        onDelete = { deleteCandidate = item.annotation }
                    )
                }
            }
        }
    }
    editing?.let { annotation ->
        EditAnnotationDialog(
            annotation = annotation,
            onDismiss = { editing = null },
            onSave = { note, color, tags ->
                viewModel.updateNote(annotation, note, color, tags)
                editing = null
            }
        )
    }
    deleteCandidate?.let { annotation ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete annotation") },
            text = { Text("Delete this ${annotation.kind.label().lowercase()}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnnotation(annotation.id)
                        deleteCandidate = null
                    }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun AnnotationRow(
    item: NoteListItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val note = item.annotation
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 10.dp)
                        .size(10.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = note.color.toAnnotationColor()
                ) {}
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(note.kind.label(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(item.book?.title ?: "Unknown book", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit annotation")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete annotation")
                }
            }
            Text(note.quote, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (note.note.isNotBlank()) {
                Text(note.note, fontWeight = FontWeight.SemiBold)
            }
            annotationTagsLabel(note.tags).takeIf { it.isNotBlank() }?.let { tags ->
                Text(tags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(DateFormat.getDateTimeInstance().format(Date(note.updatedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EditAnnotationDialog(
    annotation: AnnotationEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var note by remember(annotation.id) { mutableStateOf(annotation.note) }
    var color by remember(annotation.id) { mutableStateOf(ReaderHighlightColor.normalized(annotation.color)) }
    var tags by remember(annotation.id) { mutableStateOf(annotation.tags) }
    val canSave = annotation.kind != AnnotationKind.NOTE || normalizeAnnotationNote(note).isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (annotation.kind == AnnotationKind.NOTE) "Edit note" else "Edit highlight note") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(annotation.quote, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (annotation.kind == AnnotationKind.HIGHLIGHT) {
                    Text("Highlight color", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderHighlightColor.entries.forEach { option ->
                            FilterChip(
                                selected = ReaderHighlightColor.optionFor(color) == option,
                                onClick = { color = option.hex },
                                label = { Text(option.label) },
                                leadingIcon = { AnnotationColorSwatch(option.hex) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(note, color, tags) }, enabled = canSave) {
                Icon(Icons.Filled.Done, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val librarySettings by viewModel.librarySettings.collectAsStateWithLifecycle()
    val readAloudEngines by viewModel.readAloudEngines.collectAsStateWithLifecycle()
    val readAloudVoices by viewModel.readAloudVoices.collectAsStateWithLifecycle()
    val neuralTtsModels by viewModel.neuralTtsModels.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenance.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val backupMimeTypes = remember {
        arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
    }
    val exportNotesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportAnnotations(uri)
    }
    val importNotesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importAnnotations(uri)
    }
    val exportLibraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportLibrary(uri)
    }
    val importLibraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLibrary(uri)
    }
    val maintenanceBusy = maintenance.repairingLibrary ||
        maintenance.exportingLibrary ||
        maintenance.importingLibrary ||
        maintenance.exportingAnnotations ||
        maintenance.importingAnnotations
    LaunchedEffect(maintenance.message) {
        maintenance.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMaintenanceMessage()
        }
    }
    LaunchedEffect(settings.readAloudEngineName) {
        viewModel.refreshReadAloudOptions(settings.readAloudEngineName)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                SettingsSection("Appearance") {
                    SettingsChipGroup(
                        title = "Theme",
                        options = ReaderTheme.entries,
                        selected = settings.theme,
                        label = { it.label() },
                        onSelected = viewModel::setTheme
                    )
                }
            }
            item {
                SettingsSection("Typography") {
                    SettingsChipGroup(
                        title = "Spacing preset",
                        options = ReaderSpacingPreset.entries,
                        selected = settings.spacingPresetOrNull(),
                        label = { it.label },
                        onSelected = viewModel::setSpacingPreset
                    )
                    SettingSlider("Font size", settings.fontScale, 0.75f..1.65f, viewModel::setFontScale)
                    SettingSlider("Line height", settings.lineHeight, 1.1f..2.0f, viewModel::setLineHeight)
                    SettingSlider("Margins", settings.marginScale, 0.35f..1.8f, viewModel::setMarginScale)
                    SettingsChipGroup(
                        title = "Font",
                        options = ReaderFontFamily.entries,
                        selected = settings.fontFamily,
                        label = { it.label },
                        onSelected = viewModel::setFontFamily
                    )
                    SettingSlider(
                        "Text weight",
                        settings.fontWeight,
                        MIN_READER_FONT_WEIGHT..MAX_READER_FONT_WEIGHT,
                        viewModel::setFontWeight
                    )
                    SettingsToggleRow(
                        label = "Hyphenation",
                        checked = settings.hyphenation,
                        onCheckedChange = viewModel::setHyphenation
                    )
                    SettingsChipGroup(
                        title = "Alignment",
                        options = ReaderTextAlign.entries,
                        selected = settings.textAlign,
                        label = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                        onSelected = viewModel::setTextAlign
                    )
                    SettingsToggleRow(
                        label = "Publisher styles",
                        checked = settings.publisherStyles,
                        onCheckedChange = viewModel::setPublisherStyles
                    )
                }
            }
            item {
                SettingsSection("Reading") {
                    SettingsChipGroup(
                        title = "PDF fit",
                        options = ReaderPdfFit.entries,
                        selected = settings.pdfFit,
                        label = { it.label },
                        onSelected = viewModel::setPdfFit
                    )
                    SettingsChipGroup(
                        title = "PDF layout",
                        options = ReaderPdfScrollAxis.entries,
                        selected = settings.pdfScrollAxis,
                        label = { it.label },
                        onSelected = viewModel::setPdfScrollAxis
                    )
                    SettingsChipGroup(
                        title = "Page direction",
                        options = ReaderPageDirection.entries,
                        selected = settings.pageDirection,
                        label = { it.label },
                        onSelected = viewModel::setPageDirection
                    )
                    SettingsChipGroup(
                        title = "Orientation",
                        options = ReaderOrientation.entries,
                        selected = settings.orientation,
                        label = { it.label },
                        onSelected = viewModel::setOrientation
                    )
                    SettingsToggleRow(
                        label = "Fullscreen reading",
                        checked = settings.fullScreen,
                        onCheckedChange = viewModel::setFullScreen
                    )
                    SettingsToggleRow(
                        label = "Tap zones",
                        checked = settings.tapZonesEnabled,
                        onCheckedChange = viewModel::setTapZonesEnabled
                    )
                    if (settings.tapZonesEnabled) {
                        SettingsChipGroup(
                            title = "Tap zone size",
                            options = ReaderTapZonePreset.entries,
                            selected = settings.tapZonePreset,
                            label = { it.label },
                            onSelected = viewModel::setTapZonePreset
                        )
                    }
                    SettingsToggleRow(
                        label = "Page animations",
                        checked = settings.pageTurnAnimations,
                        onCheckedChange = viewModel::setPageTurnAnimations
                    )
                    SettingsToggleRow(
                        label = "Keep screen awake",
                        checked = settings.keepScreenAwake,
                        onCheckedChange = viewModel::setKeepScreenAwake
                    )
                    SettingsToggleRow(
                        label = "Volume buttons turn pages",
                        checked = settings.volumeKeysTurnPages,
                        onCheckedChange = viewModel::setVolumeKeysTurnPages
                    )
                    SettingSlider("Reader dim", settings.screenDim, 0f..MAX_READER_DIM_AMOUNT, viewModel::setScreenDim)
                    SettingSlider("Read aloud speed", settings.readAloudRate, 0.7f..1.4f, viewModel::setReadAloudRate)
                    SettingsChipGroup(
                        title = "Sleep timer",
                        options = ReadAloudSleepTimer.entries,
                        selected = settings.readAloudSleepTimer,
                        label = { it.label },
                        onSelected = viewModel::setReadAloudSleepTimer
                    )
                    ReadAloudEngineSettings(
                        engines = readAloudEngines,
                        selectedEngineName = settings.readAloudEngineName,
                        onSelected = viewModel::setReadAloudEngineName
                    )
                    ReadAloudVoiceSettings(
                        voices = readAloudVoices,
                        selectedVoiceName = settings.readAloudVoiceName,
                        onSelected = viewModel::setReadAloudVoiceName
                    )
                    NeuralTtsModelSettings(
                        models = neuralTtsModels,
                        selectedModelId = settings.neuralTtsModelId,
                        selectedSpeakerId = settings.neuralTtsSpeakerId,
                        gender = settings.neuralTtsGender,
                        tone = settings.neuralTtsTone,
                        pace = settings.neuralTtsPace,
                        onSelected = viewModel::setNeuralTtsModelId,
                        onSpeakerSelected = viewModel::setNeuralTtsSpeakerId,
                        onGenderSelected = viewModel::setNeuralTtsGender,
                        onToneSelected = viewModel::setNeuralTtsTone,
                        onPaceSelected = viewModel::setNeuralTtsPace,
                        onDownload = viewModel::downloadNeuralTtsModel,
                        onCancelInstall = viewModel::cancelNeuralTtsModelInstall,
                        onDelete = viewModel::deleteNeuralTtsModel,
                        onPreview = viewModel::previewNeuralTtsModel
                    )
                }
            }
            item {
                SettingsSection("Library") {
                    SettingsChipGroup(
                        title = "Sort",
                        options = LibrarySort.entries,
                        selected = librarySettings.sort,
                        label = { it.label() },
                        onSelected = viewModel::setLibrarySort
                    )
                    SettingsChipGroup(
                        title = "Density",
                        options = LibraryDensity.entries,
                        selected = librarySettings.density,
                        label = { it.label() },
                        onSelected = viewModel::setLibraryDensity
                    )
                }
            }
            item {
                SettingsSection("Maintenance") {
                    Button(
                        onClick = viewModel::repairLibrary,
                        enabled = !maintenanceBusy,
                    ) {
                        if (maintenance.repairingLibrary) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (maintenance.repairingLibrary) "Repairing library" else "Repair library")
                    }
                    Text(
                        "Rebuilds search, covers, metadata, series order, and readability for imported books.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportLibraryLauncher.launch("xreader-library-metadata.json") },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.exportingLibrary) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.exportingLibrary) "Exporting library" else "Export library")
                        }
                        Button(
                            onClick = { importLibraryLauncher.launch(backupMimeTypes) },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.importingLibrary) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileUpload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.importingLibrary) "Importing library" else "Import library")
                        }
                        Button(
                            onClick = { exportNotesLauncher.launch("xreader-notes-bookmarks.json") },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.exportingAnnotations) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.exportingAnnotations) "Exporting notes" else "Export notes")
                        }
                        Button(
                            onClick = { importNotesLauncher.launch(backupMimeTypes) },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.importingAnnotations) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileUpload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.importingAnnotations) "Importing notes" else "Import notes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun <T> SettingsChipGroup(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) }
                )
            }
        }
    }
}

@Composable
private fun ReadAloudEngineSettings(
    engines: List<ReadAloudEngineOption>,
    selectedEngineName: String?,
    onSelected: (String?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val selectedEngineAvailable = selectedEngineName == null || engines.any { it.name == selectedEngineName }
    val selectedLabel = selectedEngineLabel(engines, selectedEngineName)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Read aloud engine", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!selectedEngineAvailable) {
                    Text(
                        text = "Unavailable on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = { pickerOpen = true }) {
                Text("Choose")
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Read aloud engine") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "default") {
                        ReadAloudVoiceChoiceRow(
                            label = "Device default",
                            selected = selectedEngineName == null,
                            onClick = {
                                onSelected(null)
                                pickerOpen = false
                            }
                        )
                    }
                    items(engines.filter { it.name != null }, key = { it.name ?: it.label }) { engine ->
                        ReadAloudVoiceChoiceRow(
                            label = if (engine.isDefault) "${engine.label} (default)" else engine.label,
                            selected = selectedEngineName == engine.name,
                            onClick = {
                                onSelected(engine.name)
                                pickerOpen = false
                            }
                        )
                    }
                    if (engines.none { it.name != null }) {
                        item {
                            PickerEmptyRow("No additional TTS engines found.")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun NeuralTtsModelSettings(
    models: List<NeuralTtsModelEntity>,
    selectedModelId: String,
    selectedSpeakerId: Int,
    gender: NeuralTtsGender,
    tone: NeuralTtsTone,
    pace: NeuralTtsPace,
    onSelected: (String) -> Unit,
    onSpeakerSelected: (Int) -> Unit,
    onGenderSelected: (NeuralTtsGender) -> Unit,
    onToneSelected: (NeuralTtsTone) -> Unit,
    onPaceSelected: (NeuralTtsPace) -> Unit,
    onDownload: (String) -> Unit,
    onCancelInstall: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val spec = NeuralTtsModelCatalog.models.firstOrNull { it.modelId == selectedModelId }
        ?: NeuralTtsModelCatalog.models.first()
    val model = models.firstOrNull { it.modelId == spec.modelId }
    val status = model?.status ?: NeuralTtsModelStatus.NOT_DOWNLOADED
    val downloaded = model?.downloadedBytes ?: 0L
    val total = model?.totalBytes?.takeIf { it > 0L } ?: spec.archiveBytes
    val statusText = neuralTtsStatusText(status, downloaded, total, spec.archiveBytes, model?.error)
    val selectedSpeaker = spec.speaker(selectedSpeakerId)
    val narratorOptions = remember(spec, gender, selectedSpeaker) {
        spec.speakers
            .filter { gender == NeuralTtsGender.ANY || it.gender == gender }
            .let { filtered ->
                if (selectedSpeaker in filtered) filtered else listOf(selectedSpeaker) + filtered
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Local neural voice", style = MaterialTheme.typography.titleSmall)
        SettingsChipGroup(
            title = "Voice",
            options = NeuralTtsGender.entries,
            selected = gender,
            label = { it.label },
            onSelected = onGenderSelected
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${spec.displayName} · ${selectedSpeaker.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(statusText, spec.voiceDescription).joinToString(" - "),
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    TextButton(onClick = { onCancelInstall(spec.modelId) }) { Text("Stop") }
                }
                else -> TextButton(onClick = { pickerOpen = true }) {
                    Text("Manage")
                }
            }
        }
        if (spec.speakers.size > 1) {
            SettingsChipGroup(
                title = "Narrator",
                options = narratorOptions,
                selected = selectedSpeaker,
                label = { "${it.label} (${it.gender.label})" },
                onSelected = { onSpeakerSelected(it.id) }
            )
        }
        SettingsChipGroup(
            title = "Narration style",
            options = NeuralTtsTone.entries,
            selected = tone,
            label = { it.label },
            onSelected = onToneSelected
        )
        SettingsChipGroup(
            title = "Pacing",
            options = NeuralTtsPace.entries,
            selected = pace,
            label = { it.label },
            onSelected = onPaceSelected
        )
    }
    if (pickerOpen) {
        val visibleVoices = NeuralTtsModelCatalog.models
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Local neural voices") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleVoices, key = { it.modelId }) { voice ->
                        val rowModel = models.firstOrNull { it.modelId == voice.modelId }
                        val rowStatus = rowModel?.status ?: NeuralTtsModelStatus.NOT_DOWNLOADED
                        val rowDownloaded = rowModel?.downloadedBytes ?: 0L
                        val rowTotal = rowModel?.totalBytes?.takeIf { it > 0L } ?: voice.archiveBytes
                        NeuralTtsVoiceRow(
                            spec = voice,
                            status = rowStatus,
                            selected = voice.modelId == selectedModelId,
                            statusText = neuralTtsStatusText(rowStatus, rowDownloaded, rowTotal, voice.archiveBytes, rowModel?.error),
                            onSelected = {
                                onSelected(voice.modelId)
                                pickerOpen = false
                            },
                            onDownload = { onDownload(voice.modelId) },
                            onCancelInstall = { onCancelInstall(voice.modelId) },
                            onDelete = { onDelete(voice.modelId) },
                            onPreview = { onPreview(voice.modelId) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun NeuralTtsVoiceRow(
    spec: com.xreader.app.tts.NeuralTtsModelSpec,
    status: NeuralTtsModelStatus,
    selected: Boolean,
    statusText: String,
    onSelected: () -> Unit,
    onDownload: () -> Unit,
    onCancelInstall: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
) {
    Surface(
        tonalElevation = if (selected) 2.dp else 0.dp,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(spec.voiceDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = if (status == NeuralTtsModelStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                    Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (status) {
                    NeuralTtsModelStatus.DOWNLOADING,
                    NeuralTtsModelStatus.EXTRACTING -> {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        TextButton(onClick = onCancelInstall) { Text("Stop") }
                    }
                    NeuralTtsModelStatus.INSTALLED -> {
                        TextButton(onClick = onPreview) { Text("Preview") }
                        TextButton(onClick = onSelected, enabled = !selected) { Text("Use") }
                        TextButton(onClick = onDownload) { Text("Reinstall") }
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                    else -> TextButton(onClick = onDownload) { Text("Download") }
                }
            }
        }
    }
}

internal fun neuralTtsStatusText(
    status: NeuralTtsModelStatus,
    downloaded: Long,
    total: Long,
    archiveBytes: Long,
    error: String?,
): String =
    when (status) {
        NeuralTtsModelStatus.INSTALLED -> "Installed"
        NeuralTtsModelStatus.DOWNLOADING -> "Downloading ${downloaded.compactBytes()} of ${total.compactBytes()}"
        NeuralTtsModelStatus.EXTRACTING -> "Installing ${archiveBytes.compactBytes()} voice"
        NeuralTtsModelStatus.FAILED -> error ?: "Download failed"
        NeuralTtsModelStatus.NOT_DOWNLOADED -> "Not installed • ${archiveBytes.compactBytes()}"
    }

@Composable
private fun ReadAloudVoiceSettings(
    voices: List<ReadAloudVoiceOption>,
    selectedVoiceName: String?,
    onSelected: (String?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val selectedLabel = selectedVoiceLabel(voices, selectedVoiceName)
    val selectedVoiceAvailable = selectedVoiceName == null || voices.any { it.name == selectedVoiceName }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Read aloud voice", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!selectedVoiceAvailable) {
                    Text(
                        text = "Unavailable on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = { pickerOpen = true }) {
                Text("Choose")
            }
        }
    }

    if (pickerOpen) {
        ReadAloudVoicePickerDialog(
            voices = voices,
            selectedVoiceName = selectedVoiceName,
            onDismiss = { pickerOpen = false },
            onSelected = { voiceName ->
                onSelected(voiceName)
                pickerOpen = false
            }
        )
    }
}

private fun selectedEngineLabel(
    engines: List<ReadAloudEngineOption>,
    selected: String?,
): String =
    if (selected == null) {
        "Device default"
    } else {
        engines.firstOrNull { it.name == selected }?.label ?: "Selected engine unavailable"
    }

@Composable
private fun ReadAloudVoicePickerDialog(
    voices: List<ReadAloudVoiceOption>,
    selectedVoiceName: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedGroupKey by remember(voices, selectedVoiceName) { mutableStateOf<String?>(null) }
    val voiceGroups = remember(voices, selectedVoiceName) {
        buildReadAloudVoiceGroups(voices, selectedVoiceName)
    }
    val selectedGroup = voiceGroups.firstOrNull { it.key == selectedGroupKey }
    val matchingVoices = remember(voices, query) { filterReadAloudVoices(voices, query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read aloud voice") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedGroupKey = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search voices") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear voice search")
                            }
                        }
                    }
                )
                if (selectedGroup != null && query.isBlank()) {
                    TextButton(onClick = { selectedGroupKey = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Languages")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        query.isNotBlank() -> {
                            if (matchingVoices.isEmpty()) {
                                item {
                                    PickerEmptyRow("No matching voices.")
                                }
                            } else {
                                items(matchingVoices, key = { "search-${it.name}" }) { voice ->
                                    ReadAloudVoiceChoiceRow(
                                        label = voice.label,
                                        selected = selectedVoiceName == voice.name,
                                        onClick = { onSelected(voice.name) }
                                    )
                                }
                            }
                        }
                        selectedGroup != null -> {
                            items(selectedGroup.voices, key = { "voice-${it.name}" }) { voice ->
                                ReadAloudVoiceChoiceRow(
                                    label = voice.label,
                                    selected = selectedVoiceName == voice.name,
                                    onClick = { onSelected(voice.name) }
                                )
                            }
                        }
                        else -> {
                            item(key = "default") {
                                ReadAloudVoiceChoiceRow(
                                    label = "Device default",
                                    selected = selectedVoiceName == null,
                                    onClick = { onSelected(null) }
                                )
                            }
                            if (voices.isEmpty()) {
                                item {
                                    PickerEmptyRow("No installed voices found.")
                                }
                            } else {
                                items(voiceGroups, key = { "group-${it.key}" }) { group ->
                                    ReadAloudVoiceGroupRow(
                                        group = group,
                                        onClick = { selectedGroupKey = group.key }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ReadAloudVoiceGroupRow(
    group: ReadAloudVoiceGroup,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = group.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.voices.size} ${if (group.voices.size == 1) "voice" else "voices"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        onClick = onClick,
        leadingIcon = {
            if (group.selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    )
}

@Composable
private fun PickerEmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun ReadAloudVoiceChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = onClick,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    )
}

private fun activityTitle(
    range: AnalyticsRange,
    buckets: List<ActivityBucketAnalytics>,
): String =
    when (buckets.firstOrNull()?.granularity) {
        ActivityBucketGranularity.DAY -> when (range) {
            AnalyticsRange.WEEK -> "7-day activity"
            AnalyticsRange.MONTH -> "30-day activity"
            else -> "${range.label} activity"
        }
        ActivityBucketGranularity.WEEK -> "13-week activity"
        ActivityBucketGranularity.MONTH -> "Monthly activity"
        ActivityBucketGranularity.YEAR -> "Yearly activity"
        null -> "${range.label} activity"
    }

private fun shouldShowActivityLabel(
    index: Int,
    buckets: List<ActivityBucketAnalytics>,
): Boolean {
    val lastIndex = buckets.lastIndex
    if (lastIndex <= 13) return true
    return when (buckets.getOrNull(index)?.granularity) {
        ActivityBucketGranularity.DAY -> index == 0 || index == lastIndex || index % 5 == 0
        else -> true
    }
}

private fun formatActivityBucket(bucket: ActivityBucketAnalytics): String {
    val pattern = when (bucket.granularity) {
        ActivityBucketGranularity.DAY -> "E"
        ActivityBucketGranularity.WEEK -> "M/d"
        ActivityBucketGranularity.MONTH -> "MMM"
        ActivityBucketGranularity.YEAR -> "yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(bucket.startMillis))
}
