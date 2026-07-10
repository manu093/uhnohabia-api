package com.sharedshoppinglists.app.data.repository

import com.sharedshoppinglists.app.data.local.dao.DiscountCardDao
import com.sharedshoppinglists.app.data.local.entity.DiscountCardEntity
import com.sharedshoppinglists.app.domain.model.CardType
import com.sharedshoppinglists.app.domain.model.Discount
import com.sharedshoppinglists.app.domain.model.DiscountCard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DiscountCardRepositoryImplTest {

    private lateinit var dao: DiscountCardDao
    private lateinit var repository: DiscountCardRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        repository = DiscountCardRepositoryImpl(dao)
    }

    // --- CRUD tests (mock insert/update/delete at DAO level) ---

    @Test
    fun `addCard calls dao insert and returns success`() = runTest {
        coEvery { dao.insert(any()) } returns Unit
        val card = makeDomainCard()
        val result = repository.addCard(card)
        assertTrue(result.isSuccess)
        assertEquals(card, result.getOrNull())
        coVerify { dao.insert(any()) }
    }

    @Test
    fun `addCard returns failure on dao exception`() = runTest {
        coEvery { dao.insert(any()) } throws RuntimeException("DB error")
        val result = repository.addCard(makeDomainCard())
        assertTrue(result.isFailure)
    }

    @Test
    fun `updateCard calls dao update and returns success`() = runTest {
        coEvery { dao.update(any()) } returns Unit
        val card = makeDomainCard()
        val result = repository.updateCard(card)
        assertTrue(result.isSuccess)
        assertEquals(card, result.getOrNull())
        coVerify { dao.update(any()) }
    }

    @Test
    fun `deleteCard calls deleteById and returns success`() = runTest {
        coEvery { dao.deleteById("card-1") } returns Unit
        val result = repository.deleteCard("card-1")
        assertTrue(result.isSuccess)
        coVerify { dao.deleteById("card-1") }
    }

    @Test
    fun `deleteCard returns failure on dao exception`() = runTest {
        coEvery { dao.deleteById(any()) } throws RuntimeException("DB error")
        val result = repository.deleteCard("card-1")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getCards returns flow from dao mapped to domain`() = runTest {
        val entity = makeEntity("card-1")
        coEvery { dao.getByUserId("user-1") } returns flowOf(listOf(entity))

        val cards = repository.getCards("user-1").first()
        assertEquals(1, cards.size)
        assertEquals("card-1", cards[0].id)
    }

    // --- getApplicableDiscounts tests ---
    // These test the filtering logic. We provide entities via getAllOnce() mock.

    @Test
    fun `getApplicableDiscounts returns card applicable to supermarket`() {
        val entity = makeEntity("card-1", supermarketJson = """{"super-1":{"percentage":"10"}}""")
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val result = repository.getApplicableDiscounts(
            "super-1", BigDecimal("100"), LocalDate.of(2025, 6, 1)
        )
        assertEquals(1, result.size)
        assertEquals("card-1", result[0].id)
    }

    @Test
    fun `getApplicableDiscounts excludes card not applicable to supermarket`() {
        val entity = makeEntity("card-1", supermarketJson = """{"super-1":{"percentage":"10"}}""")
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val result = repository.getApplicableDiscounts(
            "super-999", BigDecimal("100"), LocalDate.of(2025, 6, 1)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getApplicableDiscounts excludes expired card`() {
        val entity = makeEntity(
            "card-1",
            validFrom = LocalDate.of(2024, 1, 1).toEpochDay(),
            validUntil = LocalDate.of(2024, 12, 31).toEpochDay()
        )
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val result = repository.getApplicableDiscounts(
            "super-1", BigDecimal("100"), LocalDate.of(2025, 6, 1)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getApplicableDiscounts excludes card not yet valid`() {
        val entity = makeEntity(
            "card-1",
            validFrom = LocalDate.of(2026, 1, 1).toEpochDay(),
            validUntil = LocalDate.of(2026, 12, 31).toEpochDay()
        )
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val result = repository.getApplicableDiscounts(
            "super-1", BigDecimal("100"), LocalDate.of(2025, 6, 1)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getApplicableDiscounts includes card with null dates`() {
        val entity = makeEntity("card-1", validFrom = null, validUntil = null)
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val result = repository.getApplicableDiscounts(
            "super-1", BigDecimal("100"), LocalDate.of(2025, 6, 1)
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `getApplicableDiscounts excludes card when purchase below minimum`() {
        val entity = makeEntity(
            "card-1",
            supermarketJson = """{"super-1":{"percentage":"15","minimumPurchase":"500"}}"""
        )
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val result = repository.getApplicableDiscounts(
            "super-1", BigDecimal("200"), LocalDate.of(2025, 6, 1)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getApplicableDiscounts includes card when purchase meets minimum`() {
        val entity = makeEntity(
            "card-1",
            supermarketJson = """{"super-1":{"percentage":"15","minimumPurchase":"500"}}"""
        )
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val result = repository.getApplicableDiscounts(
            "super-1", BigDecimal("500"), LocalDate.of(2025, 6, 1)
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `getApplicableDiscounts includes card on exact boundary dates`() {
        val entity = makeEntity(
            "card-1",
            validFrom = LocalDate.of(2025, 1, 1).toEpochDay(),
            validUntil = LocalDate.of(2025, 12, 31).toEpochDay()
        )
        coEvery { dao.getAllOnce() } returns listOf(entity)

        val resultFrom = repository.getApplicableDiscounts(
            "super-1", BigDecimal("100"), LocalDate.of(2025, 1, 1)
        )
        assertEquals(1, resultFrom.size)

        val resultUntil = repository.getApplicableDiscounts(
            "super-1", BigDecimal("100"), LocalDate.of(2025, 12, 31)
        )
        assertEquals(1, resultUntil.size)
    }

    // --- Helpers ---

    private fun makeDomainCard(
        id: String = "card-1",
        supermarkets: Map<String, Discount> = mapOf(
            "super-1" to Discount(BigDecimal("10"), null, null)
        ),
        validFrom: LocalDate? = null,
        validUntil: LocalDate? = null
    ) = DiscountCard(
        id = id,
        userId = "user-1",
        type = CardType.CREDIT,
        issuer = "Visa",
        applicableSupermarkets = supermarkets,
        validFrom = validFrom,
        validUntil = validUntil
    )

    private fun makeEntity(
        id: String = "card-1",
        supermarketJson: String = """{"super-1":{"percentage":"10"}}""",
        validFrom: Long? = null,
        validUntil: Long? = null
    ) = DiscountCardEntity(
        id = id,
        userId = "user-1",
        type = "CREDIT",
        issuer = "Visa",
        applicableSupermarketsJson = supermarketJson,
        validFrom = validFrom,
        validUntil = validUntil
    )
}
