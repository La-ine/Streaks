package com.diongue.streaks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diongue.streaks.ui.theme.EmptyCell
import com.diongue.streaks.ui.theme.InactiveCell
import com.diongue.streaks.ui.theme.TextSecondary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val LabelWidth = 16.dp
private val MonthLabelHeight = 14.dp
private val dayLabelFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private fun monthShort(date: LocalDate) =
    date.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())

/** Month label for each week column: shown where a month begins, plus the first column if room. */
private fun monthLabels(firstWeek: LocalDate, weeks: Int): List<String?> {
    val labels = MutableList<String?>(weeks) { w ->
        val start = firstWeek.plusWeeks(w.toLong())
        (0L until 7L).map { start.plusDays(it) }.firstOrNull { it.dayOfMonth == 1 }?.let(::monthShort)
    }
    if (weeks > 0 && labels[0] == null && labels.drop(1).take(2).all { it == null }) {
        labels[0] = monthShort(firstWeek.plusDays(6))
    }
    return labels
}

/**
 * GitHub-style heatmap: one column per week (Monday on top), the last column is the current week.
 *
 * @param weeks number of columns; null means "the whole history" (at least 26 weeks).
 * @param cellSize fixed cell size (the grid then scrolls horizontally, starting at today);
 *                 null means "fit the available width".
 */
@Composable
fun HabitHeatmap(
    habit: Habit,
    completedDays: Set<Long>,
    today: LocalDate,
    color: Color,
    modifier: Modifier = Modifier,
    weeks: Int? = 13,
    cellSize: Dp? = null,
    onToggleDay: ((Long) -> Unit)? = null
) {
    val lastWeek = today.startOfWeek()
    val historyStart = LocalDate.ofEpochDay(
        minOf(habit.createdDay(), completedDays.minOrNull() ?: Long.MAX_VALUE)
    )
    val columnCount = weeks ?: maxOf(
        26,
        ((lastWeek.toEpochDay() - historyStart.startOfWeek().toEpochDay()) / 7 + 1).toInt()
    )
    val firstWeek = lastWeek.minusWeeks(columnCount - 1L)
    val gap = 3.dp

    if (cellSize == null) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val cell = ((maxWidth - LabelWidth) / columnCount) - gap
            HeatmapBody(habit, completedDays, today, color, firstWeek, columnCount, cell, gap, historyStart, onToggleDay)
        }
    } else {
        val scroll = rememberScrollState()
        LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
        Box(modifier.horizontalScroll(scroll)) {
            HeatmapBody(habit, completedDays, today, color, firstWeek, columnCount, cellSize, gap, historyStart, onToggleDay)
        }
    }
}

@Composable
private fun HeatmapBody(
    habit: Habit,
    completedDays: Set<Long>,
    today: LocalDate,
    color: Color,
    firstWeek: LocalDate,
    weeks: Int,
    cell: Dp,
    gap: Dp,
    historyStart: LocalDate,
    onToggleDay: ((Long) -> Unit)?
) {
    val haptics = LocalHapticFeedback.current
    val labels = remember(firstWeek, weeks) { monthLabels(firstWeek, weeks) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = TextSecondary)

    Column {
        Row(Modifier.height(MonthLabelHeight)) {
            Spacer(Modifier.width(LabelWidth))
            labels.forEach { label ->
                Box(Modifier.width(cell + gap)) {
                    if (label != null) {
                        Text(
                            label, style = labelStyle, maxLines = 1, softWrap = false,
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                        )
                    }
                }
            }
        }
        Row {
            Column(Modifier.width(LabelWidth)) {
                DayOfWeek.entries.forEachIndexed { row, day ->
                    Box(Modifier.height(cell + gap), contentAlignment = Alignment.CenterStart) {
                        if (row % 2 == 0 && row < 6) {
                            Text(day.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()), style = labelStyle)
                        }
                    }
                }
            }
            for (w in 0 until weeks) {
                Column {
                    for (r in 0 until 7) {
                        val date = firstWeek.plusWeeks(w.toLong()).plusDays(r.toLong())
                        val epoch = date.toEpochDay()
                        val isFuture = date.isAfter(today)
                        val isDone = epoch in completedDays
                        val fill = when {
                            isFuture -> Color.Transparent
                            isDone -> color
                            date.isBefore(historyStart) || !habit.isScheduledOn(date) -> InactiveCell
                            else -> EmptyCell
                        }
                        var m = Modifier
                            .padding(gap / 2)
                            .size(cell)
                            .clip(RoundedCornerShape(cell * 0.2f))
                            .background(fill)
                        if (date == today) m = m.border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(cell * 0.2f))
                        if (!isFuture && onToggleDay != null) {
                            m = m
                                .semantics {
                                    contentDescription = "${date.format(dayLabelFormatter)}, " +
                                        if (isDone) "done" else "not done"
                                }
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleDay(epoch)
                                }
                        }
                        Box(m)
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyProgressBar(
    completed: Int,
    target: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics { contentDescription = "$completed of $target this week" },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0 until target) {
            val isFilled = i < completed
            val fraction = if (target > 1) i.toFloat() / (target - 1) else 1f
            val segmentColor = lerp(color.copy(alpha = 0.5f), color, fraction)

            val animatedColor by animateColorAsState(
                targetValue = if (isFilled) segmentColor else EmptyCell,
                animationSpec = tween(durationMillis = 400),
                label = "segmentColor"
            )

            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(animatedColor)
            )
        }
    }
}

/** Month view with big tap targets, for filling in or correcting past days. */
@Composable
fun MonthCalendar(
    habit: Habit,
    completedDays: Set<Long>,
    today: LocalDate,
    color: Color,
    onToggleDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    val currentMonth = YearMonth.from(today)

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < currentMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells = leading + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayOfMonth = index - leading + 1
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayOfMonth in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayOfMonth)
                            val epoch = date.toEpochDay()
                            val isDone = epoch in completedDays
                            val isFuture = date.isAfter(today)
                            val scheduled = habit.isScheduledOn(date)
                            var m = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(if (isDone) color else Color.Transparent)
                            if (date == today) m = m.border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            if (!isFuture) {
                                m = m
                                    .semantics {
                                        contentDescription = "${date.format(dayLabelFormatter)}, " +
                                            if (isDone) "done" else "not done"
                                    }
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleDay(epoch)
                                    }
                            }
                            Box(m, contentAlignment = Alignment.Center) {
                                Text(
                                    "$dayOfMonth",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isDone -> Color.Black
                                        isFuture -> TextSecondary.copy(alpha = 0.4f)
                                        !scheduled -> TextSecondary
                                        else -> Color.White
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
