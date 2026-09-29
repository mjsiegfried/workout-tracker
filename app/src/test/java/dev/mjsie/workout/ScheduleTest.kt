package dev.mjsie.workout

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class ScheduleTest {
    private val week = LocalDate.of(2026, 9, 28).toEpochDay() // Monday
    private val mwf = Schedule(setOf(MONDAY, WEDNESDAY, FRIDAY), anchorWeek = week, anchorIndex = 0)

    private fun Schedule.pattern(weekStart: Long, size: Int = 2) =
        sessionsInWeek(weekStart, size).map { it.workoutIndex }

    @Test fun `alternates A and B across weeks`() {
        assertEquals(listOf(0, 1, 0), mwf.pattern(week))       // A B A
        assertEquals(listOf(1, 0, 1), mwf.pattern(week + 7))   // B A B
        assertEquals(listOf(0, 1, 0), mwf.pattern(week + 14))
        assertEquals(listOf(1, 0, 1), mwf.pattern(week - 7))   // past weeks work too
    }

    @Test fun `sessions land on the right dates`() {
        assertEquals(listOf(week, week + 2, week + 4), mwf.sessionsInWeek(week, 2).map { it.epochDay })
    }

    @Test fun `changing days keeps this week's first workout`() {
        val next = mwf.withDays(setOf(TUESDAY, FRIDAY), week + 7) // week+7 started with B
        assertEquals(listOf(1, 0), next.pattern(week + 7))
        assertEquals(listOf(1, 0), next.pattern(week + 14))
    }

    @Test fun `start this week with B`() {
        assertEquals(listOf(1, 0, 1), mwf.startingWith(1, week).pattern(week))
    }

    @Test fun `three workout rotation and no workouts`() {
        assertEquals(listOf(0, 1, 2), mwf.pattern(week, 3))
        assertEquals(listOf(0, 1, 2), mwf.pattern(week + 7, 3))
        assertEquals(listOf(null, null, null), mwf.pattern(week, 0))
    }
}
