package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sharedshoppinglists.app.data.local.entity.PriceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceHistoryDao {
    @Insert
    suspend fun insert(entry: PriceHistoryEntity)

    @Insert
    suspend fun insertAll(entries: List<PriceHistoryEntity>)

    @Query("SELECT * FROM price_history WHERE productName = :name ORDER BY recordedAt DESC LIMIT 30")
    fun getHistory(name: String): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history WHERE productName = :name AND chain = :chain ORDER BY recordedAt DESC LIMIT 2")
    suspend fun getRecentPrices(name: String, chain: String): List<PriceHistoryEntity>

    @Query("SELECT DISTINCT productName FROM price_history ORDER BY productName")
    suspend fun getTrackedProducts(): List<String>

    @Query("DELETE FROM price_history WHERE recordedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}