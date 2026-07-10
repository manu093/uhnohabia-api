package com.sharedshoppinglists.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sharedshoppinglists.app.data.local.AppDatabase
import com.sharedshoppinglists.app.data.network.ConnectivityNetworkMonitor
import com.google.firebase.firestore.FirebaseFirestore

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val firestore = FirebaseFirestore.getInstance()
            val networkMonitor = ConnectivityNetworkMonitor(applicationContext)
            val syncManager = OfflineSyncManager(
                db.shoppingListDao(), db.productDao(), firestore, networkMonitor
            )
            syncManager.syncPendingChanges()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "sync_pending_changes"
    }
}