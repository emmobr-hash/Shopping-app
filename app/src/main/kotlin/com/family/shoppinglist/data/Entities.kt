package com.family.shoppinglist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.family.shoppinglist.core.CadenceUnit
import com.family.shoppinglist.core.Recurrence
import com.family.shoppinglist.core.RecurringEntry
import java.time.LocalDate

/** Sentinel used in store filters/pickers for "not tied to any supermarket". */
const val ANY_STORE = 0L

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** A regular item: comes back every Sunday, or every N days/weeks/months. */
@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("storeId")],
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: String = "",
    val storeId: Long? = null,
    /** True = restored every weekly reset. False = cadence of [cadenceAmount] x [cadenceUnit]. */
    val weekly: Boolean,
    val cadenceAmount: Int = 1,
    val cadenceUnit: String = CadenceUnit.WEEKS.name,
    /** Epoch day of the first time it may appear (cadence only). */
    val startsOn: Long,
    /** Epoch day it was last put on the list. */
    val lastAddedOn: Long? = null,
) {
    val recurrence: Recurrence
        get() = if (weekly) Recurrence.EveryWeek
        else Recurrence.Every(cadenceAmount, CadenceUnit.valueOf(cadenceUnit))

    fun toEntry() = RecurringEntry(
        id = id,
        recurrence = recurrence,
        startsOn = LocalDate.ofEpochDay(startsOn),
        lastAddedOn = lastAddedOn?.let(LocalDate::ofEpochDay),
    )
}

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RecurringEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("storeId"), Index("recurringId")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: String = "",
    val storeId: Long? = null,
    val checked: Boolean = false,
    val addedAt: Long,
    /** Set when the item came from a regular entry, so it isn't added twice. */
    val recurringId: Long? = null,
)

/** How often each thing has been ticked off, to work out what you "frequently buy". */
@Entity(tableName = "purchase_stats")
data class PurchaseStatEntity(
    @PrimaryKey val key: String,
    val displayName: String,
    val count: Int,
    val lastPurchasedAt: Long,
)

/** A supermarket special. Prices are free text ("£1.50", "2 for £3") since deals come in many shapes. */
@Entity(
    tableName = "offers",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("storeId")],
)
data class OfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val product: String,
    val price: String,
    val wasPrice: String = "",
    val storeId: Long? = null,
    /** Epoch day the offer ends (inclusive), or null if unknown. */
    val validUntil: Long? = null,
)
