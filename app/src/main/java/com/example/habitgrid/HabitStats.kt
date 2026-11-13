package com.example.habitgrid

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** All seven weekdays set (bit 0 = Monday … bit 6 = Sunday). */
const val ALL_DAYS_MASK = 0b1111111

fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

/** True when the habit is tied to specific weekdays rather than "N times a week". */
val Habit.usesSpecificDays: Boolean get() = scheduleDays != 0

/** How many completions per week count as "target met". */
val Habit.weeklyTarget: Int
    get() = if (usesSpecificDays) Integer.bitCount(scheduleDays) else targetPerWeek.coerceIn(1, 7)

/** Day-based habits keep a daily streak; flexible "N times a week" habits keep a weekly one. */
val Habit.streakUnit: StreakUnit
    get() = if (usesSpecificDays || targetPerWeek >= 7) StreakUnit.DAYS else StreakUnit.WEEKS

/** Whether [date] is a day this habit is expected (or, for flexible habits, allowed) to be done. */
fun Habit.isScheduledOn(date: LocalDate): Boolean =
    !usesSpecificDays || (scheduleDays and dayBit(date.dayOfWeek)) != 0

fun Habit.createdDay(zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate().toEpochDay()

fun Habit.scheduleLabel(): String = when {
    usesSpecificDays && scheduleDays == ALL_DAYS_MASK -> "Every day"
    usesSpecificDays -> DayOfWeek.entries
        .filter { scheduleDays and dayBit(it) != 0 }
        .joinToString(", ") { it.shortName() }
    targetPerWeek >= 7 -> "Every day"
    targetPerWeek == 1 -> "Once a week"
    else -> "$targetPerWeek times a week"
}

fun DayOfWeek.shortName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

fun LocalDate.startOfWeek(): LocalDate = minusDays(dayOfWeek.value - 1L)

enum class StreakUnit { DAYS, WEEKS }

data class HabitStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val unit: StreakUnit,
    val totalCompletions: Int,
    /** 0..1, or null when there isn't enough history yet. */
    val successRate: Float?,
    val successRateLabel: String,
    val thisWeekDone: Int,
    val weeklyTarget: Int
)

fun streakText(count: Int, unit: StreakUnit): String {
    val word = if (unit == StreakUnit.DAYS) "day" else "week"
    return "$count $word" + if (count == 1) "" else "s"
}

/**
 * Computes streaks and rates. Rules:
 * - Today (or the current week) not being done yet never breaks a streak; it just doesn't count yet.
 * - For specific-day habits, unscheduled days are skipped and don't affect anything.
 * - For "N times a week" habits, a week counts when it has at least N completions.
 * - History starts at the earlier of the creation day and the first completion (backfill is allowed).
 */
fun computeStats(
    habit: Habit,
    completedDays: Set<Long>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): HabitStats {
    val todayEpoch = today.toEpochDay()
    val relevant = completedDays.filter { it <= todayEpoch }
    val startDay = minOf(habit.createdDay(zone), relevant.minOrNull() ?: Long.MAX_VALUE)
        .coerceAtMost(todayEpoch)
    val start = LocalDate.ofEpochDay(startDay)
    val target = habit.weeklyTarget

    fun countsFor(date: LocalDate) = habit.isScheduledOn(date) && date.toEpochDay() in completedDays

    fun weekCount(weekStart: LocalDate): Int =
        (0L until 7L).map { weekStart.plusDays(it) }
            .count { !it.isAfter(today) && countsFor(it) }

    val thisWeekDone = weekCount(today.startOfWeek())

    val current: Int
    val best: Int
    val rate: Float?
    val rateLabel: String

    when (habit.streakUnit) {
        StreakUnit.DAYS -> {
            // Current streak: walk back over scheduled days.
            var day = today
            if (!countsFor(day)) day = day.minusDays(1) // today pending doesn't break it
            var streak = 0
            while (!day.isBefore(start)) {
                if (habit.isScheduledOn(day)) {
                    if (day.toEpochDay() in completedDays) streak++ else break
                }
                day = day.minusDays(1)
            }
            current = streak

            var run = 0
            var longest = 0
            var d = start
            while (!d.isAfter(today)) {
                if (habit.isScheduledOn(d)) {
                    if (d.toEpochDay() in completedDays) {
                        run++
                        longest = maxOf(longest, run)
                    } else if (d != today) {
                        run = 0
                    }
                }
                d = d.plusDays(1)
            }
            best = longest

            // Success rate over the last 30 days (today only counts once it's done).
            val windowStart = maxOf(start, today.minusDays(29))
            var scheduled = 0
            var done = 0
            var w = windowStart
            while (!w.isAfter(today)) {
                if (habit.isScheduledOn(w)) {
                    val isDone = w.toEpochDay() in completedDays
                    if (w != today || isDone) {
                        scheduled++
                        if (isDone) done++
                    }
                }
                w = w.plusDays(1)
            }
            rate = if (scheduled == 0) null else done.toFloat() / scheduled
            rateLabel = "Last 30 days"
        }

        StreakUnit.WEEKS -> {
            val thisWeek = today.startOfWeek()
            val firstWeek = start.startOfWeek()

            var week = thisWeek
            if (weekCount(week) < target) week = week.minusWeeks(1) // current week still open
            var streak = 0
            while (!week.isBefore(firstWeek)) {
                if (weekCount(week) >= target) streak++ else break
                week = week.minusWeeks(1)
            }
            current = streak

            var run = 0
            var longest = 0
            var wk = firstWeek
            while (!wk.isAfter(thisWeek)) {
                if (weekCount(wk) >= target) {
                    run++
                    longest = maxOf(longest, run)
                } else if (wk != thisWeek) {
                    run = 0
                }
                wk = wk.plusWeeks(1)
            }
            best = longest

            // Share of the last 12 weeks that hit the target (current week only once it's met).
            var weeks = 0
            var met = 0
            var r = maxOf(firstWeek, thisWeek.minusWeeks(11))
            while (!r.isAfter(thisWeek)) {
                val ok = weekCount(r) >= target
                if (r != thisWeek || ok) {
                    weeks++
                    if (ok) met++
                }
                r = r.plusWeeks(1)
            }
            rate = if (weeks == 0) null else met.toFloat() / weeks
            rateLabel = "Last 12 weeks"
        }
    }

    return HabitStats(
        currentStreak = current,
        bestStreak = best,
        unit = habit.streakUnit,
        totalCompletions = relevant.size,
        successRate = rate,
        successRateLabel = rateLabel,
        thisWeekDone = thisWeekDone,
        weeklyTarget = target
    )
}

/**
 * Whether a reminder should fire today: the habit is due today, isn't done yet and, for flexible
 * habits, this week's target isn't already reached.
 */
fun shouldRemindToday(habit: Habit, completedDays: Set<Long>, today: LocalDate): Boolean {
    if (habit.archived || !habit.isScheduledOn(today)) return false
    if (today.toEpochDay() in completedDays) return false
    if (!habit.usesSpecificDays && habit.targetPerWeek < 7) {
        val weekStart = today.startOfWeek()
        val doneThisWeek = (0L until 7L).map { weekStart.plusDays(it) }
            .count { !it.isAfter(today) && it.toEpochDay() in completedDays }
        if (doneThisWeek >= habit.weeklyTarget) return false
    }
    return true
}
