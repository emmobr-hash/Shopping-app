package com.family.shoppinglist.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.family.shoppinglist.data.Store

/** Picks a supermarket, or "Any store" (null) for things you'll buy wherever's convenient. */
@Composable
fun StoreDropdown(
    stores: List<Store>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val label = stores.firstOrNull { it.id == selectedId }?.name ?: "Any store"
    Box(modifier) {
        OutlinedButton(onClick = { open = true }) { Text("Store: $label") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Any store") }, onClick = { onSelect(null); open = false })
            stores.forEach { store ->
                DropdownMenuItem(text = { Text(store.name) }, onClick = { onSelect(store.id); open = false })
            }
        }
    }
}
