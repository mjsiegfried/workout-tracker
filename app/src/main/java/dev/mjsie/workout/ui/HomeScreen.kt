@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mjsie.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mjsie.workout.AppViewModel
import dev.mjsie.workout.ExerciseProgress
import dev.mjsie.workout.Outcome
import dev.mjsie.workout.result
import dev.mjsie.workout.Progression
import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.data.Workout
import dev.mjsie.workout.formatDay
import dev.mjsie.workout.formatWeekRange
import dev.mjsie.workout.formatWeight
import dev.mjsie.workout.parseWeight
import dev.mjsie.workout.weekTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val sessionDate = DateTimeFormatter.ofPattern("EEEE, MMM d")

/** "Workout A" -> "A"; other names are shortened to fit the day strip. */
fun shortName(name: String): String = name.removePrefix("Workout").trim().ifEmpty { name }.take(4)

/** The week planner: which workout falls on which day, with exercises logged and edited inline. */
@Composable
fun HomeScreen(vm: AppViewModel, onOpenSchedule: () -> Unit, onOpenExercises: () -> Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val slots by vm.slots.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val week by vm.selectedWeek.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now().toEpochDay() }
    val currentWeek = remember { Progression.weekStart(LocalDate.now()) }

    val exerciseById = exercises.associateBy { it.id }
    fun exercisesOf(workoutId: Long): List<Exercise> =
        slots.filter { it.workoutId == workoutId }.mapNotNull { exerciseById[it.exerciseId] }

    val sessions = schedule.sessionsInWeek(week, workouts.size)
        .mapNotNull { s -> s.workoutIndex?.let { workouts.getOrNull(it) }?.let { s.epochDay to it } }
    val workoutByDay = sessions.toMap()

    // In the current week, open the next session; past weeks open everything for review.
    val openOverrides = remember(week) { mutableStateMapOf<Long, Boolean>() }
    val defaultOpen = if (week == currentWeek) (sessions.firstOrNull { it.first >= today } ?: sessions.lastOrNull())?.first else null
    fun isOpen(day: Long) = openOverrides[day] ?: (week != currentWeek || day == defaultOpen)
    val editingDays = remember(week) { mutableStateMapOf<Long, Boolean>() }

    var editExercise by remember { mutableStateOf<Exercise?>(null) }
    var pickFor by remember { mutableStateOf<Long?>(null) }   // workout id
    var createFor by remember { mutableStateOf<Long?>(null) } // workout id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training") },
                actions = {
                    TextButton(onClick = onOpenExercises) { Text("Exercises") }
                    IconButton(onClick = onOpenSchedule) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StepSelector(
                    title = weekTitle(week, currentWeek),
                    subtitle = formatWeekRange(week),
                    onPrevious = { vm.selectWeek(week - 7) },
                    onNext = { vm.selectWeek(week + 7) },
                    nextEnabled = true,
                )
            }
            item {
                DayStrip(week, today, workoutByDay) { day -> openOverrides[day] = !isOpen(day) }
            }
            if (sessions.isEmpty()) {
                item {
                    Text(
                        if (workouts.isEmpty()) "No workouts yet — tap ⚙ to create them and set your schedule."
                        else "No training days set — tap ⚙ to choose your days.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(sessions, key = { it.first }) { (day, workout) ->
                val sessionExercises = exercisesOf(workout.id)
                SessionCard(
                    day = day,
                    today = today,
                    workout = workout,
                    progress = sessionExercises.map { Progression.progressFor(it, workout.id, day, logs) },
                    unit = unit,
                    open = isOpen(day),
                    editing = editingDays[day] == true,
                    onToggleOpen = { openOverrides[day] = !isOpen(day) },
                    onToggleEditing = { editingDays[day] = editingDays[day] != true },
                    onLog = { exercise, weight, outcome -> vm.logWeight(workout.id, exercise.id, day, weight, outcome) },
                    onEditExercise = { editExercise = it },
                    onMove = { exercise, delta -> vm.move(workout.id, exercise.id, delta) },
                    onRemove = { vm.removeFromWorkout(workout.id, it.id) },
                    onAdd = { pickFor = workout.id },
                )
            }
        }
    }

    editExercise?.let { exercise ->
        ExerciseEditorDialog(
            title = "Edit exercise",
            initial = exercise,
            unit = unit,
            onDismiss = { editExercise = null },
            onSave = { vm.saveExercise(it); editExercise = null },
            onDelete = { vm.deleteExercise(exercise); editExercise = null },
        )
    }
    pickFor?.let { workoutId ->
        val inWorkout = exercisesOf(workoutId).map { it.id }.toSet()
        ExercisePickerDialog(
            available = exercises.filter { it.id !in inWorkout },
            onDismiss = { pickFor = null },
            onPick = { vm.addToWorkout(workoutId, it.id); pickFor = null },
            onCreateNew = { pickFor = null; createFor = workoutId },
        )
    }
    createFor?.let { workoutId ->
        ExerciseEditorDialog(
            title = "New exercise",
            initial = Exercise(name = ""),
            unit = unit,
            onDismiss = { createFor = null },
            onSave = { vm.saveExercise(it, addToWorkout = workoutId); createFor = null },
        )
    }
}

/** Mon–Sun with the scheduled workout under each training day. */
@Composable
private fun DayStrip(week: Long, today: Long, workoutByDay: Map<Long, Workout>, onTapDay: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0..6).forEach { i ->
            val day = week + i
            val workout = workoutByDay[day]
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (workout != null) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .then(if (day == today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .clickable(enabled = workout != null) { onTapDay(day) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                Text(dayLetters[i], style = MaterialTheme.typography.labelSmall, color = dim)
                Text(LocalDate.ofEpochDay(day).dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = dim)
                Text(
                    workout?.let { shortName(it.name) } ?: "–",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (workout != null) MaterialTheme.colorScheme.onSecondaryContainer else dim,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    day: Long,
    today: Long,
    workout: Workout,
    progress: List<ExerciseProgress>,
    unit: String,
    open: Boolean,
    editing: Boolean,
    onToggleOpen: () -> Unit,
    onToggleEditing: () -> Unit,
    onLog: (Exercise, Double, Outcome) -> Unit,
    onEditExercise: (Exercise) -> Unit,
    onMove: (Exercise, Int) -> Unit,
    onRemove: (Exercise) -> Unit,
    onAdd: () -> Unit,
) {
    val loggedCount = progress.count { it.logged != null }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (day == today) "Today · " else "") + LocalDate.ofEpochDay(day).format(sessionDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(workout.name, style = MaterialTheme.typography.titleMedium)
                Text("$loggedCount/${progress.size} logged", style = MaterialTheme.typography.bodySmall)
            }
            if (open) {
                IconButton(onClick = onToggleEditing) {
                    Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, if (editing) "Done editing" else "Edit exercises")
                }
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                if (open) "Collapse" else "Expand",
                Modifier.padding(12.dp),
            )
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (progress.isEmpty()) {
                    Text("No exercises yet.", style = MaterialTheme.typography.bodyMedium)
                }
                progress.forEachIndexed { index, p ->
                    ExerciseRow(
                        progress = p,
                        unit = unit,
                        editing = editing,
                        canMoveUp = index > 0,
                        canMoveDown = index < progress.lastIndex,
                        onLog = { weight, outcome -> onLog(p.exercise, weight, outcome) },
                        onEdit = { onEditExercise(p.exercise) },
                        onMove = { onMove(p.exercise, it) },
                        onRemove = { onRemove(p.exercise) },
                    )
                }
                if (editing || progress.isEmpty()) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add exercise to ${workout.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    progress: ExerciseProgress,
    unit: String,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLog: (Double, Outcome) -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val exercise = progress.exercise
    val logged = progress.logged
    var input by remember(exercise.id, logged?.weight, progress.target) {
        mutableStateOf(formatWeight(logged?.weight ?: progress.target))
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${exercise.sets}×${exercise.reps} · target ${formatWeight(progress.target)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (editing) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove from workout") }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.width(92.dp),
                    singleLine = true,
                    suffix = { Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val parsed = parseWeight(input)
                val dirty = parsed != null && parsed != logged?.weight
                FilledTonalIconButton(
                    // Updating the weight keeps any Missed / 10+ AMRAP mark.
                    onClick = { parsed?.let { onLog(it, logged?.result ?: Outcome.DONE) } },
                    enabled = logged == null && parsed != null || dirty,
                ) {
                    Icon(Icons.Filled.Check, if (logged == null) "Log" else "Update")
                }
            }
        }
        if (!editing) {
            val outcome = logged?.result
            // The chips log the session too, so one tap is enough: weight from the box, else the target.
            val weightForChip = parseWeight(input) ?: logged?.weight ?: progress.target
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = outcome == Outcome.MISSED,
                    onClick = { onLog(weightForChip, if (outcome == Outcome.MISSED) Outcome.DONE else Outcome.MISSED) },
                    label = { Text("Missed") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
                FilterChip(
                    selected = outcome == Outcome.AMRAP_10,
                    onClick = { onLog(weightForChip, if (outcome == Outcome.AMRAP_10) Outcome.DONE else Outcome.AMRAP_10) },
                    label = { Text("10+ AMRAP") },
                )
            }
            val previous = progress.previous
            Text(
                when {
                    logged != null -> {
                        val next = "next time ${formatWeight(progress.nextTime!!)} $unit"
                        when (outcome) {
                            Outcome.MISSED -> "✗ Missed at ${formatWeight(logged.weight)} $unit · deload: $next"
                            Outcome.AMRAP_10 -> "✓ 10+ AMRAP at ${formatWeight(logged.weight)} $unit · double jump: $next"
                            else -> "✓ Logged ${formatWeight(logged.weight)} $unit · $next"
                        }
                    }
                    previous != null -> {
                        val note = when (previous.result) {
                            Outcome.MISSED -> ", missed → deload"
                            Outcome.AMRAP_10 -> ", 10+ AMRAP → double jump"
                            else -> ""
                        }
                        "Last: ${formatWeight(previous.weight)} $unit (${formatDay(previous.epochDay)}$note)"
                    }
                    else -> "No history yet — starting weight"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    outcome == Outcome.MISSED -> MaterialTheme.colorScheme.error
                    logged != null -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
