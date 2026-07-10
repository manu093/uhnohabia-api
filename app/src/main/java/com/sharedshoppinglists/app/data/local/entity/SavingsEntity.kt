package com.sharedshoppinglists.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "savings_history")
data class SavingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val chain: String,
    val listName: String,
    val recordedAt: Long
)