package com.jarvis.assistant.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val phoneVerificationId: String? = null,
    val phoneHint: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun googleTokenResult(idToken: String?) = run {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val r = repo.firebaseAuthWithGoogle(idToken)
            _state.value = when (r) {
                is AuthRepository.AuthResult.Ok -> AuthUiState(success = true)
                is AuthRepository.AuthResult.Error -> AuthUiState(error = r.message)
                else -> AuthUiState(error = "Unexpected result")
            }
        }
    }

    fun email(email: String, password: String, isNew: Boolean) {
        if (email.isBlank() || password.length < 6) {
            _state.value = AuthUiState(error = "Enter a valid email and a 6+ character password")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = if (isNew) repo.createEmail(email, password) else repo.signInEmail(email, password)
            _state.value = when (r) {
                is AuthRepository.AuthResult.Ok -> AuthUiState(success = true)
                is AuthRepository.AuthResult.Error -> AuthUiState(error = r.message)
                else -> AuthUiState(error = "Unexpected result")
            }
        }
    }

    fun phone(phoneNumber: String) {
        if (phoneNumber.length < 8) {
            _state.value = AuthUiState(error = "Enter phone with country code, e.g. +919876543210")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repo.sendPhoneOtp(phoneNumber).collect { r ->
                _state.value = when (r) {
                    is AuthRepository.AuthResult.CodeSent ->
                        AuthUiState(phoneVerificationId = r.verificationId, phoneHint = "Code sent to $phoneNumber")
                    is AuthRepository.AuthResult.Ok -> AuthUiState(success = true)
                    is AuthRepository.AuthResult.Error -> AuthUiState(error = r.message)
                }
            }
        }
    }

    fun confirmOtp(code: String) {
        val vid = _state.value.phoneVerificationId ?: return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repo.confirmOtp(vid, code)
            _state.value = when (r) {
                is AuthRepository.AuthResult.Ok -> AuthUiState(success = true)
                is AuthRepository.AuthResult.Error -> AuthUiState(error = r.message)
                else -> AuthUiState(error = "Unexpected result")
            }
        }
    }
}
