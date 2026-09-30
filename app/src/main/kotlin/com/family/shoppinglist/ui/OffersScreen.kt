package com.family.shoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.shoppinglist.data.OfferEntity
import com.family.shoppinglist.data.StoreEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersScreen(vm: AppViewModel) {
    val board by vm.offerBoard.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    // null = closed, id 0 = new offer
    var editing by remember { mutableStateOf<OfferEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Special offers") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = OfferEntity(product = "", price = "") }) {
                Icon(Icons.Filled.Add, "Add offer")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Text(
                    "Add the deals you spot (in store, leaflets or apps). Offers matching things on your list, " +
                        "your regulars or what you buy often are highlighted, and show up on the list itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (board.forYou.isEmpty() && board.others.isEmpty()) {
                item { Text("No offers added yet.", modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            if (board.forYou.isNotEmpty()) {
                item { SectionTitle("On offer for things you buy") }
                items(board.forYou, key = { "y${it.offer.id}" }) { OfferRow(it, vm, onEdit = { editing = it.offer }) }
            }
            if (board.others.isNotEmpty()) {
                item { SectionTitle("Other offers") }
                items(board.others, key = { "o${it.offer.id}" }) { OfferRow(it, vm, onEdit = { editing = it.offer }) }
            }
        }
    }

    editing?.let { offer ->
        OfferDialog(
            offer = offer,
            stores = stores,
            onSave = { vm.saveOffer(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun OfferRow(ui: OfferUi, vm: AppViewModel, onEdit: () -> Unit) {
    val o = ui.offer
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        overlineContent = ui.reason?.let { { Text(it) } },
        headlineContent = { Text(o.product) },
        supportingContent = {
            Text(
                buildString {
                    append(o.price)
                    if (o.wasPrice.isNotBlank()) append(" (was ").append(o.wasPrice).append(')')
                    ui.storeName?.let { append(" · ").append(it) }
                    o.validUntil?.let { append(" · until ").append(LocalDate.ofEpochDay(it).format(dayFormat)) }
                }
            )
        },
        trailingContent = {
            IconButton(onClick = { vm.deleteOffer(o) }) { Icon(Icons.Filled.Delete, "Delete offer") }
        },
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferDialog(
    offer: OfferEntity,
    stores: List<StoreEntity>,
    onSave: (OfferEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var product by remember { mutableStateOf(offer.product) }
    var price by remember { mutableStateOf(offer.price) }
    var wasPrice by remember { mutableStateOf(offer.wasPrice) }
    var storeId by remember { mutableStateOf(offer.storeId) }
    var validUntil by remember { mutableStateOf(offer.validUntil) }
    var pickingDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (offer.id == 0L) "Add offer" else "Edit offer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(product, { product = it }, label = { Text("Product") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Offer price, e.g. £1.50 or 2 for £3") }, singleLine = true)
                OutlinedTextField(wasPrice, { wasPrice = it }, label = { Text("Normal price (optional)") }, singleLine = true)
                StoreDropdown(stores, storeId, onSelect = { storeId = it })
                OutlinedButton(onClick = { pickingDate = true }) {
                    Text(validUntil?.let { "Ends: ${LocalDate.ofEpochDay(it).format(dayFormat)}" } ?: "Set end date (optional)")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = product.isNotBlank() && price.isNotBlank(),
                onClick = {
                    onSave(offer.copy(product = product, price = price, wasPrice = wasPrice, storeId = storeId, validUntil = validUntil))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (validUntil?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker reports midnight UTC of the chosen day.
                    validUntil = state.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { validUntil = null; pickingDate = false }) { Text("No end date") }
            },
        ) { DatePicker(state) }
    }
}
