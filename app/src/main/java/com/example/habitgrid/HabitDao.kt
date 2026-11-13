package com.example.habitgrid

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HabitDao {
    // ---- Habits ----

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    abstract fun getActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC")
    abstract fun getArchivedHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    abstract fun getHabitFlow(id: Long): Flow<Habit?>

    @Query("SELECT * FROM habits WHERE id = :id")
    abstract suspend fun getHabit(id: Long): Habit?

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    abstract suspend fun getActiveHabitsOnce(): List<Habit>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    abstract suspend fun getAllHabitsOnce(): List<Habit>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits")
    abstract suspend fun nextSortOrder(): Int

    @Insert
    abstract suspend fun insertHabit(habit: Habit): Long

    @Update
    abstract suspend fun updateHabit(habit: Habit)

    @Delete
    abstract suspend fun deleteHabit(habit: Habit)

    @Query("DELETE FROM habits")
    abstract suspend fun deleteAllHabits()

    @Query("UPDATE habits SET sortOrder = :order WHERE id = :id")
    abstract suspend fun setSortOrder(id: Long, order: Int)

    /** Persists a new order by id only, so it can't overwrite concurrent edits to other fields. */
    @Transaction
    open suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    // ---- Completions ----

    @Query("SELECT * FROM completions")
    abstract fun getAllCompletions(): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM completions WHERE habitId = :habitId")
    abstract fun getCompletionsForHabit(habitId: Long): Flow<List<HabitCompletion>>

    @Query("SELECT dateEpochDay FROM completions WHERE habitId = :habitId")
    abstract suspend fun getCompletedDaysOnce(habitId: Long): List<Long>

    @Query("SELECT * FROM completions")
    abstract suspend fun getAllCompletionsOnce(): List<HabitCompletion>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCompletion(completion: HabitCompletion): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCompletions(completions: List<HabitCompletion>)

    @Query("DELETE FROM completions WHERE habitId = :habitId AND dateEpochDay = :day")
    abstract suspend fun deleteCompletion(habitId: Long, day: Long): Int

    /** Flips the completion for [day]. Returns true if the day is now completed. */
    @Transaction
    open suspend fun toggleCompletion(habitId: Long, day: Long): Boolean {
        val removed = deleteCompletion(habitId, day)
        if (removed > 0) return false
        insertCompletion(HabitCompletion(habitId = habitId, dateEpochDay = day))
        return true
    }

    /** Replaces every habit and completion with [habits] (used by backup import). */
    @Transaction
    open suspend fun replaceAll(habits: List<Pair<Habit, List<Long>>>) {
        deleteAllHabits() // completions cascade
        habits.forEach { (habit, days) ->
            val newId = insertHabit(habit.copy(id = 0))
            insertCompletions(days.map { HabitCompletion(habitId = newId, dateEpochDay = it) })
        }
    }
}
