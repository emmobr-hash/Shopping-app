package com.family.shoppinglist.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private enum class Tab(val label: String, val icon: ImageVector) {
    List("List", Icons.AutoMirrored.Filled.List),
    Offers("Offers", Icons.Filled.Star),
    Regulars("Regulars", Icons.Filled.Refresh),
    Stores("Stores", Icons.Filled.Place),
}

@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Catch up on a missed Sunday reset / due items whenever the app comes to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.maintain() }

    val share: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, vm.shareText())
        }
        context.startActivity(Intent.createChooser(send, "Share shopping list"))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
            when (Tab.entries[tab]) {
                Tab.List -> ListScreen(vm, onShare = share)
                Tab.Offers -> OffersScreen(vm)
                Tab.Regulars -> RegularsScreen(vm)
                Tab.Stores -> StoresScreen(vm)
            }
        }
    }
}
