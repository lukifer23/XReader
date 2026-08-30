@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xreader.app.AppContainer
import com.xreader.app.data.ReaderTheme
import com.xreader.app.importer.toIncomingBookImport
import com.xreader.app.importer.IncomingImportKind
import com.xreader.app.importer.classifyIncomingImport
import com.xreader.app.importer.ACSM_MIME_TYPE
import kotlinx.coroutines.delay

@Composable
fun XReaderApp(
    container: AppContainer,
    incomingIntent: Intent? = null,
    onIncomingIntentConsumed: (Intent) -> Unit = {},
) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val activity = LocalContext.current.findActivity()
    val context = LocalContext.current
    var acsmUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(container) {
        delay(READER_SERVICE_WARMUP_DELAY_MS)
        container.warmReaderServices()
        delay(READER_WEBVIEW_WARMUP_DELAY_MS - READER_SERVICE_WARMUP_DELAY_MS)
        container.warmReaderWebView()
    }
    LaunchedEffect(incomingIntent) {
        val intent = incomingIntent ?: return@LaunchedEffect
        val incomingImport = intent.toIncomingBookImport()
        if (incomingImport != null) {
            val classified = incomingImport.uris.groupBy { context.contentResolver.classifyIncomingImport(it) }
            classified[IncomingImportKind.BOOK].orEmpty().takeIf { it.isNotEmpty() }?.let { books ->
                navController.navigate("library") {
                    launchSingleTop = true
                    restoreState = true
                }
                libraryViewModel.importFiles(books)
            }
            classified[IncomingImportKind.AUDIOBOOK].orEmpty().takeIf { it.isNotEmpty() }?.let { audio ->
                navController.navigate("audiobooks") {
                    launchSingleTop = true
                    restoreState = true
                }
                runCatching { container.audiobookRepository.importUris(audio) }
            }
            classified[IncomingImportKind.ACSM]?.firstOrNull()?.let { acsmUri = it }
        }
        onIncomingIntentConsumed(intent)
    }
    fun navigatePrimary(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo("library") {
                saveState = true
            }
        }
    }
    XReaderTheme(readerTheme = settings.theme) {
        AppSystemBars(activity = activity, theme = settings.theme)
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("library") {
                LibraryRoute(
                    container = container,
                    viewModel = libraryViewModel,
                    openReaderAt = { bookId, locator ->
                        if (locator == null) {
                            navController.navigate("reader/$bookId")
                        } else {
                            navController.navigate("reader/$bookId?locator=${Uri.encode(locator)}")
                        }
                    },
                    openLibrary = { navigatePrimary("library") },
                    openAnalytics = { navigatePrimary("analytics") },
                    openAudiobooks = { navigatePrimary("audiobooks") },
                    openNotes = { navigatePrimary("notes") },
                    openSettings = { navigatePrimary("settings") },
                    currentTheme = settings.theme,
                    onToggleTheme = settingsViewModel::toggleLightDark
                )
            }
            composable(
                route = "reader/{bookId}?locator={locator}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("locator") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                ReaderRoute(
                    bookId = entry.arguments?.getLong("bookId") ?: 0L,
                    initialLocatorJson = entry.arguments?.getString("locator")?.takeIf { it.isNotBlank() },
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("analytics") {
                AnalyticsRoute(
                    container = container,
                    bottomBar = {
                        PrimaryBottomBar(
                            selectedTab = AppTab.STATS,
                            navigatePrimary = ::navigatePrimary
                        )
                    }
                )
            }
            composable("audiobooks") {
                AudiobooksRoute(
                    container = container,
                    openReaderAt = { bookId -> navController.navigate("reader/$bookId") },
                    bottomBar = {
                        PrimaryBottomBar(
                            selectedTab = AppTab.AUDIO,
                            navigatePrimary = ::navigatePrimary
                        )
                    }
                )
            }
            composable("notes") {
                NotesRoute(
                    container = container,
                    openReaderAt = { bookId, locator ->
                        navController.navigate("reader/$bookId?locator=${Uri.encode(locator)}")
                    },
                    bottomBar = {
                        PrimaryBottomBar(
                            selectedTab = AppTab.NOTES,
                            navigatePrimary = ::navigatePrimary
                        )
                    }
                )
            }
            composable("settings") {
                SettingsRoute(
                    viewModel = settingsViewModel,
                    bottomBar = {
                        PrimaryBottomBar(
                            selectedTab = AppTab.SETTINGS,
                            navigatePrimary = ::navigatePrimary
                        )
                    }
                )
            }
        }
        acsmUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { acsmUri = null },
                title = { Text("Adobe license file") },
                text = {
                    Text("This ACSM file contains loan instructions, not an ebook. XReader does not remove DRM or fulfill Adobe loans. You can hand it to an authorized Adobe-compatible app.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        val view = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, ACSM_MIME_TYPE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(Intent.createChooser(view, "Open ACSM with")) }
                        acsmUri = null
                    }) { Text("Open externally") }
                },
                dismissButton = { TextButton(onClick = { acsmUri = null }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun PrimaryBottomBar(
    selectedTab: AppTab,
    navigatePrimary: (String) -> Unit,
) {
    AppBottomBar(
        selectedTab = selectedTab,
        openLibrary = { navigatePrimary("library") },
        openAnalytics = { navigatePrimary("analytics") },
        openAudiobooks = { navigatePrimary("audiobooks") },
        openNotes = { navigatePrimary("notes") },
        openSettings = { navigatePrimary("settings") }
    )
}
