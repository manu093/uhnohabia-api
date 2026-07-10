package com.sharedshoppinglists.app.data.repository

import com.sharedshoppinglists.app.data.local.dao.AffinityProgramDao
import com.sharedshoppinglists.app.data.local.dao.ManualPriceDao
import com.sharedshoppinglists.app.data.local.dao.SupermarketDao
import com.sharedshoppinglists.app.data.local.mapper.toDomain
import com.sharedshoppinglists.app.data.local.mapper.toEntity
import com.sharedshoppinglists.app.domain.model.AffinityProgram
import com.sharedshoppinglists.app.domain.model.ComparisonResult
import com.sharedshoppinglists.app.domain.model.ManualPrice
import com.sharedshoppinglists.app.domain.model.MySupermarket
import com.sharedshoppinglists.app.domain.repository.DiscountCardRepository
import com.sharedshoppinglists.app.domain.repository.ManualPriceComparatorRepository
import com.sharedshoppinglists.app.domain.repository.ShoppingListRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

class ManualPriceComparatorRepositoryImpl @Inject constructor(
    private val supermarketDao: SupermarketDao,
    private val manualPriceDao: ManualPriceDao,
    private val affinityProgramDao: AffinityProgramDao,
    private val shoppingListRepository: ShoppingListRepository,
    private val discountCardRepository: DiscountCardRepository,
    private val firestore: FirebaseFirestore
) : ManualPriceComparatorRepository {

    // --- Supermarkets ---
    override fun getSupermarkets(userId: String): Flow<List<MySupermarket>> =
        supermarketDao.getByUserId(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun addSupermarket(supermarket: MySupermarket): Result<Unit> = runCatching {
        supermarketDao.insert(supermarket.toEntity())
        syncSupermarketToFirestore(supermarket)
    }

    override suspend fun updateSupermarket(supermarket: MySupermarket): Result<Unit> = runCatching {
        supermarketDao.update(supermarket.toEntity())
        syncSupermarketToFirestore(supermarket)
    }

    override suspend fun deleteSupermarket(id: String): Result<Unit> = runCatching {
        supermarketDao.deleteById(id)
        try { firestore.collection("supermarkets").document(id).delete().await() } catch (_: Exception) {}
    }

    // --- Manual prices ---
    override fun getPricesBySupermarket(supermarketId: String): Flow<List<ManualPrice>> =
        manualPriceDao.getBySupermarketId(supermarketId).map { list -> list.map { it.toDomain() } }

    override fun getAllPrices(userId: String): Flow<List<ManualPrice>> =
        manualPriceDao.getByUserId(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun addPrice(price: ManualPrice): Result<Unit> = runCatching {
        manualPriceDao.insert(price.toEntity())
        syncPriceToFirestore(price)
    }

    override suspend fun updatePrice(price: ManualPrice): Result<Unit> = runCatching {
        manualPriceDao.update(price.toEntity())
        syncPriceToFirestore(price)
    }

    override suspend fun deletePrice(id: String): Result<Unit> = runCatching {
        manualPriceDao.deleteById(id)
        try { firestore.collection("manualPrices").document(id).delete().await() } catch (_: Exception) {}
    }

    override suspend fun getDistinctProductNames(userId: String): List<String> =
        manualPriceDao.getDistinctProductNames(userId)

    // --- Affinity programs ---
    override fun getAffinityPrograms(userId: String): Flow<List<AffinityProgram>> =
        affinityProgramDao.getByUserId(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun addAffinityProgram(program: AffinityProgram): Result<Unit> = runCatching {
        affinityProgramDao.insert(program.toEntity())
        syncAffinityToFirestore(program)
    }

    override suspend fun updateAffinityProgram(program: AffinityProgram): Result<Unit> = runCatching {
        affinityProgramDao.update(program.toEntity())
        syncAffinityToFirestore(program)
    }

    override suspend fun deleteAffinityProgram(id: String): Result<Unit> = runCatching {
        affinityProgramDao.deleteById(id)
        try { firestore.collection("affinityPrograms").document(id).delete().await() } catch (_: Exception) {}
    }

    // --- Comparison calculator ---
    override suspend fun calculateBestSupermarket(
        userId: String,
        listId: String
    ): Result<List<ComparisonResult>> = runCatching {
        val products = shoppingListRepository.getProducts(listId).first()
        val supermarkets = supermarketDao.getByUserIdOnce(userId).map { it.toDomain() }
        val allPrices = manualPriceDao.getByUserIdOnce(userId).map { it.toDomain() }
        val affinityPrograms = affinityProgramDao.getByUserIdOnce(userId).map { it.toDomain() }
        val today = LocalDate.now()

        val productNames = products.map { it.name.lowercase().trim() }

        supermarkets.map { supermarket ->
            val pricesForSuper = allPrices.filter { it.supermarketId == supermarket.id }
            val priceMap = pricesForSuper.associateBy { it.productName.lowercase().trim() }

            var subtotal = 0.0
            val missing = mutableListOf<String>()

            for (product in products) {
                val key = product.name.lowercase().trim()
                val priceEntry = priceMap[key]
                if (priceEntry != null) {
                    subtotal += priceEntry.price * product.quantity
                } else {
                    missing.add(product.name)
                }
            }

            // Apply discount cards
            val applicableCards = discountCardRepository.getApplicableDiscounts(
                supermarketId = supermarket.name,
                purchaseAmount = BigDecimal.valueOf(subtotal),
                date = today
            )
            val bestCard = applicableCards.maxByOrNull {
                it.applicableSupermarkets[supermarket.name]?.percentage?.toDouble() ?: 0.0
            }
            val cardDiscountPct = bestCard?.applicableSupermarkets?.get(supermarket.name)
                ?.percentage?.toDouble() ?: 0.0
            val cardDiscount = subtotal * cardDiscountPct / 100.0

            // Apply affinity programs
            val applicableAffinity = affinityPrograms.filter { prog ->
                prog.supermarketId == supermarket.id &&
                    (prog.validFrom == null || !today.isBefore(prog.validFrom)) &&
                    (prog.validUntil == null || !today.isAfter(prog.validUntil))
            }
            val bestAffinity = applicableAffinity.maxByOrNull { it.discountPercentage }
            val affinityDiscountAmt = (subtotal - cardDiscount) * (bestAffinity?.discountPercentage ?: 0.0) / 100.0

            val total = subtotal - cardDiscount - affinityDiscountAmt

            ComparisonResult(
                supermarket = supermarket,
                subtotal = subtotal,
                cardDiscount = cardDiscount,
                affinityDiscount = affinityDiscountAmt,
                total = total,
                missingProducts = missing,
                appliedCardName = bestCard?.issuer,
                appliedAffinityName = bestAffinity?.name
            )
        }.sortedBy { it.total }
    }

    // --- Price history ---
    override suspend fun getPriceHistory(userId: String, productName: String): List<ManualPrice> {
        return manualPriceDao.getByProductName(userId, productName).map { it.toDomain() }
    }

    // --- Firestore sync helpers (best-effort, fire-and-forget) ---
    private suspend fun syncSupermarketToFirestore(s: MySupermarket) {
        try {
            firestore.collection("supermarkets").document(s.id).set(
                mapOf("id" to s.id, "userId" to s.userId, "name" to s.name, "address" to s.address)
            ).await()
        } catch (_: Exception) {}
    }

    private suspend fun syncPriceToFirestore(p: ManualPrice) {
        try {
            firestore.collection("manualPrices").document(p.id).set(
                mapOf(
                    "id" to p.id, "userId" to p.userId, "supermarketId" to p.supermarketId,
                    "productName" to p.productName, "price" to p.price,
                    "updatedAt" to p.updatedAt.toEpochMilli()
                )
            ).await()
        } catch (_: Exception) {}
    }

    private suspend fun syncAffinityToFirestore(a: AffinityProgram) {
        try {
            firestore.collection("affinityPrograms").document(a.id).set(
                mapOf(
                    "id" to a.id, "userId" to a.userId, "name" to a.name,
                    "supermarketId" to a.supermarketId, "discountPercentage" to a.discountPercentage,
                    "validFrom" to a.validFrom?.toEpochDay(), "validUntil" to a.validUntil?.toEpochDay()
                )
            ).await()
        } catch (_: Exception) {}
    }
}
