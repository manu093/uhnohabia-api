package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.SupermarketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupermarketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supermarket: SupermarketEntity)

    @Update
    suspend fun update(supermarket: SupermarketEntity)

    @Query("DELETE FROM supermarkets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM supermarkets WHERE userId = :userId ORDER BY name ASC")
    fun getByUserId(userId: String): Flow<List<SupermarketEntity>>

    @Query("SELECT * FROM supermarkets WHERE userId = :userId ORDER BY name ASC")
    suspend fun getByUserIdOnce(userId: String): List<SupermarketEntity>

    @Query("SELECT * FROM supermarkets WHERE id = :id")
    suspend fun getById(id: String): SupermarketEntity?
}
