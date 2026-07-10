package com.sharedshoppinglists.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discount_cards")
data class DiscountCardEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val issuer: String,
    val applicableSupermarketsJson: String,
    val validFrom: Long?,
    val validUntil: Long?
)
