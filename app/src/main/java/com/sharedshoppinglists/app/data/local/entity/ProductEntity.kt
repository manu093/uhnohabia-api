package com.sharedshoppinglists.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(
        entity = ShoppingListEntity::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["listId"])]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val categoryId: String = "",
    val categoryName: String = "Otros",
    val categoryEmoji: String = "📦",
    val emoji: String = "",
    val preferredBrand: String = "",
    val isPurchased: Boolean,
    val lastModifiedBy: String,
    val lastModifiedAt: Long,
    val pendingSync: Boolean = false
)
