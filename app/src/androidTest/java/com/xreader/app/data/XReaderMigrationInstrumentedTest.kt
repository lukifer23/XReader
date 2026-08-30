package com.xreader.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XReaderMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        XReaderDatabase::class.java,
    )

    @Test
    fun everyHistoricalSchemaMigratesToCurrentWithoutDestructiveFallback() {
        for (version in 1 until 15) {
            val name = "xreader-migration-$version"
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, 15, true, *XReaderDatabase.ALL_MIGRATIONS).close()
        }
    }

    @Test
    fun migration14To15DropsOnlyRedundantNormalizedBodyIndex() {
        val name = "xreader-migration-index"
        helper.createDatabase(name, 14).apply {
            execSQL(
                """INSERT INTO books (
                    id, title, author, sortTitle, series, seriesIndex, genre, year, description, language,
                    format, sourceExtension, fileName, filePath, coverImagePath, checksum, fileSizeBytes,
                    wordCount, pageCount, readabilityScore, readabilityGradeLevel, importedAt, updatedAt,
                    lastOpenedAt, favorite, finished
                ) VALUES (1, 'Migration Book', 'XReader', 'migration book', NULL, NULL, NULL, NULL, NULL, 'en',
                    'EPUB', 'epub', 'migration.epub', '/private/migration.epub', NULL, 'abc123', 42,
                    100, NULL, NULL, NULL, 10, 20, NULL, 1, 0)""".trimIndent(),
            )
            execSQL(
                "INSERT INTO search_index (id, bookId, locator, heading, body, normalizedBody, unitIndex) " +
                    "VALUES (1, 1, 'locator', 'Heading', 'Retained body', 'retained body', 0)",
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 15, true, *XReaderDatabase.ALL_MIGRATIONS)
        migrated.query("PRAGMA index_list('search_index')").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertFalse("Redundant full-text B-tree index must be removed.", "index_search_index_normalizedBody" in names)
        }
        migrated.query("SELECT b.title, s.body FROM books b JOIN search_index s ON s.bookId = b.id").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Migration Book", cursor.getString(0))
            assertEquals("Retained body", cursor.getString(1))
        }
        migrated.close()
    }
}
