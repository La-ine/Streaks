package com.example.habitgrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {

    @Test fun `round trip keeps habits and completions`() {
        val habits = listOf(
            Habit(id = 7, name = "Read", createdAt = 1_700_000_000_000, colorArgb = 0xFF339AF0,
                targetPerWeek = 3, sortOrder = 0, scheduleDays = 0, reminderMinutes = 20 * 60),
            Habit(id = 9, name = "Gym", createdAt = 1_700_000_100_000, scheduleDays = 0b0010101,
                sortOrder = 1, archived = true)
        )
        val completions = listOf(
            HabitCompletion(habitId = 7, dateEpochDay = 20_000),
            HabitCompletion(habitId = 7, dateEpochDay = 20_001),
            HabitCompletion(habitId = 9, dateEpochDay = 20_002)
        )

        val restored = Backup.fromJson(Backup.toJson(habits, completions, exportedAt = 0))

        assertEquals(2, restored.size)
        val (read, readDays) = restored[0]
        assertEquals(habits[0].copy(id = 0), read.copy(id = 0))
        assertEquals(listOf(20_000L, 20_001L), readDays)
        val (gym, gymDays) = restored[1]
        assertEquals(true, gym.archived)
        assertEquals(0b0010101, gym.scheduleDays)
        assertNull(gym.reminderMinutes)
        assertEquals(listOf(20_002L), gymDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-json`() {
        Backup.fromJson("not json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects json from another app`() {
        Backup.fromJson("""{"habits": []}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects newer backup versions`() {
        Backup.fromJson("""{"format":"habitgrid-backup","version":99,"habits":[]}""")
    }
}
