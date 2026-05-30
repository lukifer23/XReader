@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.xreader.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Trace
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReaderTheme
import com.xreader.app.reader.OpenPublication
import com.xreader.app.settings.ReaderPageDirection
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderPdfScrollAxis
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderTextAlign
import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.ReadingProgression as NavigatorReadingProgression
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.ReadingProgression as PublicationReadingProgression
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ReadiumPublicationView(
    publication: OpenPublication.Readium,
    initialLocatorJson: String?,
    settings: ReaderSettings,
    controller: ReaderPagingController,
    onLocator: (Locator) -> Unit,
    onLookup: (String) -> Unit,
    onSelectedNote: (Locator, String) -> Unit,
    onSelectedHighlight: (Locator, String) -> Unit,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val containerId = remember(publication.book.id) { View.generateViewId() }
    val tag = remember(publication.book.id) { "readium-reader-${publication.book.id}" }
    val latestSettings by rememberUpdatedState(settings)
    val latestToggleChrome by rememberUpdatedState(onToggleChrome)
    val latestLocatorHandler by rememberUpdatedState(onLocator)
    val latestLookupHandler by rememberUpdatedState(onLookup)
    val latestSelectedNoteHandler by rememberUpdatedState(onSelectedNote)
    val latestSelectedHighlightHandler by rememberUpdatedState(onSelectedHighlight)
    val tapEdgeInsetPx = with(LocalDensity.current) { settings.tapZonePreset.edgeGuardDp.dp.toPx() }
    val latestTapEdgeInsetPx by rememberUpdatedState(tapEdgeInsetPx)
    val selectionScope = rememberCoroutineScope()
    var navigator by remember(publication.book.id) { mutableStateOf<OverflowableNavigator?>(null) }
    val keyListener = remember(publication.book.id) {
        View.OnKeyListener { view, keyCode, event ->
            val active = navigator ?: return@OnKeyListener false
            return@OnKeyListener when (
                readerHardwareKeyHandling(
                    keyCode = keyCode,
                    action = event.action,
                    repeatCount = event.repeatCount,
                    volumeKeysTurnPages = latestSettings.volumeKeysTurnPages,
                    pageDirection = latestSettings.effectivePageDirection(publication)
                )
            ) {
                ReaderHardwareKeyHandling.BACKWARD -> {
                    active.goBackward(animated = latestSettings.pageTurnAnimations)
                    view.post { active.publicationView.prepareReaderInputRecursively(keyListener = null) }
                    true
                }
                ReaderHardwareKeyHandling.FORWARD -> {
                    active.goForward(animated = latestSettings.pageTurnAnimations)
                    view.post { active.publicationView.prepareReaderInputRecursively(keyListener = null) }
                    true
                }
                ReaderHardwareKeyHandling.CHROME -> {
                    view.post { active.publicationView.prepareReaderInputRecursively(keyListener = null) }
                    latestToggleChrome()
                    true
                }
                ReaderHardwareKeyHandling.CONSUME -> true
                ReaderHardwareKeyHandling.IGNORE -> false
            }
        }
    }

    if (activity == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Reader host unavailable.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    fun publishLocator(locator: Locator) {
        latestLocatorHandler(locator)
        val index = publication.positionIndexFor(locator).coerceAtLeast(0)
        controller.updateVisiblePosition(
            page = index,
            locatorJson = locator.toJSON().toString(),
            pageCount = publication.units.size.coerceAtLeast(1)
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { FrameLayout(it).apply { id = containerId } }
    )

    DisposableEffect(publication.book.id, initialLocatorJson, containerId) {
        val previousFactory = activity.supportFragmentManager.fragmentFactory
        val initialLocator = initialLocatorJson.toReadiumLocator()
        val selectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, HIGHLIGHT_SELECTION_ID, 0, "Highlight")
                menu.add(0, NOTE_SELECTION_ID, 1, "Note")
                menu.add(0, DEFINE_SELECTION_ID, 2, "Define")
                menu.add(0, COPY_SELECTION_ID, 3, "Copy")
                menu.add(0, SHARE_SELECTION_ID, 4, "Share")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId !in SELECTION_ACTION_IDS) return false
                val selectable = navigator as? SelectableNavigator ?: return false
                selectionScope.launch {
                    val selected = selectable.currentSelection()
                    val locator = selected?.locator
                    if (locator != null) {
                        val text = locator.text.highlight
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                        if (text != null) {
                            when (item.itemId) {
                                COPY_SELECTION_ID -> context.copyReaderSelection(text)
                                SHARE_SELECTION_ID -> context.shareReaderSelection(text)
                                DEFINE_SELECTION_ID -> latestLookupHandler(text)
                                NOTE_SELECTION_ID -> latestSelectedNoteHandler(locator, text)
                                HIGHLIGHT_SELECTION_ID -> latestSelectedHighlightHandler(locator, text)
                            }
                        }
                    }
                    selectable.clearSelection()
                    mode.finish()
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }
        val inputListener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val active = navigator ?: return false
                active.publicationView.disableScrollbarsRecursively()
                val width = active.publicationView.width.toFloat().takeIf { it > 0f } ?: return false
                val x = event.point.x
                return when (
                    resolveReaderTapAction(
                        x = x,
                        width = width,
                        settings = latestSettings,
                        edgeGuardPx = latestTapEdgeInsetPx,
                        pageDirection = latestSettings.effectivePageDirection(publication)
                    )
                ) {
                    ReaderTapAction.BACKWARD -> {
                        active.goBackward(animated = latestSettings.pageTurnAnimations)
                        active.publicationView.post { active.publicationView.disableScrollbarsRecursively() }
                        true
                    }
                    ReaderTapAction.FORWARD -> {
                        active.goForward(animated = latestSettings.pageTurnAnimations)
                        active.publicationView.post { active.publicationView.disableScrollbarsRecursively() }
                        true
                    }
                    ReaderTapAction.CHROME -> {
                        active.publicationView.post { active.publicationView.disableScrollbarsRecursively() }
                        latestToggleChrome()
                        true
                    }
                }
            }
        }
        val factory = publication.fragmentFactory(
            initialLocator = initialLocator,
            settings = settings,
            onLocator = ::publishLocator,
            context = context,
            selectionActionModeCallback = selectionActionModeCallback
        )

        traced("XReader:attachNavigator") {
            activity.supportFragmentManager.fragmentFactory = factory
            activity.supportFragmentManager.commitNow {
                replace(
                    containerId,
                    if (publication.format == BookFormat.PDF) PdfNavigatorFragment::class.java else EpubNavigatorFragment::class.java,
                    null,
                    tag
                )
            }
        }
        navigator = activity.supportFragmentManager.findFragmentByTag(tag) as? OverflowableNavigator
        navigator?.publicationView?.prepareReaderInputRecursively(keyListener)
        navigator?.addInputListener(inputListener)

        onDispose {
            navigator?.removeInputListener(inputListener)
            navigator?.publicationView?.clearReaderKeyListenersRecursively()
            navigator = null
            activity.supportFragmentManager.findFragmentByTag(tag)?.let { fragment ->
                activity.supportFragmentManager.commitNow(allowStateLoss = true) {
                    remove(fragment)
                }
            }
            activity.supportFragmentManager.fragmentFactory = previousFactory
        }
    }

    SideEffect {
        controller.pageCount = publication.units.size.coerceAtLeast(1)
        controller.goToPage = { page ->
            val index = page.coerceIn(0, publication.positions.lastIndex.coerceAtLeast(0))
            publication.positions.getOrNull(index)?.let { locator ->
                publishLocator(locator)
                navigator?.go(locator, animated = latestSettings.pageTurnAnimations)
            }
        }
        controller.goToUnit = { unit ->
            val index = unit.coerceIn(0, publication.positions.lastIndex.coerceAtLeast(0))
            publication.positions.getOrNull(index)?.let { locator ->
                publishLocator(locator)
                navigator?.go(locator, animated = latestSettings.pageTurnAnimations)
            }
        }
        controller.goToLocator = { locatorJson ->
            locatorJson.toReadiumLocator()?.let {
                publishLocator(it)
                navigator?.go(it, animated = latestSettings.pageTurnAnimations)
            } ?: controller.goToUnit(locatorToUnit(locatorJson, publication.units))
        }
        controller.goToProgress = { progress ->
            val index = ((publication.positions.size - 1).coerceAtLeast(0) * progress).roundToInt()
            controller.goToUnit(index)
        }
    }

    LaunchedEffect(navigator, publication.book.id) {
        val active = navigator ?: return@LaunchedEffect
        active.currentLocator.collect { locator ->
            active.publicationView.disableScrollbarsRecursively()
            publishLocator(locator)
        }
    }

    LaunchedEffect(navigator, publication.book.id) {
        val active = navigator ?: return@LaunchedEffect
        repeat(16) {
            active.publicationView.prepareReaderInputRecursively(keyListener)
            delay(250)
        }
    }

    LaunchedEffect(navigator, settings) {
        traced("XReader:readerPreferences") {
            when (val active = navigator) {
                is EpubNavigatorFragment -> active.submitPreferences(settings.toEpubPreferences())
                is PdfNavigatorFragment<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    (active as PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>)
                        .submitPreferences(settings.toPdfiumPreferences())
                }
            }
        }
    }

    if (navigator == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

private fun OpenPublication.Readium.fragmentFactory(
    initialLocator: Locator?,
    settings: ReaderSettings,
    onLocator: (Locator) -> Unit,
    context: Context,
    selectionActionModeCallback: ActionMode.Callback,
): FragmentFactory =
    when (format) {
        BookFormat.EPUB -> EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            readingOrder = null,
            initialPreferences = settings.toEpubPreferences(),
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: org.readium.r2.shared.util.AbsoluteUrl) {
                    context.openExternal(url.toString())
                }
            },
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                    onLocator(locator)
                }
            },
            configuration = EpubNavigatorFragment.Configuration(selectionActionModeCallback = selectionActionModeCallback)
        )
        BookFormat.PDF -> PdfNavigatorFactory(publication, PdfiumEngineProvider())
            .createFragmentFactory(
                initialLocator,
                settings.toPdfiumPreferences(),
                object : PdfNavigatorFragment.Listener {}
            )
    }

private fun ReaderSettings.toEpubPreferences(): EpubPreferences =
    EpubPreferences(
        fontSize = fontScale.toDouble(),
        fontFamily = fontFamily.readiumName?.let(::ReadiumFontFamily),
        fontWeight = fontWeight.toDouble(),
        hyphens = hyphenation,
        lineHeight = lineHeight.toDouble(),
        pageMargins = marginScale.toDouble(),
        publisherStyles = publisherStyles,
        readingProgression = pageDirection.toReadiumReadingProgression(),
        scroll = false,
        spread = Spread.NEVER,
        textAlign = when (textAlign) {
            ReaderTextAlign.START -> ReadiumTextAlign.START
            ReaderTextAlign.JUSTIFY -> ReadiumTextAlign.JUSTIFY
        },
        theme = when (theme) {
            ReaderTheme.LIGHT -> Theme.LIGHT
            ReaderTheme.DARK -> Theme.DARK
            ReaderTheme.SEPIA -> Theme.SEPIA
            ReaderTheme.OLED -> Theme.DARK
        },
        backgroundColor = if (theme == ReaderTheme.OLED) ReadiumColor(0xFF000000.toInt()) else null,
        textColor = if (theme == ReaderTheme.OLED) ReadiumColor(0xFFEFEFEF.toInt()) else null
    )

private fun ReaderSettings.toPdfiumPreferences(): PdfiumPreferences =
    PdfiumPreferences(
        fit = when (pdfFit) {
            ReaderPdfFit.CONTAIN -> Fit.CONTAIN
            ReaderPdfFit.WIDTH -> Fit.WIDTH
            ReaderPdfFit.HEIGHT -> Fit.HEIGHT
        },
        pageSpacing = (12.0 * marginScale.toDouble()).coerceIn(4.0, 32.0),
        readingProgression = pageDirection.toReadiumReadingProgression(),
        scrollAxis = when (pdfScrollAxis) {
            ReaderPdfScrollAxis.HORIZONTAL -> Axis.HORIZONTAL
            ReaderPdfScrollAxis.VERTICAL -> Axis.VERTICAL
        }
    )

private fun ReaderPageDirection.toReadiumReadingProgression(): NavigatorReadingProgression? =
    when (this) {
        ReaderPageDirection.AUTO -> null
        ReaderPageDirection.LEFT_TO_RIGHT -> NavigatorReadingProgression.LTR
        ReaderPageDirection.RIGHT_TO_LEFT -> NavigatorReadingProgression.RTL
    }

private fun ReaderSettings.effectivePageDirection(publication: OpenPublication.Readium): ReaderPageDirection =
    when {
        pageDirection != ReaderPageDirection.AUTO -> pageDirection
        publication.publication.metadata.readingProgression == PublicationReadingProgression.RTL ->
            ReaderPageDirection.RIGHT_TO_LEFT
        else -> ReaderPageDirection.LEFT_TO_RIGHT
    }

private fun String?.toReadiumLocator(): Locator? {
    if (isNullOrBlank() || !trimStart().startsWith("{")) return null
    return runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }

private fun Context.openExternal(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.copyReaderSelection(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Book selection", text))
    Toast.makeText(this, "Copied selection", Toast.LENGTH_SHORT).show()
}

private fun Context.shareReaderSelection(text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(sendIntent, "Share selection")
    if (this !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(chooser) }
}

private fun View.disableScrollbarsRecursively() {
    disableScrollbars()
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).disableScrollbarsRecursively()
        }
    }
}

private fun View.disableScrollbars() {
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
}

private fun View.prepareReaderInputRecursively(keyListener: View.OnKeyListener?) {
    disableScrollbars()
    isFocusable = true
    isFocusableInTouchMode = true
    keyListener?.let(::setOnKeyListener)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).prepareReaderInputRecursively(keyListener)
        }
    }
    if (hasFocus().not()) requestFocus()
}

private fun View.clearReaderKeyListenersRecursively() {
    setOnKeyListener(null)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).clearReaderKeyListenersRecursively()
        }
    }
}

@SuppressLint("UnclosedTrace")
private inline fun <T> traced(name: String, block: () -> T): T {
    Trace.beginSection(name.take(127))
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

private const val DEFINE_SELECTION_ID = 42_001
private const val NOTE_SELECTION_ID = 42_002
private const val HIGHLIGHT_SELECTION_ID = 42_003
private const val COPY_SELECTION_ID = 42_004
private const val SHARE_SELECTION_ID = 42_005
private val SELECTION_ACTION_IDS = setOf(
    DEFINE_SELECTION_ID,
    NOTE_SELECTION_ID,
    HIGHLIGHT_SELECTION_ID,
    COPY_SELECTION_ID,
    SHARE_SELECTION_ID
)
