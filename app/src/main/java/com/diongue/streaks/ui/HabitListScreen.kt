package com.diongue.streaks.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diongue.streaks.Habit
import com.diongue.streaks.HabitHeatmap
import com.diongue.streaks.HabitUi
import com.diongue.streaks.HabitViewModel
import com.diongue.streaks.R
import com.diongue.streaks.WeeklyProgressBar
import com.diongue.streaks.streakText
import com.diongue.streaks.ui.theme.AppBackground
import com.diongue.streaks.ui.theme.CardSurface
import com.diongue.streaks.ui.theme.TextSecondary
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    vm: HabitViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenHabit: (Long) -> Unit,
    onOpenArchived: () -> Unit
) {
    val habits by vm.activeHabits.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Habit?>(null) }
    val haptics = LocalHapticFeedback.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importFrom) }
    val launchImport = {
        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
    }

    // Local copy so drag-and-drop feels instant; persisted when the drag ends and
    // replaced by the database's version whenever that changes.
    var local by remember(habits) { mutableStateOf(habits.orEmpty()) }
    var dragging by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        local = local.toMutableList().apply {
            val fromIndex = indexOfFirst { it.habit.id == from.key }
            val toIndex = indexOfFirst { it.habit.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground),
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Archived habits") }, onClick = {
                                menuOpen = false; onOpenArchived()
                            })
                            DropdownMenuItem(text = { Text("Export backup") }, onClick = {
                                menuOpen = false
                                exportLauncher.launch("habitgrid-backup-${LocalDate.now()}.json")
                            })
                            DropdownMenuItem(text = { Text("Import backup") }, onClick = {
                                menuOpen = false
                                if (local.isEmpty()) launchImport() else confirmImport = true
                            })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add habit")
            }
        }
    ) { innerPadding ->
        when {
            habits == null -> Unit // still loading; avoid flashing the empty state
            local.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No habits yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap + to add your first one", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
            ) {
                items(local, key = { it.habit.id }) { ui ->
                    ReorderableItem(reorderState, key = ui.habit.id) { isDragging ->
                        HabitCard(
                            ui = ui,
                            modifier = Modifier.graphicsLayer {
                                val s = if (isDragging) 1.02f else 1f
                                scaleX = s; scaleY = s
                            },
                            onOpen = { onOpenHabit(ui.habit.id) },
                            onToggleToday = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleToday(ui.habit.id)
                            },
                            onToggleDay = { day -> vm.toggleDay(ui.habit.id, day) },
                            onArchive = { vm.setArchived(ui.habit, true) },
                            onDelete = { pendingDelete = ui.habit },
                            onUpdate = vm::updateHabit,
                            dragHandle = {
                                Icon(
                                    Icons.Default.DragIndicator,
                                    contentDescription = "Drag to reorder",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .draggableHandle(
                                            onDragStarted = {
                                                dragging = true
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                dragging = false
                                                vm.reorder(local.map { it.habit.id })
                                            }
                                        )
                                        .padding(end = 4.dp)
                                        .size(24.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        HabitEditDialog(
            title = "New habit",
            initial = Habit(name = ""),
            onDismiss = { showAddDialog = false },
            onSave = {
                vm.addHabit(it.copy(createdAt = System.currentTimeMillis()))
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { habit ->
        DeleteHabitDialog(
            habit = habit,
            onDismiss = { pendingDelete = null },
            onArchive = { vm.setArchived(habit, true); pendingDelete = null },
            onDelete = { vm.deleteHabit(habit); pendingDelete = null }
        )
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Replace all habits?") },
            text = {
                Text("Importing a backup replaces every current habit and its history with the backup's contents. Export first if you want to keep what you have.")
            },
            confirmButton = {
                TextButton(onClick = { confirmImport = false; launchImport() }) { Text("Choose file") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun HabitCard(
    ui: HabitUi,
    onOpen: () -> Unit,
    onToggleToday: () -> Unit,
    onToggleDay: (Long) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (Habit) -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit = {}
) {
    val habit = ui.habit
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val habitColor = Color(habit.colorArgb.toInt())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dragHandle()
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpen)
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            ) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreakBadge(ui, habitColor)
                    Spacer(Modifier.width(10.dp))
                    WeeklyProgressBar(
                        completed = ui.stats.thisWeekDone,
                        target = ui.stats.weeklyTarget,
                        color = habitColor
                    )
                }
            }
            DoneButton(ui.doneToday, ui.scheduledToday, habitColor, onToggleToday)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options for ${habit.name}", tint = TextSecondary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Details") }, onClick = { menuExpanded = false; onOpen() })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; showEditDialog = true })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { menuExpanded = false; onArchive() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HabitHeatmap(
            habit = habit,
            completedDays = ui.completedDays,
            today = ui.today,
            color = habitColor,
            weeks = 13,
            onToggleDay = onToggleDay,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
        )
    }

    if (showEditDialog) {
        HabitEditDialog(
            title = "Edit habit",
            initial = habit,
            onDismiss = { showEditDialog = false },
            onSave = { onUpdate(it); showEditDialog = false }
        )
    }
}

@Composable
fun StreakBadge(ui: HabitUi, color: Color) {
    val streak = ui.stats.currentStreak
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "Current streak ${streakText(streak, ui.stats.unit)}" }
    ) {
        Icon(
            Icons.Default.LocalFireDepartment,
            contentDescription = null,
            tint = if (streak > 0) color else TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            streakText(streak, ui.stats.unit),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
