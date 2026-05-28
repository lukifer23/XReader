package com.xreader.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xreader.app.data.BookEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BookCoverTile(
    book: BookEntity,
    modifier: Modifier = Modifier,
    width: Dp = 58.dp,
    height: Dp = 82.dp,
) {
    val context = LocalContext.current
    val coverBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = book.coverImagePath,
        key2 = context.filesDir.absolutePath,
    ) {
        val path = book.coverImagePath
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                CoverBitmapCache.load(File(context.filesDir, path), targetMaxPixels = 420)
            }
        }
    }
    val initial = book.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "X"
    Surface(
        modifier = modifier.size(width = width, height = height),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap!!.asImageBitmap(),
                contentDescription = "${book.title} cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                )
                Text(
                    initial,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private object CoverBitmapCache {
    private val maxSizeKb = ((Runtime.getRuntime().maxMemory() / 1024L) / 16L)
        .coerceIn(4 * 1024L, 12 * 1024L)
        .toInt()
    private val cache = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun load(file: File, targetMaxPixels: Int): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$targetMaxPixels"
        cache.get(key)?.let { return it }
        val bitmap = decodeSampled(file, targetMaxPixels) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeSampled(file: File, targetMaxPixels: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetMaxPixels)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun sampleSize(width: Int, height: Int, targetMaxPixels: Int): Int {
        var sample = 1
        var scaledWidth = width
        var scaledHeight = height
        while (scaledWidth / 2 >= targetMaxPixels || scaledHeight / 2 >= targetMaxPixels) {
            sample *= 2
            scaledWidth /= 2
            scaledHeight /= 2
        }
        return sample.coerceAtLeast(1)
    }
}
