package com.family.shoppinglist

import android.app.Application
import android.content.Context
import com.family.shoppinglist.data.ShoppingRepository
import com.family.shoppinglist.work.ResetScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ShoppingApp : Application() {
    private val prefs by lazy { getSharedPreferences("family", Context.MODE_PRIVATE) }

    /** The family code this phone belongs to, or null if it hasn't created/joined a family yet. */
    var familyCode: String?
        get() = prefs.getString("code", null)
        set(value) = prefs.edit().apply { if (value == null) remove("code") else putString("code", value) }.apply()

    /** False until google-services.json has been added to the build (see README). */
    val firebaseConfigured: Boolean get() = FirebaseApp.getApps(this).isNotEmpty()

    override fun onCreate() {
        super.onCreate()
        ResetScheduler.scheduleNext(this)
    }

    fun householdRef(code: String): DocumentReference =
        FirebaseFirestore.getInstance().collection("households").document(code)

    fun repositoryFor(code: String) = ShoppingRepository(householdRef(code), ResetScheduler.schedule)

    /** Firestore rules require a signed-in user; each phone quietly signs in anonymously. */
    suspend fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    /** For background work: the repository for this phone's family, or null if not set up. */
    suspend fun currentRepository(): ShoppingRepository? {
        if (!firebaseConfigured) return null
        val code = familyCode ?: return null
        ensureSignedIn()
        return repositoryFor(code)
    }
}
