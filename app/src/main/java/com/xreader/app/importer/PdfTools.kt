package com.xreader.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.xreader.app.core.TextTools
import com.xreader.app.reader.PublicationMetadata
import com.xreader.app.reader.ReadingUnit
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

class PdfTools(private val context: Context) {
    data class ParsedPdf(
        val metadata: PublicationMetadata,
        val pageCount: Int,
        val units: List<ReadingUnit>,
        val coverImage: EpubParser.CoverImage?,
    )

    fun parse(file: File): ParsedPdf {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file).use { document ->
            val info = document.documentInformation
            val title = info.title?.takeIf { it.isNotBlank() }
            val author = info.author?.takeIf { it.isNotBlank() }
            val subject = info.subject?.takeIf { it.isNotBlank() }
            val genre = PublicationMetadataTools.cleanGenre(listOfNotNull(subject))
            val year = info.creationDate?.get(java.util.Calendar.YEAR)
            val stripper = PDFTextStripper()
            val pages = (1..document.numberOfPages).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(document).replace(Regex("\\s+"), " ").trim()
                ReadingUnit(
                    index = page - 1,
                    locator = "pdf:${page - 1}",
                    heading = "Page $page",
                    body = text,
                    wordCount = TextTools.wordCount(text)
                )
            }
            return ParsedPdf(
                metadata = PublicationMetadata(title, author, null, subject, genre, year, null, null),
                pageCount = document.numberOfPages,
                units = pages,
                coverImage = renderCover(file)
            )
        }
    }

    fun renderCover(file: File): EpubParser.CoverImage? =
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        if (page.width <= 0 || page.height <= 0) return@runCatching null
                        val scale = (480.0 / page.width.toDouble()).coerceAtMost(1.75)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val output = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)
                        bitmap.recycle()
                        EpubParser.CoverImage(output.toByteArray(), "png")
                    }
                }
            }
        }.getOrNull()
}
