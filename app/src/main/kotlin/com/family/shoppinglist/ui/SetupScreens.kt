package com.family.shoppinglist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
private fun SetupColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Family Shopping", style = MaterialTheme.typography.headlineMedium)
        content()
    }
}

@Composable
fun LoadingScreen() = SetupColumn { CircularProgressIndicator() }

@Composable
fun NotConfiguredScreen() = SetupColumn {
    Text("Firebase isn't set up yet", style = MaterialTheme.typography.titleMedium)
    Text(
        "To share the list between phones this app needs a free Firebase project. Follow the steps in the README " +
            "(\"Setting up Firebase\"), add app/google-services.json, and build again.",
    )
}

@Composable
fun FailedScreen(message: String, onRetry: () -> Unit) = SetupColumn {
    Text(message)
    Button(onClick = onRetry) { Text("Try again") }
}

@Composable
fun FamilyScreen(state: Session.NeedsFamily, onCreate: () -> Unit, onJoin: (String) -> Unit) = SetupColumn {
    var code by rememberSaveable { mutableStateOf("") }
    Text("Start a new family list on this phone, or join the one your family already has.")
    Button(onClick = onCreate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Start a new family") }
    Text("Already have a code?", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.take(14) },
        label = { Text("Family code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = { onJoin(code) }, enabled = !state.busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Join")
    }
    if (state.busy) CircularProgressIndicator()
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
