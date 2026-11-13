package com.diongue.streaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class HabitStatsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 12) // a Thursday

    private fun habit(
        createdDaysAgo: Long = 60,
        targetPerWeek: Int = 7,
        scheduleDays: Int = 0,
        archived: Boolean = false
    ) = Habit(
        id = 1,
        name = "Test",
        createdAt = today.minusDays(createdDaysAgo).atStartOfDay(zone).toInstant().toEpochMilli(),
        targetPerWeek = targetPerWeek,
        scheduleDays = scheduleDays,
        archived = archived
    )

    private fun daysAgo(vararg n: Long) = n.map { today.minusDays(it).toEpochDay() }.toSet()
    private fun dates(vararg d: LocalDate) = d.map { it.toEpochDay() }.toSet()
    private fun stats(h: Habit, done: Set<Long>) = computeStats(h, done, today, zone)

    @Test fun `daily streak counts consecutive days including today`() {
        assertEquals(5, stats(habit(), daysAgo(0, 1, 2, 3, 4)).currentStreak)
    }

    @Test fun `today not done yet does not break the streak`() {
        assertEquals(5, stats(habit(), daysAgo(1, 2, 3, 4, 5)).currentStreak)
    }

    @Test fun `missing yesterday breaks the streak`() {
        assertEquals(0, stats(habit(), daysAgo(2, 3, 4)).currentStreak)
    }

    @Test fun `best streak is the longest run`() {
        val s = stats(habit(), daysAgo(0, 1, 2) + daysAgo(10, 11, 12, 13, 14, 15))
        assertEquals(3, s.currentStreak)
        assertEquals(6, s.bestStreak)
        assertEquals(StreakUnit.DAYS, s.unit)
    }

    @Test fun `specific days skip unscheduled days`() {
        val mwf = dayBit(DayOfWeek.MONDAY) or dayBit(DayOfWeek.WEDNESDAY) or dayBit(DayOfWeek.FRIDAY)
        val h = habit(scheduleDays = mwf)
        // Today is Thursday (unscheduled). Wed 11, Mon 9, Fri 6, Wed 4 done.
        val done = dates(
            LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 4)
        )
        assertEquals(4, stats(h, done).currentStreak)
        // Missing Friday the 6th breaks it after two.
        assertEquals(2, stats(h, done - LocalDate.of(2026, 11, 6).toEpochDay()).currentStreak)
        // A bonus completion on an unscheduled Tuesday changes nothing.
        assertEquals(4, stats(h, done + LocalDate.of(2026, 11, 10).toEpochDay()).currentStreak)
        assertEquals(3, stats(h, done).weeklyTarget)
    }

    @Test fun `weekly habit counts weeks that hit the target`() {
        val h = habit(targetPerWeek = 3)
        val thisWeek = dates(LocalDate.of(2026, 11, 10)) // 1 of 3 so far
        val lastWeek = dates(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 8))
        val twoAgo = dates(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28))
        val threeAgo = dates(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)) // missed
        val s = stats(h, thisWeek + lastWeek + twoAgo + threeAgo)
        assertEquals(StreakUnit.WEEKS, s.unit)
        assertEquals(2, s.currentStreak) // open current week neither counts nor breaks
        assertEquals(1, s.thisWeekDone)
        assertEquals(3, s.weeklyTarget)
    }

    @Test fun `weekly habit counts the current week once met`() {
        val h = habit(targetPerWeek = 2)
        val done = dates(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10))
        assertEquals(1, stats(h, done).currentStreak)
    }

    @Test fun `success rate ignores today until it is done`() {
        val h = habit(createdDaysAgo = 10) // window = 11 days incl. today
        val s = stats(h, daysAgo(1, 3, 5, 7, 9))
        assertEquals(0.5f, s.successRate!!, 0.001f)
    }

    @Test fun `new habit has no rate yet`() {
        assertNull(stats(habit(createdDaysAgo = 0), emptySet()).successRate)
    }

    @Test fun `backfilled days before creation extend history`() {
        val s = stats(habit(createdDaysAgo = 1), daysAgo(1, 2, 3, 4))
        assertEquals(4, s.currentStreak)
        assertEquals(4, s.totalCompletions)
    }

    @Test fun `future completions are ignored`() {
        assertEquals(0, stats(habit(), setOf(today.plusDays(1).toEpochDay())).totalCompletions)
    }

    @Test fun `reminder rules`() {
        assertTrue(shouldRemindToday(habit(), emptySet(), today))
        assertFalse(shouldRemindToday(habit(), daysAgo(0), today))
        assertFalse(shouldRemindToday(habit(archived = true), emptySet(), today))
        // Thursday not in a Mon-only schedule.
        assertFalse(shouldRemindToday(habit(scheduleDays = dayBit(DayOfWeek.MONDAY)), emptySet(), today))
        // Weekly target already reached this week.
        val twice = habit(targetPerWeek = 2)
        assertFalse(shouldRemindToday(twice, dates(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)), today))
        assertTrue(shouldRemindToday(twice, dates(LocalDate.of(2026, 11, 9)), today))
    }

    @Test fun `schedule labels`() {
        assertEquals("Every day", habit().scheduleLabel())
        assertEquals("3 times a week", habit(targetPerWeek = 3).scheduleLabel())
        assertEquals(
            "Mon, Fri",
            habit(scheduleDays = dayBit(DayOfWeek.MONDAY) or dayBit(DayOfWeek.FRIDAY)).scheduleLabel()
        )
    }

    @Test fun `next reminder trigger is today if still ahead, else tomorrow`() {
        val now = LocalDate.of(2026, 11, 12).atTime(9, 0)
        val zoneNow = java.time.ZoneId.systemDefault()
        val later = ReminderScheduler.nextTriggerMillis(10 * 60, now)
        assertEquals(now.withHour(10).atZone(zoneNow).toInstant().toEpochMilli(), later)
        val earlier = ReminderScheduler.nextTriggerMillis(8 * 60, now)
        assertEquals(now.plusDays(1).withHour(8).atZone(zoneNow).toInstant().toEpochMilli(), earlier)
    }
}
