package com.diongue.streaks

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val colorArgb: Long = 0xFF39D353, // default green, stored as ARGB
    /** Used when [scheduleDays] is 0: "N times a week, any days". 7 = every day. */
    val targetPerWeek: Int = 7,
    val sortOrder: Int = 0,
    /** Bitmask of specific weekdays (bit 0 = Monday … bit 6 = Sunday). 0 = flexible schedule. */
    val scheduleDays: Int = 0,
    /** Daily reminder time in minutes after midnight, or null for no reminder. */
    val reminderMinutes: Int? = null,
    /** Archived habits are hidden from the main list, widget and reminders but keep their history. */
    val archived: Boolean = false
)
