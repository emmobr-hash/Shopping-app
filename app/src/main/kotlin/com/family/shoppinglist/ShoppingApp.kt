package com.family.shoppinglist

import android.app.Application
import com.family.shoppinglist.data.AppDatabase
import com.family.shoppinglist.data.ResetState
import com.family.shoppinglist.data.ShoppingRepository
import com.family.shoppinglist.work.ResetScheduler

class ShoppingApp : Application() {
    lateinit var repository: ShoppingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ShoppingRepository(AppDatabase.build(this), ResetState(this, ResetScheduler.schedule))
        ResetScheduler.scheduleNext(this)
    }
}
