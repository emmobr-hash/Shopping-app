package com.family.shoppinglist.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyScheduleTest {
    private val london = ZoneId.of("Europe/London")
    private val schedule = WeeklySchedule() // Sunday 06:00

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), london)

    @Test
    fun `2026-09-30 is a Wednesday and the latest reset was the previous Sunday`() {
        val now = at(2026, 9, 30, 12)
        assertEquals(DayOfWeek.WEDNESDAY, now.dayOfWeek)
        assertEquals(at(2026, 9, 27, 6), schedule.latestAtOrBefore(now))
        assertEquals(at(2026, 10, 4, 6), schedule.nextAfter(now))
    }

    @Test
    fun `sunday before 6am still belongs to the previous week`() {
        val now = at(2026, 10, 4, 5, 59)
        assertEquals(at(2026, 9, 27, 6), schedule.latestAtOrBefore(now))
        assertEquals(at(2026, 10, 4, 6), schedule.nextAfter(now))
    }

    @Test
    fun `exactly 6am on sunday is the reset moment`() {
        val now = at(2026, 10, 4, 6)
        assertEquals(now, schedule.latestAtOrBefore(now))
        assertEquals(at(2026, 10, 11, 6), schedule.nextAfter(now))
    }

    @Test
    fun `due once a reset moment has passed since the last run`() {
        val lastRun = at(2026, 9, 27, 6).toInstant()
        assertFalse(schedule.isDue(lastRun, at(2026, 10, 4, 5, 59)))
        assertTrue(schedule.isDue(lastRun, at(2026, 10, 4, 6)))
        assertTrue(schedule.isDue(lastRun, at(2026, 10, 20, 9))) // missed weeks collapse into one reset
    }

    @Test
    fun `not due again right after it has run`() {
        val now = at(2026, 10, 4, 8)
        val lastRun = schedule.latestAtOrBefore(now).toInstant()
        assertFalse(schedule.isDue(lastRun, now))
    }

    @Test
    fun `never run means not due`() {
        assertFalse(schedule.isDue(null, at(2026, 10, 4, 8)))
    }

    @Test
    fun `stays at 6am local time across clocks changing`() {
        // UK clocks go forward on Sunday 2026-03-29 and back on Sunday 2026-10-25
        assertEquals(at(2026, 3, 29, 6), schedule.nextAfter(at(2026, 3, 25, 12)))
        assertEquals(at(2026, 10, 25, 6), schedule.nextAfter(at(2026, 10, 20, 12)))
        assertEquals(6, schedule.nextAfter(at(2026, 3, 25, 12)).hour)
    }

    @Test
    fun `works in other time zones`() {
        val sydney = ZonedDateTime.of(LocalDateTime.of(2026, 9, 30, 12, 0), ZoneId.of("Australia/Sydney"))
        assertEquals(6, schedule.nextAfter(sydney).hour)
        assertEquals(DayOfWeek.SUNDAY, schedule.nextAfter(sydney).dayOfWeek)
        assertTrue(schedule.nextAfter(sydney).toInstant() > Instant.from(sydney))
    }
}
