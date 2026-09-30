package com.family.shoppinglist.data

import android.content.Context
import com.family.shoppinglist.core.WeeklySchedule
import java.time.Instant
import java.time.ZonedDateTime

/** Remembers when the weekly reset last ran, so a missed Sunday is caught up next time the app runs. */
class ResetState(context: Context, private val schedule: WeeklySchedule) {
    private val prefs = context.getSharedPreferences("reset_state", Context.MODE_PRIVATE)

    /** On first run this starts at the most recent Sunday, so installing mid-week doesn't wipe the list. */
    private fun lastRun(now: ZonedDateTime): Instant {
        if (!prefs.contains(KEY)) markDone(now)
        return Instant.ofEpochMilli(prefs.getLong(KEY, 0L))
    }

    fun isDue(now: ZonedDateTime): Boolean = schedule.isDue(lastRun(now), now)

    fun markDone(now: ZonedDateTime) {
        prefs.edit().putLong(KEY, schedule.latestAtOrBefore(now).toInstant().toEpochMilli()).apply()
    }

    private companion object {
        const val KEY = "last_reset_epoch_ms"
    }
}
