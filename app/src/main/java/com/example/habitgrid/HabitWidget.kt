package com.example.habitgrid

import android.content.Context
import androidx.compose.runtime.Composable
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
import com.example.habitgrid.ui.theme.ButtonIdle
import com.example.habitgrid.ui.theme.CardSurface
import com.example.habitgrid.ui.theme.EmptyCell
import com.example.habitgrid.ui.theme.InactiveCell
import com.example.habitgrid.ui.theme.TextSecondary
import java.time.LocalDate

val habitIdKey = ActionParameters.Key<Long>("habitId")

private val RowHeight = 40.dp
private val CellSize = 18.dp

private data class WidgetHabit(val habit: Habit, val completedDays: Set<Long>)

class HabitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.getInstance(context).habitDao()
        val completions = dao.getAllCompletionsOnce()
            .groupBy({ it.habitId }, { it.dateEpochDay })
        val data = dao.getActiveHabitsOnce().map { habit ->
            WidgetHabit(habit, completions[habit.id].orEmpty().toSet())
        }
        val today = LocalDate.now()

        provideContent {
            WidgetContent(data, today)
        }
    }
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

        Spacer(modifier = GlanceModifier.width(8.dp))

        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .cornerRadius(16.dp)
                .background(if (doneToday) habitColor else ButtonIdle)
                .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(habitIdKey to habit.id))),
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
