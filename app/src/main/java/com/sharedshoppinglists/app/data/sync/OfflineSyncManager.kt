package com.sharedshoppinglists.app.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sharedshoppinglists.app.data.local.dao.ProductDao
import com.sharedshoppinglists.app.data.local.dao.ShoppingListDao
import com.sharedshoppinglists.app.data.local.entity.ProductEntity
import com.sharedshoppinglists.app.data.local.entity.ShoppingListEntity
import com.sharedshoppinglists.app.data.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncManager @Inject constructor(
    private val shoppingListDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val firestore: FirebaseFirestore,
    private val networkMonitor: NetworkMonitor
) {

    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .filter { it }
                .collect { syncPendingChanges() }
        }
    }

    suspend fun syncPendingChanges() {
        try {
            syncPendingLists()
            syncPendingProducts()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing pending changes", e)
        }
    }

    private suspend fun syncPendingLists() {
        val pendingLists = shoppingListDao.getPendingSync()
        for (list in pendingLists) {
            try {
                syncListToFirestore(list)
                shoppingListDao.updatePendingSync(list.id, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync list ${list.id}", e)
            }
        }
    }

    private suspend fun syncPendingProducts() {
        val pendingProducts = productDao.getPendingSync()
        for (product in pendingProducts) {
            try {
                syncProductToFirestore(product)
                productDao.updatePendingSync(product.id, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync product ${product.id}", e)
            }
        }
    }

    private suspend fun syncListToFirestore(list: ShoppingListEntity) {
        val data = mapOf(
            "name" to list.name,
            "ownerId" to list.ownerId,
            "isShared" to list.isShared,
            "createdAt" to list.createdAt,
            "updatedAt" to list.updatedAt
        )
        firestore.collection(LISTS_COLLECTION)
            .document(list.id)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    private suspend fun syncProductToFirestore(product: ProductEntity) {
        val data = mapOf(
            "name" to product.name,
            "quantity" to product.quantity,
            "unit" to product.unit,
            "isPurchased" to product.isPurchased,
            "lastModifiedBy" to product.lastModifiedBy,
            "lastModifiedAt" to product.lastModifiedAt
        )
        firestore.collection(LISTS_COLLECTION)
            .document(product.listId)
            .collection(PRODUCTS_SUBCOLLECTION)
            .document(product.id)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    companion object {
        private const val TAG = "OfflineSyncManager"
        private const val LISTS_COLLECTION = "shoppingLists"
        private const val PRODUCTS_SUBCOLLECTION = "products"
    }
}
