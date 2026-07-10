package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.AffinityProgramEntity
import com.sharedshoppinglists.app.domain.model.AffinityProgram
import java.time.LocalDate

fun AffinityProgram.toEntity(): AffinityProgramEntity = AffinityProgramEntity(
    id = id, userId = userId, name = name, supermarketId = supermarketId,
    discountPercentage = discountPercentage,
    validFrom = validFrom?.toEpochDay(), validUntil = validUntil?.toEpochDay()
)

fun AffinityProgramEntity.toDomain(): AffinityProgram = AffinityProgram(
    id = id, userId = userId, name = name, supermarketId = supermarketId,
    discountPercentage = discountPercentage,
    validFrom = validFrom?.let { LocalDate.ofEpochDay(it) },
    validUntil = validUntil?.let { LocalDate.ofEpochDay(it) }
)
