package com.family.shoppinglist.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** A weekly moment in time, e.g. "Sunday at 06:00", evaluated in the device's time zone. */
class WeeklySchedule(
    val day: DayOfWeek = DayOfWeek.SUNDAY,
    val time: LocalTime = LocalTime.of(6, 0),
) {
    /** The most recent scheduled moment at or before [now]. */
    fun latestAtOrBefore(now: ZonedDateTime): ZonedDateTime {
        val zone = now.zone
        val date = now.toLocalDate().with(TemporalAdjusters.previousOrSame(day))
        val candidate = ZonedDateTime.of(date, time, zone)
        return if (candidate.isAfter(now)) ZonedDateTime.of(date.minusWeeks(1), time, zone) else candidate
    }

    /** The first scheduled moment strictly after [now]. */
    fun nextAfter(now: ZonedDateTime): ZonedDateTime {
        val latest = latestAtOrBefore(now)
        return ZonedDateTime.of(latest.toLocalDate().plusWeeks(1), time, now.zone)
    }

    /** True if a scheduled moment has passed since [lastRun]. A null [lastRun] is never due. */
    fun isDue(lastRun: Instant?, now: ZonedDateTime): Boolean =
        lastRun != null && latestAtOrBefore(now).toInstant().isAfter(lastRun)
}
