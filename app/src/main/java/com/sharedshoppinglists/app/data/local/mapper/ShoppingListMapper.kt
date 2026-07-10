package com.sharedshoppinglists.app.data.local.mapper

import com.sharedshoppinglists.app.data.local.entity.ShoppingListEntity
import com.sharedshoppinglists.app.domain.model.ShoppingList
import java.time.Instant

fun ShoppingList.toEntity(pendingSync: Boolean = false): ShoppingListEntity {
    return ShoppingListEntity(
        id = id, name = name, ownerId = ownerId, isShared = isShared, emoji = emoji, color = color,
        createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(), pendingSync = pendingSync
    )
}

fun ShoppingListEntity.toDomain(members: List<String> = emptyList()): ShoppingList {
    return ShoppingList(
        id = id, name = name, ownerId = ownerId, members = members, isShared = isShared, emoji = emoji, color = color,
        createdAt = Instant.ofEpochMilli(createdAt), updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}