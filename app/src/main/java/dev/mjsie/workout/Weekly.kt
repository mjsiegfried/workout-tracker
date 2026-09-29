package dev.mjsie.workout

import dev.mjsie.workout.data.BodyWeight
import dev.mjsie.workout.data.LogEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One Mon–Sun week of morning body weights. */
data class BodyWeek(
    val weekStart: Long,
    /** epochDay -> weight, only for days that were logged. */
    val byDay: Map<Long, Double>,
) {
    val average: Double? get() = byDay.values.takeIf { it.isNotEmpty() }?.average()
    val days: List<Long> get() = (0L..6L).map { weekStart + it }
}

object Weekly {
    /** Every week from [currentWeek] back to the oldest entry, newest first (empty weeks included). */
    fun bodyWeeks(entries: List<BodyWeight>, currentWeek: Long): List<BodyWeek> {
        val byWeek = entries.groupBy { Progression.weekStart(LocalDate.ofEpochDay(it.epochDay)) }
        return weeksBack(currentWeek, byWeek.keys.minOrNull()).map { week ->
            BodyWeek(week, byWeek[week].orEmpty().associate { it.epochDay to it.weight })
        }
    }

    /** Weeks with workout or body-weight data, from [currentWeek] back to the oldest, newest first. */
    fun historyWeeks(logs: List<LogEntry>, bodyWeights: List<BodyWeight>, currentWeek: Long): List<Long> {
        val oldest = (logs.map { Progression.weekStart(it.epochDay) } +
            bodyWeights.map { Progression.weekStart(LocalDate.ofEpochDay(it.epochDay)) }).minOrNull()
        return weeksBack(currentWeek, oldest)
    }

    private fun weeksBack(currentWeek: Long, oldest: Long?): List<Long> {
        val start = minOf(oldest ?: currentWeek, currentWeek)
        return generateSequence(currentWeek) { it - 7 }.takeWhile { it >= start }.toList()
    }
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d")
private val dayLabel = DateTimeFormatter.ofPattern("EEE, MMM d")

/** "Sep 28 – Oct 4" */
fun formatWeekRange(weekStart: Long): String {
    val start = LocalDate.ofEpochDay(weekStart)
    return "${start.format(shortDate)} – ${start.plusDays(6).format(shortDate)}"
}

/** "This week", "Last week" or "Week of Sep 14, 2026" */
fun weekTitle(weekStart: Long, currentWeek: Long): String = when (weekStart) {
    currentWeek -> "This week"
    currentWeek - 7 -> "Last week"
    else -> "Week of ${formatWeek(weekStart)}"
}

/** "Today", "Yesterday" or "Mon, Sep 28" */
fun dayTitle(epochDay: Long, today: Long): String = when (epochDay) {
    today -> "Today"
    today - 1 -> "Yesterday"
    else -> LocalDate.ofEpochDay(epochDay).format(dayLabel)
}

/** Averages are shown to one decimal: 81.43 -> "81.4" */
fun formatAverage(value: Double): String =
    formatWeight(BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble())

/** "+0.4" / "−0.3" / "±0" */
fun formatDelta(value: Double): String {
    val rounded = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()
    return when {
        rounded > 0 -> "+${formatWeight(rounded)}"
        rounded < 0 -> "−${formatWeight(-rounded)}"
        else -> "±0"
    }
}
