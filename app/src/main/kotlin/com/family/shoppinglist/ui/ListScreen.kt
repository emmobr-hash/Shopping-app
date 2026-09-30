package com.family.shoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.shoppinglist.core.NameMatcher
import com.family.shoppinglist.data.ANY_STORE
import com.family.shoppinglist.data.ItemEntity
import com.family.shoppinglist.data.StoreEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: AppViewModel, onShare: () -> Unit) {
    val items by vm.listItems.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val filter by vm.storeFilter.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<ItemEntity?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var pickedStore by rememberSaveable { mutableStateOf<Long?>(null) }
    // New items default to whichever supermarket tab you're looking at.
    val addStore = pickedStore ?: filter?.takeIf { it != ANY_STORE }

    val visible = items.filter {
        when (filter) {
            null -> true
            ANY_STORE -> it.item.storeId == null
            else -> it.item.storeId == filter
        }
    }
    val storeOrder = stores.map { it.id }
    val groups = visible.groupBy { it.item.storeId }.toList()
        .sortedBy { (id, _) -> id?.let(storeOrder::indexOf)?.takeIf { it >= 0 } ?: Int.MAX_VALUE }

    val onListKeys = items.map { NameMatcher.key(it.item.name) }.toSet()
    val query = newName.trim()
    val suggestions = stats
        .filter { it.key !in onListKeys }
        .filter { if (query.isEmpty()) it.count >= 2 else it.displayName.contains(query, ignoreCase = true) }
        .take(6)

    fun submit() {
        vm.addItem(newName, storeId = addStore)
        newName = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Shopping list") },
                actions = {
                    IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share list") }
                    IconButton(onClick = vm::clearTicked, enabled = items.any { it.item.checked }) {
                        Icon(Icons.Filled.Done, "Clear ticked items")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(filter == null, { vm.storeFilter.value = null }, { Text("All") }) }
                    items(stores, key = { it.id }) { s ->
                        FilterChip(filter == s.id, { vm.storeFilter.value = s.id }, { Text(s.name) })
                    }
                    item { FilterChip(filter == ANY_STORE, { vm.storeFilter.value = ANY_STORE }, { Text("Any store") }) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Add an item") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                        )
                        IconButton(onClick = ::submit, enabled = newName.isNotBlank()) {
                            Icon(Icons.Filled.Add, "Add item")
                        }
                    }
                    StoreDropdown(stores, addStore, onSelect = { pickedStore = it })
                    if (suggestions.isNotEmpty()) {
                        Text(
                            if (query.isEmpty()) "Often bought" else "Suggestions",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions, key = { it.key }) { s ->
                                SuggestionChip(
                                    onClick = { vm.addItem(s.displayName, storeId = addStore); newName = "" },
                                    label = { Text(s.displayName) },
                                )
                            }
                        }
                    }
                    Text(
                        "Ticked items clear and regulars come back ${vm.nextResetLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Add something above.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            groups.forEach { (storeId, group) ->
                item(key = "header-$storeId") {
                    Text(
                        stores.firstOrNull { it.id == storeId }?.name ?: "Any store",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(group, key = { it.item.id }) { ui ->
                    ItemRow(ui, vm, onEdit = { editing = ui.item })
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { item ->
        ItemDialog(
            item = item,
            stores = stores,
            onSave = { vm.updateItem(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ItemRow(ui: ItemUi, vm: AppViewModel, onEdit: () -> Unit) {
    val item = ui.item
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        leadingContent = {
            Checkbox(checked = item.checked, onCheckedChange = { vm.setChecked(item, it) })
        },
        headlineContent = {
            Text(
                if (item.quantity.isBlank()) item.name else "${item.name} (${item.quantity})",
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = if (ui.offers.isEmpty()) null else {
            {
                Column {
                    ui.offers.forEach { o ->
                        Text(
                            buildString {
                                append("On offer: ").append(o.offer.product).append(" ").append(o.offer.price)
                                o.storeName?.let { append(" at ").append(it) }
                            },
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = { vm.deleteItem(item) }) { Icon(Icons.Filled.Delete, "Delete ${item.name}") }
        },
    )
}

@Composable
private fun ItemDialog(
    item: ItemEntity,
    stores: List<StoreEntity>,
    onSave: (ItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var storeId by remember { mutableStateOf(item.storeId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Item") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity (optional)") }, singleLine = true)
                StoreDropdown(stores, storeId, onSelect = { storeId = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(item.copy(name = name, quantity = quantity, storeId = storeId)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
