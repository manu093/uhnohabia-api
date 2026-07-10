package com.sharedshoppinglists.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manual_prices",
    foreignKeys = [ForeignKey(
        entity = SupermarketEntity::class,
        parentColumns = ["id"],
        childColumns = ["supermarketId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["supermarketId"]), Index(value = ["productName"])]
)
data class ManualPriceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val supermarketId: String,
    val productName: String,
    val price: Double,
    val updatedAt: Long
)
