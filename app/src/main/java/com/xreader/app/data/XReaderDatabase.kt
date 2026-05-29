package com.xreader.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        AnnotationEntity::class,
        BookmarkEntity::class,
        SearchIndexEntity::class,
        SearchIndexFtsEntity::class,
        DictionaryEntryEntity::class
    ],
    version = 3,
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

    companion object {
        @Volatile private var instance: XReaderDatabase? = null

        fun get(context: Context): XReaderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    XReaderDatabase::class.java,
                    "xreader.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(false)
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
    }
}
