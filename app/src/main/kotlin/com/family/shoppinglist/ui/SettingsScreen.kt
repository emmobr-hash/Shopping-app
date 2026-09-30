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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.shoppinglist.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, familyCode: String, onShareCode: () -> Unit, onLeaveFamily: () -> Unit) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    var newName by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Store?>(null) }
    var deleting by remember { mutableStateOf<Store?>(null) }
    var leaving by remember { mutableStateOf(false) }

    fun submit() {
        vm.addStore(newName)
        newName = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Family code", style = MaterialTheme.typography.titleSmall)
                        Text(
                            SessionViewModel.display(familyCode),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Everyone who enters this code sees and edits the same list.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = onShareCode) { Text("Send code") }
                            TextButton(onClick = { leaving = true }) { Text("Leave family") }
                        }
                    }
                }
            }
            item {
                Text(
                    "Supermarkets",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a supermarket") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    IconButton(onClick = ::submit, enabled = newName.isNotBlank()) { Icon(Icons.Filled.Add, "Add supermarket") }
                }
            }
            items(stores, key = { it.id }) { store ->
                ListItem(
                    modifier = Modifier.clickable { renaming = store },
                    headlineContent = { Text(store.name) },
                    supportingContent = { Text("Tap to rename") },
                    trailingContent = {
                        IconButton(onClick = { deleting = store }) { Icon(Icons.Filled.Delete, "Delete ${store.name}") }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    renaming?.let { store ->
        var name by remember(store.id) { mutableStateOf(store.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename supermarket") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.renameStore(store, name); renaming = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { store ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${store.name}?") },
            text = { Text("Items and regulars for this supermarket are kept, but will show as \"Any store\".") },
            confirmButton = { TextButton(onClick = { vm.deleteStore(store); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave this family?") },
            text = { Text("This phone will stop showing the family list. The list isn't deleted; you can rejoin any time with the code (${SessionViewModel.display(familyCode)}).") },
            confirmButton = { TextButton(onClick = { leaving = false; onLeaveFamily() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
        )
    }
}
