package com.sharedshoppinglists.app.data.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private val firestore: FirebaseFirestore
) : SharedListRepository {

    override suspend fun shareList(listId: String, inviteeEmail: String): Result<Unit> {
        return try {
            val invitationId = UUID.randomUUID().toString()
            val invitation = mapOf(
                "listId" to listId,
                "inviteeEmail" to inviteeEmail,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection(INVITATIONS_COLLECTION)
                .document(invitationId)
                .set(invitation)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun shareListByLink(listId: String): Result<String> {
        return try {
            val linkId = UUID.randomUUID().toString()
            val linkData = mapOf(
                "listId" to listId,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection(INVITATIONS_COLLECTION)
                .document(linkId)
                .set(linkData)
                .await()
            val shareUrl = "$SHARE_BASE_URL$linkId"
            Result.success(shareUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptInvitation(invitationId: String, userId: String): Result<Unit> {
        return try {
            val invitationDoc = firestore.collection(INVITATIONS_COLLECTION)
                .document(invitationId)
                .get()
                .await()

            if (!invitationDoc.exists()) {
                return Result.failure(Exception("Invitation not found"))
            }

            val listId = invitationDoc.getString("listId")
                ?: return Result.failure(Exception("Invalid invitation: missing listId"))

            firestore.runTransaction { transaction ->
                val listRef = firestore.collection(LISTS_COLLECTION).document(listId)
                val listSnapshot = transaction.get(listRef)

                @Suppress("UNCHECKED_CAST")
                val currentMembers = listSnapshot.get("members") as? List<String> ?: emptyList()

                if (!currentMembers.contains(userId)) {
                    transaction.update(listRef, mapOf(
                        "members" to currentMembers + userId,
                        "isShared" to true
                    ))
                }

                val invitationRef = firestore.collection(INVITATIONS_COLLECTION).document(invitationId)
                transaction.update(invitationRef, "status", "accepted")
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineInvitation(invitationId: String): Result<Unit> {
        return try {
            firestore.collection(INVITATIONS_COLLECTION)
                .document(invitationId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPendingInvitations(email: String): List<PendingInvitation> {
        return try {
            val invitationDocs = firestore.collection(INVITATIONS_COLLECTION)
                .whereEqualTo("inviteeEmail", email)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            invitationDocs.documents.mapNotNull { doc ->
                val listId = doc.getString("listId") ?: return@mapNotNull null
                val inviteeEmail = doc.getString("inviteeEmail") ?: return@mapNotNull null

                val listDoc = firestore.collection(LISTS_COLLECTION)
                    .document(listId)
                    .get()
                    .await()
                val listName = listDoc.getString("name") ?: "Lista sin nombre"

                PendingInvitation(
                    id = doc.id,
                    listId = listId,
                    listName = listName,
                    inviteeEmail = inviteeEmail
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

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
                    val timestamp = doc.getLong("lastModifiedAt") ?: System.currentTimeMillis()

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
