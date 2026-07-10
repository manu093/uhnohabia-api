package com.sharedshoppinglists.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.sharedshoppinglists.app.data.local.AppDatabase
import com.sharedshoppinglists.app.domain.model.AuthProvider
import com.sharedshoppinglists.app.domain.model.User
import com.sharedshoppinglists.app.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val appDatabase: AppDatabase
) : AuthRepository {

    override suspend fun registerWithEmail(email: String, password: String): Result<User> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
                ?: return Result.failure(Exception(GENERIC_AUTH_ERROR))
            Result.success(firebaseUser.toDomainUser(AuthProvider.EMAIL))
        } catch (e: FirebaseAuthException) {
            val msg = when (e.errorCode) {
                "ERROR_WEAK_PASSWORD" -> "La contraseña debe tener al menos 6 caracteres."
                "ERROR_INVALID_EMAIL" -> "El correo electrónico no es válido."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con este correo."
                else -> e.localizedMessage ?: GENERIC_AUTH_ERROR
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: GENERIC_AUTH_ERROR))
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): Result<User> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
                ?: return Result.failure(Exception(GENERIC_AUTH_ERROR))
            Result.success(firebaseUser.toDomainUser(AuthProvider.EMAIL))
        } catch (e: FirebaseAuthException) {
            val msg = when (e.errorCode) {
                "ERROR_USER_NOT_FOUND" -> "No existe una cuenta con este correo."
                "ERROR_WRONG_PASSWORD" -> "La contraseña es incorrecta."
                "ERROR_INVALID_EMAIL" -> "El correo electrónico no es válido."
                "ERROR_USER_DISABLED" -> "Esta cuenta fue deshabilitada."
                "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Intentá más tarde."
                else -> e.localizedMessage ?: GENERIC_AUTH_ERROR
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: GENERIC_AUTH_ERROR))
        }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = result.user
                ?: return Result.failure(Exception(GENERIC_AUTH_ERROR))
            Result.success(firebaseUser.toDomainUser(AuthProvider.GOOGLE))
        } catch (e: FirebaseAuthException) {
            Result.failure(Exception(GENERIC_AUTH_ERROR))
        } catch (e: Exception) {
            Result.failure(Exception(GENERIC_AUTH_ERROR))
        }
    }

    override suspend fun logout() {
        firebaseAuth.signOut()
        appDatabase.clearAllTables()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toDomainUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    private fun FirebaseUser.toDomainUser(
        provider: AuthProvider? = null
    ): User {
        val authProvider = provider ?: when {
            providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProvider.GOOGLE
            else -> AuthProvider.EMAIL
        }
        return User(
            id = uid,
            email = email.orEmpty(),
            displayName = displayName.orEmpty(),
            authProvider = authProvider
        )
    }

    companion object {
        internal const val GENERIC_AUTH_ERROR = "Las credenciales proporcionadas no son válidas. Por favor, intente nuevamente."
    }
}
