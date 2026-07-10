package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.KnownProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: KnownProductEntity)

    @Update
    suspend fun update(product: KnownProductEntity)

    @Query("SELECT * FROM known_products ORDER BY timesUsed DESC")
    fun getAll(): Flow<List<KnownProductEntity>>

    @Query("SELECT * FROM known_products WHERE name LIKE '%' || :query || '%' ORDER BY timesUsed DESC LIMIT 10")
    suspend fun searchByName(query: String): List<KnownProductEntity>

    @Query("UPDATE known_products SET timesUsed = timesUsed + 1 WHERE id = :productId")
    suspend fun incrementTimesUsed(productId: String)

    @Query("SELECT * FROM known_products WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): KnownProductEntity?

    @Query("SELECT * FROM known_products ORDER BY timesUsed DESC LIMIT :limit")
    fun getMostUsed(limit: Int): Flow<List<KnownProductEntity>>

    @Query("DELETE FROM known_products WHERE id = :id")
    suspend fun deleteById(id: String)
}
