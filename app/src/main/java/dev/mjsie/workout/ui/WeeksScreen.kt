@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mjsie.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mjsie.workout.AppViewModel
import dev.mjsie.workout.Progression
import dev.mjsie.workout.Schedule
import dev.mjsie.workout.Weekly
import dev.mjsie.workout.data.BodyWeight
import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.data.LogEntry
import dev.mjsie.workout.data.Workout
import dev.mjsie.workout.formatAverage
import dev.mjsie.workout.formatDay
import dev.mjsie.workout.formatWeekRange
import dev.mjsie.workout.formatWeight
import dev.mjsie.workout.weekTitle
import java.time.LocalDate

/** Week-by-week overview: every session (scheduled or logged) with its lifts, plus average body weight. */
@Composable
fun WeeksScreen(vm: AppViewModel, onOpenWeek: (weekStart: Long) -> Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val slots by vm.slots.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val bodyWeights by vm.bodyWeights.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val currentWeek = remember { Progression.weekStart(LocalDate.now()) }

    val exerciseById = exercises.associateBy { it.id }
    val exercisesByWorkout: Map<Long, List<Exercise>> = workouts.associate { workout ->
        workout.id to slots.filter { it.workoutId == workout.id }.mapNotNull { exerciseById[it.exerciseId] }
    }
    val weeks = Weekly.historyWeeks(logs, bodyWeights, currentWeek)

    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(weeks, key = { it }) { week ->
                WeekCard(
                    week, currentWeek, sessionsFor(week, schedule, workouts, logs),
                    exercisesByWorkout, exerciseById, logs, bodyWeights, unit,
                ) { onOpenWeek(week) }
            }
        }
    }
}

/** Scheduled sessions plus any logged sessions in the week (e.g. from before a schedule change). */
private fun sessionsFor(week: Long, schedule: Schedule, workouts: List<Workout>, logs: List<LogEntry>): List<Pair<Long, Workout>> {
    val byId = workouts.associateBy { it.id }
    val scheduled = schedule.sessionsInWeek(week, workouts.size)
        .mapNotNull { s -> s.workoutIndex?.let { workouts.getOrNull(it) }?.let { s.epochDay to it } }
    val logged = logs.filter { it.epochDay in week..week + 6 }
        .mapNotNull { l -> byId[l.workoutId]?.let { l.epochDay to it } }
    return (scheduled + logged).distinctBy { (day, w) -> day to w.id }.sortedBy { it.first }
}

@Composable
private fun WeekCard(
    week: Long,
    currentWeek: Long,
    sessions: List<Pair<Long, Workout>>,
    exercisesByWorkout: Map<Long, List<Exercise>>,
    exerciseById: Map<Long, Exercise>,
    logs: List<LogEntry>,
    bodyWeights: List<BodyWeight>,
    unit: String,
    onClick: () -> Unit,
) {
    val body = bodyWeights.filter { it.epochDay in week..week + 6 }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(weekTitle(week, currentWeek), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatWeekRange(week), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (body.isEmpty()) "Body weight: —"
                else "Body weight avg: ${formatAverage(body.map { it.weight }.average())} $unit (${body.size} day${if (body.size == 1) "" else "s"})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            sessions.forEach { (day, workout) ->
                // The workout's current exercises, plus anything logged that session that's since been removed.
                val loggedIds = logs.filter { it.workoutId == workout.id && it.epochDay == day }.map { it.exerciseId }
                val shown = (exercisesByWorkout[workout.id].orEmpty() + loggedIds.mapNotNull { exerciseById[it] }).distinctBy { it.id }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${formatDay(day)} · ${workout.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                shown.forEach { exercise ->
                    val progress = Progression.progressFor(exercise, workout.id, day, logs)
                    val logged = progress.logged
                    Row {
                        Text(exercise.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        when {
                            logged == null -> Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            logged.weight >= progress.target -> Text("${formatWeight(logged.weight)} $unit ✓")
                            else -> Text("${formatWeight(logged.weight)} $unit ↓", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (shown.isEmpty()) Text("No exercises", style = MaterialTheme.typography.bodySmall)
            }
            if (sessions.isEmpty()) Text("No sessions", style = MaterialTheme.typography.bodySmall)
        }
    }
}
