package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.CustomCategory
import kotlinx.coroutines.flow.Flow

interface CustomCategoryRepository {
    fun getAll(): Flow<List<CustomCategory>>
    suspend fun getAllOnce(): List<CustomCategory>
    suspend fun insert(category: CustomCategory)
    suspend fun update(category: CustomCategory)
    suspend fun delete(category: CustomCategory)
}
