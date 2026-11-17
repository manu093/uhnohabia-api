package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.EditingStatus
import com.sharedshoppinglists.app.domain.model.ListChangeEvent
import com.sharedshoppinglists.app.domain.model.PendingInvitation
import kotlinx.coroutines.flow.Flow

interface SharedListRepository {
    /** Invita por email. Devuelve el mensaje para mostrar. */
    suspend fun shareList(listId: String, inviteeEmail: String): Result<String>
    /** Genera un link de invitacion reutilizable (vence a los 30 dias). */
    suspend fun shareListByLink(listId: String): Result<String>
    /** Acepta una invitacion (de email o de link). Devuelve el nombre de la lista. */
    suspend fun acceptInvitation(invitationId: String): Result<String>
    suspend fun declineInvitation(invitationId: String): Result<Unit>
    /** Invitaciones pendientes para el email del usuario logueado. */
    suspend fun getPendingInvitations(): Result<List<PendingInvitation>>
    fun observeListChanges(listId: String): Flow<ListChangeEvent>
    fun observeEditingStatus(listId: String): Flow<Map<String, EditingStatus>>
    suspend fun setEditingStatus(listId: String, productId: String, userId: String, editing: Boolean)
}
