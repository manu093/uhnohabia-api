package com.sharedshoppinglists.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_products")
data class KnownProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val categoryId: String,
    val defaultUnit: String,
    val timesUsed: Int = 0
)
