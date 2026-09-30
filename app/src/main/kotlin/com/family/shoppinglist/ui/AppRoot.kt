package com.family.shoppinglist.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

private enum class Tab(val label: String, val icon: ImageVector) {
    List("List", Icons.AutoMirrored.Filled.List),
    Regulars("Regulars", Icons.Filled.Refresh),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun AppRoot(session: SessionViewModel) {
    val state by session.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Session.NotConfigured -> NotConfiguredScreen()
        Session.Loading -> LoadingScreen()
        is Session.Failed -> FailedScreen(s.message, onRetry = session::start)
        is Session.NeedsFamily -> FamilyScreen(s, onCreate = session::createFamily, onJoin = session::joinFamily)
        is Session.Ready -> {
            // Keyed by code so leaving one family and joining another starts with fresh data.
            val vm: AppViewModel = viewModel(key = s.code, factory = viewModelFactory { initializer { AppViewModel(s.repository) } })
            MainScreen(vm, s.code, onLeaveFamily = session::leaveFamily)
        }
    }
}

@Composable
private fun MainScreen(vm: AppViewModel, familyCode: String, onLeaveFamily: () -> Unit) {
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

    val shareCode: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Join our family shopping list: open Family Shopping, tap \"Join\" and enter ${SessionViewModel.display(familyCode)}",
            )
        }
        context.startActivity(Intent.createChooser(send, "Send family code"))
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
                Tab.Regulars -> RegularsScreen(vm)
                Tab.Settings -> SettingsScreen(vm, familyCode, onShareCode = shareCode, onLeaveFamily = onLeaveFamily)
            }
        }
    }
}
