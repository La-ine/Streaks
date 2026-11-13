package com.diongue.streaks

import android.content.Context
import androidx.glance.appwidget.updateAll
import java.time.LocalDate

/**
 * Mutations shared by the app, the widget and notification actions, so every entry point
 * keeps the widget in sync.
 */
object HabitActions {

    /** Toggles [day] for a habit. Future days are ignored. Returns true if now completed. */
    suspend fun toggleDay(context: Context, habitId: Long, day: Long): Boolean? {
        if (day > LocalDate.now().toEpochDay()) return null
        val done = AppDatabase.getInstance(context).habitDao().toggleCompletion(habitId, day)
        refreshWidgets(context)
        return done
    }

    suspend fun markDone(context: Context, habitId: Long, day: Long) {
        AppDatabase.getInstance(context).habitDao()
            .insertCompletion(HabitCompletion(habitId = habitId, dateEpochDay = day))
        refreshWidgets(context)
    }

    suspend fun refreshWidgets(context: Context) {
        HabitWidget().updateAll(context.applicationContext)
    }
}
