package com.family.shoppinglist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.family.shoppinglist.ShoppingApp
import com.family.shoppinglist.core.NameMatcher
import com.family.shoppinglist.data.ANY_STORE
import com.family.shoppinglist.data.ItemEntity
import com.family.shoppinglist.data.OfferEntity
import com.family.shoppinglist.data.RecurringEntity
import com.family.shoppinglist.data.ShoppingRepository
import com.family.shoppinglist.data.StoreEntity
import com.family.shoppinglist.work.ResetScheduler
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OfferUi(val offer: OfferEntity, val storeName: String?, val reason: String?)

data class ItemUi(val item: ItemEntity, val storeName: String?, val offers: List<OfferUi>)

/** Offers split into ones relevant to you (list, regulars, frequent buys) and everything else. */
data class OfferBoard(val forYou: List<OfferUi>, val others: List<OfferUi>)

class AppViewModel(private val repo: ShoppingRepository) : ViewModel() {

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val stores = repo.storesFlow.state(emptyList())
    val recurring = repo.recurringFlow.state(emptyList())
    val stats = repo.statsFlow.state(emptyList())
    val offers = repo.offersFlow.state(emptyList())

    /** null = all stores, [ANY_STORE] = items not tied to a supermarket, otherwise a store id. */
    val storeFilter = MutableStateFlow<Long?>(null)

    val listItems: StateFlow<List<ItemUi>> = combine(repo.itemsFlow, repo.storesFlow, repo.offersFlow) { items, stores, offers ->
        val names = stores.associate { it.id to it.name }
        items.map { item ->
            val matching = if (item.checked) emptyList() else offers
                .filter { NameMatcher.matches(item.name, it.product) }
                .map { OfferUi(it, it.storeId?.let(names::get), null) }
            ItemUi(item, item.storeId?.let(names::get), matching)
        }
    }.state(emptyList())

    val offerBoard: StateFlow<OfferBoard> = combine(
        repo.offersFlow, repo.storesFlow, repo.statsFlow, repo.itemsFlow, repo.recurringFlow,
    ) { offers, stores, stats, items, regulars ->
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
        viewModelScope.launch { repo.runMaintenance() }
    }

    fun nextResetLabel(): String =
        ResetScheduler.schedule.nextAfter(ZonedDateTime.now()).format(DateTimeFormatter.ofPattern("EEEE d MMM, HH:mm"))

    // list
    fun addItem(name: String, quantity: String = "", storeId: Long? = null) =
        viewModelScope.launch { repo.addItem(name, quantity, storeId) }

    fun setChecked(item: ItemEntity, checked: Boolean) = viewModelScope.launch { repo.setChecked(item, checked) }
    fun updateItem(item: ItemEntity) = viewModelScope.launch { repo.updateItem(item) }
    fun deleteItem(item: ItemEntity) = viewModelScope.launch { repo.deleteItem(item) }
    fun clearTicked() = viewModelScope.launch { repo.clearTicked() }

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
    fun addStore(name: String) = viewModelScope.launch { repo.addStore(name) }
    fun renameStore(store: StoreEntity, name: String) = viewModelScope.launch { repo.renameStore(store, name) }
    fun deleteStore(store: StoreEntity) = viewModelScope.launch {
        if (storeFilter.value == store.id) storeFilter.value = null
        repo.deleteStore(store)
    }

    // offers
    fun saveOffer(offer: OfferEntity) = viewModelScope.launch { repo.saveOffer(offer) }
    fun deleteOffer(offer: OfferEntity) = viewModelScope.launch { repo.deleteOffer(offer) }

    // regulars
    fun saveRecurring(entry: RecurringEntity) = viewModelScope.launch { repo.saveRecurring(entry) }
    fun deleteRecurring(entry: RecurringEntity) = viewModelScope.launch { repo.deleteRecurring(entry) }

    companion object {
        val Factory = viewModelFactory {
            initializer { AppViewModel((this[APPLICATION_KEY] as ShoppingApp).repository) }
        }
    }
}
