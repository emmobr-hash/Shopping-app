package com.family.shoppinglist.data

import androidx.room.withTransaction
import com.family.shoppinglist.core.NameMatcher
import com.family.shoppinglist.core.RecurringPlanner
import java.time.LocalDate
import java.time.ZonedDateTime

class ShoppingRepository(
    private val db: AppDatabase,
    private val resetState: ResetState,
) {
    private val items = db.items()
    private val stores = db.stores()
    private val recurring = db.recurring()
    private val stats = db.stats()
    private val offers = db.offers()

    val itemsFlow = items.observeAll()
    val storesFlow = stores.observeAll()
    val recurringFlow = recurring.observeAll()
    val statsFlow = stats.observeAll()
    val offersFlow = offers.observeAll()

    // ---- list ----

    /** Adds an item unless the same thing is already waiting, unticked, for the same store. */
    suspend fun addItem(name: String, quantity: String = "", storeId: Long? = null) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (items.countUncheckedDuplicates(clean, storeId) > 0) return
        items.insert(ItemEntity(name = clean, quantity = quantity.trim(), storeId = storeId, addedAt = System.currentTimeMillis()))
    }

    suspend fun updateItem(item: ItemEntity) = items.update(item.copy(name = item.name.trim(), quantity = item.quantity.trim()))

    suspend fun deleteItem(item: ItemEntity) = items.delete(item)

    /** Ticking counts as a purchase (feeding "frequently bought"); unticking undoes that. */
    suspend fun setChecked(item: ItemEntity, checked: Boolean) = db.withTransaction {
        if (item.checked == checked) return@withTransaction
        items.setChecked(item.id, checked)
        val key = NameMatcher.key(item.name)
        val now = System.currentTimeMillis()
        if (checked) stats.insertIfMissing(PurchaseStatEntity(key, item.name, 0, now))
        stats.bump(key, if (checked) 1 else -1, now)
    }

    suspend fun clearTicked() = items.deleteChecked()

    // ---- stores ----

    suspend fun addStore(name: String) {
        if (name.isNotBlank()) stores.insert(StoreEntity(name = name.trim()))
    }

    suspend fun renameStore(store: StoreEntity, name: String) {
        if (name.isNotBlank()) stores.update(store.copy(name = name.trim()))
    }

    suspend fun deleteStore(store: StoreEntity) = stores.delete(store)

    // ---- offers ----

    suspend fun saveOffer(offer: OfferEntity) {
        if (offer.product.isBlank()) return
        val clean = offer.copy(product = offer.product.trim(), price = offer.price.trim(), wasPrice = offer.wasPrice.trim())
        if (clean.id == 0L) offers.insert(clean) else offers.update(clean)
    }

    suspend fun deleteOffer(offer: OfferEntity) = offers.delete(offer)

    // ---- regulars ----

    /** Saves a regular. New weekly regulars go on the list straight away; cadence ones follow their start date. */
    suspend fun saveRecurring(entry: RecurringEntity, today: LocalDate = LocalDate.now()) = db.withTransaction {
        if (entry.name.isBlank()) return@withTransaction
        val clean = entry.copy(name = entry.name.trim(), quantity = entry.quantity.trim())
        if (clean.id != 0L) {
            recurring.update(clean)
            return@withTransaction
        }
        val id = recurring.insert(clean)
        if (clean.weekly) addRecurringToList(clean.copy(id = id), today)
        else addDueCadence(today)
    }

    suspend fun deleteRecurring(entry: RecurringEntity) = recurring.delete(entry)

    // ---- weekly reset + cadence ----

    /**
     * Idempotent housekeeping, run from the Sunday worker and whenever the app opens:
     *  - if a Sunday-morning reset has been missed, clear ticked items and restore the weekly regulars
     *    (unticked leftovers carry over to the new week);
     *  - add any cadence items (e.g. toilet paper every 3 weeks) that have come due;
     *  - drop offers that have expired.
     */
    suspend fun runMaintenance(now: ZonedDateTime = ZonedDateTime.now()) {
        val today = now.toLocalDate()
        db.withTransaction {
            if (resetState.isDue(now)) {
                items.deleteChecked()
                val entries = recurring.all()
                val onList = items.recurringIdsOnList().toSet()
                val toAdd = RecurringPlanner.weeklyIdsToAdd(entries.map { it.toEntry() }, onList).toSet()
                entries.filter { it.id in toAdd }.forEach { addRecurringToList(it, today) }
                resetState.markDone(now)
            }
            addDueCadence(today)
        }
        offers.deleteExpired(today.toEpochDay())
    }

    private suspend fun addDueCadence(today: LocalDate) {
        val entries = recurring.all()
        val onList = items.recurringIdsOnList().toSet()
        val due = RecurringPlanner.dueCadenceIds(entries.map { it.toEntry() }, onList, today).toSet()
        entries.filter { it.id in due }.forEach { addRecurringToList(it, today) }
    }

    private suspend fun addRecurringToList(entry: RecurringEntity, today: LocalDate) {
        items.insert(
            ItemEntity(
                name = entry.name,
                quantity = entry.quantity,
                storeId = entry.storeId,
                addedAt = System.currentTimeMillis(),
                recurringId = entry.id,
            )
        )
        recurring.markAdded(entry.id, today.toEpochDay())
    }
}
