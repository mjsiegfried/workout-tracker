package dev.mjsie.workout

import dev.mjsie.workout.data.BodyWeight
import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.data.LogEntry
import dev.mjsie.workout.data.Workout
import dev.mjsie.workout.data.WorkoutExercise
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek

/** Everything the app stores: database tables plus settings. */
data class Snapshot(
    val workouts: List<Workout>,
    val exercises: List<Exercise>,
    val slots: List<WorkoutExercise>,
    val logs: List<LogEntry>,
    val bodyWeights: List<BodyWeight>,
    val unit: String,
    val schedule: Schedule,
)

/** Human-readable JSON backup format. */
object Backup {
    private const val FORMAT = "workout-tracker-backup"
    private const val VERSION = 2 // v2 adds log outcome (missed / 10+ AMRAP)

    fun toJson(s: Snapshot): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("settings", JSONObject()
            .put("unit", s.unit)
            .put("scheduleDays", JSONArray(s.schedule.days.map { it.value }.sorted()))
            .put("scheduleAnchorWeek", s.schedule.anchorWeek)
            .put("scheduleAnchorIndex", s.schedule.anchorIndex))
        .put("workouts", array(s.workouts) { JSONObject().put("id", it.id).put("name", it.name).put("position", it.position) })
        .put("exercises", array(s.exercises) {
            JSONObject().put("id", it.id).put("name", it.name).put("sets", it.sets).put("reps", it.reps)
                .put("increment", it.increment).put("startingWeight", it.startingWeight)
        })
        .put("workoutExercises", array(s.slots) {
            JSONObject().put("workoutId", it.workoutId).put("exerciseId", it.exerciseId).put("position", it.position)
        })
        .put("logs", array(s.logs) {
            JSONObject().put("workoutId", it.workoutId).put("exerciseId", it.exerciseId)
                .put("epochDay", it.epochDay).put("weight", it.weight).put("outcome", it.outcome)
        })
        .put("bodyWeights", array(s.bodyWeights) { JSONObject().put("epochDay", it.epochDay).put("weight", it.weight) })
        .toString(2)

    /** @throws IllegalArgumentException if the text isn't a complete backup from this app. */
    fun fromJson(text: String): Snapshot = try {
        parse(JSONObject(text))
    } catch (e: JSONException) {
        throw IllegalArgumentException("Not a valid backup file", e)
    }

    private fun parse(root: JSONObject): Snapshot {
        require(root.optString("format") == FORMAT) { "Not a Workout Tracker backup" }
        require(root.optInt("version") <= VERSION) { "Backup is from a newer version of the app" }
        val settings = root.getJSONObject("settings")
        val days = settings.getJSONArray("scheduleDays")
        return Snapshot(
            workouts = list(root, "workouts") { Workout(it.getLong("id"), it.getString("name"), it.getInt("position")) },
            exercises = list(root, "exercises") {
                Exercise(it.getLong("id"), it.getString("name"), it.getInt("sets"), it.getInt("reps"),
                    it.getDouble("increment"), it.getDouble("startingWeight"))
            },
            slots = list(root, "workoutExercises") {
                WorkoutExercise(it.getLong("workoutId"), it.getLong("exerciseId"), it.getInt("position"))
            },
            logs = list(root, "logs") {
                LogEntry(it.getLong("workoutId"), it.getLong("exerciseId"), it.getLong("epochDay"), it.getDouble("weight"),
                    it.optInt("outcome", 0))
            },
            bodyWeights = list(root, "bodyWeights") { BodyWeight(it.getLong("epochDay"), it.getDouble("weight")) },
            unit = settings.getString("unit"),
            schedule = Schedule(
                days = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
                anchorWeek = settings.getLong("scheduleAnchorWeek"),
                anchorIndex = settings.getInt("scheduleAnchorIndex"),
            ),
        )
    }

    private fun <T> array(items: List<T>, toJson: (T) -> JSONObject) = JSONArray(items.map(toJson))

    private fun <T> list(root: JSONObject, key: String, fromJson: (JSONObject) -> T): List<T> {
        val array = root.getJSONArray(key)
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }
}
