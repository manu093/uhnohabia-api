package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.EditingStatus
import com.sharedshoppinglists.app.domain.model.ListChangeEvent
import com.sharedshoppinglists.app.domain.model.PendingInvitation
import kotlinx.coroutines.flow.Flow

interface SharedListRepository {
    suspend fun shareList(listId: String, inviteeEmail: String): Result<Unit>
    suspend fun shareListByLink(listId: String): Result<String>
    suspend fun acceptInvitation(invitationId: String, userId: String): Result<Unit>
    suspend fun declineInvitation(invitationId: String): Result<Unit>
    suspend fun getPendingInvitations(email: String): List<PendingInvitation>
    fun observeListChanges(listId: String): Flow<ListChangeEvent>
    fun observeEditingStatus(listId: String): Flow<Map<String, EditingStatus>>
    suspend fun setEditingStatus(listId: String, productId: String, userId: String, editing: Boolean)
}
