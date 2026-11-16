package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.domain.model.CardType
import com.sharedshoppinglists.app.domain.model.Discount
import com.sharedshoppinglists.app.domain.model.DiscountCard
import com.sharedshoppinglists.app.domain.model.KnownProduct
import com.sharedshoppinglists.app.domain.model.Product
import com.sharedshoppinglists.app.domain.model.ShoppingList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Ida y vuelta domain -> entity -> domain. Si alguien agrega un campo al modelo y se
 * olvida de mapearlo, estos tests fallan en lugar de perder datos en silencio.
 */
class MappersTest : FunSpec({

    val instant = Arb.long(0L, 4_102_444_800_000L).map { Instant.ofEpochMilli(it) }

    val productArb = Arb.bind(
        Arb.string(1, 20), Arb.string(0, 30), Arb.double(0.0, 1000.0), Arb.string(0, 10),
        Arb.string(0, 10), Arb.boolean(), Arb.string(0, 10), instant,
    ) { id, name, qty, unit, brand, purchased, by, at ->
        Product(
            id = id, name = name, quantity = qty, unit = unit, categoryId = "c1", categoryName = "Almacén",
            categoryEmoji = "🏪", emoji = "🥛", preferredBrand = brand, isPurchased = purchased,
            lastModifiedBy = by, lastModifiedAt = at,
        )
    }

    test("Product sobrevive la ida y vuelta a ProductEntity") {
        checkAll(productArb, Arb.string(1, 20), Arb.boolean()) { product, listId, pending ->
            val entity = product.toEntity(listId, pending)
            entity.listId shouldBe listId
            entity.pendingSync shouldBe pending
            entity.toDomain() shouldBe product
        }
    }

    test("ShoppingList sobrevive la ida y vuelta (members viaja aparte)") {
        checkAll(Arb.string(1, 20), Arb.string(0, 30), Arb.boolean(), instant, Arb.list(Arb.string(1, 10), 0..4)) {
                id, name, shared, at, members ->
            val list = ShoppingList(id, name, "owner", members, shared, "🛒", "#FF0000", at, at.plusMillis(5))
            list.toEntity().toDomain(members) shouldBe list
        }
    }

    test("KnownProduct sobrevive la ida y vuelta") {
        checkAll(Arb.string(1, 20), Arb.string(0, 30), Arb.int(0, 10_000)) { id, name, used ->
            val kp = KnownProduct(id, name, "🍞", "cat", "Unidad", used)
            kp.toEntity().toDomain() shouldBe kp
        }
    }

    test("DiscountCard conserva descuentos por supermercado y fechas") {
        checkAll(Arb.enum<CardType>(), Arb.int(1, 90)) { type, pct ->
            val card = DiscountCard(
                id = "card-1", userId = "u1", type = type, issuer = "Banco Nación",
                applicableSupermarkets = mapOf(
                    "DIA" to Discount(BigDecimal(pct), null, BigDecimal("15000.50")),
                    "Coto" to Discount(null, BigDecimal("2000"), null),
                ),
                validFrom = LocalDate.of(2026, 1, 1), validUntil = null,
            )
            card.toEntity().toDomain() shouldBe card
        }
    }
})
