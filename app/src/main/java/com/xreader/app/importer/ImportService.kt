package com.xreader.app.importer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.xreader.app.core.TextTools
import com.xreader.app.data.BookDao
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.SearchDao
import com.xreader.app.data.SearchIndexEntity
import com.xreader.app.data.XReaderDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale
import kotlin.math.roundToInt

class ImportService(
    private val context: Context,
    private val database: XReaderDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val bookDao: BookDao = database.books()
    private val searchDao: SearchDao = database.search()
    data class ImportResult(
        val bookId: Long,
        val duplicate: Boolean,
    )

    data class ImportBatchResult(
        val scanned: Int,
        val imported: Int,
        val duplicates: Int,
        val unsupported: Int,
        val failed: Int,
    )

    data class LibraryRepairResult(
        val scanned: Int,
        val coversUpdated: Int,
        val metadataUpdated: Int,
        val searchRows: Int,
        val failed: Int,
    )

    data class BookRepairResult(
        val bookId: Long,
        val coverUpdated: Boolean,
        val metadataUpdated: Boolean,
        val searchRows: Int,
        val failed: Boolean,
    )

    data class BookHealth(
        val bookId: Long,
        val fileAvailable: Boolean,
        val coverAvailable: Boolean,
        val searchRows: Int,
    )

    private val epubParser = EpubParser()
    private val txtConverter = TxtToEpubConverter()
    private val cbzConverter = CbzToEpubConverter()
    private val fb2Converter = Fb2ToEpubConverter()
    private val rtfConverter = RtfToEpubConverter()
    private val odtConverter = OdtToEpubConverter()
    private val pdfTools = PdfTools(context)

    suspend fun importMany(uris: List<Uri>): ImportBatchResult = withContext(Dispatchers.IO) {
        importUris(uris.distinct())
    }

    suspend fun importFolder(treeUri: Uri): ImportBatchResult = withContext(Dispatchers.IO) {
        val scan = scanTreeForBooks(treeUri)
        val imports = importUris(scan.bookUris.distinct())
        imports.copy(
            scanned = imports.scanned + scan.unsupportedFiles,
            unsupported = imports.unsupported + scan.unsupportedFiles
        )
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val displayName = context.contentResolver.displayName(uri)
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val sourceExtension = sourceExtension(displayName, mimeType)
        require(sourceExtension in SUPPORTED_BOOK_EXTENSIONS) {
            "Unsupported file type: .$sourceExtension"
        }

        val safeName = displayName.replace(Regex("""[^\w.\- ]"""), "_")
        val tmp = File(context.cacheDir, "import-${System.nanoTime()}-$safeName")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open selected file." }
            tmp.outputStream().buffered().use { output -> input.copyTo(output) }
        }

        val checksum = sha256(tmp)
        bookDao.getByChecksum(checksum)?.let {
            tmp.delete()
            return@withContext ImportResult(it.id, duplicate = true)
        }

        val now = clock.millis()
        val libraryDir = File(context.filesDir, "library/books").apply { mkdirs() }
        val stagingDir = File(context.filesDir, "library/tmp").apply { mkdirs() }
        val storedFormat = when (sourceExtension) {
            "pdf" -> BookFormat.PDF
            else -> BookFormat.EPUB
        }
        val storedExtension = when (storedFormat) {
            BookFormat.PDF -> "pdf"
            BookFormat.EPUB -> "epub"
        }
        val storedFile = File(libraryDir, "$checksum.$storedExtension")
        val stagedFile = File(stagingDir, "$checksum-${System.nanoTime()}.$storedExtension")

        try {
            val convertedPageCount = when (sourceExtension) {
                "txt" -> {
                    txtConverter.convert(tmp, stagedFile, sourceTitle(displayName, sourceExtension))
                    null
                }
                "cbz" -> {
                    cbzConverter.convert(tmp, stagedFile, sourceTitle(displayName, sourceExtension)).pageCount
                }
                "fb2", "fb2.zip" -> {
                    fb2Converter.convert(tmp, stagedFile, sourceTitle(displayName, sourceExtension))
                    null
                }
                "rtf" -> {
                    rtfConverter.convert(tmp, stagedFile, sourceTitle(displayName, sourceExtension))
                    null
                }
                "odt" -> {
                    odtConverter.convert(tmp, stagedFile, sourceTitle(displayName, sourceExtension))
                    null
                }
                else -> {
                    tmp.copyTo(stagedFile, overwrite = true)
                    null
                }
            }
            tmp.delete()

            val parsed = parseStoredBook(
                file = stagedFile,
                format = storedFormat,
                fallbackTitle = sourceTitle(displayName, sourceExtension),
                fallbackAuthor = "Unknown Author"
            )

            stagedFile.copyTo(storedFile, overwrite = true)
            stagedFile.delete()
            val coverFile = parsed.coverImage?.let { cover ->
                File(context.filesDir, writeCover(checksum, cover))
            }

            val wordCount = parsed.units.sumOf { it.wordCount }
            val book = BookEntity(
                title = TextTools.cleanTitle(parsed.title),
                author = parsed.author.ifBlank { "Unknown Author" },
                sortTitle = TextTools.sortTitle(parsed.title),
                genre = parsed.genre?.takeIf { it.isNotBlank() },
                year = parsed.year,
                series = parsed.series,
                seriesIndex = parsed.seriesIndex,
                description = parsed.description,
                language = parsed.language,
                format = storedFormat,
                sourceExtension = sourceExtension,
                fileName = storedFile.name,
                filePath = storedFile.relativeTo(context.filesDir).path,
                coverImagePath = coverFile?.relativeTo(context.filesDir)?.path,
                checksum = checksum,
                fileSizeBytes = storedFile.length(),
                wordCount = wordCount,
                pageCount = parsed.pageCount ?: convertedPageCount,
                importedAt = now,
                updatedAt = now
            )

            val bookId = try {
                database.withTransaction {
                    val insertedId = bookDao.insert(book)
                    bookDao.insertAuthor(com.xreader.app.data.AuthorEntity(name = book.author))
                    book.genre?.let { bookDao.insertGenre(com.xreader.app.data.GenreEntity(name = it)) }
                    book.series?.let { bookDao.insertSeries(com.xreader.app.data.SeriesEntity(name = it)) }
                    searchDao.replaceForBook(insertedId, parsed.units.toSearchRows(insertedId))
                    insertedId
                }
            } catch (error: Throwable) {
                storedFile.delete()
                coverFile?.delete()
                throw error
            }
            inferMissingSeriesFromTitlesInternal()
            normalizeSeriesOrderInternal()
            ImportResult(bookId, duplicate = false)
        } catch (error: Throwable) {
            tmp.delete()
            stagedFile.delete()
            throw error
        }
    }

    private suspend fun importUris(uris: List<Uri>): ImportBatchResult {
        var imported = 0
        var duplicates = 0
        var unsupported = 0
        var failed = 0
        uris.forEach { uri ->
            runCatching { import(uri) }
                .onSuccess { result ->
                    if (result.duplicate) {
                        duplicates += 1
                    } else {
                        imported += 1
                    }
                }
                .onFailure { error ->
                    if (error.isUnsupportedImport()) {
                        unsupported += 1
                    } else {
                        failed += 1
                    }
                }
        }
        return ImportBatchResult(
            scanned = uris.size,
            imported = imported,
            duplicates = duplicates,
            unsupported = unsupported,
            failed = failed
        )
    }

    suspend fun deleteStoredFile(book: BookEntity) = withContext(Dispatchers.IO) {
        File(context.filesDir, book.filePath).delete()
        book.coverImagePath?.let { File(context.filesDir, it).delete() }
    }

    suspend fun exportStoredFile(book: BookEntity, uri: Uri): Long = withContext(Dispatchers.IO) {
        val source = File(context.filesDir, book.filePath)
        require(source.isFile) { "Book file is missing. Reimport or repair this title before exporting." }
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not open export destination." }
            source.inputStream().buffered().use { input ->
                input.copyTo(output)
            }
        }
        source.length()
    }

    suspend fun backfillLibraryDetails(limit: Int = 50) = withContext(Dispatchers.IO) {
        backfillMissingCoversInternal(limit)
        backfillMetadataInternal(limit)
        inferMissingSeriesFromTitlesInternal()
        normalizeSeriesOrderInternal()
    }

    suspend fun repairLibrary(limit: Int = 500): LibraryRepairResult = withContext(Dispatchers.IO) {
        var scanned = 0
        var coversUpdated = 0
        var metadataUpdated = 0
        var searchRows = 0
        var failed = 0
        bookDao.booksForMaintenance(limit).forEach { book ->
            scanned += 1
            val outcome = repairBookInternal(book)
            if (outcome.failed) {
                failed += 1
                return@forEach
            }
            if (outcome.coverUpdated) coversUpdated += 1
            if (outcome.metadataUpdated) metadataUpdated += 1
            searchRows += outcome.searchRows
        }
        inferMissingSeriesFromTitlesInternal()
        normalizeSeriesOrderInternal()
        LibraryRepairResult(
            scanned = scanned,
            coversUpdated = coversUpdated,
            metadataUpdated = metadataUpdated,
            searchRows = searchRows,
            failed = failed
        )
    }

    suspend fun repairBook(bookId: Long): BookRepairResult = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: error("Book not found.")
        val outcome = repairBookInternal(book)
        if (!outcome.failed) {
            inferMissingSeriesFromTitlesInternal()
            normalizeSeriesOrderInternal()
        }
        outcome
    }

    suspend fun bookHealth(bookId: Long): BookHealth = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: error("Book not found.")
        BookHealth(
            bookId = book.id,
            fileAvailable = File(context.filesDir, book.filePath).exists(),
            coverAvailable = book.coverImagePath
                ?.let { File(context.filesDir, it).exists() }
                ?: false,
            searchRows = searchDao.indexedRowCountForBook(book.id)
        )
    }

    suspend fun backfillMissingCovers(limit: Int = 50) = withContext(Dispatchers.IO) {
        backfillMissingCoversInternal(limit)
    }

    suspend fun replaceCover(book: BookEntity, uri: Uri): String = withContext(Dispatchers.IO) {
        val cover = decodeCustomCover(uri)
        val coverPath = writeCustomCover(book.checksum, cover)
        bookDao.setCoverImagePath(book.id, coverPath, clock.millis())
        book.coverImagePath
            ?.takeIf { it != coverPath }
            ?.let { File(context.filesDir, it).delete() }
        coverPath
    }

    private suspend fun backfillMissingCoversInternal(limit: Int) {
        bookDao.booksMissingCovers().take(limit).forEach { book ->
            val file = File(context.filesDir, book.filePath)
            if (!file.exists()) return@forEach
            val cover = runCatching {
                when (book.format) {
                    BookFormat.EPUB -> epubParser.parseCover(file)
                    BookFormat.PDF -> pdfTools.renderCover(file)
                }
            }.getOrNull() ?: return@forEach
            val coverPath = writeCover(book.checksum, cover)
            bookDao.setCoverImagePath(book.id, coverPath, clock.millis())
        }
    }

    private suspend fun backfillMetadataInternal(limit: Int) {
        bookDao.epubBooksForMetadataAudit(limit).forEach { book ->
            val file = File(context.filesDir, book.filePath)
            if (!file.exists()) return@forEach
            val metadata = runCatching { epubParser.readMetadata(file) }.getOrNull() ?: return@forEach
            val genre = if (PublicationMetadataTools.shouldReplaceGenre(book.genre, metadata.genre)) {
                metadata.genre
            } else {
                book.genre
            }
            val series = book.series ?: metadata.series
            val seriesIndex = book.seriesIndex ?: metadata.seriesIndex
            val year = book.year ?: metadata.year
            if (genre == book.genre && series == book.series && seriesIndex == book.seriesIndex && year == book.year) {
                return@forEach
            }
            val updated = book.copy(
                genre = genre,
                series = series,
                seriesIndex = seriesIndex,
                year = year,
                updatedAt = clock.millis()
            )
            database.withTransaction {
                bookDao.update(updated)
                updated.genre?.let { bookDao.insertGenre(com.xreader.app.data.GenreEntity(name = it)) }
                updated.series?.let { bookDao.insertSeries(com.xreader.app.data.SeriesEntity(name = it)) }
            }
        }
    }

    private suspend fun normalizeSeriesOrderInternal() {
        bookDao.booksWithSeriesForNormalization()
            .groupBy { book ->
                "${book.author.trim().lowercase(Locale.US)}\u0000${book.series.orEmpty().trim().lowercase(Locale.US)}"
            }
            .values
            .filter { it.size > 1 }
            .forEach { books ->
                val chronological = books.sortedWith(
                    compareBy<BookEntity> { it.year ?: Int.MAX_VALUE }
                        .thenBy { it.sortTitle.lowercase(Locale.US) }
                        .thenBy { it.importedAt }
                )
                val indexed = books
                    .filter { it.seriesIndex != null }
                    .sortedWith(
                        compareBy<BookEntity> { it.seriesIndex ?: Double.MAX_VALUE }
                            .thenBy { it.year ?: Int.MAX_VALUE }
                            .thenBy { it.sortTitle.lowercase(Locale.US) }
                    )
                val missingIndex = books.any { it.seriesIndex == null }
                val duplicateIndex = indexed.mapNotNull { it.seriesIndex }.distinct().size != indexed.size
                val indexConflictsWithYear = indexed.size == books.size && indexed.map { it.id } != chronological.map { it.id }
                if (!missingIndex && !duplicateIndex && !indexConflictsWithYear) return@forEach

                database.withTransaction {
                    chronological.forEachIndexed { index, book ->
                        val expected = (index + 1).toDouble()
                        if (book.seriesIndex != expected) {
                            bookDao.update(book.copy(seriesIndex = expected, updatedAt = clock.millis()))
                        }
                    }
                }
            }
    }

    private suspend fun inferMissingSeriesFromTitlesInternal() {
        val books = bookDao.booksForSeriesInference()
        val seriesByAuthor = books
            .mapNotNull { book ->
                val series = book.series?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                book.author.trim().lowercase(Locale.US) to series
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.distinctBy { it.lowercase(Locale.US) } }
        books
            .filter { it.series.isNullOrBlank() }
            .forEach { book ->
                val series = seriesByAuthor[book.author.trim().lowercase(Locale.US)]
                    ?.firstOrNull { it.equals(book.title, ignoreCase = true) }
                    ?: return@forEach
                bookDao.update(book.copy(series = series, updatedAt = clock.millis()))
                bookDao.insertSeries(com.xreader.app.data.SeriesEntity(name = series))
            }
    }

    private fun parseStoredBook(
        file: File,
        format: BookFormat,
        fallbackTitle: String,
        fallbackAuthor: String,
    ): ParsedImport =
        when (format) {
            BookFormat.EPUB -> {
                val epub = epubParser.parse(file)
                ParsedImport(
                    title = epub.metadata.title ?: fallbackTitle,
                    author = epub.metadata.author ?: fallbackAuthor,
                    year = epub.metadata.year,
                    genre = epub.metadata.genre,
                    series = epub.metadata.series,
                    seriesIndex = epub.metadata.seriesIndex,
                    description = epub.metadata.description,
                    language = epub.metadata.language,
                    units = epub.units,
                    pageCount = null,
                    coverImage = epub.coverImage
                )
            }
            BookFormat.PDF -> {
                val pdf = pdfTools.parse(file)
                ParsedImport(
                    title = pdf.metadata.title ?: fallbackTitle,
                    author = pdf.metadata.author ?: fallbackAuthor,
                    year = pdf.metadata.year,
                    genre = pdf.metadata.genre,
                    series = pdf.metadata.series,
                    seriesIndex = pdf.metadata.seriesIndex,
                    description = pdf.metadata.description,
                    language = pdf.metadata.language,
                    units = pdf.units,
                    pageCount = pdf.pageCount,
                    coverImage = pdf.coverImage
                )
            }
        }

    private fun BookEntity.repairedCopy(
        parsed: ParsedImport,
        refreshedCoverPath: String?,
        fileSizeBytes: Long,
    ): BookEntity {
        val repaired = copy(
            genre = if (PublicationMetadataTools.shouldReplaceGenre(genre, parsed.genre)) parsed.genre else genre,
            year = year ?: parsed.year,
            series = series ?: parsed.series,
            seriesIndex = seriesIndex ?: parsed.seriesIndex,
            description = description ?: parsed.description,
            language = language ?: parsed.language,
            coverImagePath = refreshedCoverPath ?: coverImagePath,
            fileSizeBytes = fileSizeBytes,
            wordCount = parsed.units.sumOf { it.wordCount },
            pageCount = parsed.pageCount ?: pageCount,
        )
        return if (repaired == this) this else repaired.copy(updatedAt = clock.millis())
    }

    private suspend fun repairBookInternal(book: BookEntity): BookRepairResult {
        val file = File(context.filesDir, book.filePath)
        if (!file.exists()) {
            return BookRepairResult(
                bookId = book.id,
                coverUpdated = false,
                metadataUpdated = false,
                searchRows = 0,
                failed = true
            )
        }
        return runCatching {
            val parsed = parseStoredBook(
                file = file,
                format = book.format,
                fallbackTitle = book.title,
                fallbackAuthor = book.author
            )
            val refreshedCoverPath = if (book.coverImagePath?.contains("-custom-") == true) {
                null
            } else {
                parsed.coverImage?.let { writeCover(book.checksum, it) }
            }
            val rows = parsed.units.toSearchRows(book.id)
            val updated = book.repairedCopy(parsed, refreshedCoverPath, file.length())
            val changedMetadata = updated != book
            database.withTransaction {
                if (changedMetadata) bookDao.update(updated)
                updated.genre?.let { bookDao.insertGenre(com.xreader.app.data.GenreEntity(name = it)) }
                updated.series?.let { bookDao.insertSeries(com.xreader.app.data.SeriesEntity(name = it)) }
                searchDao.replaceForBook(book.id, rows)
            }
            BookRepairResult(
                bookId = book.id,
                coverUpdated = refreshedCoverPath != null,
                metadataUpdated = changedMetadata,
                searchRows = rows.size,
                failed = false
            )
        }.getOrElse {
            BookRepairResult(
                bookId = book.id,
                coverUpdated = false,
                metadataUpdated = false,
                searchRows = 0,
                failed = true
            )
        }
    }

    private data class ParsedImport(
        val title: String,
        val author: String,
        val year: Int?,
        val genre: String?,
        val series: String?,
        val seriesIndex: Double?,
        val description: String?,
        val language: String?,
        val units: List<com.xreader.app.reader.ReadingUnit>,
        val pageCount: Int?,
        val coverImage: EpubParser.CoverImage?,
    )

    private fun List<com.xreader.app.reader.ReadingUnit>.toSearchRows(bookId: Long): List<SearchIndexEntity> =
        mapNotNull { unit ->
            unit.body.takeIf { it.isNotBlank() }?.let {
                SearchIndexEntity(
                    bookId = bookId,
                    locator = unit.locator,
                    heading = unit.heading,
                    body = it,
                    normalizedBody = TextTools.normalizeForSearch(it),
                    unitIndex = unit.index
                )
            }
        }

    private fun writeCover(checksum: String, cover: EpubParser.CoverImage): String {
        val target = File(context.filesDir, "library/covers/$checksum.${cover.extension}")
        target.parentFile?.mkdirs()
        target.writeBytes(cover.bytes)
        return target.relativeTo(context.filesDir).path
    }

    private fun writeCustomCover(checksum: String, cover: EpubParser.CoverImage): String {
        val target = File(context.filesDir, "library/covers/$checksum-custom-${clock.millis()}.${cover.extension}")
        target.parentFile?.mkdirs()
        target.writeBytes(cover.bytes)
        return target.relativeTo(context.filesDir).path
    }

    private fun decodeCustomCover(uri: Uri): EpubParser.CoverImage {
        val tmp = File(context.cacheDir, "cover-${System.nanoTime()}")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open selected cover image." }
                tmp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "Selected file is not a readable image."
            }
            val decoded = BitmapFactory.decodeFile(
                tmp.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = coverSampleSize(bounds.outWidth, bounds.outHeight)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: error("Selected file is not a readable image.")
            val scaled = decoded.scaledToMaxEdge(CUSTOM_COVER_MAX_EDGE)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, CUSTOM_COVER_JPEG_QUALITY, output)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            return EpubParser.CoverImage(output.toByteArray(), "jpg")
        } finally {
            tmp.delete()
        }
    }

    private fun coverSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > CUSTOM_COVER_MAX_EDGE * 2 || height / sample > CUSTOM_COVER_MAX_EDGE * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun Bitmap.scaledToMaxEdge(maxEdge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return this
        val scale = maxEdge.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun ContentResolver.displayName(uri: Uri): String {
        runCatching {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "book"
    }

    private data class FolderScanResult(
        val bookUris: List<Uri>,
        val unsupportedFiles: Int,
    )

    private fun scanTreeForBooks(treeUri: Uri): FolderScanResult {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        val bookUris = mutableListOf<Uri>()
        var unsupportedFiles = 0
        val visitedDirectories = mutableSetOf<String>()
        fun scanDirectory(directoryUri: Uri) {
            val directoryId = DocumentsContract.getDocumentId(directoryUri)
            if (!visitedDirectories.add(directoryId)) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val displayName = cursor.getString(nameIndex).orEmpty()
                    val mimeType = cursor.getString(mimeIndex).orEmpty()
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanDirectory(documentUri)
                    } else if (isSupportedBook(displayName, mimeType)) {
                        bookUris += documentUri
                    } else {
                        unsupportedFiles += 1
                    }
                }
            }
        }
        scanDirectory(rootDocumentUri)
        return FolderScanResult(bookUris = bookUris, unsupportedFiles = unsupportedFiles)
    }

    private fun isSupportedBook(displayName: String, mimeType: String): Boolean =
        sourceExtension(displayName, mimeType) in SUPPORTED_BOOK_EXTENSIONS ||
            mimeType in SUPPORTED_BOOK_MIME_TYPES

    private fun Throwable.isUnsupportedImport(): Boolean =
        message?.startsWith("Unsupported file type:") == true

    private fun sourceExtension(displayName: String, mimeType: String = ""): String {
        val lower = displayName.lowercase(Locale.US)
        return when {
            lower.endsWith(".fb2.zip") -> "fb2.zip"
            else -> TextTools.extension(displayName).ifBlank { extensionForMimeType(mimeType) }
        }
    }

    private fun extensionForMimeType(mimeType: String): String =
        when (mimeType.lowercase(Locale.US)) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "application/rtf", "text/rtf", "application/x-rtf" -> "rtf"
            "application/vnd.oasis.opendocument.text" -> "odt"
            "application/x-fictionbook+xml", "application/fb2+xml", "text/fb2+xml" -> "fb2"
            else -> ""
        }

    private fun sourceTitle(displayName: String, sourceExtension: String): String =
        when (sourceExtension) {
            "fb2.zip" -> displayName.dropLast(".fb2.zip".length)
            else -> displayName.substringBeforeLast('.')
        }.ifBlank { displayName }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val SUPPORTED_BOOK_EXTENSIONS = setOf("epub", "pdf", "txt", "cbz", "fb2", "fb2.zip", "rtf", "odt")
        val SUPPORTED_BOOK_MIME_TYPES = setOf(
            "application/epub+zip",
            "application/pdf",
            "text/plain",
            "application/rtf",
            "text/rtf",
            "application/x-rtf",
            "application/vnd.oasis.opendocument.text",
            "application/zip",
            "application/x-cbz",
            "application/vnd.comicbook+zip",
            "application/x-fictionbook+xml",
            "application/fb2+xml",
            "text/fb2+xml"
        )
        const val CUSTOM_COVER_MAX_EDGE = 1400
        const val CUSTOM_COVER_JPEG_QUALITY = 90
    }
}
