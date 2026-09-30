package com.family.shoppinglist.core

import java.time.LocalDate

enum class CadenceUnit {
    DAYS, WEEKS, MONTHS;

    fun addTo(date: LocalDate, amount: Int): LocalDate = when (this) {
        DAYS -> date.plusDays(amount.toLong())
        WEEKS -> date.plusWeeks(amount.toLong())
        MONTHS -> date.plusMonths(amount.toLong())
    }
}

/** How a regular item comes back onto the list. */
sealed interface Recurrence {
    /** Restored on the list every weekly reset (Sunday morning). */
    data object EveryWeek : Recurrence

    /** Added whenever [amount] [unit]s have passed since it was last added, e.g. toilet paper every 3 weeks. */
    data class Every(val amount: Int, val unit: CadenceUnit) : Recurrence {
        init {
            require(amount >= 1) { "amount must be at least 1" }
        }
    }
}

data class RecurringEntry(
    val id: String,
    val recurrence: Recurrence,
    /** First day the item may appear (cadence items only). */
    val startsOn: LocalDate,
    /** Last day the item was put on the list, or null if never. */
    val lastAddedOn: LocalDate?,
)
