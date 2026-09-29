package dev.mjsie.workout.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A workout slot, e.g. "Workout A" / "Workout B". */
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
)

/**
 * An exercise in the shared library. The same exercise (e.g. Squat) can appear in
 * several workouts and shares its weight history across them.
 */
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sets: Int = 3,
    val reps: Int = 5,
    /** How much to add each week once a weight has been logged. */
    val increment: Double = 2.5,
    /** Suggested weight before anything has been logged. */
    val startingWeight: Double = 20.0,
)

/** Join table: which exercises belong to which workout, and in what order. */
@Entity(
    tableName = "workout_exercises",
    primaryKeys = ["workoutId", "exerciseId"],
    foreignKeys = [
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId")],
)
data class WorkoutExercise(
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
)

/**
 * The weight lifted for an exercise in one session of a workout. Squat in Workout A and
 * Squat in Workout B are tracked separately.
 */
@Entity(
    tableName = "log_entries",
    primaryKeys = ["workoutId", "exerciseId", "epochDay"],
    foreignKeys = [
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId")],
)
data class LogEntry(
    val workoutId: Long,
    val exerciseId: Long,
    /** The session's date. */
    val epochDay: Long,
    val weight: Double,
    /** How it went; see [dev.mjsie.workout.Outcome]. 0 = completed. */
    @ColumnInfo(defaultValue = "0") val outcome: Int = 0,
)

/** Morning body weight for one day. */
@Entity(tableName = "body_weights")
data class BodyWeight(
    @PrimaryKey val epochDay: Long,
    val weight: Double,
)
