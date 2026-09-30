package com.family.shoppinglist.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.shoppinglist.core.NameMatcher
import com.family.shoppinglist.data.Item
import com.family.shoppinglist.data.Offer
import com.family.shoppinglist.data.Regular
import com.family.shoppinglist.data.ShoppingRepository
import com.family.shoppinglist.data.Store
import com.family.shoppinglist.work.ResetScheduler
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OfferUi(val offer: Offer, val storeName: String?, val reason: String?)

data class ItemUi(val item: Item, val storeName: String?, val offers: List<OfferUi>)

/** Offers split into ones relevant to you (list, regulars, frequent buys) and everything else. */
data class OfferBoard(val forYou: List<OfferUi>, val others: List<OfferUi>)

class AppViewModel(private val repo: ShoppingRepository) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // One listener per collection, shared by everything below.
    private val items = repo.itemsFlow.state(emptyList())
    val stores = repo.storesFlow.state(emptyList())
    val regulars = repo.regularsFlow.state(emptyList())
    val stats = repo.statsFlow.state(emptyList())
    private val offers = repo.offersFlow.state(emptyList())

    /** null = all stores, [com.family.shoppinglist.data.ANY_STORE] = items not tied to a supermarket, otherwise a store id. */
    val storeFilter = MutableStateFlow<String?>(null)

    val listItems: StateFlow<List<ItemUi>> = combine(items, stores, offers) { items, stores, offers ->
        val names = stores.associate { it.id to it.name }
        items.map { item ->
            // Items pointing at a store that has since been deleted count as "any store".
            val storeId = item.storeId?.takeIf { it in names }
            val matching = if (item.checked) emptyList() else offers
                .filter { NameMatcher.matches(item.name, it.product) }
                .map { OfferUi(it, it.storeId?.let(names::get), null) }
            ItemUi(item.copy(storeId = storeId), storeId?.let(names::get), matching)
        }
    }.state(emptyList())

    val offerBoard: StateFlow<OfferBoard> = combine(offers, stores, stats, items, regulars) { offers, stores, stats, items, regulars ->
        val names = stores.associate { it.id to it.name }
        val frequent = stats.filter { it.count >= NameMatcher.FREQUENT_THRESHOLD }
        val ui = offers.map { offer ->
            val reason = when {
                items.any { !it.checked && NameMatcher.matches(it.name, offer.product) } -> "On your list"
                regulars.any { NameMatcher.matches(it.name, offer.product) } -> "One of your regulars"
                else -> frequent.firstOrNull { NameMatcher.matches(it.displayName, offer.product) }
                    ?.let { "You buy this often (${it.count}x)" }
            }
            OfferUi(offer, offer.storeId?.let(names::get), reason)
        }
        val (relevant, rest) = ui.partition { it.reason != null }
        OfferBoard(relevant, rest)
    }.state(OfferBoard(emptyList(), emptyList()))

    init {
        maintain()
    }

    /** Applies any missed Sunday reset and adds due cadence items. Safe to call as often as you like. */
    fun maintain() {
        viewModelScope.launch {
            try {
                repo.runMaintenance()
            } catch (e: Exception) {
                Log.w("AppViewModel", "Maintenance failed", e)
            }
        }
    }

    fun nextResetLabel(): String =
        ResetScheduler.schedule.nextAfter(ZonedDateTime.now()).format(DateTimeFormatter.ofPattern("EEEE d MMM, HH:mm"))

    // list
    fun addItem(name: String, quantity: String = "", storeId: String? = null) {
        val clean = name.trim()
        // Don't double up if someone else in the family has just added the same thing.
        if (items.value.any { !it.checked && it.name.equals(clean, ignoreCase = true) && it.storeId == storeId }) return
        repo.addItem(clean, quantity, storeId)
    }

    fun setChecked(item: Item, checked: Boolean) = repo.setChecked(item, checked)
    fun updateItem(item: Item) = repo.updateItem(item)
    fun deleteItem(item: Item) = repo.deleteItem(item)
    fun clearTicked() {
        viewModelScope.launch {
            try {
                repo.clearTicked()
            } catch (e: Exception) {
                Log.w("AppViewModel", "Clear ticked failed", e)
            }
        }
    }

    /** Plain-text version of what's still to buy, grouped by supermarket, for sharing to the family chat. */
    fun shareText(): String {
        val todo = listItems.value.filter { !it.item.checked }
        if (todo.isEmpty()) return "Shopping list is empty"
        return buildString {
            append("Shopping list")
            todo.groupBy { it.storeName }.forEach { (store, group) ->
                append("\n\n").append(store ?: "Any store").append(':')
                group.forEach { ui ->
                    append("\n- ").append(ui.item.name)
                    if (ui.item.quantity.isNotBlank()) append(" (").append(ui.item.quantity).append(')')
                }
            }
        }
    }

    // stores
    fun addStore(name: String) = repo.addStore(name)
    fun renameStore(store: Store, name: String) = repo.renameStore(store, name)
    fun deleteStore(store: Store) {
        if (storeFilter.value == store.id) storeFilter.value = null
        repo.deleteStore(store)
    }

    // offers
    fun saveOffer(offer: Offer) = repo.saveOffer(offer)
    fun deleteOffer(offer: Offer) = repo.deleteOffer(offer)

    // regulars
    fun saveRegular(regular: Regular) = repo.saveRegular(regular)
    fun deleteRegular(regular: Regular) = repo.deleteRegular(regular)
}
