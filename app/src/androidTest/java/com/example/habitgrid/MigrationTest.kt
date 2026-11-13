package com.example.habitgrid

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that real user data at schema v5 survives the upgrade to v6. Run on a device/emulator. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate5To6_keepsDataAndDropsOrphans() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO habits (id, name, createdAt, colorArgb, targetPerWeek, sortOrder) VALUES (1, 'Read', 0, 4281979731, 5, 0)")
            execSQL("INSERT INTO completions (habitId, dateEpochDay) VALUES (1, 20000)")
            execSQL("INSERT INTO completions (habitId, dateEpochDay) VALUES (1, 20001)")
            execSQL("INSERT INTO completions (habitId, dateEpochDay) VALUES (99, 20000)") // orphan
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, AppDatabase.MIGRATION_5_6)

        db.query("SELECT name, targetPerWeek, scheduleDays, reminderMinutes, archived FROM habits").use {
            it.moveToFirst()
            assertEquals("Read", it.getString(0))
            assertEquals(5, it.getInt(1))
            assertEquals(0, it.getInt(2))
            assertEquals(true, it.isNull(3))
            assertEquals(0, it.getInt(4))
        }
        db.query("SELECT COUNT(*) FROM completions").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
        }
        // Cascade works after migration.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM habits WHERE id = 1")
        db.query("SELECT COUNT(*) FROM completions").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }
}
