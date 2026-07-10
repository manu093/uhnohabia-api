package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.KnownProduct
import kotlinx.coroutines.flow.Flow

interface KnownProductRepository {
    suspend fun searchByName(query: String): List<KnownProduct>
    suspend fun saveOrIncrement(product: KnownProduct)
    fun getAll(): Flow<List<KnownProduct>>
    suspend fun delete(id: String)
    suspend fun update(product: KnownProduct)
    fun getMostUsed(limit: Int): kotlinx.coroutines.flow.Flow<List<com.sharedshoppinglists.app.domain.model.KnownProduct>>
}
