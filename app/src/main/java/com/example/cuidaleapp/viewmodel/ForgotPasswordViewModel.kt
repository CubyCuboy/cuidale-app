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

enum class ForgotPasswordStep {
    EMAIL, CODE, PASSWORD, SUCCESS
}

sealed class ForgotPasswordStatus {
    object Idle : ForgotPasswordStatus()
    object Loading : ForgotPasswordStatus()
    data class Error(val message: String) : ForgotPasswordStatus()
}

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val functions = FirebaseFunctions.getInstance("us-central1")
    
    private val _currentStep = MutableStateFlow(ForgotPasswordStep.EMAIL)
    val currentStep: StateFlow<ForgotPasswordStep> = _currentStep

    private val _status = MutableStateFlow<ForgotPasswordStatus>(ForgotPasswordStatus.Idle)
    val status: StateFlow<ForgotPasswordStatus> = _status

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _lastRequestedEmail = MutableStateFlow("")
    val lastRequestedEmail: StateFlow<String> = _lastRequestedEmail

    private val _verifiedCode = MutableStateFlow("") 
    val verifiedCode: StateFlow<String> = _verifiedCode

    private val _timerSeconds = MutableStateFlow(300) 
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
            _status.value = ForgotPasswordStatus.Error("Formato de correo inválido")
            return
        }

        viewModelScope.launch {
            _status.value = ForgotPasswordStatus.Loading

            try {
                val data = hashMapOf("email" to normalizedEmail)
                functions.getHttpsCallable("sendVerificationCode").call(data).await()

                _lastRequestedEmail.value = normalizedEmail
                startTimer()
                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.CODE
            } catch (e: Exception) {
                _status.value = ForgotPasswordStatus.Error(e.message ?: "Error al enviar el código")
            }
        }
    }

    fun verifyCode(code: String) {
        if (code.isBlank()) {
            _status.value = ForgotPasswordStatus.Error("Ingresa el código")
            return
        }

        viewModelScope.launch {
            _status.value = ForgotPasswordStatus.Loading
            try {
                val data = hashMapOf(
                    "email" to _lastRequestedEmail.value,
                    "code" to code
                )
                functions.getHttpsCallable("verifyCode").call(data).await()
                
                _verifiedCode.value = code
                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.PASSWORD
            } catch (e: Exception) {
                _status.value = ForgotPasswordStatus.Error(e.message ?: "Código inválido")
            }
        }
    }

    fun updatePassword(password: String) {
        viewModelScope.launch {
            _status.value = ForgotPasswordStatus.Loading
            try {
                val data = hashMapOf(
                    "email" to _lastRequestedEmail.value,
                    "code" to _verifiedCode.value,
                    "newPassword" to password
                )
                functions.getHttpsCallable("updatePassword").call(data).await()
                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.SUCCESS
            } catch (e: Exception) {
                _status.value = ForgotPasswordStatus.Error(e.message ?: "Error al actualizar la contraseña")
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

    fun goToStep(step: ForgotPasswordStep) {
        _currentStep.value = step
    }

    fun clearError() {
        if (_status.value is ForgotPasswordStatus.Error) {
            _status.value = ForgotPasswordStatus.Idle
        }
    }

    fun resetToStart() {
        _lastRequestedEmail.value = ""
        _verifiedCode.value = ""
        timerJob?.cancel()
        _status.value = ForgotPasswordStatus.Idle
        _currentStep.value = ForgotPasswordStep.EMAIL
    }
}
