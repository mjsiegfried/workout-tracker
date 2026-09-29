package dev.mjsie.workout

import java.time.DayOfWeek

/** A training day in a given week, and which workout (index into the rotation) falls on it. */
data class ScheduledDay(val epochDay: Long, val workoutIndex: Int?)

/**
 * Training days per week plus a rotation that continues across weeks.
 * E.g. Mon/Wed/Fri with rotation [A, B]: week 1 = A B A, week 2 = B A B, ...
 *
 * Sessions are numbered continuously; [anchorIndex] is the session number of the first
 * training day in [anchorWeek]. The workout for session n is rotation[n mod rotationSize].
 */
data class Schedule(
    val days: Set<DayOfWeek>,
    val anchorWeek: Long,
    val anchorIndex: Int,
) {
    private val ordered: List<DayOfWeek> get() = days.sortedBy { it.value }

    fun firstSessionNumber(weekStart: Long): Long =
        Math.floorDiv(weekStart - anchorWeek, 7L) * ordered.size + anchorIndex

    fun sessionsInWeek(weekStart: Long, rotationSize: Int): List<ScheduledDay> {
        val first = firstSessionNumber(weekStart)
        return ordered.mapIndexed { i, day ->
            ScheduledDay(
                epochDay = weekStart + day.value - 1,
                workoutIndex = if (rotationSize > 0) Math.floorMod(first + i, rotationSize.toLong()).toInt() else null,
            )
        }
    }

    /** Change training days without changing which workout starts [currentWeek]. */
    fun withDays(newDays: Set<DayOfWeek>, currentWeek: Long): Schedule =
        Schedule(newDays, currentWeek, firstSessionNumber(currentWeek).toInt())

    /** Make [currentWeek] start with rotation entry [index]; later weeks follow on from it. */
    fun startingWith(index: Int, currentWeek: Long): Schedule = copy(anchorWeek = currentWeek, anchorIndex = index)
}
