package com.example.habitgrid.ui

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.habitgrid.Habit
import com.example.habitgrid.ReminderScheduler
import com.example.habitgrid.dayBit
import com.example.habitgrid.shortName
import com.example.habitgrid.ui.theme.ButtonIdle
import com.example.habitgrid.ui.theme.Danger
import com.example.habitgrid.ui.theme.TextSecondary
import com.example.habitgrid.ui.theme.habitColorOptions
import java.time.DayOfWeek
import kotlin.math.roundToInt

/** Add/edit dialog. [initial] is the habit being edited, or a blank draft for a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditDialog(
    title: String,
    initial: Habit,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var selectedColor by remember { mutableLongStateOf(initial.colorArgb) }
    var target by remember { mutableIntStateOf(initial.targetPerWeek.coerceIn(1, 7)) }
    var specificDays by remember { mutableStateOf(initial.scheduleDays != 0) }
    var daysMask by remember {
        // Mon–Fri as a sensible starting point when switching to specific days.
        mutableIntStateOf(if (initial.scheduleDays != 0) initial.scheduleDays else 0b0011111)
    }
    var reminderOn by remember { mutableStateOf(initial.reminderMinutes != null) }
    var reminderMinutes by remember { mutableIntStateOf(initial.reminderMinutes ?: 20 * 60) }
    var showTimePicker by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(ReminderScheduler.canPostNotifications(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> canNotify = granted }

    val valid = name.isNotBlank() && (!specificDays || daysMask != 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                SectionLabel("Color")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    habitColorOptions.forEach { colorLong ->
                        val selected = selectedColor == colorLong
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong.toInt()))
                                .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                .semantics { this.selected = selected; contentDescription = "Color option" }
                                .clickable(role = Role.RadioButton) { selectedColor = colorLong }
                        )
                    }
                }

                SectionLabel("Schedule")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !specificDays,
                        onClick = { specificDays = false },
                        label = { Text("Times a week") }
                    )
                    FilterChip(
                        selected = specificDays,
                        onClick = { specificDays = true },
                        label = { Text("Specific days") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (specificDays) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val on = daysMask and dayBit(day) != 0
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (on) Color(selectedColor.toInt()) else ButtonIdle)
                                    .semantics { selected = on; contentDescription = day.shortName() }
                                    .clickable(role = Role.Checkbox) { daysMask = daysMask xor dayBit(day) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    day.shortName().take(1),
                                    color = if (on) Color.Black else Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    if (daysMask == 0) {
                        Text("Pick at least one day", color = Danger, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        if (target == 7) "Every day" else "$target time${if (target == 1) "" else "s"} a week, any days",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = target.toFloat(),
                        onValueChange = { target = it.roundToInt() },
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }

                SectionLabel("Reminder")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = reminderOn,
                        onCheckedChange = { on ->
                            reminderOn = on
                            if (on && !canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    if (reminderOn) {
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(ReminderScheduler.formatTime(reminderMinutes))
                        }
                    } else {
                        Text("Off", color = TextSecondary)
                    }
                }
                if (reminderOn && !canNotify) {
                    Text(
                        "Notifications are blocked for this app. Allow them in system settings to get reminders.",
                        color = Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            colorArgb = selectedColor,
                            targetPerWeek = target,
                            scheduleDays = if (specificDays) daysMask else 0,
                            reminderMinutes = if (reminderOn) reminderMinutes else null
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = reminderMinutes / 60,
            initialMinute = reminderMinutes % 60,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    reminderMinutes = state.hour * 60 + state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
}

/** Delete confirmation that offers archiving as the non-destructive alternative. */
@Composable
fun DeleteHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onArchive: (() -> Unit)?,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${habit.name}\"?") },
        text = {
            Text(
                "This permanently deletes the habit and its whole history." +
                    if (onArchive != null) " Archive it instead to hide it and keep the history." else ""
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = Danger) }
        },
        dismissButton = {
            Row {
                if (onArchive != null) TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun DoneButton(done: Boolean, scheduled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (done) color else if (scheduled) ButtonIdle else Color.Transparent)
            .then(if (!done && !scheduled) Modifier.border(1.dp, ButtonIdle, CircleShape) else Modifier)
            .semantics {
                contentDescription = if (done) "Done today, tap to undo" else "Mark done today"
            }
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
    }
}
