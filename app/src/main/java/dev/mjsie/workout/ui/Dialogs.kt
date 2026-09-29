package dev.mjsie.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.formatWeight
import dev.mjsie.workout.parseWeight

/** Delete button that asks "Really delete?" on the first tap. */
@Composable
private fun ConfirmingDeleteButton(onDelete: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    TextButton(onClick = { if (armed) onDelete() else armed = true }) {
        Text(if (armed) "Really delete?" else "Delete", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) ConfirmingDeleteButton(onDelete)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ExerciseEditorDialog(
    title: String,
    initial: Exercise,
    unit: String,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial.name) }
    var sets by remember { mutableStateOf(initial.sets.toString()) }
    var reps by remember { mutableStateOf(initial.reps.toString()) }
    var increment by remember { mutableStateOf(formatWeight(initial.increment)) }
    var starting by remember { mutableStateOf(formatWeight(initial.startingWeight)) }

    val parsedSets = sets.toIntOrNull()?.takeIf { it > 0 }
    val parsedReps = reps.toIntOrNull()?.takeIf { it > 0 }
    val parsedIncrement = parseWeight(increment)
    val parsedStarting = parseWeight(starting)
    val valid = name.isNotBlank() && parsedSets != null && parsedReps != null &&
        parsedIncrement != null && parsedStarting != null

    val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
    val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sets, { sets = it }, Modifier.weight(1f), label = { Text("Sets") },
                        singleLine = true, keyboardOptions = numberKeys, isError = parsedSets == null)
                    OutlinedTextField(reps, { reps = it }, Modifier.weight(1f), label = { Text("Reps") },
                        singleLine = true, keyboardOptions = numberKeys, isError = parsedReps == null)
                }
                OutlinedTextField(increment, { increment = it }, label = { Text("Increment per session ($unit)") },
                    singleLine = true, keyboardOptions = decimalKeys, isError = parsedIncrement == null)
                OutlinedTextField(starting, { starting = it }, label = { Text("Starting weight ($unit)") },
                    singleLine = true, keyboardOptions = decimalKeys, isError = parsedStarting == null)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(),
                        sets = parsedSets!!,
                        reps = parsedReps!!,
                        increment = parsedIncrement!!,
                        startingWeight = parsedStarting!!,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) ConfirmingDeleteButton(onDelete)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ExercisePickerDialog(
    available: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            if (available.isEmpty()) {
                Text("All exercises in your library are already in this workout.")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(available, key = { it.id }) { exercise ->
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text("${exercise.sets}×${exercise.reps}") },
                            modifier = Modifier.clickable { onPick(exercise) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreateNew) { Text("New exercise…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
