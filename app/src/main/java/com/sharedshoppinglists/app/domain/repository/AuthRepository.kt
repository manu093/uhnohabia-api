package com.sharedshoppinglists.app.domain.repository

import com.sharedshoppinglists.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun registerWithEmail(email: String, password: String): Result<User>
    suspend fun loginWithEmail(email: String, password: String): Result<User>
    suspend fun loginWithGoogle(idToken: String): Result<User>
    suspend fun logout()
    suspend fun sendPasswordResetEmail(email: String)
    fun getCurrentUser(): Flow<User?>
}
