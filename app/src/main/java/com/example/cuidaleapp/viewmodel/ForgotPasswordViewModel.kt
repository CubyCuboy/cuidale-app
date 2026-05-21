package com.example.cuidaleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class ForgotPasswordUiState {
    object Idle : ForgotPasswordUiState()
    object Loading : ForgotPasswordUiState()
    object Success : ForgotPasswordUiState()
    data class Error(val message: String) : ForgotPasswordUiState()
}

class ForgotPasswordViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState

    fun sendResetEmail(email: String) {
        val normalizedEmail = email.lowercase().trim()
        
        if (!isValidEmail(normalizedEmail)) {
            _uiState.value = ForgotPasswordUiState.Error("Formato de correo inválido")
            return
        }

        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                auth.sendPasswordResetEmail(normalizedEmail).await()
                _uiState.value = ForgotPasswordUiState.Success
            } catch (e: FirebaseAuthInvalidUserException) {
                _uiState.value = ForgotPasswordUiState.Error("correo no registrado")
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Error al enviar el correo")
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun clearError() {
        _uiState.value = ForgotPasswordUiState.Idle
    }
}
