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
    /** What you SHOULD lift: based on last session's weight and outcome, or the starting weight. */
    val target: Double
        get() = previous?.let { Progression.nextTarget(it, exercise) } ?: exercise.startingWeight

    /** Preview for the next session of this workout, once this one is logged. */
    val nextTime: Double?
        get() = logged?.let { Progression.nextTarget(it, exercise) }
}

/** How a logged session went. Stored in [LogEntry.outcome] as [code]. */
enum class Outcome(val code: Int) {
    /** Completed all reps: add the usual increment. */
    DONE(0),
    /** Didn't complete the exercise: deload by 10%. */
    MISSED(1),
    /** 10+ reps on the last AMRAP set: add double the increment. */
    AMRAP_10(2);

    companion object {
        fun of(code: Int): Outcome = entries.firstOrNull { it.code == code } ?: DONE
    }
}

val LogEntry.result: Outcome get() = Outcome.of(outcome)

object Progression {
    const val DELOAD_FRACTION = 0.10
    const val PLATE_STEP = 2.5

    /** Next session's target after [entry]. */
    fun nextTarget(entry: LogEntry, exercise: Exercise): Double = when (entry.result) {
        Outcome.DONE -> entry.weight + exercise.increment
        Outcome.AMRAP_10 -> entry.weight + 2 * exercise.increment
        Outcome.MISSED -> maxOf(0.0, entry.weight - roundToStep(entry.weight * DELOAD_FRACTION))
    }

    /** Nearest multiple of 2.5 (halves round up): 13.5 -> 12.5, 9.5 -> 10. */
    fun roundToStep(value: Double, step: Double = PLATE_STEP): Double = Math.round(value / step) * step

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
