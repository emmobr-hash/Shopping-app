package com.family.shoppinglist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.shoppinglist.core.CadenceUnit
import com.family.shoppinglist.core.RecurringPlanner
import com.family.shoppinglist.data.RecurringEntity
import com.family.shoppinglist.data.StoreEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

private fun unitLabel(unit: CadenceUnit, amount: Int): String {
    val singular = unit.name.lowercase().dropLast(1)
    return if (amount == 1) singular else singular + "s"
}

private fun describe(e: RecurringEntity, today: LocalDate): String {
    if (e.weekly) return "Every Sunday"
    val every = "Every ${if (e.cadenceAmount == 1) "" else "${e.cadenceAmount} "}${unitLabel(CadenceUnit.valueOf(e.cadenceUnit), e.cadenceAmount)}"
    val next = RecurringPlanner.nextDue(e.toEntry()) ?: return every
    return if (next.isAfter(today)) "$every · next ${next.format(dayFormat)}" else "$every · due now"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegularsScreen(vm: AppViewModel) {
    val regulars by vm.recurring.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    // null = closed, id 0 = new regular
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }
    val today = LocalDate.now()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Regular items") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = RecurringEntity(name = "", weekly = true, startsOn = today.toEpochDay())
            }) { Icon(Icons.Filled.Add, "Add regular item") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Text(
                    "Weekly items are put back on the list every Sunday morning. Others, like toilet paper, " +
                        "turn up at the interval you choose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (regulars.isEmpty()) {
                item { Text("No regular items yet.", modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(regulars, key = { it.id }) { r ->
                ListItem(
                    modifier = Modifier.clickable { editing = r },
                    headlineContent = { Text(if (r.quantity.isBlank()) r.name else "${r.name} (${r.quantity})") },
                    supportingContent = {
                        val store = stores.firstOrNull { it.id == r.storeId }?.name
                        Text(describe(r, today) + (store?.let { " · $it" } ?: ""))
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.deleteRecurring(r) }) { Icon(Icons.Filled.Delete, "Delete ${r.name}") }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { entry ->
        RegularDialog(
            existing = entry,
            stores = stores,
            onSave = { vm.saveRecurring(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RegularDialog(
    existing: RecurringEntity,
    stores: List<StoreEntity>,
    onSave: (RecurringEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val isNew = existing.id == 0L
    var name by remember { mutableStateOf(existing.name) }
    var quantity by remember { mutableStateOf(existing.quantity) }
    var storeId by remember { mutableStateOf(existing.storeId) }
    var weekly by remember { mutableStateOf(existing.weekly) }
    var amountText by remember { mutableStateOf(existing.cadenceAmount.toString()) }
    var unit by remember { mutableStateOf(CadenceUnit.valueOf(existing.cadenceUnit)) }
    var addNow by remember { mutableStateOf(true) }

    val amount = amountText.toIntOrNull()?.takeIf { it >= 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add regular item" else "Edit regular item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Item") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity (optional)") }, singleLine = true)
                StoreDropdown(stores, storeId, onSelect = { storeId = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(weekly, { weekly = true }, { Text("Every Sunday") })
                    FilterChip(!weekly, { weekly = false }, { Text("Every…") })
                }
                if (!weekly) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            amountText,
                            { amountText = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.width(72.dp),
                            singleLine = true,
                            isError = amount == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        CadenceUnit.entries.forEach { u ->
                            FilterChip(unit == u, { unit = u }, { Text(unitLabel(u, amount ?: 2)) })
                        }
                    }
                    if (isNew) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(addNow, { addNow = it })
                            Text("Add to the list now", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (weekly || amount != null),
                onClick = {
                    val n = amount ?: 1
                    val today = LocalDate.now()
                    onSave(
                        existing.copy(
                            name = name,
                            quantity = quantity,
                            storeId = storeId,
                            weekly = weekly,
                            cadenceAmount = n,
                            cadenceUnit = unit.name,
                            startsOn = if (isNew) (if (addNow || weekly) today else unit.addTo(today, n)).toEpochDay()
                            else existing.startsOn,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
