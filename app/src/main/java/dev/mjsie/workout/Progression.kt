package dev.mjsie.workout

import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.data.LogEntry
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Where an exercise stands in one session of a workout.
 * Pure data, so the progression rules are easy to unit test.
 */
data class ExerciseProgress(
    val exercise: Exercise,
    /** Most recent earlier session of the same workout that logged this exercise. */
    val previous: LogEntry?,
    /** This session's entry, if already logged. */
    val logged: LogEntry?,
) {
    /** What you SHOULD lift: last session's weight + increment, or the starting weight. */
    val target: Double
        get() = previous?.let { it.weight + exercise.increment } ?: exercise.startingWeight

    /** Preview for the next session of this workout, once this one is logged. */
    val nextTime: Double?
        get() = logged?.let { it.weight + exercise.increment }
}

object Progression {
    /** Weeks start on Monday; returns that Monday as an epoch day. */
    fun weekStart(date: LocalDate): Long =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

    fun weekStart(epochDay: Long): Long = weekStart(LocalDate.ofEpochDay(epochDay))

    /** Progress of [exercise] in the [workoutId] session on [epochDay]. Other workouts' logs are ignored. */
    fun progressFor(exercise: Exercise, workoutId: Long, epochDay: Long, logs: List<LogEntry>): ExerciseProgress {
        val mine = logs.filter { it.workoutId == workoutId && it.exerciseId == exercise.id }
        return ExerciseProgress(
            exercise = exercise,
            previous = mine.filter { it.epochDay < epochDay }.maxByOrNull { it.epochDay },
            logged = mine.find { it.epochDay == epochDay },
        )
    }
}

private val shortDay = DateTimeFormatter.ofPattern("EEE, MMM d")

/** "Mon, Sep 28" */
fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDay)

private val weekFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun formatWeek(weekStart: Long): String = LocalDate.ofEpochDay(weekStart).format(weekFormatter)

/** 60.0 -> "60", 62.5 -> "62.5" */
fun formatWeight(weight: Double): String = BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString()

/** Accepts "62.5" or "62,5". */
fun parseWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
