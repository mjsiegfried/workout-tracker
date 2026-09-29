@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mjsie.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mjsie.workout.AppViewModel
import dev.mjsie.workout.formatDay
import dev.mjsie.workout.formatWeight

/** Every logged session of one exercise, across all workouts. */
@Composable
fun HistoryScreen(vm: AppViewModel, exerciseId: Long, onBack: () -> Unit) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val entries = logs.filter { it.exerciseId == exerciseId } // already newest first

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercises.find { it.id == exerciseId }?.name ?: "History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                item { Text("Nothing logged yet.", Modifier.padding(16.dp)) }
            }
            items(entries, key = { "${it.workoutId}-${it.epochDay}" }) { entry ->
                ListItem(
                    headlineContent = { Text("${formatWeight(entry.weight)} $unit") },
                    supportingContent = {
                        Text("${formatDay(entry.epochDay)} · ${workouts.find { it.id == entry.workoutId }?.name ?: "?"}")
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.deleteLog(entry) }) { Icon(Icons.Filled.Delete, "Delete entry") }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
