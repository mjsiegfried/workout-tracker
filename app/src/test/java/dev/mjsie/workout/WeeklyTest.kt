package dev.mjsie.workout

import dev.mjsie.workout.data.BodyWeight
import dev.mjsie.workout.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeeklyTest {
    private val monday = LocalDate.of(2026, 9, 28)
    private val week = monday.toEpochDay()

    @Test fun `body weight averages per week including empty gap weeks`() {
        val entries = listOf(
            BodyWeight(week, 81.0),
            BodyWeight(week + 2, 80.5),
            BodyWeight(week - 14 + 6, 82.0), // Sunday two weeks ago
        )
        val weeks = Weekly.bodyWeeks(entries, week)
        assertEquals(listOf(week, week - 7, week - 14), weeks.map { it.weekStart })
        assertEquals(80.75, weeks[0].average!!, 1e-9)
        assertNull(weeks[1].average)
        assertEquals(82.0, weeks[2].average!!, 1e-9)
        assertEquals(7, weeks[0].days.size)
    }

    @Test fun `no data still shows the current week`() {
        assertEquals(listOf(week), Weekly.bodyWeeks(emptyList(), week).map { it.weekStart })
        assertEquals(listOf(week), Weekly.historyWeeks(emptyList(), emptyList(), week))
    }

    @Test fun `history spans workout logs and body weights`() {
        val logs = listOf(LogEntry(1, 1, week - 21, 60.0))
        val body = listOf(BodyWeight(week - 3, 80.0)) // previous week's Friday
        assertEquals(listOf(week, week - 7, week - 14, week - 21), Weekly.historyWeeks(logs, body, week))
    }

    @Test fun `formatting`() {
        assertEquals("81.4", formatAverage(81.43))
        assertEquals("−0.3", formatDelta(-0.26))
        assertEquals("+1", formatDelta(1.0))
        assertEquals("±0", formatDelta(0.01))
        assertEquals("Sep 28 – Oct 4", formatWeekRange(week))
    }
}
