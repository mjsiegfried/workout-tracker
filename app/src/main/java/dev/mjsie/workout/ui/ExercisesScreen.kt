@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mjsie.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mjsie.workout.AppViewModel
import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.formatWeight

/** The exercise library: create, edit (name, sets, reps, increment, starting weight) and delete exercises. */
@Composable
fun ExercisesScreen(vm: AppViewModel, onBack: () -> Unit, onOpenHistory: (Long) -> Unit) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercises") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Exercise(name = "") }) { Icon(Icons.Filled.Add, "New exercise") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(exercises, key = { it.id }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = {
                        Text(
                            "${exercise.sets}×${exercise.reps} · +${formatWeight(exercise.increment)} $unit/session · " +
                                "start ${formatWeight(exercise.startingWeight)} $unit"
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onOpenHistory(exercise.id) }) {
                            Icon(Icons.AutoMirrored.Filled.List, "History")
                        }
                    },
                    modifier = Modifier.clickable { editing = exercise },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { exercise ->
        val isNew = exercise.id == 0L
        ExerciseEditorDialog(
            title = if (isNew) "New exercise" else "Edit exercise",
            initial = exercise,
            unit = unit,
            onDismiss = { editing = null },
            onSave = { vm.saveExercise(it); editing = null },
            onDelete = if (isNew) null else ({ vm.deleteExercise(exercise); editing = null }),
        )
    }
}
