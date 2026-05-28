package com.xreader.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.xreader.app.data.ReaderTheme

@Composable
fun XReaderTheme(
    readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme(readerTheme),
        typography = MaterialTheme.typography,
        content = content
    )
}

fun colorScheme(theme: ReaderTheme): ColorScheme =
    when (theme) {
        ReaderTheme.LIGHT -> lightColorScheme(
            primary = Color(0xFF2F6F6B),
            onPrimary = Color.White,
            secondary = Color(0xFF7A5C2E),
            background = Color(0xFFF8F9F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E8E4),
            onSurface = Color(0xFF17201E)
        )
        ReaderTheme.DARK -> darkColorScheme(
            primary = Color(0xFF85D4C7),
            onPrimary = Color(0xFF003B36),
            secondary = Color(0xFFE2C17E),
            background = Color(0xFF111716),
            surface = Color(0xFF18211F),
            surfaceVariant = Color(0xFF2A3532),
            onSurface = Color(0xFFE7ECE9)
        )
        ReaderTheme.SEPIA -> lightColorScheme(
            primary = Color(0xFF6B5936),
            onPrimary = Color.White,
            secondary = Color(0xFF2F6F6B),
            background = Color(0xFFF2E9D8),
            surface = Color(0xFFFFF7E8),
            surfaceVariant = Color(0xFFE8DCC5),
            onSurface = Color(0xFF241E14)
        )
        ReaderTheme.OLED -> darkColorScheme(
            primary = Color(0xFF72D0C5),
            onPrimary = Color(0xFF003B36),
            secondary = Color(0xFFE5C36A),
            background = Color.Black,
            surface = Color(0xFF050505),
            surfaceVariant = Color(0xFF181818),
            onSurface = Color(0xFFEFEFEF)
        )
    }
