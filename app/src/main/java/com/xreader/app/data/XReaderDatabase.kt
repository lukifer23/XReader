package com.xreader.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val XREADER_DATABASE_VERSION = 16

@Database(
    entities = [
        BookEntity::class,
        AuthorEntity::class,
        SeriesEntity::class,
        GenreEntity::class,
        CollectionEntity::class,
        BookCollectionEntity::class,
        ReadingStateEntity::class,
        ReadingSessionEntity::class,
        NeuralTtsModelEntity::class,
        BookAudioEntity::class,
        ImportedAudiobookEntity::class,
        AudiobookTrackEntity::class,
        AudiobookChapterEntity::class,
        AudiobookBookmarkEntity::class,
        AudiobookCollectionEntity::class,
        PronunciationRuleEntity::class,
        NarrationOverrideEntity::class,
        RestoreOperationEntity::class,
        AnnotationEntity::class,
        BookmarkEntity::class,
        SearchIndexEntity::class,
        SearchIndexFtsEntity::class,
        DictionaryEntryEntity::class
    ],
    version = XREADER_DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class XReaderDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun collections(): CollectionDao
    abstract fun reading(): ReadingDao
    abstract fun annotations(): AnnotationDao
    abstract fun search(): SearchDao
    abstract fun dictionary(): DictionaryDao
    abstract fun neuralTts(): NeuralTtsDao
    abstract fun audiobooks(): AudiobookDao
    abstract fun narration(): NarrationDao
    abstract fun restoreOperations(): RestoreOperationDao

    companion object {
        @Volatile private var instance: XReaderDatabase? = null

        fun get(context: Context): XReaderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    XReaderDatabase::class.java,
                    "xreader.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigration(false)
                    .enableMultiInstanceInvalidation()
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverImagePath TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT COLLATE NOCASE NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_name ON collections(name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS book_collections (
                        bookId INTEGER NOT NULL,
                        collectionId INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(bookId, collectionId),
                        FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(collectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_collections_bookId ON book_collections(bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_collections_collectionId ON book_collections(collectionId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN readabilityScore REAL")
                db.execSQL("ALTER TABLE books ADD COLUMN readabilityGradeLevel REAL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS neural_tts_models (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        modelId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        engine TEXT NOT NULL,
                        status TEXT NOT NULL,
                        localPath TEXT,
                        downloadedBytes INTEGER NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        checksumSha256 TEXT,
                        installedAt INTEGER,
                        updatedAt INTEGER NOT NULL,
                        error TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_neural_tts_models_modelId ON neural_tts_models(modelId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS book_audio (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        modelId TEXT NOT NULL,
                        modelDisplayName TEXT NOT NULL,
                        speakerId INTEGER NOT NULL,
                        speed REAL NOT NULL,
                        status TEXT NOT NULL,
                        filePath TEXT,
                        segmentCount INTEGER NOT NULL,
                        wordCount INTEGER NOT NULL,
                        sampleRate INTEGER NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        generatedAt INTEGER,
                        updatedAt INTEGER NOT NULL,
                        error TEXT,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_audio_bookId ON book_audio(bookId)")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_book_audio_bookId_modelId_speakerId_speed
                    ON book_audio(bookId, modelId, speakerId, speed)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_audio ADD COLUMN tone TEXT NOT NULL DEFAULT 'NATURAL'")
                db.execSQL("DROP INDEX IF EXISTS index_book_audio_bookId_modelId_speakerId_speed")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_book_audio_bookId_modelId_speakerId_speed_tone
                    ON book_audio(bookId, modelId, speakerId, speed, tone)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_audio ADD COLUMN completedSegments INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_audio ADD COLUMN playbackSegmentIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_audio ADD COLUMN playbackPositionMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_audio ADD COLUMN generationStartedAt INTEGER")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_audio ADD COLUMN scope TEXT NOT NULL DEFAULT 'FULL_BOOK'")
                db.execSQL("ALTER TABLE book_audio ADD COLUMN scopeLabel TEXT NOT NULL DEFAULT 'Full book'")
                db.execSQL("DROP INDEX IF EXISTS index_book_audio_bookId_modelId_speakerId_speed_tone")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_book_audio_bookId_modelId_speakerId_speed_tone_scope
                    ON book_audio(bookId, modelId, speakerId, speed, tone, scope)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_audio ADD COLUMN generationSessionStartCompletedSegments INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_audio ADD COLUMN generationProvider TEXT")
                db.execSQL("ALTER TABLE book_audio ADD COLUMN generationAudioMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_audio ADD COLUMN generationComputeMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_audio_modelId ON book_audio(modelId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_audio_status ON book_audio(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_audio_updatedAt ON book_audio(updatedAt)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_audio_completedSegments ON book_audio(completedSegments)")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_search_index_normalizedBody")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS imported_audiobooks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        sortTitle TEXT NOT NULL,
                        narrator TEXT,
                        series TEXT,
                        seriesIndex REAL,
                        description TEXT,
                        language TEXT,
                        isbn TEXT,
                        checksum TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        sourceExtension TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        coverImagePath TEXT,
                        durationMs INTEGER NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        trackCount INTEGER NOT NULL,
                        linkedBookId INTEGER,
                        playbackTrackIndex INTEGER NOT NULL,
                        playbackPositionMs INTEGER NOT NULL,
                        playbackSpeed REAL NOT NULL,
                        importedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastPlayedAt INTEGER,
                        favorite INTEGER NOT NULL,
                        finished INTEGER NOT NULL,
                        FOREIGN KEY(linkedBookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_imported_audiobooks_checksum ON imported_audiobooks(checksum)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_audiobooks_linkedBookId ON imported_audiobooks(linkedBookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_audiobooks_title ON imported_audiobooks(title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_audiobooks_author ON imported_audiobooks(author)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_audiobooks_series ON imported_audiobooks(series)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_audiobooks_updatedAt ON imported_audiobooks(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_imported_audiobooks_lastPlayedAt ON imported_audiobooks(lastPlayedAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audiobook_tracks (
                        audiobookId INTEGER NOT NULL,
                        trackIndex INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        discNumber INTEGER,
                        trackNumber INTEGER,
                        PRIMARY KEY(audiobookId, trackIndex),
                        FOREIGN KEY(audiobookId) REFERENCES imported_audiobooks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_tracks_audiobookId ON audiobook_tracks(audiobookId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audiobook_chapters (
                        audiobookId INTEGER NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        trackIndex INTEGER NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        PRIMARY KEY(audiobookId, chapterIndex),
                        FOREIGN KEY(audiobookId) REFERENCES imported_audiobooks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_chapters_audiobookId ON audiobook_chapters(audiobookId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audiobook_bookmarks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        audiobookId INTEGER NOT NULL,
                        trackIndex INTEGER NOT NULL,
                        positionMs INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(audiobookId) REFERENCES imported_audiobooks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_bookmarks_audiobookId ON audiobook_bookmarks(audiobookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_bookmarks_createdAt ON audiobook_bookmarks(createdAt)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_audiobook_bookmarks_audiobookId_trackIndex_positionMs_createdAt ON audiobook_bookmarks(audiobookId, trackIndex, positionMs, createdAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audiobook_collections (
                        audiobookId INTEGER NOT NULL,
                        collectionId INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(audiobookId, collectionId),
                        FOREIGN KEY(audiobookId) REFERENCES imported_audiobooks(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(collectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_collections_audiobookId ON audiobook_collections(audiobookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_collections_collectionId ON audiobook_collections(collectionId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pronunciation_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER,
                        languageTag TEXT NOT NULL,
                        phrase TEXT NOT NULL,
                        replacement TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pronunciation_rules_bookId ON pronunciation_rules(bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pronunciation_rules_languageTag ON pronunciation_rules(languageTag)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS narration_overrides (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        sourceKey TEXT NOT NULL,
                        `include` INTEGER NOT NULL,
                        replacementText TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_narration_overrides_bookId ON narration_overrides(bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_narration_overrides_sourceKey ON narration_overrides(sourceKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_narration_overrides_bookId_sourceKey ON narration_overrides(bookId, sourceKey)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS restore_operations (
                        operationId TEXT NOT NULL,
                        planSha256 TEXT NOT NULL,
                        formatVersion INTEGER NOT NULL,
                        committedAt INTEGER NOT NULL,
                        PRIMARY KEY(operationId)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
        )
    }
}
