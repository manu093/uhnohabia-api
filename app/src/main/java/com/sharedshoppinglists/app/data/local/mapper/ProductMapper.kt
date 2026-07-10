package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.ProductEntity
import com.sharedshoppinglists.app.domain.model.Product
import java.time.Instant

fun Product.toEntity(listId: String, pendingSync: Boolean = false): ProductEntity {
    return ProductEntity(
        id = id,
        listId = listId,
        name = name,
        quantity = quantity,
        unit = unit,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryEmoji = categoryEmoji,
        emoji = emoji,
        preferredBrand = preferredBrand,
        isPurchased = isPurchased,
        lastModifiedBy = lastModifiedBy,
        lastModifiedAt = lastModifiedAt.toEpochMilli(),
        pendingSync = pendingSync
    )
}

fun ProductEntity.toDomain(): Product {
    return Product(
        id = id,
        name = name,
        quantity = quantity,
        unit = unit,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryEmoji = categoryEmoji,
        emoji = emoji,
        preferredBrand = preferredBrand,
        isPurchased = isPurchased,
        lastModifiedBy = lastModifiedBy,
        lastModifiedAt = Instant.ofEpochMilli(lastModifiedAt)
    )
}
