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
        ReadingStateEntity::class,
        ReadingSessionEntity::class,
        AnnotationEntity::class,
        BookmarkEntity::class,
        SearchIndexEntity::class,
        SearchIndexFtsEntity::class,
        DictionaryEntryEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class XReaderDatabase : RoomDatabase() {
    abstract fun books(): BookDao
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
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverImagePath TEXT")
            }
        }
    }
}
