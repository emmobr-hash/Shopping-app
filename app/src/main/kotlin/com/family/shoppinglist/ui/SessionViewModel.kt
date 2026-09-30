package com.family.shoppinglist.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.family.shoppinglist.ShoppingApp
import com.family.shoppinglist.data.ShoppingRepository
import com.family.shoppinglist.work.ResetScheduler
import java.security.SecureRandom
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Session {
    /** google-services.json hasn't been added to the build yet. */
    data object NotConfigured : Session
    data object Loading : Session
    data class Failed(val message: String) : Session
    data class NeedsFamily(val busy: Boolean = false, val error: String? = null) : Session
    data class Ready(val code: String, val repository: ShoppingRepository) : Session
}

/** Signs the phone in and works out which family (household) it belongs to. */
class SessionViewModel(private val app: ShoppingApp) : ViewModel() {
    private val _state = MutableStateFlow<Session>(Session.Loading)
    val state: StateFlow<Session> = _state

    init {
        start()
    }

    fun start() {
        if (!app.firebaseConfigured) {
            _state.value = Session.NotConfigured
            return
        }
        val existing = app.familyCode
        if (existing != null) {
            // Already a member: open straight away (works offline); the anonymous sign-in is persisted, so
            // this only does anything if it was somehow lost.
            _state.value = Session.Ready(existing, app.repositoryFor(existing))
            viewModelScope.launch {
                try {
                    app.ensureSignedIn()
                } catch (e: Exception) {
                    Log.w(TAG, "Background sign-in failed", e)
                }
            }
            return
        }
        _state.value = Session.Loading
        viewModelScope.launch {
            _state.value = try {
                val signedIn = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { app.ensureSignedIn() }
                if (signedIn == null) Session.Failed("Couldn't reach the server. Check your internet connection.")
                else Session.NeedsFamily()
            } catch (e: Exception) {
                Log.w(TAG, "Sign-in failed", e)
                Session.Failed("Couldn't sign in: ${e.message ?: "unknown error"}. Is Anonymous sign-in enabled in Firebase?")
            }
        }
    }

    /** Starts a new family: makes a code, and seeds the usual supermarkets. */
    fun createFamily() {
        _state.value = Session.NeedsFamily(busy = true)
        viewModelScope.launch {
            val code = generateCode()
            val ok = try {
                val ref = app.householdRef(code)
                val batch = ref.firestore.batch()
                batch.set(ref, mapOf("createdAt" to System.currentTimeMillis()))
                DEFAULT_STORES.forEach { batch.set(ref.collection("stores").document(), mapOf("name" to it)) }
                val lastReset = ResetScheduler.schedule.latestAtOrBefore(ZonedDateTime.now()).toInstant().toEpochMilli()
                batch.set(ref.collection("meta").document("reset"), mapOf("lastResetAt" to lastReset))
                // commit() yields no value, so return true explicitly; null means we timed out.
                withTimeoutOrNull(NETWORK_TIMEOUT_MS) { batch.commit().await(); true } == true
            } catch (e: Exception) {
                Log.w(TAG, "Create family failed", e)
                false
            }
            if (ok) finish(code)
            else _state.value = Session.NeedsFamily(error = "Couldn't create the family. Check your internet connection and try again.")
        }
    }

    fun joinFamily(input: String) {
        val code = normalise(input)
        if (code.length != CODE_LENGTH) {
            _state.value = Session.NeedsFamily(error = "Family codes have $CODE_LENGTH letters and numbers.")
            return
        }
        _state.value = Session.NeedsFamily(busy = true)
        viewModelScope.launch {
            val error = try {
                val snapshot = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { app.householdRef(code).get().await() }
                when {
                    snapshot == null -> "Couldn't reach the server. Check your internet connection."
                    !snapshot.exists() -> "No family found with that code."
                    else -> null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Join family failed", e)
                "Couldn't join: ${e.message ?: "unknown error"}"
            }
            if (error == null) finish(code) else _state.value = Session.NeedsFamily(error = error)
        }
    }

    fun leaveFamily() {
        app.familyCode = null
        _state.value = Session.NeedsFamily()
    }

    private fun finish(code: String) {
        app.familyCode = code
        _state.value = Session.Ready(code, app.repositoryFor(code))
    }

    companion object {
        private const val TAG = "SessionViewModel"
        private const val NETWORK_TIMEOUT_MS = 15_000L
        const val CODE_LENGTH = 10

        /** No 0/O/1/I so codes are easy to read out or type. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val DEFAULT_STORES = listOf("Dunnes Stores", "Aldi", "Lidl", "SuperValu")

        fun generateCode(): String {
            val random = SecureRandom()
            return buildString { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        }

        fun normalise(input: String) = input.uppercase().filter { it in ALPHABET }

        /** ABCDE-FGHJK */
        fun display(code: String) = code.chunked(5).joinToString("-")

        val Factory = viewModelFactory {
            initializer { SessionViewModel(this[APPLICATION_KEY] as ShoppingApp) }
        }
    }
}
