@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mjsie.workout.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mjsie.workout.AppViewModel
import dev.mjsie.workout.Progression
import dev.mjsie.workout.data.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Training days, workout rotation, which workout this week starts with, units, and backup. */
@Composable
fun ScheduleScreen(vm: AppViewModel, onBack: () -> Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val currentWeek = remember { Progression.weekStart(LocalDate.now()) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Workout?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }

    fun preview(week: Long): String =
        schedule.sessionsInWeek(week, workouts.size).joinToString(" · ") { s ->
            val day = LocalDate.ofEpochDay(s.epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "$day ${s.workoutIndex?.let { workouts.getOrNull(it) }?.let { shortName(it.name) } ?: "?"}"
        }.ifEmpty { "No training days" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Training days")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in schedule.days
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { vm.toggleTrainingDay(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionTitle("Rotation")
            Text(
                "Training days cycle through these workouts in order, continuing from week to week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            workouts.forEachIndexed { index, workout ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${workout.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { vm.moveWorkout(workout, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                        }
                        IconButton(onClick = { vm.moveWorkout(workout, 1) }, enabled = index < workouts.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                        }
                        IconButton(onClick = { renaming = workout }) { Icon(Icons.Filled.Edit, "Rename or delete") }
                    }
                }
            }
            OutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add workout")
            }

            if (workouts.size > 1 && schedule.days.isNotEmpty()) {
                HorizontalDivider()
                SectionTitle("This week starts with")
                val selected = schedule.sessionsInWeek(currentWeek, workouts.size).firstOrNull()?.workoutIndex
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    workouts.forEachIndexed { index, workout ->
                        FilterChip(
                            selected = index == selected,
                            onClick = { vm.startThisWeekWith(index) },
                            label = { Text(workout.name) },
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionTitle("Preview")
            Text("This week: ${preview(currentWeek)}", style = MaterialTheme.typography.bodyMedium)
            Text("Next week: ${preview(currentWeek + 7)}", style = MaterialTheme.typography.bodyMedium)
            Text("Week after: ${preview(currentWeek + 14)}", style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider()
            SectionTitle("Units")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("kg", "lb").forEach { u ->
                    FilterChip(selected = unit == u, onClick = { vm.setUnit(u) }, label = { Text(u) })
                }
            }

            HorizontalDivider()
            SectionTitle("Backup")
            Text(
                "Save everything (workouts, exercises, lifts, body weight, schedule) to a file — e.g. Google Drive " +
                    "or Downloads. Import restores a saved file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("workout-backup-${LocalDate.now()}.json") }) { Text("Export") }
                OutlinedButton(onClick = { confirmImport = true }) { Text("Import") }
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import backup?") },
            text = { Text("This replaces ALL current data and settings with the backup file's contents.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }

    if (adding) {
        TextInputDialog(
            title = "New workout",
            initial = "Workout ${'A' + workouts.size}",
            confirmLabel = "Add",
            onDismiss = { adding = false },
            onConfirm = { vm.addWorkout(it); adding = false },
        )
    }
    renaming?.let { workout ->
        TextInputDialog(
            title = "Edit workout",
            initial = workout.name,
            confirmLabel = "Save",
            onDismiss = { renaming = null },
            onConfirm = { vm.renameWorkout(workout, it); renaming = null },
            onDelete = { vm.deleteWorkout(workout); renaming = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
