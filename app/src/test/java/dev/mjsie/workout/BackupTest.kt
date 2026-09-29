package dev.mjsie.workout

import dev.mjsie.workout.data.BodyWeight
import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.data.LogEntry
import dev.mjsie.workout.data.Workout
import dev.mjsie.workout.data.WorkoutExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek

class BackupTest {
    private val snapshot = Snapshot(
        workouts = listOf(Workout(1, "Workout A", 0), Workout(2, "Workout B", 1)),
        exercises = listOf(Exercise(1, "Squat", 5, 5, 5.0, 135.0), Exercise(2, "Bench \"Press\"", 3, 8, 2.5, 95.0)),
        slots = listOf(WorkoutExercise(1, 1, 0), WorkoutExercise(1, 2, 1), WorkoutExercise(2, 1, 0)),
        logs = listOf(LogEntry(1, 1, 20724, 135.0), LogEntry(2, 1, 20726, 140.5, Outcome.MISSED.code)),
        bodyWeights = listOf(BodyWeight(20724, 181.2)),
        unit = "lb",
        schedule = Schedule(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 20724, 3),
    )

    @Test fun `round trips everything`() {
        assertEquals(snapshot, Backup.fromJson(Backup.toJson(snapshot)))
    }

    @Test fun `version 1 backups without outcomes still import`() {
        val v1 = Backup.toJson(snapshot).replace(Regex(""",\s*"outcome": \d"""), "").replace("\"version\": 2", "\"version\": 1")
        val restored = Backup.fromJson(v1)
        assertEquals(listOf(0, 0), restored.logs.map { it.outcome })
    }

    @Test fun `rejects files that are not backups`() {
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("not json") }
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("""{"hello": 1}""") }
    }
}
