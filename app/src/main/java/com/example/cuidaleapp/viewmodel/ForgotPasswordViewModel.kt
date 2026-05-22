package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
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

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

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
