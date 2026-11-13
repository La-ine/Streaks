package com.example.habitgrid

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily per-habit reminders via inexact AlarmManager alarms (no exact-alarm permission needed;
 * Android may deliver them a few minutes late in Doze). Each alarm reschedules the next one.
 * A separate alarm just after midnight refreshes the widget for the new day.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "habit_reminders"
    const val ACTION_REMIND = "com.example.habitgrid.action.REMIND"
    const val ACTION_MARK_DONE = "com.example.habitgrid.action.MARK_DONE"
    const val ACTION_NEW_DAY = "com.example.habitgrid.action.NEW_DAY"
    const val EXTRA_HABIT_ID = "habitId"
    private const val NEW_DAY_REQUEST_CODE = -1

    fun formatTime(minutes: Int): String =
        LocalTime.of(minutes / 60, minutes % 60).toString() // HH:mm

    fun nextTriggerMillis(minutes: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var next = now.toLocalDate().atTime(minutes / 60, minutes % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun reminderIntent(context: Context, habitId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_HABIT_ID, habitId)
        return PendingIntent.getBroadcast(
            context, habitId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, habit: Habit) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pi = reminderIntent(context, habit.id)
        val minutes = habit.reminderMinutes
        if (minutes == null || habit.archived) {
            alarms.cancel(pi)
            return
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMillis(minutes), pi)
    }

    fun cancel(context: Context, habitId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(reminderIntent(context, habitId))
    }

    fun scheduleNewDay(context: Context) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_NEW_DAY)
        val pi = PendingIntent.getBroadcast(
            context, NEW_DAY_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val midnight = LocalDate.now().plusDays(1).atTime(0, 1)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, midnight, pi)
    }

    suspend fun rescheduleAll(context: Context) {
        AppDatabase.getInstance(context).habitDao().getAllHabitsOnce().forEach { schedule(context, it) }
        scheduleNewDay(context)
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Daily reminders for habits you haven't done yet" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReminder(context: Context, habit: Habit) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context, habit.id.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markDone = PendingIntent.getBroadcast(
            context, habit.id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_MARK_DONE)
                .putExtra(EXTRA_HABIT_ID, habit.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(habit.colorArgb.toInt())
            .setContentTitle(habit.name)
            .setContentText("Not done yet today")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_notification, "Mark done", markDone)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(habit.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } finally { pending.finish() }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val habitId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1L)
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> runAsync {
                val dao = AppDatabase.getInstance(app).habitDao()
                val habit = dao.getHabit(habitId) ?: return@runAsync
                ReminderScheduler.schedule(app, habit) // queue tomorrow's
                val done = dao.getCompletedDaysOnce(habit.id).toSet()
                if (shouldRemindToday(habit, done, LocalDate.now())) {
                    ReminderScheduler.showReminder(app, habit)
                }
            }
            ReminderScheduler.ACTION_MARK_DONE -> runAsync {
                NotificationManagerCompat.from(app).cancel(habitId.toInt())
                HabitActions.markDone(app, habitId, LocalDate.now().toEpochDay())
            }
            ReminderScheduler.ACTION_NEW_DAY -> runAsync {
                HabitActions.refreshWidgets(app)
                ReminderScheduler.scheduleNewDay(app)
            }
        }
    }
}

/** Alarms are cleared on reboot and skewed by clock/zone changes, so re-arm them. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        runAsync {
            ReminderScheduler.rescheduleAll(app)
            HabitActions.refreshWidgets(app)
        }
    }
}
