@file:OptIn(ExperimentalMaterial3Api::class)

package dev.mjsie.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mjsie.workout.AppViewModel
import dev.mjsie.workout.BodyWeek
import dev.mjsie.workout.Progression
import dev.mjsie.workout.Weekly
import dev.mjsie.workout.dayTitle
import dev.mjsie.workout.formatAverage
import dev.mjsie.workout.formatDelta
import dev.mjsie.workout.formatWeekRange
import dev.mjsie.workout.formatWeight
import dev.mjsie.workout.parseWeight
import dev.mjsie.workout.weekTitle
import java.time.LocalDate

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

/** Daily morning weigh-ins shown as a Mon–Sun calendar per week, with weekly averages. */
@Composable
fun BodyWeightScreen(vm: AppViewModel) {
    val entries by vm.bodyWeights.collectAsStateWithLifecycle()
    val unit by vm.unit.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now().toEpochDay() }
    val currentWeek = remember { Progression.weekStart(LocalDate.now()) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today) }

    val existing = entries.find { it.epochDay == selectedDay }
    val lastKnown = entries.firstOrNull()?.weight
    // Pre-fill with the selected day's value, otherwise the most recent weigh-in.
    var input by remember(selectedDay, existing?.weight, lastKnown) {
        mutableStateOf((existing?.weight ?: lastKnown)?.let(::formatWeight) ?: "")
    }
    val weeks = Weekly.bodyWeeks(entries, currentWeek)

    Scaffold(topBar = { TopAppBar(title = { Text("Body weight") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepSelector(
                            title = dayTitle(selectedDay, today),
                            subtitle = if (existing != null) "Logged ${formatWeight(existing.weight)} $unit" else "Not logged",
                            onPrevious = { selectedDay -= 1 },
                            onNext = { selectedDay += 1 },
                            nextEnabled = selectedDay < today,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Weight ($unit)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Spacer(Modifier.width(8.dp))
                            val parsed = parseWeight(input)
                            Button(onClick = { parsed?.let { vm.logBodyWeight(selectedDay, it) } }, enabled = parsed != null) {
                                Text(if (existing == null) "Save" else "Update")
                            }
                        }
                        if (existing != null) {
                            TextButton(onClick = { vm.deleteBodyWeight(selectedDay) }) {
                                Text("Delete this day", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            itemsIndexed(weeks, key = { _, w -> w.weekStart }) { index, week ->
                // Nearest older week that has an average, for the week-over-week change.
                val previousAverage = weeks.drop(index + 1).firstNotNullOfOrNull { it.average }
                BodyWeekCard(week, currentWeek, previousAverage, today, selectedDay, unit) { selectedDay = it }
            }
        }
    }
}

@Composable
private fun BodyWeekCard(
    week: BodyWeek,
    currentWeek: Long,
    previousAverage: Double?,
    today: Long,
    selectedDay: Long,
    unit: String,
    onSelectDay: (Long) -> Unit,
) {
    val average = week.average
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(weekTitle(week.weekStart, currentWeek), style = MaterialTheme.typography.titleSmall)
                    Text(formatWeekRange(week.weekStart), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (average != null) "avg ${formatAverage(average)} $unit" else "no data",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (average != null && previousAverage != null) {
                        Text("${formatDelta(average - previousAverage)} $unit vs prev.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                week.days.forEachIndexed { i, day ->
                    val selected = day == selectedDay
                    val future = day > today
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable(enabled = !future) { onSelectDay(day) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val dim = MaterialTheme.colorScheme.onSurfaceVariant
                        Text(dayLetters[i], style = MaterialTheme.typography.labelSmall, color = dim)
                        Text(LocalDate.ofEpochDay(day).dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = dim)
                        Text(
                            week.byDay[day]?.let(::formatWeight) ?: "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (future) dim.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
