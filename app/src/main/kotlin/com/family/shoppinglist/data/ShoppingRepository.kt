package com.family.shoppinglist.data

import android.util.Log
import com.family.shoppinglist.core.NameMatcher
import com.family.shoppinglist.core.RecurringPlanner
import com.family.shoppinglist.core.WeeklySchedule
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val TAG = "ShoppingRepository"

/**
 * The family's shared data, kept in Firestore under `households/{code}`. Firestore caches on the device, so
 * the list works offline (in a shop with no signal) and syncs when you're back online. Writes are
 * fire-and-forget for that reason: they apply locally straight away and are sent when possible.
 */
class ShoppingRepository(
    private val household: DocumentReference,
    private val schedule: WeeklySchedule,
) {
    private val itemsCol = household.collection("items")
    private val storesCol = household.collection("stores")
    private val regularsCol = household.collection("regulars")
    private val statsCol = household.collection("stats")
    private val resetDoc = household.collection("meta").document("reset")
    private val firestore = household.firestore
    private val maintenanceLock = Mutex()

    val itemsFlow: Flow<List<Item>> = itemsCol.observe { it.toItem() }
        .map { list -> list.sortedWith(compareBy({ it.checked }, { it.name.lowercase() })) }
    val storesFlow: Flow<List<Store>> = storesCol.observe { it.toStore() }
        .map { list -> list.sortedBy { it.name.lowercase() } }
    val regularsFlow: Flow<List<Regular>> = regularsCol.observe { it.toRegular() }
        .map { list -> list.sortedWith(compareBy({ !it.weekly }, { it.name.lowercase() })) }
    val statsFlow: Flow<List<Stat>> = statsCol.observe { it.toStat() }
        .map { list -> list.sortedWith(compareBy({ -it.count }, { it.displayName.lowercase() })) }

    // ---- list ----

    fun addItem(name: String, quantity: String = "", storeId: String? = null) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val item = Item(name = clean, quantity = quantity.trim(), storeId = storeId, addedAt = System.currentTimeMillis())
        itemsCol.document(newId()).set(item.toMap()).logFailure("add item")
    }

    fun updateItem(item: Item) {
        itemsCol.document(item.id)
            .update(mapOf<String, Any?>("name" to item.name.trim(), "quantity" to item.quantity.trim(), "storeId" to item.storeId))
            .logFailure("update item")
    }

    fun deleteItem(item: Item) {
        itemsCol.document(item.id).delete().logFailure("delete item")
    }

    /** Ticking counts as a purchase (feeding "frequently bought"); unticking undoes that. */
    fun setChecked(item: Item, checked: Boolean) {
        if (item.checked == checked) return
        itemsCol.document(item.id).update("checked", checked).logFailure("tick item")
        val stat = mutableMapOf<String, Any>(
            "displayName" to item.name,
            "count" to FieldValue.increment(if (checked) 1L else -1L),
        )
        if (checked) stat["lastPurchasedAt"] = System.currentTimeMillis()
        statsCol.document(statDocId(item.name)).set(stat, SetOptions.merge()).logFailure("update stats")
    }

    /** Removes every ticked item. */
    suspend fun clearTicked() {
        val ticked = itemsCol.whereEqualTo("checked", true).get().await()
        val batch = firestore.batch()
        ticked.documents.forEach { batch.delete(it.reference) }
        batch.commit().logFailure("clear ticked")
    }

    // ---- stores ----

    fun addStore(name: String) {
        if (name.isBlank()) return
        storesCol.document(newId()).set(mapOf("name" to name.trim())).logFailure("add store")
    }

    fun renameStore(store: Store, name: String) {
        if (name.isBlank()) return
        storesCol.document(store.id).update("name", name.trim()).logFailure("rename store")
    }

    fun deleteStore(store: Store) {
        storesCol.document(store.id).delete().logFailure("delete store")
    }

    // ---- regulars ----

    /**
     * Saves a regular. A new weekly regular goes on the list straight away, as does a new cadence regular
     * whose start date has arrived; later ones are added by [runMaintenance] when they fall due.
     */
    fun saveRegular(regular: Regular, today: LocalDate = LocalDate.now()) {
        if (regular.name.isBlank()) return
        val isNew = regular.id.isEmpty()
        val id = if (isNew) newId() else regular.id
        var saved = regular.copy(id = id, name = regular.name.trim(), quantity = regular.quantity.trim())
        val batch = firestore.batch()
        if (isNew && (saved.weekly || LocalDate.ofEpochDay(saved.startsOn) <= today)) {
            saved = saved.copy(lastAddedOn = today.toEpochDay())
            batch.set(itemsCol.document(recurringItemId(id)), saved.toListItem().toMap())
        }
        batch.set(regularsCol.document(id), saved.toMap())
        batch.commit().logFailure("save regular")
    }

    fun deleteRegular(regular: Regular) {
        regularsCol.document(regular.id).delete().logFailure("delete regular")
    }

    // ---- weekly reset + cadence ----

    /**
     * Idempotent housekeeping, run from the Sunday worker and whenever the app opens on either phone:
     *  - if a Sunday-morning reset has been missed, clear ticked items and restore the weekly regulars
     *    (unticked leftovers carry over to the new week);
     *  - add any cadence items (e.g. toilet paper every 3 weeks) that have come due.
     *
     * Items that come from a regular have a fixed document id (`r-{regularId}`), so if both phones run this at
     * the same moment they write the same document rather than creating duplicates.
     */
    suspend fun runMaintenance(now: ZonedDateTime = ZonedDateTime.now()) = maintenanceLock.withLock {
        val today = now.toLocalDate()
        val latestReset = schedule.latestAtOrBefore(now).toInstant().toEpochMilli()

        val lastReset = resetDoc.get().await().getLong("lastResetAt")
        if (lastReset == null) {
            // Household predates reset tracking: start counting from the most recent Sunday.
            resetDoc.set(mapOf("lastResetAt" to latestReset)).logFailure("init reset")
        }
        val due = schedule.isDue(lastReset?.let(Instant::ofEpochMilli), now)

        val items = itemsCol.get().await().documents.mapNotNull { it.toItem() }
        val regulars = regularsCol.get().await().documents.mapNotNull { it.toRegular() }

        val stay = if (due) items.filterNot { it.checked } else items
        val onList = stay.mapNotNull { it.recurringId }.toMutableSet()
        val entries = regulars.map { it.toEntry() }
        val toAdd = LinkedHashSet<String>()
        if (due) toAdd += RecurringPlanner.weeklyIdsToAdd(entries, onList)
        toAdd += RecurringPlanner.dueCadenceIds(entries, onList + toAdd, today)

        val batch = firestore.batch()
        var writes = 0
        if (due) {
            val readding = toAdd.map(::recurringItemId).toSet()
            items.filter { it.checked && it.id !in readding }.forEach {
                batch.delete(itemsCol.document(it.id)); writes++
            }
        }
        regulars.filter { it.id in toAdd }.forEach { r ->
            batch.set(itemsCol.document(recurringItemId(r.id)), r.toListItem().toMap())
            batch.update(regularsCol.document(r.id), "lastAddedOn", today.toEpochDay())
            writes += 2
        }
        if (due) {
            batch.set(resetDoc, mapOf("lastResetAt" to latestReset)); writes++
        }
        if (writes > 0) batch.commit().logFailure("maintenance")
    }

    private fun Regular.toListItem() = Item(
        id = recurringItemId(id),
        name = name,
        quantity = quantity,
        storeId = storeId,
        addedAt = System.currentTimeMillis(),
        recurringId = id,
    )

    private fun newId() = UUID.randomUUID().toString()

    private fun recurringItemId(regularId: String) = "r-$regularId"

    /** Firestore document ids can't contain '/' or look like `__x__`. */
    private fun statDocId(name: String) = NameMatcher.key(name).replace('/', '_').let { if (it.startsWith("__")) "k$it" else it }

    private fun <T> com.google.android.gms.tasks.Task<T>.logFailure(what: String) {
        addOnFailureListener { Log.w(TAG, "Failed to $what", it) }
    }
}

private fun <T : Any> CollectionReference.observe(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Listen failed on $path", error)
        } else if (snapshot != null) {
            trySend(snapshot.documents.mapNotNull(map))
        }
    }
    awaitClose { registration.remove() }
}

private fun DocumentSnapshot.toItem(): Item? {
    val name = getString("name") ?: return null
    return Item(
        id = id,
        name = name,
        quantity = getString("quantity") ?: "",
        storeId = getString("storeId"),
        checked = getBoolean("checked") ?: false,
        addedAt = getLong("addedAt") ?: 0L,
        recurringId = getString("recurringId"),
    )
}

private fun Item.toMap(): Map<String, Any?> = mapOf(
    "name" to name, "quantity" to quantity, "storeId" to storeId,
    "checked" to checked, "addedAt" to addedAt, "recurringId" to recurringId,
)

private fun DocumentSnapshot.toStore(): Store? {
    val name = getString("name") ?: return null
    return Store(id, name)
}

private fun DocumentSnapshot.toRegular(): Regular? {
    val name = getString("name") ?: return null
    return Regular(
        id = id,
        name = name,
        quantity = getString("quantity") ?: "",
        storeId = getString("storeId"),
        weekly = getBoolean("weekly") ?: true,
        cadenceAmount = (getLong("cadenceAmount") ?: 1L).toInt().coerceAtLeast(1),
        cadenceUnit = getString("cadenceUnit") ?: "WEEKS",
        startsOn = getLong("startsOn") ?: 0L,
        lastAddedOn = getLong("lastAddedOn"),
    )
}

private fun Regular.toMap(): Map<String, Any?> = mapOf(
    "name" to name, "quantity" to quantity, "storeId" to storeId, "weekly" to weekly,
    "cadenceAmount" to cadenceAmount, "cadenceUnit" to cadenceUnit,
    "startsOn" to startsOn, "lastAddedOn" to lastAddedOn,
)

private fun DocumentSnapshot.toStat(): Stat? {
    val displayName = getString("displayName") ?: return null
    return Stat(
        key = id,
        displayName = displayName,
        count = (getLong("count") ?: 0L).toInt().coerceAtLeast(0),
        lastPurchasedAt = getLong("lastPurchasedAt") ?: 0L,
    )
}
