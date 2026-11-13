package com.diongue.streaks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.diongue.streaks.ui.theme.ButtonIdle
import com.diongue.streaks.ui.theme.CardSurface
import com.diongue.streaks.ui.theme.EmptyCell
import com.diongue.streaks.ui.theme.InactiveCell
import com.diongue.streaks.ui.theme.TextSecondary
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

val habitIdKey = ActionParameters.Key<Long>("habitId")

private val RowHeight = 44.dp
private val CellSize = 18.dp

private data class WidgetHabit(val habit: Habit, val completedDays: Set<Long>)

class HabitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.getInstance(context).habitDao()
        // Snapshot for the first frame, then live updates from Room. While a Glance session is
        // running, update() only recomposes — it does NOT call provideGlance again — so data read
        // once up here would go stale and taps would appear to do nothing. Collecting the flows
        // inside the composition makes every change (widget, app, notification) show immediately.
        val initial = toWidgetHabits(dao.getActiveHabitsOnce(), dao.getAllCompletionsOnce())
        val live = combine(dao.getActiveHabits(), dao.getAllCompletions(), ::toWidgetHabits)

        provideContent {
            val habits by live.collectAsState(initial)
            val today by produceState(LocalDate.now()) {
                while (true) {
                    val now = LocalDateTime.now()
                    delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000)
                    value = LocalDate.now()
                }
            }
            WidgetContent(habits, today)
        }
    }
}

private fun toWidgetHabits(habits: List<Habit>, completions: List<HabitCompletion>): List<WidgetHabit> {
    val byHabit = completions.groupBy({ it.habitId }, { it.dateEpochDay })
    return habits.map { WidgetHabit(it, byHabit[it.id].orEmpty().toSet()) }
}

class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[habitIdKey] ?: return
        HabitActions.toggleDay(context, habitId, LocalDate.now().toEpochDay()) // refreshes all widgets
    }
}

@Composable
private fun WidgetContent(habits: List<WidgetHabit>, today: LocalDate) {
    val size = LocalSize.current
    val padding = 12.dp
    // Fit as many rows as the widget's height allows, and as many days as its width allows.
    val maxRows = ((size.height - padding * 2) / RowHeight).toInt().coerceAtLeast(1)
    val nameAndButtonWidth = 150.dp
    val days = ((size.width - padding * 2 - nameAndButtonWidth) / CellSize).toInt().coerceIn(0, 7)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CardSurface)
            .cornerRadius(16.dp)
            .padding(padding)
    ) {
        if (habits.isEmpty()) {
            Text(
                text = "No habits yet. Tap to add one.",
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())
            )
        } else {
            habits.take(maxRows).forEach { item ->
                HabitRow(item, today, days, GlanceModifier.defaultWeight())
            }
            val hidden = habits.size - maxRows
            if (hidden > 0) {
                Text(
                    text = "+$hidden more",
                    style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                )
            }
        }
    }
}

@Composable
private fun HabitRow(item: WidgetHabit, today: LocalDate, dayCount: Int, modifier: GlanceModifier) {
    val habit = item.habit
    val todayEpoch = today.toEpochDay()
    val doneToday = todayEpoch in item.completedDays
    val habitColor = Color(habit.colorArgb.toInt())

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name + recent days: opens the app.
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = habit.name,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Row {
                ((dayCount - 1) downTo 0).forEach { offset ->
                    val date = today.minusDays(offset.toLong())
                    val done = date.toEpochDay() in item.completedDays
                    val fill = when {
                        done -> habitColor
                        habit.isScheduledOn(date) -> EmptyCell
                        else -> InactiveCell
                    }
                    Box(modifier = GlanceModifier.size(CellSize).padding(2.dp)) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .cornerRadius(4.dp)
                                .background(fill)
                        ) {}
                    }
                }
            }
        }

        Spacer(modifier = GlanceModifier.width(2.dp))

        // 44dp transparent touch target around the 32dp visual button, so taps don't miss.
        Box(
            modifier = GlanceModifier
                .size(44.dp)
                .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(habitIdKey to habit.id))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .cornerRadius(16.dp)
                    .background(if (doneToday) habitColor else ButtonIdle),
                contentAlignment = Alignment.Center
            ) {
                if (doneToday) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_check_dark),
                        contentDescription = "Done today",
                        modifier = GlanceModifier.size(20.dp)
                    )
                }
            }
        }
    }
}
