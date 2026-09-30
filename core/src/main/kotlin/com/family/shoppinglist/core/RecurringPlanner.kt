package com.family.shoppinglist.core

import java.time.LocalDate

object RecurringPlanner {

    /** The day a cadence entry is next due, or null for weekly entries (which follow the reset instead). */
    fun nextDue(entry: RecurringEntry): LocalDate? = when (val r = entry.recurrence) {
        Recurrence.EveryWeek -> null
        is Recurrence.Every -> entry.lastAddedOn?.let { r.unit.addTo(it, r.amount) } ?: entry.startsOn
    }

    /** Cadence entries that are due today and aren't already sitting on the list. */
    fun dueCadenceIds(entries: List<RecurringEntry>, onList: Set<Long>, today: LocalDate): List<Long> =
        entries.filter { it.id !in onList }
            .filter { entry -> nextDue(entry)?.let { !it.isAfter(today) } == true }
            .map { it.id }

    /** Weekly entries that need adding at a reset because they aren't already on the list. */
    fun weeklyIdsToAdd(entries: List<RecurringEntry>, onList: Set<Long>): List<Long> =
        entries.filter { it.recurrence == Recurrence.EveryWeek && it.id !in onList }.map { it.id }
}
