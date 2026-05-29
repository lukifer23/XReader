package com.xreader.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookFormat {
    EPUB,
    PDF,
}

enum class AnnotationKind {
    NOTE,
    HIGHLIGHT,
}

enum class ReaderTheme {
    LIGHT,
    DARK,
    SEPIA,
    OLED,
}

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["checksum"], unique = true),
        Index(value = ["title"]),
        Index(value = ["author"]),
        Index(value = ["series"]),
        Index(value = ["genre"]),
        Index(value = ["year"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val sortTitle: String,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val genre: String? = null,
    val year: Int? = null,
    val description: String? = null,
    val language: String? = null,
    val format: BookFormat,
    val sourceExtension: String,
    val fileName: String,
    val filePath: String,
    val coverImagePath: String? = null,
    val checksum: String,
    val fileSizeBytes: Long,
    val wordCount: Int,
    val pageCount: Int? = null,
    val importedAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
    val favorite: Boolean = false,
    val finished: Boolean = false,
)

@Entity(tableName = "authors", indices = [Index(value = ["name"], unique = true)])
data class AuthorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "series", indices = [Index(value = ["name"], unique = true)])
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "genres", indices = [Index(value = ["name"], unique = true)])
data class GenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "book_collections",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["collectionId"])]
)
data class BookCollectionEntity(
    val bookId: Long,
    val collectionId: Long,
    val addedAt: Long,
)

@Entity(
    tableName = "reading_states",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"], unique = true)]
)
data class ReadingStateEntity(
    @PrimaryKey val bookId: Long,
    val locator: String,
    val progress: Double,
    val currentUnit: Int,
    val totalUnits: Int,
    val activeMillis: Long,
    val estimatedWpm: Int,
    val lastReadAt: Long,
    val finishedAt: Long? = null,
)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["startedAt"])]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val activeMillis: Long,
    val startUnit: Int,
    val endUnit: Int,
    val wordsRead: Int,
    val wpm: Int,
)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["locator"])]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val kind: AnnotationKind,
    val locator: String,
    val quote: String,
    val note: String,
    val color: String,
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["locator"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locator: String,
    val label: String,
    val progress: Double,
    val createdAt: Long,
)

@Entity(
    tableName = "search_index",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["normalizedBody"])]
)
data class SearchIndexEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locator: String,
    val heading: String,
    val body: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val normalizedBody: String,
    val unitIndex: Int,
)

@Fts4
@Entity(tableName = "search_index_fts")
data class SearchIndexFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val bookId: String,
    val locator: String,
    val heading: String,
    val body: String,
    val normalizedBody: String,
    val unitIndex: String,
)

@Entity(
    tableName = "dictionary_entries",
    indices = [Index(value = ["lemma"]), Index(value = ["lemma", "partOfSpeech"])]
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val lemma: String,
    val partOfSpeech: String,
    val definition: String,
    val synonyms: String,
)
