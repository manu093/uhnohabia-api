package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.Product
import com.sharedshoppinglists.app.domain.model.ShoppingList
import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    suspend fun createList(name: String, ownerId: String, emoji: String = ""): Result<ShoppingList>
    suspend fun renameList(listId: String, newName: String): Result<Unit>
    suspend fun deleteList(listId: String): Result<Unit>
    fun getLists(userId: String): Flow<List<ShoppingList>>
    fun getProducts(listId: String): Flow<List<Product>>
    suspend fun addProduct(listId: String, product: Product): Result<Product>
    suspend fun updateProduct(listId: String, product: Product): Result<Product>
    suspend fun removeProduct(listId: String, productId: String): Result<Unit>
    suspend fun markProductAsPurchased(listId: String, productId: String, purchased: Boolean): Result<Unit>
    suspend fun updateListEmoji(listId: String, emoji: String): Result<Unit>
}
