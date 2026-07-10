package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.DiscountCard
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

interface DiscountCardRepository {
    suspend fun addCard(card: DiscountCard): Result<DiscountCard>
    suspend fun updateCard(card: DiscountCard): Result<DiscountCard>
    suspend fun deleteCard(cardId: String): Result<Unit>
    fun getCards(userId: String): Flow<List<DiscountCard>>
    fun getApplicableDiscounts(
        supermarketId: String,
        purchaseAmount: BigDecimal,
        date: LocalDate = LocalDate.now()
    ): List<DiscountCard>
}
