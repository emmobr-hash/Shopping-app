package com.family.shoppinglist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StoreEntity::class,
        ItemEntity::class,
        RecurringEntity::class,
        PurchaseStatEntity::class,
        OfferEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stores(): StoreDao
    abstract fun items(): ItemDao
    abstract fun recurring(): RecurringDao
    abstract fun stats(): PurchaseStatDao
    abstract fun offers(): OfferDao

    companion object {
        /** Starter supermarkets; rename, delete or add your own on the Stores tab. */
        private val defaultStores = listOf(
            "Tesco", "Sainsbury's", "Asda", "Aldi", "Lidl", "Morrisons", "Waitrose", "Co-op",
        )

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "shopping.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        defaultStores.forEach { db.execSQL("INSERT INTO stores (name) VALUES (?)", arrayOf(it)) }
                    }
                })
                .build()
    }
}
