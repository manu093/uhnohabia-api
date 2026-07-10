package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.AffinityProgram
import com.sharedshoppinglists.app.domain.model.ComparisonResult
import com.sharedshoppinglists.app.domain.model.ManualPrice
import com.sharedshoppinglists.app.domain.model.MySupermarket
import kotlinx.coroutines.flow.Flow

interface ManualPriceComparatorRepository {
    // Supermarkets
    fun getSupermarkets(userId: String): Flow<List<MySupermarket>>
    suspend fun addSupermarket(supermarket: MySupermarket): Result<Unit>
    suspend fun updateSupermarket(supermarket: MySupermarket): Result<Unit>
    suspend fun deleteSupermarket(id: String): Result<Unit>

    // Manual prices
    fun getPricesBySupermarket(supermarketId: String): Flow<List<ManualPrice>>
    fun getAllPrices(userId: String): Flow<List<ManualPrice>>
    suspend fun addPrice(price: ManualPrice): Result<Unit>
    suspend fun updatePrice(price: ManualPrice): Result<Unit>
    suspend fun deletePrice(id: String): Result<Unit>
    suspend fun getDistinctProductNames(userId: String): List<String>

    // Affinity programs
    fun getAffinityPrograms(userId: String): Flow<List<AffinityProgram>>
    suspend fun addAffinityProgram(program: AffinityProgram): Result<Unit>
    suspend fun updateAffinityProgram(program: AffinityProgram): Result<Unit>
    suspend fun deleteAffinityProgram(id: String): Result<Unit>

    // Comparison calculator
    suspend fun calculateBestSupermarket(
        userId: String,
        listId: String
    ): Result<List<ComparisonResult>>

    // Price history for a product across supermarkets
    suspend fun getPriceHistory(userId: String, productName: String): List<ManualPrice>
}
