package dev.mjsie.workout

import dev.mjsie.workout.data.Exercise
import dev.mjsie.workout.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgressionTest {
    private val squat = Exercise(id = 1, name = "Squat", increment = 2.5, startingWeight = 20.0)
    private val mon = LocalDate.of(2026, 9, 28).toEpochDay() // a Monday
    private val a = 1L
    private val b = 2L

    @Test fun `weeks start on Monday`() {
        assertEquals(mon, Progression.weekStart(LocalDate.of(2026, 10, 4))) // Sunday
        assertEquals(mon + 7, Progression.weekStart(LocalDate.of(2026, 10, 5)))
    }

    @Test fun `no history suggests starting weight`() {
        val p = Progression.progressFor(squat, a, mon, emptyList())
        assertEquals(20.0, p.target, 0.0)
        assertNull(p.nextTime)
    }

    @Test fun `target is last session of the same workout plus increment`() {
        val logs = listOf(
            LogEntry(a, 1, mon - 10, 55.0),
            LogEntry(a, 1, mon - 3, 60.0),  // last A session (previous Friday)
            LogEntry(b, 1, mon - 5, 100.0), // squat in B is tracked separately
            LogEntry(a, 2, mon - 3, 999.0), // other exercise
        )
        val p = Progression.progressFor(squat, a, mon, logs)
        assertEquals(62.5, p.target, 0.0)
        assertNull(p.logged)
        assertEquals(102.5, Progression.progressFor(squat, b, mon, logs).target, 0.0)
    }

    @Test fun `second session of a workout in the same week builds on the first`() {
        val logs = listOf(LogEntry(a, 1, mon, 60.0))
        val friday = Progression.progressFor(squat, a, mon + 4, logs)
        assertEquals(62.5, friday.target, 0.0)
        val monday = Progression.progressFor(squat, a, mon, logs)
        assertEquals(60.0, monday.logged!!.weight, 0.0)
        assertEquals(62.5, monday.nextTime!!, 0.0)
    }

    @Test fun `weight formatting and parsing`() {
        assertEquals("60", formatWeight(60.0))
        assertEquals("62.5", formatWeight(62.5))
        assertEquals(62.5, parseWeight("62,5")!!, 0.0)
        assertNull(parseWeight("abc"))
    }
}
