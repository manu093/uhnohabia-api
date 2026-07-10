package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.ManualPriceEntity
import com.sharedshoppinglists.app.domain.model.ManualPrice
import java.time.Instant

fun ManualPrice.toEntity(): ManualPriceEntity = ManualPriceEntity(
    id = id, userId = userId, supermarketId = supermarketId,
    productName = productName, price = price, updatedAt = updatedAt.toEpochMilli()
)

fun ManualPriceEntity.toDomain(): ManualPrice = ManualPrice(
    id = id, userId = userId, supermarketId = supermarketId,
    productName = productName, price = price, updatedAt = Instant.ofEpochMilli(updatedAt)
)
