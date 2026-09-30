package com.family.shoppinglist.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StoreEntity>>

    @Insert
    suspend fun insert(store: StoreEntity): Long

    @Update
    suspend fun update(store: StoreEntity)

    @Delete
    suspend fun delete(store: StoreEntity)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY checked, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ItemEntity>>

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("UPDATE items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE items SET checked = 0 WHERE recurringId IN (:recurringIds)")
    suspend fun uncheckRecurring(recurringIds: List<Long>)

    @Query("DELETE FROM items WHERE checked = 1")
    suspend fun deleteChecked()

    @Query("SELECT recurringId FROM items WHERE recurringId IS NOT NULL")
    suspend fun recurringIdsOnList(): List<Long>

    @Query("SELECT COUNT(*) FROM items WHERE checked = 0 AND LOWER(name) = LOWER(:name) AND storeId IS :storeId")
    suspend fun countUncheckedDuplicates(name: String, storeId: Long?): Int
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY weekly DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<RecurringEntity>

    @Insert
    suspend fun insert(entry: RecurringEntity): Long

    @Update
    suspend fun update(entry: RecurringEntity)

    @Delete
    suspend fun delete(entry: RecurringEntity)

    @Query("UPDATE recurring SET lastAddedOn = :epochDay WHERE id = :id")
    suspend fun markAdded(id: Long, epochDay: Long)
}

@Dao
interface PurchaseStatDao {
    @Query("SELECT * FROM purchase_stats ORDER BY count DESC, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<PurchaseStatEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(stat: PurchaseStatEntity)

    @Query(
        "UPDATE purchase_stats SET count = MAX(count + :delta, 0), " +
            "lastPurchasedAt = CASE WHEN :delta > 0 THEN :at ELSE lastPurchasedAt END WHERE `key` = :key"
    )
    suspend fun bump(key: String, delta: Int, at: Long)
}

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers ORDER BY product COLLATE NOCASE")
    fun observeAll(): Flow<List<OfferEntity>>

    @Insert
    suspend fun insert(offer: OfferEntity): Long

    @Update
    suspend fun update(offer: OfferEntity)

    @Delete
    suspend fun delete(offer: OfferEntity)

    @Query("DELETE FROM offers WHERE validUntil IS NOT NULL AND validUntil < :today")
    suspend fun deleteExpired(today: Long)
}
