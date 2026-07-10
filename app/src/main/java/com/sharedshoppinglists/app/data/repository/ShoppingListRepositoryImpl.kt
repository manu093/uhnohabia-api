package com.sharedshoppinglists.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.sharedshoppinglists.app.data.local.dao.CustomCategoryDao
import com.sharedshoppinglists.app.data.local.dao.ProductDao
import com.sharedshoppinglists.app.data.local.dao.ShoppingListDao
import com.sharedshoppinglists.app.data.local.entity.CustomCategoryEntity
import com.sharedshoppinglists.app.data.local.entity.ProductEntity
import com.sharedshoppinglists.app.data.local.entity.ShoppingListEntity
import com.sharedshoppinglists.app.data.local.mapper.toDomain
import com.sharedshoppinglists.app.data.local.mapper.toEntity
import com.sharedshoppinglists.app.data.network.NetworkMonitor
import com.sharedshoppinglists.app.domain.model.Product
import com.sharedshoppinglists.app.domain.model.ShoppingList
import com.sharedshoppinglists.app.domain.repository.ShoppingListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class ShoppingListRepositoryImpl @Inject constructor(
    private val shoppingListDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val customCategoryDao: CustomCategoryDao,
    private val firestore: FirebaseFirestore,
    private val networkMonitor: NetworkMonitor,
    private val externalScope: CoroutineScope
) : ShoppingListRepository {

    override suspend fun createList(name: String, ownerId: String, emoji: String): Result<ShoppingList> {
        return try {
            val now = Instant.now()
            val list = ShoppingList(
                id = UUID.randomUUID().toString(),
                name = name,
                ownerId = ownerId,
                members = listOf(ownerId),
                isShared = false,
                emoji = emoji,
                createdAt = now,
                updatedAt = now
            )
            val isOnline = networkMonitor.isCurrentlyOnline()
            shoppingListDao.insert(list.toEntity(pendingSync = !isOnline))

            if (isOnline) {
                syncListToFirestore(list)
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameList(listId: String, newName: String): Result<Unit> {
        return try {
            val entity = shoppingListDao.getById(listId) ?: return Result.failure(Exception("List not found"))
            shoppingListDao.update(entity.copy(name = newName, updatedAt = Instant.now().toEpochMilli()))
            if (networkMonitor.isCurrentlyOnline()) {
                firestore.collection(LISTS_COLLECTION).document(listId)
                    .update(mapOf("name" to newName, "updatedAt" to Instant.now().toEpochMilli()))
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteList(listId: String): Result<Unit> {
        return try {
            // Remove locally
            productDao.deleteByListId(listId)
            shoppingListDao.deleteById(listId)

            if (networkMonitor.isCurrentlyOnline()) {
                val listDoc = firestore.collection(LISTS_COLLECTION).document(listId).get().await()
                if (listDoc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val members = listDoc.get("members") as? List<String> ?: emptyList()
                    val ownerId = listDoc.getString("ownerId") ?: ""

                    if (members.size <= 1) {
                        // Only member or owner alone: delete entirely
                        firestore.collection(LISTS_COLLECTION).document(listId).delete().await()
                    } else {
                        // Shared list: remove this user from members, add notification
                        val currentUserId = members.find { it != ownerId } ?: ownerId
                        // Try to figure out who is deleting based on who's NOT the remaining members
                        // We remove the current user from members
                        val updatedMembers = members.toMutableList()
                        // We need the userId - get from auth
                        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                        val userId = auth.currentUser?.uid ?: ""
                        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                            ?: auth.currentUser?.email?.substringBefore("@")
                            ?: "Alguien"

                        if (userId.isNotBlank()) {
                            updatedMembers.remove(userId)
                            val updates = mutableMapOf<String, Any>(
                                "members" to updatedMembers
                            )
                            if (updatedMembers.size <= 1) {
                                updates["isShared"] = false
                            }
                            firestore.collection(LISTS_COLLECTION).document(listId).update(updates).await()

                            // Create a notification for remaining members
                            val listName = listDoc.getString("name") ?: "una lista"
                            for (memberId in updatedMembers) {
                                firestore.collection("notifications").document().set(
                                    mapOf(
                                        "userId" to memberId,
                                        "message" to "$userName eliminó la lista \"$listName\" de su dispositivo",
                                        "type" to "member_left",
                                        "listId" to listId,
                                        "read" to false,
                                        "createdAt" to System.currentTimeMillis()
                                    )
                                ).await()
                            }
                        }
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getLists(userId: String): Flow<List<ShoppingList>> {
        syncSharedListsFromFirestore(userId)
        return shoppingListDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun syncSharedListsFromFirestore(userId: String) {
        externalScope.launch {
            try {
                if (!networkMonitor.isCurrentlyOnline()) return@launch
                val sharedDocs = firestore.collection(LISTS_COLLECTION)
                    .whereArrayContains("members", userId)
                    .get()
                    .await()

                val remoteIds = mutableSetOf<String>()
                for (doc in sharedDocs.documents) {
                    val id = doc.id
                    remoteIds.add(id)
                    val remoteUpdatedAt = getTimestampMillis(doc, "updatedAt")
                    val existing = shoppingListDao.getById(id)
                    if (existing == null) {
                        shoppingListDao.insert(ShoppingListEntity(
                            id = id,
                            name = doc.getString("name") ?: "",
                            ownerId = doc.getString("ownerId") ?: "",
                            isShared = doc.getBoolean("isShared") ?: false,
                            emoji = doc.getString("emoji") ?: "",
                            createdAt = getTimestampMillis(doc, "createdAt"),
                            updatedAt = remoteUpdatedAt,
                            pendingSync = false
                        ))
                    } else if (!existing.pendingSync && remoteUpdatedAt >= existing.updatedAt) {
                        // Aplica cambios remotos que antes NO bajaban: renombrar, emoji, dejar de compartir.
                        shoppingListDao.update(existing.copy(
                            name = doc.getString("name") ?: existing.name,
                            ownerId = doc.getString("ownerId") ?: existing.ownerId,
                            isShared = doc.getBoolean("isShared") ?: existing.isShared,
                            emoji = doc.getString("emoji") ?: existing.emoji,
                            updatedAt = remoteUpdatedAt
                        ))
                    }
                }

                // Reconciliacion de borrado: una lista COMPARTIDA ya sincronizada que no vuelve
                // en la consulta = te sacaron o la borraron -> borrar local. Conservador: solo
                // shared + no pendingSync, para no tocar listas propias ni creadas offline.
                for (local in shoppingListDao.getAllSync()) {
                    if (local.isShared && !local.pendingSync && local.id !in remoteIds) {
                        productDao.deleteByListId(local.id)
                        shoppingListDao.deleteById(local.id)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun getProducts(listId: String): Flow<List<Product>> {
        syncProductsFromFirestore(listId)
        return productDao.getByListId(listId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun syncProductsFromFirestore(listId: String) {
        externalScope.launch {
            try {
                if (!networkMonitor.isCurrentlyOnline()) return@launch
                val productDocs = firestore.collection(LISTS_COLLECTION)
                    .document(listId)
                    .collection(PRODUCTS_SUBCOLLECTION)
                    .get()
                    .await()

                // Load existing categories to check for new ones
                val existingCategories = customCategoryDao.getAllOnce().map { it.name }.toMutableSet()
                var maxOrder = customCategoryDao.getAllOnce().maxOfOrNull { it.sortOrder } ?: 7

                for (doc in productDocs.documents) {
                    val id = doc.id
                    val categoryName = doc.getString("categoryName") ?: "Otros"
                    val categoryEmoji = doc.getString("categoryEmoji") ?: "📦"
                    val categoryId = doc.getString("categoryId") ?: ""

                    // Auto-create category if it doesn't exist locally
                    if (categoryName.isNotBlank() && categoryName !in existingCategories) {
                        maxOrder++
                        customCategoryDao.insert(CustomCategoryEntity(
                            id = categoryId.ifBlank { UUID.randomUUID().toString() },
                            name = categoryName,
                            emoji = categoryEmoji,
                            sortOrder = maxOrder
                        ))
                        existingCategories.add(categoryName)
                    }

                    val existing = productDao.getById(id)
                    if (existing == null) {
                        val entity = ProductEntity(
                            id = id,
                            listId = listId,
                            name = doc.getString("name") ?: "",
                            quantity = (doc.get("quantity") as? Number)?.toDouble() ?: 1.0,
                            unit = doc.getString("unit") ?: "Unidad",
                            categoryId = categoryId,
                            categoryName = categoryName,
                            categoryEmoji = categoryEmoji,
                            emoji = doc.getString("emoji") ?: "",
                            preferredBrand = doc.getString("preferredBrand") ?: "",
                            isPurchased = doc.getBoolean("isPurchased") ?: false,
                            lastModifiedBy = doc.getString("lastModifiedBy") ?: "",
                            lastModifiedAt = getTimestampMillis(doc, "lastModifiedAt"),
                            pendingSync = false
                        )
                        productDao.insert(entity)
                    } else {
                        val remoteTimestamp = getTimestampMillis(doc, "lastModifiedAt")
                        if (remoteTimestamp > existing.lastModifiedAt) {
                            productDao.update(existing.copy(
                                name = doc.getString("name") ?: existing.name,
                                quantity = (doc.get("quantity") as? Number)?.toDouble() ?: existing.quantity,
                                unit = doc.getString("unit") ?: existing.unit,
                                categoryId = categoryId.ifBlank { existing.categoryId },
                                categoryName = categoryName,
                                categoryEmoji = categoryEmoji,
                                emoji = doc.getString("emoji") ?: existing.emoji,
                                preferredBrand = doc.getString("preferredBrand") ?: existing.preferredBrand,
                                isPurchased = doc.getBoolean("isPurchased") ?: existing.isPurchased,
                                lastModifiedBy = doc.getString("lastModifiedBy") ?: existing.lastModifiedBy,
                                lastModifiedAt = remoteTimestamp,
                                pendingSync = false
                            ))
                        }
                    }
                }

                // Reconciliacion: productos borrados en otro dispositivo -> borrarlos local
                // (solo los ya sincronizados; los agregados offline con pendingSync se respetan).
                val remoteProductIds = productDocs.documents.map { it.id }.toSet()
                for (local in productDao.getByListIdSync(listId)) {
                    if (!local.pendingSync && local.id !in remoteProductIds) {
                        productDao.deleteById(local.id)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override suspend fun addProduct(listId: String, product: Product): Result<Product> {
        return try {
            val newProduct = product.copy(id = UUID.randomUUID().toString())
            val isOnline = networkMonitor.isCurrentlyOnline()
            productDao.insert(newProduct.toEntity(listId, pendingSync = !isOnline))

            updateListTimestamp(listId, !isOnline)

            if (isOnline) {
                syncProductToFirestore(listId, newProduct)
            }

            Result.success(newProduct)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProduct(listId: String, product: Product): Result<Product> {
        return try {
            val updated = product.copy(lastModifiedAt = Instant.now())
            val isOnline = networkMonitor.isCurrentlyOnline()
            productDao.update(updated.toEntity(listId, pendingSync = !isOnline))

            updateListTimestamp(listId, !isOnline)

            if (isOnline) {
                syncProductToFirestore(listId, updated)
            }

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeProduct(listId: String, productId: String): Result<Unit> {
        return try {
            productDao.deleteById(productId)

            updateListTimestamp(listId, !networkMonitor.isCurrentlyOnline())

            if (networkMonitor.isCurrentlyOnline()) {
                firestore.collection(LISTS_COLLECTION)
                    .document(listId)
                    .collection(PRODUCTS_SUBCOLLECTION)
                    .document(productId)
                    .delete()
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markProductAsPurchased(
        listId: String,
        productId: String,
        purchased: Boolean
    ): Result<Unit> {
        return try {
            val now = Instant.now()
            val entity = productDao.getById(productId)
                ?: return Result.failure(Exception("Product not found"))

            productDao.markAsPurchased(
                productId = productId,
                purchased = purchased,
                modifiedBy = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: entity.lastModifiedBy,
                modifiedAt = now.toEpochMilli()
            )

            updateListTimestamp(listId, !networkMonitor.isCurrentlyOnline())

            if (networkMonitor.isCurrentlyOnline()) {
                firestore.collection(LISTS_COLLECTION)
                    .document(listId)
                    .collection(PRODUCTS_SUBCOLLECTION)
                    .document(productId)
                    .update(
                        mapOf(
                            "isPurchased" to purchased,
                            "lastModifiedAt" to now.toEpochMilli()
                        )
                    )
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateListTimestamp(listId: String, pendingSync: Boolean) {
        val listEntity = shoppingListDao.getById(listId) ?: return
        shoppingListDao.update(
            listEntity.copy(
                updatedAt = Instant.now().toEpochMilli(),
                pendingSync = pendingSync
            )
        )
    }

    private suspend fun syncListToFirestore(list: ShoppingList) {
        val data = mapOf(
            "name" to list.name,
            "ownerId" to list.ownerId,
            "members" to list.members,
            "isShared" to list.isShared,
            "emoji" to list.emoji,
            "createdAt" to list.createdAt.toEpochMilli(),
            "updatedAt" to list.updatedAt.toEpochMilli()
        )
        firestore.collection(LISTS_COLLECTION).document(list.id).set(data).await()
    }

    private suspend fun syncProductToFirestore(listId: String, product: Product) {
        val data = mapOf(
            "name" to product.name,
            "quantity" to product.quantity,
            "unit" to product.unit,
            "categoryId" to product.categoryId,
            "categoryName" to product.categoryName,
            "categoryEmoji" to product.categoryEmoji,
            "emoji" to product.emoji,
            "preferredBrand" to product.preferredBrand,
            "isPurchased" to product.isPurchased,
            "lastModifiedBy" to product.lastModifiedBy,
            "lastModifiedAt" to product.lastModifiedAt.toEpochMilli()
        )
        firestore.collection(LISTS_COLLECTION)
            .document(listId)
            .collection(PRODUCTS_SUBCOLLECTION)
            .document(product.id)
            .set(data)
            .await()
    }

    companion object {
        private const val LISTS_COLLECTION = "shoppingLists"
        private const val PRODUCTS_SUBCOLLECTION = "products"

        private fun getTimestampMillis(doc: com.google.firebase.firestore.DocumentSnapshot, field: String): Long {
            return try {
                doc.getLong(field) ?: doc.getTimestamp(field)?.toDate()?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    override suspend fun updateListEmoji(listId: String, emoji: String): Result<Unit> {
        return try {
            val entity = shoppingListDao.getById(listId) ?: return Result.failure(Exception("List not found"))
            shoppingListDao.update(entity.copy(emoji = emoji, updatedAt = Instant.now().toEpochMilli()))
            if (networkMonitor.isCurrentlyOnline()) {
                firestore.collection(LISTS_COLLECTION).document(listId).update("emoji", emoji).await()
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}