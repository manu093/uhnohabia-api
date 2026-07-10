package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.ManualPriceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualPriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: ManualPriceEntity)

    @Update
    suspend fun update(price: ManualPriceEntity)

    @Query("DELETE FROM manual_prices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM manual_prices WHERE supermarketId = :supermarketId ORDER BY productName ASC")
    fun getBySupermarketId(supermarketId: String): Flow<List<ManualPriceEntity>>

    @Query("SELECT * FROM manual_prices WHERE userId = :userId ORDER BY productName ASC")
    fun getByUserId(userId: String): Flow<List<ManualPriceEntity>>

    @Query("SELECT * FROM manual_prices WHERE userId = :userId")
    suspend fun getByUserIdOnce(userId: String): List<ManualPriceEntity>

    @Query("SELECT * FROM manual_prices WHERE userId = :userId AND productName = :productName")
    suspend fun getByProductName(userId: String, productName: String): List<ManualPriceEntity>

    @Query("SELECT DISTINCT productName FROM manual_prices WHERE userId = :userId ORDER BY productName ASC")
    suspend fun getDistinctProductNames(userId: String): List<String>
}
