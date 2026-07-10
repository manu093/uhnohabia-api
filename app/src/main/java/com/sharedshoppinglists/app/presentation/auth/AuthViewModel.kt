package com.sharedshoppinglists.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser = authRepository.getCurrentUser()

    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.loginWithEmail(email, password)
                .onSuccess { user -> _authState.value = AuthState.Success(user) }
                .onFailure { error ->
                    _authState.value = AuthState.Error(
                        error.message ?: GENERIC_ERROR
                    )
                }
        }
    }

    fun register(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.registerWithEmail(email, password)
                .onSuccess { user -> _authState.value = AuthState.Success(user) }
                .onFailure { error ->
                    _authState.value = AuthState.Error(
                        error.message ?: GENERIC_ERROR
                    )
                }
        }
    }

    fun loginWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.loginWithGoogle(idToken)
                .onSuccess { user -> _authState.value = AuthState.Success(user) }
                .onFailure { error ->
                    _authState.value = AuthState.Error(
                        error.message ?: GENERIC_ERROR
                    )
                }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            try {
                authRepository.sendPasswordResetEmail(email)
            } catch (_: Exception) { }
        }
    }

    companion object {
        internal const val GENERIC_ERROR =
            "Las credenciales proporcionadas no son válidas. Por favor, intente nuevamente."
    }
}
