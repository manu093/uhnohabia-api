package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: ShoppingListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lists: List<ShoppingListEntity>)

    @Update
    suspend fun update(list: ShoppingListEntity)

    @Delete
    suspend fun delete(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :listId")
    suspend fun deleteById(listId: String)

    @Query("SELECT * FROM shopping_lists WHERE id = :listId")
    suspend fun getById(listId: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists WHERE ownerId = :userId ORDER BY updatedAt DESC")
    fun getByUserId(userId: String): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<ShoppingListEntity>

    @Query("UPDATE shopping_lists SET pendingSync = :pending WHERE id = :listId")
    suspend fun updatePendingSync(listId: String, pending: Boolean)

    @Query("SELECT * FROM shopping_lists ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists ORDER BY updatedAt DESC")
    fun getAllSync(): List<ShoppingListEntity>
}
