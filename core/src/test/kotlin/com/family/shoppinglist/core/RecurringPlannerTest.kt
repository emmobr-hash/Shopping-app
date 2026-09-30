package com.family.shoppinglist.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurringPlannerTest {
    private val today = LocalDate.of(2026, 9, 30)
    private fun every(n: Int, unit: CadenceUnit) = Recurrence.Every(n, unit)

    private fun entry(
        id: Long,
        recurrence: Recurrence,
        startsOn: LocalDate = today,
        lastAddedOn: LocalDate? = null,
    ) = RecurringEntry(id, recurrence, startsOn, lastAddedOn)

    @Test
    fun `new cadence entry is due from its start date`() {
        val e = entry(1, every(3, CadenceUnit.WEEKS), startsOn = today.minusDays(1))
        assertEquals(today.minusDays(1), RecurringPlanner.nextDue(e))
        assertEquals(listOf(1L), RecurringPlanner.dueCadenceIds(listOf(e), emptySet(), today))
    }

    @Test
    fun `entry starting in the future is not due yet`() {
        val e = entry(1, every(3, CadenceUnit.WEEKS), startsOn = today.plusWeeks(3))
        assertEquals(emptyList<Long>(), RecurringPlanner.dueCadenceIds(listOf(e), emptySet(), today))
    }

    @Test
    fun `toilet paper every 3 weeks comes back exactly 3 weeks after it was added`() {
        val added = LocalDate.of(2026, 9, 9)
        val e = entry(1, every(3, CadenceUnit.WEEKS), lastAddedOn = added)
        assertEquals(LocalDate.of(2026, 9, 30), RecurringPlanner.nextDue(e))
        assertEquals(listOf(1L), RecurringPlanner.dueCadenceIds(listOf(e), emptySet(), today))
        assertEquals(emptyList<Long>(), RecurringPlanner.dueCadenceIds(listOf(e), emptySet(), today.minusDays(1)))
    }

    @Test
    fun `overdue entries are still due`() {
        val e = entry(1, every(1, CadenceUnit.MONTHS), lastAddedOn = LocalDate.of(2026, 6, 1))
        assertEquals(listOf(1L), RecurringPlanner.dueCadenceIds(listOf(e), emptySet(), today))
    }

    @Test
    fun `entry already on the list is not added twice`() {
        val e = entry(1, every(1, CadenceUnit.DAYS))
        assertEquals(emptyList<Long>(), RecurringPlanner.dueCadenceIds(listOf(e), setOf(1L), today))
    }

    @Test
    fun `month arithmetic clamps to the end of short months`() {
        val e = entry(1, every(1, CadenceUnit.MONTHS), lastAddedOn = LocalDate.of(2026, 1, 31))
        assertEquals(LocalDate.of(2026, 2, 28), RecurringPlanner.nextDue(e))
    }

    @Test
    fun `weekly entries have no cadence date and are never picked up as cadence`() {
        val w = entry(1, Recurrence.EveryWeek)
        assertNull(RecurringPlanner.nextDue(w))
        assertEquals(emptyList<Long>(), RecurringPlanner.dueCadenceIds(listOf(w), emptySet(), today))
    }

    @Test
    fun `weekly reset adds only the weekly entries that are missing`() {
        val entries = listOf(
            entry(1, Recurrence.EveryWeek),
            entry(2, Recurrence.EveryWeek),
            entry(3, every(2, CadenceUnit.WEEKS)),
        )
        assertEquals(listOf(2L), RecurringPlanner.weeklyIdsToAdd(entries, onList = setOf(1L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cadence of zero is rejected`() {
        every(0, CadenceUnit.DAYS)
    }
}
