package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.CustomCategoryEntity
import com.sharedshoppinglists.app.domain.model.CustomCategory

fun CustomCategory.toEntity(): CustomCategoryEntity {
    return CustomCategoryEntity(
        id = id,
        name = name,
        emoji = emoji,
        sortOrder = sortOrder
    )
}

fun CustomCategoryEntity.toDomain(): CustomCategory {
    return CustomCategory(
        id = id,
        name = name,
        emoji = emoji,
        sortOrder = sortOrder
    )
}
