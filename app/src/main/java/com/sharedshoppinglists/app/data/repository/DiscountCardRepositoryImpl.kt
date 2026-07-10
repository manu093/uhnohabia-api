package com.sharedshoppinglists.app.data.repository

import com.sharedshoppinglists.app.data.local.dao.DiscountCardDao
import com.sharedshoppinglists.app.data.local.mapper.toDomain
import com.sharedshoppinglists.app.data.local.mapper.toEntity
import com.sharedshoppinglists.app.domain.model.DiscountCard
import com.sharedshoppinglists.app.domain.repository.DiscountCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

class DiscountCardRepositoryImpl @Inject constructor(
    private val discountCardDao: DiscountCardDao
) : DiscountCardRepository {

    override suspend fun addCard(card: DiscountCard): Result<DiscountCard> {
        return try {
            discountCardDao.insert(card.toEntity())
            Result.success(card)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateCard(card: DiscountCard): Result<DiscountCard> {
        return try {
            discountCardDao.update(card.toEntity())
            Result.success(card)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteCard(cardId: String): Result<Unit> {
        return try {
            discountCardDao.deleteById(cardId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCards(userId: String): Flow<List<DiscountCard>> {
        return discountCardDao.getByUserId(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getApplicableDiscounts(
        supermarketId: String,
        purchaseAmount: BigDecimal,
        date: LocalDate
    ): List<DiscountCard> {
        val allCards = runBlocking {
            discountCardDao.getAllOnce()
        }
        return allCards
            .map { it.toDomain() }
            .filter { card ->
                // Card must have a discount entry for this supermarket
                val discount = card.applicableSupermarkets[supermarketId] ?: return@filter false

                // Validate date range: validFrom <= date (null = always valid from the past)
                val fromValid = card.validFrom == null || !date.isBefore(card.validFrom)
                // Validate date range: date <= validUntil (null = always valid into the future)
                val untilValid = card.validUntil == null || !date.isAfter(card.validUntil)

                if (!fromValid || !untilValid) return@filter false

                // Check minimum purchase requirement
                val meetsMinimum = discount.minimumPurchase == null ||
                    purchaseAmount >= discount.minimumPurchase

                meetsMinimum
            }
    }
}
