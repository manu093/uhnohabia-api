package com.sharedshoppinglists.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sharedshoppinglists.app.data.local.entity.CustomCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CustomCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CustomCategoryEntity>)

    @Update
    suspend fun update(category: CustomCategoryEntity)

    @Delete
    suspend fun delete(category: CustomCategoryEntity)

    @Query("SELECT * FROM custom_categories ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<CustomCategoryEntity>

    @Query("SELECT COUNT(*) FROM custom_categories")
    suspend fun count(): Int
}
