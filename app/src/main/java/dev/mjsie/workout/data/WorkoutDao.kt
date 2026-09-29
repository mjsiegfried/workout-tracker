package dev.mjsie.workout.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    // --- Workouts ---

    @Query("SELECT * FROM workouts ORDER BY position, id")
    abstract fun workouts(): Flow<List<Workout>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workouts")
    abstract suspend fun nextWorkoutPosition(): Int

    @Insert
    abstract suspend fun insertWorkout(workout: Workout): Long

    @Update
    abstract suspend fun updateWorkout(workout: Workout)

    @Update
    abstract suspend fun updateWorkouts(workouts: List<Workout>)

    @Delete
    protected abstract suspend fun deleteWorkoutRow(workout: Workout)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    protected abstract suspend fun deleteSlotsForWorkout(workoutId: Long)

    @Query("DELETE FROM log_entries WHERE workoutId = :workoutId")
    protected abstract suspend fun deleteLogsForWorkout(workoutId: Long)

    @Transaction
    open suspend fun deleteWorkout(workout: Workout) {
        deleteLogsForWorkout(workout.id)
        deleteSlotsForWorkout(workout.id)
        deleteWorkoutRow(workout)
    }

    // --- Exercise library ---

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    abstract fun exercises(): Flow<List<Exercise>>

    @Insert
    abstract suspend fun insertExercise(exercise: Exercise): Long

    @Update
    abstract suspend fun updateExercise(exercise: Exercise)

    @Delete
    protected abstract suspend fun deleteExerciseRow(exercise: Exercise)

    @Query("DELETE FROM workout_exercises WHERE exerciseId = :exerciseId")
    protected abstract suspend fun deleteSlotsForExercise(exerciseId: Long)

    @Query("DELETE FROM log_entries WHERE exerciseId = :exerciseId")
    protected abstract suspend fun deleteLogsForExercise(exerciseId: Long)

    @Transaction
    open suspend fun deleteExercise(exercise: Exercise) {
        deleteSlotsForExercise(exercise.id)
        deleteLogsForExercise(exercise.id)
        deleteExerciseRow(exercise)
    }

    // --- Exercises within a workout ---

    @Query(
        """
        SELECT e.* FROM exercises e
        JOIN workout_exercises we ON we.exerciseId = e.id
        WHERE we.workoutId = :workoutId
        ORDER BY we.position
        """
    )
    abstract fun exercisesFor(workoutId: Long): Flow<List<Exercise>>

    @Query("SELECT * FROM workout_exercises ORDER BY workoutId, position")
    abstract fun allSlots(): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    abstract suspend fun slots(workoutId: Long): List<WorkoutExercise>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workout_exercises WHERE workoutId = :workoutId")
    abstract suspend fun nextSlotPosition(workoutId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSlot(slot: WorkoutExercise)

    @Update
    abstract suspend fun updateSlots(slots: List<WorkoutExercise>)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    abstract suspend fun removeSlot(workoutId: Long, exerciseId: Long)

    @Transaction
    open suspend fun addToWorkout(workoutId: Long, exerciseId: Long) {
        insertSlot(WorkoutExercise(workoutId, exerciseId, nextSlotPosition(workoutId)))
    }

    // --- Weight log ---

    @Query("SELECT * FROM log_entries ORDER BY epochDay DESC")
    abstract fun logs(): Flow<List<LogEntry>>

    @Upsert
    abstract suspend fun upsertLog(entry: LogEntry)

    @Delete
    abstract suspend fun deleteLog(entry: LogEntry)

    // --- Backup / restore ---

    @Query("SELECT * FROM workouts") abstract suspend fun allWorkoutsOnce(): List<Workout>
    @Query("SELECT * FROM exercises") abstract suspend fun allExercisesOnce(): List<Exercise>
    @Query("SELECT * FROM workout_exercises") abstract suspend fun allSlotsOnce(): List<WorkoutExercise>
    @Query("SELECT * FROM log_entries") abstract suspend fun allLogsOnce(): List<LogEntry>
    @Query("SELECT * FROM body_weights") abstract suspend fun allBodyWeightsOnce(): List<BodyWeight>

    @Query("DELETE FROM log_entries") protected abstract suspend fun clearLogs()
    @Query("DELETE FROM workout_exercises") protected abstract suspend fun clearSlots()
    @Query("DELETE FROM body_weights") protected abstract suspend fun clearBodyWeights()
    @Query("DELETE FROM workouts") protected abstract suspend fun clearWorkouts()
    @Query("DELETE FROM exercises") protected abstract suspend fun clearExercises()

    @Insert protected abstract suspend fun insertWorkouts(items: List<Workout>)
    @Insert protected abstract suspend fun insertExercises(items: List<Exercise>)
    @Insert protected abstract suspend fun insertSlots(items: List<WorkoutExercise>)
    @Insert protected abstract suspend fun insertLogs(items: List<LogEntry>)
    @Insert protected abstract suspend fun insertBodyWeights(items: List<BodyWeight>)

    /** Replaces all data in one transaction: either everything is restored or nothing changes. */
    @Transaction
    open suspend fun replaceAll(
        workouts: List<Workout>,
        exercises: List<Exercise>,
        slots: List<WorkoutExercise>,
        logs: List<LogEntry>,
        bodyWeights: List<BodyWeight>,
    ) {
        clearLogs(); clearSlots(); clearBodyWeights(); clearWorkouts(); clearExercises()
        insertWorkouts(workouts); insertExercises(exercises); insertSlots(slots)
        insertLogs(logs); insertBodyWeights(bodyWeights)
    }

    // --- Body weight ---

    @Query("SELECT * FROM body_weights ORDER BY epochDay DESC")
    abstract fun bodyWeights(): Flow<List<BodyWeight>>

    @Upsert
    abstract suspend fun upsertBodyWeight(entry: BodyWeight)

    @Query("DELETE FROM body_weights WHERE epochDay = :epochDay")
    abstract suspend fun deleteBodyWeight(epochDay: Long)
}
