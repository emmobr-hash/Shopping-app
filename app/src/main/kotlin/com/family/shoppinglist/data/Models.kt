package com.family.shoppinglist.data

import com.family.shoppinglist.core.CadenceUnit
import com.family.shoppinglist.core.Recurrence
import com.family.shoppinglist.core.RecurringEntry
import java.time.LocalDate

/** Sentinel used in store filters for "not tied to any supermarket". */
const val ANY_STORE = "any"

data class Store(val id: String, val name: String)

/** A regular item: comes back every Sunday, or every N days/weeks/months. */
data class Regular(
    val id: String = "",
    val name: String,
    val quantity: String = "",
    val storeId: String? = null,
    /** True = restored every weekly reset. False = cadence of [cadenceAmount] x [cadenceUnit]. */
    val weekly: Boolean,
    val cadenceAmount: Int = 1,
    val cadenceUnit: String = CadenceUnit.WEEKS.name,
    /** Epoch day of the first time it may appear (cadence only). */
    val startsOn: Long,
    /** Epoch day it was last put on the list. */
    val lastAddedOn: Long? = null,
) {
    fun toEntry() = RecurringEntry(
        id = id,
        recurrence = if (weekly) Recurrence.EveryWeek else Recurrence.Every(cadenceAmount, CadenceUnit.valueOf(cadenceUnit)),
        startsOn = LocalDate.ofEpochDay(startsOn),
        lastAddedOn = lastAddedOn?.let(LocalDate::ofEpochDay),
    )
}

data class Item(
    val id: String = "",
    val name: String,
    val quantity: String = "",
    val storeId: String? = null,
    val checked: Boolean = false,
    val addedAt: Long = 0L,
    /** Set when the item came from a regular entry, so it isn't added twice. */
    val recurringId: String? = null,
)

/** How often each thing has been ticked off, to work out what you "frequently buy". */
data class Stat(val key: String, val displayName: String, val count: Int, val lastPurchasedAt: Long)

/** A supermarket special. Prices are free text ("€1.50", "2 for €3") since deals come in many shapes. */
data class Offer(
    val id: String = "",
    val product: String,
    val price: String,
    val wasPrice: String = "",
    val storeId: String? = null,
    /** Epoch day the offer ends (inclusive), or null if unknown. */
    val validUntil: Long? = null,
)
