package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.DiscountCardEntity
import com.sharedshoppinglists.app.domain.model.CardType
import com.sharedshoppinglists.app.domain.model.Discount
import com.sharedshoppinglists.app.domain.model.DiscountCard
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

fun DiscountCard.toEntity(): DiscountCardEntity {
    return DiscountCardEntity(
        id = id,
        userId = userId,
        type = type.name,
        issuer = issuer,
        applicableSupermarketsJson = serializeSupermarkets(applicableSupermarkets),
        validFrom = validFrom?.toEpochDay(),
        validUntil = validUntil?.toEpochDay()
    )
}

fun DiscountCardEntity.toDomain(): DiscountCard {
    return DiscountCard(
        id = id,
        userId = userId,
        type = CardType.valueOf(type),
        issuer = issuer,
        applicableSupermarkets = deserializeSupermarkets(applicableSupermarketsJson),
        validFrom = validFrom?.let { LocalDate.ofEpochDay(it) },
        validUntil = validUntil?.let { LocalDate.ofEpochDay(it) }
    )
}

private fun serializeSupermarkets(supermarkets: Map<String, Discount>): String {
    val json = JSONObject()
    for ((supermarketId, discount) in supermarkets) {
        val discountJson = JSONObject()
        discount.percentage?.let { discountJson.put("percentage", it.toPlainString()) }
        discount.fixedAmount?.let { discountJson.put("fixedAmount", it.toPlainString()) }
        discount.minimumPurchase?.let { discountJson.put("minimumPurchase", it.toPlainString()) }
        json.put(supermarketId, discountJson)
    }
    return json.toString()
}

private fun deserializeSupermarkets(jsonString: String): Map<String, Discount> {
    if (jsonString.isBlank()) return emptyMap()
    val json = JSONObject(jsonString)
    val result = mutableMapOf<String, Discount>()
    for (key in json.keys()) {
        val discountJson = json.getJSONObject(key)
        result[key] = Discount(
            percentage = discountJson.optString("percentage", "").takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
            fixedAmount = discountJson.optString("fixedAmount", "").takeIf { it.isNotEmpty() }?.let { BigDecimal(it) },
            minimumPurchase = discountJson.optString("minimumPurchase", "").takeIf { it.isNotEmpty() }?.let { BigDecimal(it) }
        )
    }
    return result
}
