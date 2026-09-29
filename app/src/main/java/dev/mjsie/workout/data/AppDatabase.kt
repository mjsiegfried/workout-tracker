package dev.mjsie.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Workout::class, Exercise::class, WorkoutExercise::class, LogEntry::class, BodyWeight::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "workouts.db")
                    .addCallback(SeedData)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}

/** v2 adds the body weight table; existing workout data is kept. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_weights` " +
                "(`epochDay` INTEGER NOT NULL, `weight` REAL NOT NULL, PRIMARY KEY(`epochDay`))"
        )
    }
}

/**
 * v3 logs weights per workout session (workout, exercise, day) instead of per exercise per week.
 * Old weekly entries move to the Monday of their week, under the first workout containing the exercise.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `log_entries_new` (
              `workoutId` INTEGER NOT NULL, `exerciseId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `weight` REAL NOT NULL,
              PRIMARY KEY(`workoutId`, `exerciseId`, `epochDay`),
              FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
            """
        )
        db.execSQL(
            """
            INSERT INTO `log_entries_new` (workoutId, exerciseId, epochDay, weight)
            SELECT (SELECT we.workoutId FROM workout_exercises we JOIN workouts w ON w.id = we.workoutId
                    WHERE we.exerciseId = l.exerciseId ORDER BY w.position, w.id LIMIT 1),
                   l.exerciseId, l.weekStart, l.weight
            FROM log_entries l
            WHERE EXISTS (SELECT 1 FROM workout_exercises we WHERE we.exerciseId = l.exerciseId)
            """
        )
        db.execSQL("DROP TABLE `log_entries`")
        db.execSQL("ALTER TABLE `log_entries_new` RENAME TO `log_entries`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_exerciseId` ON `log_entries` (`exerciseId`)")
    }
}

/** Starter A/B program inserted the first time the database is created. Everything is editable in the app. */
private object SeedData : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO workouts (id, name, position) VALUES (1, 'Workout A', 0), (2, 'Workout B', 1)")
        db.execSQL(
            """
            INSERT INTO exercises (id, name, sets, reps, increment, startingWeight) VALUES
              (1, 'Squat',          5, 5, 2.5, 20),
              (2, 'Bench Press',    5, 5, 2.5, 20),
              (3, 'Barbell Row',    5, 5, 2.5, 30),
              (4, 'Overhead Press', 5, 5, 2.5, 20),
              (5, 'Deadlift',       1, 5, 5.0, 40)
            """
        )
        db.execSQL(
            """
            INSERT INTO workout_exercises (workoutId, exerciseId, position) VALUES
              (1, 1, 0), (1, 2, 1), (1, 3, 2),
              (2, 1, 0), (2, 4, 1), (2, 5, 2)
            """
        )
    }
}
