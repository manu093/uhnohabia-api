package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteById(productId: String)

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getById(productId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE listId = :listId ORDER BY name ASC")
    fun getByListId(listId: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET isPurchased = :purchased, lastModifiedBy = :modifiedBy, lastModifiedAt = :modifiedAt WHERE id = :productId")
    suspend fun markAsPurchased(productId: String, purchased: Boolean, modifiedBy: String, modifiedAt: Long)

    @Query("SELECT * FROM products WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<ProductEntity>

    @Query("UPDATE products SET pendingSync = :pending WHERE id = :productId")
    suspend fun updatePendingSync(productId: String, pending: Boolean)

    @Query("SELECT COUNT(*) FROM products WHERE listId = :listId")
    fun countByListId(listId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE listId = :listId AND isPurchased = 0")
    fun countPendingByListId(listId: String): Int

    @Query("DELETE FROM products WHERE listId = :listId")
    suspend fun deleteByListId(listId: String)

    // Sync methods for widget (runs on background thread)
    @Query("SELECT * FROM products WHERE listId = :listId ORDER BY isPurchased ASC, name ASC")
    fun getByListIdSync(listId: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :productId")
    fun getByIdSync(productId: String): ProductEntity?

    @Query("UPDATE products SET isPurchased = :purchased, lastModifiedAt = :modifiedAt WHERE id = :productId")
    fun markAsPurchasedSync(productId: String, purchased: Boolean, modifiedAt: Long)
}
