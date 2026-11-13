package com.diongue.streaks

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON backup format. Pure conversion only (no Android I/O) so it can be unit-tested.
 * Completions are stored as epoch days per habit.
 */
object Backup {
    const val FORMAT = "habitgrid-backup"
    const val VERSION = 1

    fun toJson(habits: List<Habit>, completions: List<HabitCompletion>, exportedAt: Long): String {
        val byHabit = completions.groupBy({ it.habitId }, { it.dateEpochDay })
        val array = JSONArray()
        habits.forEach { h ->
            array.put(
                JSONObject()
                    .put("name", h.name)
                    .put("createdAt", h.createdAt)
                    .put("colorArgb", h.colorArgb)
                    .put("targetPerWeek", h.targetPerWeek)
                    .put("sortOrder", h.sortOrder)
                    .put("scheduleDays", h.scheduleDays)
                    .put("reminderMinutes", h.reminderMinutes ?: JSONObject.NULL)
                    .put("archived", h.archived)
                    .put("completions", JSONArray((byHabit[h.id] ?: emptyList()).sorted()))
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("habits", array)
            .toString(2)
    }

    /** Parses a backup. Throws [IllegalArgumentException] with a readable message on bad input. */
    fun fromJson(text: String): List<Pair<Habit, List<Long>>> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid backup file")
        }
        require(root.optString("format") == FORMAT) { "Not a HabitGrid backup file" }
        require(root.optInt("version") in 1..VERSION) { "Backup was made by a newer version of the app" }
        val array = root.getJSONArray("habits")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val name = o.getString("name").trim()
            require(name.isNotEmpty()) { "Backup contains a habit without a name" }
            val habit = Habit(
                name = name,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                colorArgb = o.optLong("colorArgb", 0xFF39D353),
                targetPerWeek = o.optInt("targetPerWeek", 7).coerceIn(1, 7),
                sortOrder = o.optInt("sortOrder", i),
                scheduleDays = o.optInt("scheduleDays", 0) and ALL_DAYS_MASK,
                reminderMinutes = if (o.isNull("reminderMinutes")) null
                    else o.getInt("reminderMinutes").coerceIn(0, 24 * 60 - 1),
                archived = o.optBoolean("archived", false)
            )
            val days = o.optJSONArray("completions") ?: JSONArray()
            habit to (0 until days.length()).map { days.getLong(it) }.distinct()
        }
    }
}
