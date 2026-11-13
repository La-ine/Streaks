package com.diongue.streaks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Habit::class, HabitCompletion::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "habit_database"
                )
                    .addMigrations(MIGRATION_5_6)
                    // Only versions older than 5 (pre-release builds) may be wiped; real data is migrated.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
                    .build().also { INSTANCE = it }
            }
        }

        /**
         * v6: schedule days, reminders and archiving on habits; completions get a
         * foreign key with ON DELETE CASCADE (SQLite needs a table rebuild for that).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `scheduleDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `reminderMinutes` INTEGER")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `completions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habitId` INTEGER NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                // Drops completions orphaned by habits deleted before cascading existed.
                db.execSQL(
                    "INSERT INTO `completions_new` (`id`, `habitId`, `dateEpochDay`) " +
                        "SELECT `id`, `habitId`, `dateEpochDay` FROM `completions` " +
                        "WHERE `habitId` IN (SELECT `id` FROM `habits`)"
                )
                db.execSQL("DROP TABLE `completions`")
                db.execSQL("ALTER TABLE `completions_new` RENAME TO `completions`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_completions_habitId_dateEpochDay` " +
                        "ON `completions` (`habitId`, `dateEpochDay`)"
                )
            }
        }
    }
}
