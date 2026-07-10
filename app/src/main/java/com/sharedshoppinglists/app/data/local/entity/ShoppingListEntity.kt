package com.sharedshoppinglists.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ownerId: String,
    val isShared: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val emoji: String = "",
    val color: String = "",
    val pendingSync: Boolean = false
)
