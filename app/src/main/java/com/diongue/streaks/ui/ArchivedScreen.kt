package com.diongue.streaks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diongue.streaks.Habit
import com.diongue.streaks.HabitViewModel
import com.diongue.streaks.streakText
import com.diongue.streaks.ui.theme.AppBackground
import com.diongue.streaks.ui.theme.CardSurface
import com.diongue.streaks.ui.theme.Danger
import com.diongue.streaks.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    vm: HabitViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenHabit: (Long) -> Unit,
    onBack: () -> Unit
) {
    val habits by vm.archivedHabits.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Habit?>(null) }

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
                title = { Text("Archived habits") }
            )
        }
    ) { padding ->
        val list = habits ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing archived.\nArchived habits keep their history but are hidden from the list, widget and reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.habit.id }) { ui ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardSurface)
                        .clickable { onOpenHabit(ui.habit.id) }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(ui.habit.colorArgb.toInt()))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ui.habit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${ui.stats.totalCompletions} check-ins · best ${streakText(ui.stats.bestStreak, ui.stats.unit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    TextButton(onClick = { vm.setArchived(ui.habit, false) }) { Text("Restore") }
                    TextButton(onClick = { pendingDelete = ui.habit }) { Text("Delete", color = Danger) }
                }
            }
        }
    }

    pendingDelete?.let { habit ->
        DeleteHabitDialog(
            habit = habit,
            onDismiss = { pendingDelete = null },
            onArchive = null,
            onDelete = { vm.deleteHabit(habit); pendingDelete = null }
        )
    }
}
