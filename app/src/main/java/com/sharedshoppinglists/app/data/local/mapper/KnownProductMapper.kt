package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.KnownProductEntity
import com.sharedshoppinglists.app.domain.model.KnownProduct

fun KnownProduct.toEntity(): KnownProductEntity {
    return KnownProductEntity(
        id = id,
        name = name,
        emoji = emoji,
        categoryId = categoryId,
        defaultUnit = defaultUnit,
        timesUsed = timesUsed
    )
}

fun KnownProductEntity.toDomain(): KnownProduct {
    return KnownProduct(
        id = id,
        name = name,
        emoji = emoji,
        categoryId = categoryId,
        defaultUnit = defaultUnit,
        timesUsed = timesUsed
    )
}
