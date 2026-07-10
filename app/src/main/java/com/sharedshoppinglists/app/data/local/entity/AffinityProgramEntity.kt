package com.sharedshoppinglists.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "affinity_programs")
data class AffinityProgramEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val supermarketId: String,
    val discountPercentage: Double,
    val validFrom: Long?,
    val validUntil: Long?
)
