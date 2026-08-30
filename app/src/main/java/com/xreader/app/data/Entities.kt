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

enum class NeuralTtsModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    EXTRACTING,
    INSTALLED,
    FAILED,
}

enum class BookAudioStatus {
    GENERATED,
    GENERATING,
    CANCELED,
    FAILED,
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
    val readabilityScore: Double? = null,
    val readabilityGradeLevel: Double? = null,
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
    tableName = "neural_tts_models",
    indices = [Index(value = ["modelId"], unique = true)]
)
data class NeuralTtsModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelId: String,
    val displayName: String,
    val engine: String,
    val status: NeuralTtsModelStatus,
    val localPath: String? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val checksumSha256: String? = null,
    val installedAt: Long? = null,
    val updatedAt: Long,
    val error: String? = null,
)

@Entity(
    tableName = "book_audio",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["modelId"]),
        Index(value = ["status"]),
        Index(value = ["completedSegments"]),
        Index(value = ["updatedAt"]),
        Index(value = ["bookId", "modelId", "speakerId", "speed", "tone", "scope"], unique = true)
    ]
)
data class BookAudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val modelId: String,
    val modelDisplayName: String,
    val speakerId: Int,
    val speed: Float,
    val tone: String = "NATURAL",
    val scope: String = "FULL_BOOK",
    val scopeLabel: String = "Full book",
    val status: BookAudioStatus,
    val filePath: String? = null,
    val segmentCount: Int = 0,
    val completedSegments: Int = 0,
    val wordCount: Int = 0,
    val sampleRate: Int = 0,
    val fileSizeBytes: Long = 0,
    val generationProvider: String? = null,
    val generationAudioMillis: Long = 0,
    val generationComputeMillis: Long = 0,
    val generationStartedAt: Long? = null,
    val generationSessionStartCompletedSegments: Int = 0,
    val generatedAt: Long? = null,
    val playbackSegmentIndex: Int = 0,
    val playbackPositionMs: Int = 0,
    val updatedAt: Long,
    val error: String? = null,
)

@Entity(
    tableName = "imported_audiobooks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedBookId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["checksum"], unique = true),
        Index(value = ["linkedBookId"]),
        Index(value = ["title"]),
        Index(value = ["author"]),
        Index(value = ["series"]),
        Index(value = ["updatedAt"]),
        Index(value = ["lastPlayedAt"]),
    ]
)
data class ImportedAudiobookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val sortTitle: String,
    val narrator: String? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val checksum: String,
    val fileName: String,
    val filePath: String,
    val sourceExtension: String,
    val mimeType: String,
    val coverImagePath: String? = null,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val trackCount: Int,
    val linkedBookId: Long? = null,
    val playbackTrackIndex: Int = 0,
    val playbackPositionMs: Int = 0,
    val playbackSpeed: Float = 1f,
    val importedAt: Long,
    val updatedAt: Long,
    val lastPlayedAt: Long? = null,
    val favorite: Boolean = false,
    val finished: Boolean = false,
)

@Entity(
    tableName = "audiobook_tracks",
    primaryKeys = ["audiobookId", "trackIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ImportedAudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["audiobookId"])]
)
data class AudiobookTrackEntity(
    val audiobookId: Long,
    val trackIndex: Int,
    val title: String,
    val fileName: String,
    val relativePath: String,
    val checksum: String,
    val mimeType: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
)

@Entity(
    tableName = "audiobook_chapters",
    primaryKeys = ["audiobookId", "chapterIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ImportedAudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["audiobookId"])]
)
data class AudiobookChapterEntity(
    val audiobookId: Long,
    val chapterIndex: Int,
    val title: String,
    val trackIndex: Int,
    val startMs: Long,
    val endMs: Long,
)

@Entity(
    tableName = "audiobook_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ImportedAudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["audiobookId"]),
        Index(value = ["createdAt"]),
        Index(value = ["audiobookId", "trackIndex", "positionMs", "createdAt"], unique = true),
    ]
)
data class AudiobookBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audiobookId: Long,
    val trackIndex: Int,
    val positionMs: Long,
    val label: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "audiobook_collections",
    primaryKeys = ["audiobookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = ImportedAudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["audiobookId"]), Index(value = ["collectionId"])]
)
data class AudiobookCollectionEntity(
    val audiobookId: Long,
    val collectionId: Long,
    val addedAt: Long,
)

@Entity(
    tableName = "pronunciation_rules",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["languageTag"])]
)
data class PronunciationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long? = null,
    val languageTag: String = "en-US",
    val phrase: String,
    val replacement: String,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "narration_overrides",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["sourceKey"]), Index(value = ["bookId", "sourceKey"], unique = true)]
)
data class NarrationOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val sourceKey: String,
    val include: Boolean,
    val replacementText: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "restore_operations")
data class RestoreOperationEntity(
    @PrimaryKey val operationId: String,
    val planSha256: String,
    val formatVersion: Int,
    val committedAt: Long,
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
    indices = [Index(value = ["bookId"])]
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
