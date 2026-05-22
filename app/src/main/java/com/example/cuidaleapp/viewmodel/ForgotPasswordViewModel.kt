package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class ForgotPasswordUiState {
    object EnteringEmail : ForgotPasswordUiState()
    object EnteringCode : ForgotPasswordUiState()
    object EnteringNewPassword : ForgotPasswordUiState()
    object Success : ForgotPasswordUiState()
    object Loading : ForgotPasswordUiState()
    data class Error(val message: String) : ForgotPasswordUiState()
}

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val functions = FirebaseFunctions.getInstance("us-central1")
    
    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.EnteringEmail)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _lastRequestedEmail = MutableStateFlow("")
    val lastRequestedEmail: StateFlow<String> = _lastRequestedEmail

    private val _verifiedCode = MutableStateFlow("") // Stores the code after verification to use it in updatePassword
    val verifiedCode: StateFlow<String> = _verifiedCode

    private val _timerSeconds = MutableStateFlow(300) // 5 minutes
    val timerSeconds: StateFlow<Int> = _timerSeconds

    private var timerJob: Job? = null

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun sendVerificationCode(email: String) {
        val normalizedEmail = email.lowercase().trim()

        if (!isValidEmail(normalizedEmail)) {
            _uiState.value = ForgotPasswordUiState.Error("Formato de correo inválido")
            return
        }

        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading

            try {
                val data = hashMapOf("email" to normalizedEmail)
                functions.getHttpsCallable("sendVerificationCode").call(data).await()

                _lastRequestedEmail.value = normalizedEmail
                startTimer()
                _uiState.value = ForgotPasswordUiState.EnteringCode
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Error al enviar el código")
            }
        }
    }

    fun verifyCode(code: String) {
        if (code.isBlank()) {
            _uiState.value = ForgotPasswordUiState.Error("Ingresa el código")
            return
        }

        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                val data = hashMapOf(
                    "email" to _lastRequestedEmail.value,
                    "code" to code
                )
                functions.getHttpsCallable("verifyCode").call(data).await()
                
                _verifiedCode.value = code
                _uiState.value = ForgotPasswordUiState.EnteringNewPassword
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Código inválido")
            }
        }
    }

    fun updatePassword(password: String) {
        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState.Loading
            try {
                val data = hashMapOf(
                    "email" to _lastRequestedEmail.value,
                    "code" to _verifiedCode.value,
                    "newPassword" to password
                )
                functions.getHttpsCallable("updatePassword").call(data).await()
                _uiState.value = ForgotPasswordUiState.Success
            } catch (e: Exception) {
                _uiState.value = ForgotPasswordUiState.Error(e.message ?: "Error al actualizar la contraseña")
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _timerSeconds.value = 300
        timerJob = viewModelScope.launch {
            while (_timerSeconds.value > 0) {
                delay(1000)
                _timerSeconds.value -= 1
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun goToStep(state: ForgotPasswordUiState) {
        _uiState.value = state
    }

    fun clearError() {
        if (_uiState.value is ForgotPasswordUiState.Error) {
            // Logic to go back to the appropriate interactive state
            val lastStep = when {
                _verifiedCode.value.isNotEmpty() -> ForgotPasswordUiState.EnteringNewPassword
                _lastRequestedEmail.value.isNotEmpty() -> ForgotPasswordUiState.EnteringCode
                else -> ForgotPasswordUiState.EnteringEmail
            }
            _uiState.value = lastStep
        }
    }

    fun resetToStart() {
        _lastRequestedEmail.value = ""
        _verifiedCode.value = ""
        timerJob?.cancel()
        _uiState.value = ForgotPasswordUiState.EnteringEmail
    }
}
