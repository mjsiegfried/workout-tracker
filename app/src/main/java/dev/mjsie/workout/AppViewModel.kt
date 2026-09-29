package dev.mjsie.workout

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mjsie.workout.data.AppDatabase
import dev.mjsie.workout.data.BodyWeight
import dev.mjsie.workout.data.WorkoutExercise
import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.data.LogEntry
import dev.mjsie.workout.data.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val workouts: StateFlow<List<Workout>> =
        dao.workouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exercises: StateFlow<List<Exercise>> =
        dao.exercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val logs: StateFlow<List<LogEntry>> =
        dao.logs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val slots: StateFlow<List<WorkoutExercise>> =
        dao.allSlots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bodyWeights: StateFlow<List<BodyWeight>> =
        dao.bodyWeights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _unit = MutableStateFlow(prefs.getString("unit", "kg") ?: "kg")
    val unit: StateFlow<String> = _unit.asStateFlow()

    fun setUnit(unit: String) {
        prefs.edit().putString("unit", unit).apply()
        _unit.value = unit
    }

    /** Week shown on the home screen (History can jump to a past week). */
    private val _selectedWeek = MutableStateFlow(currentWeek())
    val selectedWeek: StateFlow<Long> = _selectedWeek.asStateFlow()

    fun selectWeek(weekStart: Long) {
        _selectedWeek.value = weekStart
    }

    // --- Schedule (stored in SharedPreferences) ---

    private val _schedule = MutableStateFlow(loadSchedule())
    val schedule: StateFlow<Schedule> = _schedule.asStateFlow()

    private fun currentWeek(): Long = Progression.weekStart(LocalDate.now())

    private fun loadSchedule(): Schedule {
        val days = (prefs.getString("scheduleDays", "1,3,5") ?: "")
            .split(',').filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet()
        val anchorWeek = prefs.getLong("scheduleAnchorWeek", -1L).takeIf { it >= 0 } ?: currentWeek()
        return Schedule(days, anchorWeek, prefs.getInt("scheduleAnchorIndex", 0)).also(::saveSchedule)
    }

    private fun saveSchedule(schedule: Schedule) {
        prefs.edit()
            .putString("scheduleDays", schedule.days.map { it.value }.sorted().joinToString(","))
            .putLong("scheduleAnchorWeek", schedule.anchorWeek)
            .putInt("scheduleAnchorIndex", schedule.anchorIndex)
            .apply()
    }

    private fun updateSchedule(schedule: Schedule) {
        saveSchedule(schedule)
        _schedule.value = schedule
    }

    fun toggleTrainingDay(day: DayOfWeek) {
        val current = _schedule.value
        val days = if (day in current.days) current.days - day else current.days + day
        updateSchedule(current.withDays(days, currentWeek()))
    }

    fun startThisWeekWith(rotationIndex: Int) {
        updateSchedule(_schedule.value.startingWith(rotationIndex, currentWeek()))
    }

    // --- Workouts ---

    /** Moves a workout earlier (-1) or later (+1) in the rotation. */
    fun moveWorkout(workout: Workout, delta: Int) {
        viewModelScope.launch {
            val list = workouts.value.toMutableList()
            val from = list.indexOfFirst { it.id == workout.id }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@launch
            list.add(to, list.removeAt(from))
            dao.updateWorkouts(list.mapIndexed { i, w -> w.copy(position = i) })
        }
    }

    fun addWorkout(name: String) {
        viewModelScope.launch { dao.insertWorkout(Workout(name = name, position = dao.nextWorkoutPosition())) }
    }

    fun renameWorkout(workout: Workout, name: String) {
        viewModelScope.launch { dao.updateWorkout(workout.copy(name = name)) }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch { dao.deleteWorkout(workout) }
    }

    /** Inserts or updates an exercise; optionally adds a newly created one to a workout. */
    fun saveExercise(exercise: Exercise, addToWorkout: Long? = null) {
        viewModelScope.launch {
            val id = if (exercise.id == 0L) dao.insertExercise(exercise) else exercise.id.also { dao.updateExercise(exercise) }
            if (addToWorkout != null) dao.addToWorkout(addToWorkout, id)
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch { dao.deleteExercise(exercise) }
    }

    fun addToWorkout(workoutId: Long, exerciseId: Long) {
        viewModelScope.launch { dao.addToWorkout(workoutId, exerciseId) }
    }

    fun removeFromWorkout(workoutId: Long, exerciseId: Long) {
        viewModelScope.launch { dao.removeSlot(workoutId, exerciseId) }
    }

    /** Moves an exercise up (-1) or down (+1) within a workout. */
    fun move(workoutId: Long, exerciseId: Long, delta: Int) {
        viewModelScope.launch {
            val slots = dao.slots(workoutId).toMutableList()
            val from = slots.indexOfFirst { it.exerciseId == exerciseId }
            val to = from + delta
            if (from < 0 || to !in slots.indices) return@launch
            slots.add(to, slots.removeAt(from))
            dao.updateSlots(slots.mapIndexed { i, slot -> slot.copy(position = i) })
        }
    }

    fun logWeight(workoutId: Long, exerciseId: Long, epochDay: Long, weight: Double, outcome: Outcome = Outcome.DONE) {
        viewModelScope.launch { dao.upsertLog(LogEntry(workoutId, exerciseId, epochDay, weight, outcome.code)) }
    }

    fun deleteLog(entry: LogEntry) {
        viewModelScope.launch { dao.deleteLog(entry) }
    }

    // --- Backup ---

    /** Writes all data and settings as JSON to a file the user picked. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val snapshot = Snapshot(
                    dao.allWorkoutsOnce(), dao.allExercisesOnce(), dao.allSlotsOnce(),
                    dao.allLogsOnce(), dao.allBodyWeightsOnce(), _unit.value, _schedule.value,
                )
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(Backup.toJson(snapshot)) }
                }
                "Exported ${snapshot.logs.size} lifts and ${snapshot.bodyWeights.size} weigh-ins"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            toast(message)
        }
    }

    /** Replaces all data and settings with the contents of a backup file. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                val s = Backup.fromJson(text)
                dao.replaceAll(s.workouts, s.exercises, s.slots, s.logs, s.bodyWeights)
                setUnit(s.unit)
                updateSchedule(s.schedule)
                "Imported ${s.logs.size} lifts and ${s.bodyWeights.size} weigh-ins"
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            toast(message)
        }
    }

    private val resolver get() = getApplication<Application>().contentResolver

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }

    fun logBodyWeight(epochDay: Long, weight: Double) {
        viewModelScope.launch { dao.upsertBodyWeight(BodyWeight(epochDay, weight)) }
    }

    fun deleteBodyWeight(epochDay: Long) {
        viewModelScope.launch { dao.deleteBodyWeight(epochDay) }
    }
}
