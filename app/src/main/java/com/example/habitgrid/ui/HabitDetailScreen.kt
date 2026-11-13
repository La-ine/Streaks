package com.example.habitgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitgrid.HabitHeatmap
import com.example.habitgrid.HabitUi
import com.example.habitgrid.HabitViewModel
import com.example.habitgrid.MonthCalendar
import com.example.habitgrid.ReminderScheduler
import com.example.habitgrid.WeeklyProgressBar
import com.example.habitgrid.createdDay
import com.example.habitgrid.scheduleLabel
import com.example.habitgrid.streakText
import com.example.habitgrid.ui.theme.AppBackground
import com.example.habitgrid.ui.theme.CardSurface
import com.example.habitgrid.ui.theme.TextSecondary
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private class Loaded(val ui: HabitUi?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    vm: HabitViewModel,
    habitId: Long,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val loaded by remember(habitId) { vm.habit(habitId).map { Loaded(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val ui = loaded?.ui

    // Habit was deleted (here or elsewhere): leave the screen.
    LaunchedEffect(loaded) { if (loaded != null && ui == null) onBack() }

    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(ui?.habit?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (ui != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit habit")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (ui.habit.archived) "Restore" else "Archive") },
                                    onClick = {
                                        menuOpen = false
                                        vm.setArchived(ui.habit, !ui.habit.archived)
                                        if (!ui.habit.archived) onBack()
                                    }
                                )
                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                    menuOpen = false; confirmDelete = true
                                })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (ui == null) return@Scaffold
        val habit = ui.habit
        val color = Color(habit.colorArgb.toInt())
        val stats = ui.stats

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Current streak", streakText(stats.currentStreak, stats.unit), Modifier.weight(1f))
                StatTile("Best streak", streakText(stats.bestStreak, stats.unit), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "Success · ${stats.successRateLabel.lowercase()}",
                    stats.successRate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    Modifier.weight(1f)
                )
                StatTile("Total check-ins", "${stats.totalCompletions}", Modifier.weight(1f))
            }

            SectionCard {
                InfoLine("Schedule", habit.scheduleLabel())
                InfoLine("Reminder", habit.reminderMinutes?.let(ReminderScheduler::formatTime) ?: "Off")
                InfoLine(
                    "Started",
                    LocalDate.ofEpochDay(habit.createdDay())
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        "This week  ${stats.thisWeekDone} / ${stats.weeklyTarget}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    WeeklyProgressBar(stats.thisWeekDone, stats.weeklyTarget, color)
                }
            }

            SectionCard {
                Text("Tap a day to mark or unmark it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                MonthCalendar(
                    habit = habit,
                    completedDays = ui.completedDays,
                    today = ui.today,
                    color = color,
                    onToggleDay = { vm.toggleDay(habit.id, it) }
                )
            }

            SectionCard {
                Text("History", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                HabitHeatmap(
                    habit = habit,
                    completedDays = ui.completedDays,
                    today = ui.today,
                    color = color,
                    weeks = null,
                    cellSize = 14.dp,
                    onToggleDay = { vm.toggleDay(habit.id, it) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showEdit) {
            HabitEditDialog(
                title = "Edit habit",
                initial = habit,
                onDismiss = { showEdit = false },
                onSave = { vm.updateHabit(it); showEdit = false }
            )
        }
        if (confirmDelete) {
            DeleteHabitDialog(
                habit = habit,
                onDismiss = { confirmDelete = false },
                onArchive = if (habit.archived) null else ({
                    confirmDelete = false
                    vm.setArchived(habit, true)
                    onBack()
                }),
                onDelete = {
                    confirmDelete = false
                    vm.deleteHabit(habit)
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(14.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
