package com.diongue.streaks

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Everything a screen needs to draw one habit, derived from the database and today's date. */
data class HabitUi(
    val habit: Habit,
    val completedDays: Set<Long>,
    val today: LocalDate,
    val stats: HabitStats
) {
    val doneToday: Boolean get() = today.toEpochDay() in completedDays
    val scheduledToday: Boolean get() = habit.isScheduledOn(today)
}

class HabitViewModel(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()
    private val dao = AppDatabase.getInstance(app).habitDao()

    /** Re-emitted at midnight and on resume so "today" never goes stale. */
    private val today = MutableStateFlow(LocalDate.now())

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val completionsByHabit: Flow<Map<Long, Set<Long>>> =
        dao.getAllCompletions().map { list ->
            list.groupBy({ it.habitId }, { it.dateEpochDay }).mapValues { it.value.toSet() }
        }

    private fun uiFlow(habits: Flow<List<Habit>>): StateFlow<List<HabitUi>?> =
        combine(habits, completionsByHabit, today) { list, completions, day ->
            list.map { h ->
                val done = completions[h.id] ?: emptySet()
                HabitUi(h, done, day, computeStats(h, done, day))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null until the first database read, so the empty state doesn't flash on launch. */
    val activeHabits: StateFlow<List<HabitUi>?> = uiFlow(dao.getActiveHabits())
    val archivedHabits: StateFlow<List<HabitUi>?> = uiFlow(dao.getArchivedHabits())

    fun habit(id: Long): Flow<HabitUi?> =
        combine(dao.getHabitFlow(id), dao.getCompletionsForHabit(id), today) { h, c, day ->
            h?.let {
                val done = c.map { it.dateEpochDay }.toSet()
                HabitUi(it, done, day, computeStats(it, done, day))
            }
        }

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
                refreshDate()
            }
        }
        viewModelScope.launch { ReminderScheduler.rescheduleAll(context) }
    }

    fun refreshDate() {
        today.value = LocalDate.now()
    }

    fun toggleDay(habitId: Long, day: Long) {
        viewModelScope.launch { HabitActions.toggleDay(context, habitId, day) }
    }

    fun toggleToday(habitId: Long) = toggleDay(habitId, LocalDate.now().toEpochDay())

    fun addHabit(draft: Habit) {
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            val habit = draft.copy(name = draft.name.trim(), sortOrder = dao.nextSortOrder())
            val id = dao.insertHabit(habit)
            ReminderScheduler.schedule(context, habit.copy(id = id))
            HabitActions.refreshWidgets(context)
        }
    }

    fun updateHabit(habit: Habit) {
        if (habit.name.isBlank()) return
        viewModelScope.launch {
            val trimmed = habit.copy(name = habit.name.trim())
            dao.updateHabit(trimmed)
            ReminderScheduler.schedule(context, trimmed)
            HabitActions.refreshWidgets(context)
        }
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        viewModelScope.launch {
            val updated = habit.copy(
                archived = archived,
                // Restored habits go to the bottom of the list.
                sortOrder = if (archived) habit.sortOrder else dao.nextSortOrder()
            )
            dao.updateHabit(updated)
            ReminderScheduler.schedule(context, updated)
            HabitActions.refreshWidgets(context)
            messageChannel.send(if (archived) "\"${habit.name}\" archived" else "\"${habit.name}\" restored")
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            ReminderScheduler.cancel(context, habit.id)
            dao.deleteHabit(habit) // completions cascade
            HabitActions.refreshWidgets(context)
        }
    }

    fun reorder(idsInOrder: List<Long>) {
        viewModelScope.launch {
            dao.reorder(idsInOrder)
            HabitActions.refreshWidgets(context)
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = Backup.toJson(
                        dao.getAllHabitsOnce(), dao.getAllCompletionsOnce(), System.currentTimeMillis()
                    )
                    context.contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            messageChannel.send(
                if (result.isSuccess) "Backup saved" else "Export failed: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    val habits = Backup.fromJson(text) // validate fully before touching data
                    dao.getAllHabitsOnce().forEach { ReminderScheduler.cancel(context, it.id) }
                    dao.replaceAll(habits)
                    ReminderScheduler.rescheduleAll(context)
                    habits.size
                }
            }
            HabitActions.refreshWidgets(context)
            messageChannel.send(
                result.fold(
                    onSuccess = { "Imported $it habit" + if (it == 1) "" else "s" },
                    onFailure = { "Import failed: ${it.message}" }
                )
            )
        }
    }
}
