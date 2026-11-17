package com.sharedshoppinglists.app.data.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.sharedshoppinglists.app.data.remote.ShareApiClient
import com.sharedshoppinglists.app.domain.model.ChangeType
import com.sharedshoppinglists.app.domain.model.EditingStatus
import com.sharedshoppinglists.app.domain.model.ListChangeEvent
import com.sharedshoppinglists.app.domain.model.PendingInvitation
import com.sharedshoppinglists.app.domain.repository.SharedListRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class SharedListRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val shareApi: ShareApiClient
) : SharedListRepository {

    // Compartir pasa por el backend: el invitado no es miembro todavia y las reglas de
    // Firestore no le dejan leer la lista ni agregarse a members.

    override suspend fun shareList(listId: String, inviteeEmail: String): Result<String> =
        runCatching { shareApi.inviteByEmail(listId, inviteeEmail) }

    override suspend fun shareListByLink(listId: String): Result<String> =
        runCatching { shareApi.createLink(listId) }

    override suspend fun acceptInvitation(invitationId: String): Result<String> =
        runCatching { shareApi.accept(invitationId).listName }

    override suspend fun declineInvitation(invitationId: String): Result<Unit> =
        runCatching { shareApi.decline(invitationId) }

    override suspend fun getPendingInvitations(): Result<List<PendingInvitation>> =
        runCatching { shareApi.pendingInvitations() }

    override fun observeListChanges(listId: String): Flow<ListChangeEvent> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(LISTS_COLLECTION)
            .document(listId)
            .collection(PRODUCTS_SUBCOLLECTION)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    val productId = doc.id
                    val modifiedBy = doc.getString("lastModifiedBy") ?: ""
                    // Puede ser Long (app) o Timestamp (productos viejos escritos por Alexa): getLong
                    // lanza excepcion con un Timestamp, asi que se leen ambos formatos.
                    val timestamp = when (val raw = doc.get("lastModifiedAt")) {
                        is Number -> raw.toLong()
                        is com.google.firebase.Timestamp -> raw.toDate().time
                        else -> System.currentTimeMillis()
                    }

                    val changeType = when (change.type) {
                        DocumentChange.Type.ADDED -> ChangeType.ADDED
                        DocumentChange.Type.MODIFIED -> {
                            val isPurchased = doc.getBoolean("isPurchased") ?: false
                            if (isPurchased) ChangeType.PURCHASED else ChangeType.UPDATED
                        }
                        DocumentChange.Type.REMOVED -> ChangeType.REMOVED
                    }

                    trySend(
                        ListChangeEvent(
                            productId = productId,
                            changeType = changeType,
                            modifiedBy = modifiedBy,
                            timestamp = timestamp
                        )
                    )
                }
            }

        awaitClose { listener.remove() }
    }

    override fun observeEditingStatus(listId: String): Flow<Map<String, EditingStatus>> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection(LISTS_COLLECTION)
            .document(listId)
            .collection(EDITING_STATUS_SUBCOLLECTION)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val statusMap = mutableMapOf<String, EditingStatus>()
                val now = System.currentTimeMillis()

                snapshots?.documents?.forEach { doc ->
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val isEditing = doc.getBoolean("isEditing") ?: false

                    // Apply TTL: ignore entries older than 30 seconds
                    if (isEditing && (now - timestamp) < EDITING_TTL_MS) {
                        statusMap[doc.id] = EditingStatus(
                            productId = doc.id,
                            userId = doc.getString("userId") ?: "",
                            userName = doc.getString("userName") ?: "",
                            isEditing = true
                        )
                    }
                }

                trySend(statusMap)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun setEditingStatus(
        listId: String,
        productId: String,
        userId: String,
        editing: Boolean
    ) {
        val data = mapOf(
            "userId" to userId,
            "userName" to userId, // Will be resolved to display name by the caller/ViewModel
            "isEditing" to editing,
            "timestamp" to System.currentTimeMillis()
        )
        firestore.collection(LISTS_COLLECTION)
            .document(listId)
            .collection(EDITING_STATUS_SUBCOLLECTION)
            .document(productId)
            .set(data)
            .await()
    }

    companion object {
        private const val LISTS_COLLECTION = "shoppingLists"
        private const val PRODUCTS_SUBCOLLECTION = "products"
        private const val INVITATIONS_COLLECTION = "invitations"
        private const val EDITING_STATUS_SUBCOLLECTION = "editingStatus"
        private const val EDITING_TTL_MS = 30_000L
        private const val SHARE_BASE_URL = "https://sharedshoppinglists.app/invite/"
    }
}
