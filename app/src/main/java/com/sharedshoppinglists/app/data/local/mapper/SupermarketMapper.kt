package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.SupermarketEntity
import com.sharedshoppinglists.app.domain.model.MySupermarket

fun MySupermarket.toEntity(): SupermarketEntity = SupermarketEntity(
    id = id, userId = userId, name = name, address = address
)

fun SupermarketEntity.toDomain(): MySupermarket = MySupermarket(
    id = id, userId = userId, name = name, address = address
)
