package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sharedshoppinglists.app.data.local.entity.SavingsEntity

@Dao
interface SavingsDao {
    @Insert
    suspend fun insert(entry: SavingsEntity)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM savings_history WHERE recordedAt >= :since")
    suspend fun getTotalSavingsSince(since: Long): Double

    @Query("SELECT * FROM savings_history ORDER BY recordedAt DESC LIMIT 20")
    suspend fun getRecent(): List<SavingsEntity>
}