package com.example.cuidaleapp.viewmodel

import android.os.CountDownTimer
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import kotlinx.coroutines.delay

sealed class RecoveryUiState {
    object Idle : RecoveryUiState()
    object Loading : RecoveryUiState()
    object CodeSent : RecoveryUiState()
    object CodeVerified : RecoveryUiState()
    object Success : RecoveryUiState()
    data class Error(val message: String) : RecoveryUiState()
}

class RecoveryViewModel : ViewModel() {
    private val functions = FirebaseFunctions.getInstance("southamerica-west1")

    private val _uiState = MutableStateFlow<RecoveryUiState>(RecoveryUiState.Idle)
    val uiState: StateFlow<RecoveryUiState> = _uiState

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code

    private val _timerText = mutableStateOf("05:00")
    val timerText: State<String> = _timerText

    private val _attemptsRemaining = mutableStateOf(5)
    val attemptsRemaining: State<Int> = _attemptsRemaining

    private var countDownTimer: CountDownTimer? = null

    fun onEmailChange(newEmail: String) {
        _email.value = newEmail
        if (_uiState.value is RecoveryUiState.Error) {
            _uiState.value = RecoveryUiState.Idle
        }
    }

    fun onCodeChange(newCode: String) {
        if (newCode.length <= 6) {
            _code.value = newCode
            // Limpiar error inmediatamente al escribir
            if (_uiState.value is RecoveryUiState.Error) {
                _uiState.value = RecoveryUiState.CodeSent
            }
        }
    }

    fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun sendRecoveryCode() {
        val emailValue = _email.value.trim().lowercase()
        if (!isEmailValid(emailValue)) {
            viewModelScope.launch {
                _uiState.value = RecoveryUiState.Error("Correo electrónico no válido")
                delay(3000)
                if (_uiState.value is RecoveryUiState.Error) {
                    _uiState.value = RecoveryUiState.Idle
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = RecoveryUiState.Loading
            try {
                val data = hashMapOf("email" to emailValue)
                val result = functions
                    .getHttpsCallable("sendRecoveryCode")
                    .call(data)
                    .await()
                
                // Reiniciar timer
                startTimer(5 * 60 * 1000)
                _attemptsRemaining.value = 5
                _uiState.value = RecoveryUiState.CodeSent
            } catch (e: Exception) {
                val message = if (e is FirebaseFunctionsException) e.message else e.localizedMessage
                _uiState.value = RecoveryUiState.Error(message ?: "Error al enviar código")
                delay(3000)
                if (_uiState.value is RecoveryUiState.Error) {
                    _uiState.value = RecoveryUiState.Idle
                }
            }
        }
    }

    fun verifyCode() {
        val emailValue = _email.value.trim().lowercase()
        val codeValue = _code.value.trim()

        if (codeValue.length != 6) {
            viewModelScope.launch {
                _uiState.value = RecoveryUiState.Error("El código debe ser de 6 dígitos")
                delay(3000)
                if (_uiState.value is RecoveryUiState.Error) {
                    _uiState.value = RecoveryUiState.CodeSent
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = RecoveryUiState.Loading
            try {
                val data = hashMapOf("email" to emailValue, "code" to codeValue)
                functions
                    .getHttpsCallable("verifyRecoveryCode")
                    .call(data)
                    .await()
                
                stopTimer()
                _uiState.value = RecoveryUiState.CodeVerified
            } catch (e: Exception) {
                if (e is FirebaseFunctionsException) {
                    val remaining = e.details?.let { (it as Map<*, *>)["remaining"] as? Int }
                    if (remaining != null) {
                        _attemptsRemaining.value = remaining
                    }
                    _uiState.value = RecoveryUiState.Error(e.message ?: "Código incorrecto")
                } else {
                    _uiState.value = RecoveryUiState.Error(e.localizedMessage ?: "Error de verificación")
                }
                delay(3000)
                if (_uiState.value is RecoveryUiState.Error) {
                    _uiState.value = RecoveryUiState.CodeSent // Volver al estado previo al error
                }
            }
        }
    }

    fun updatePassword(newPassword: String) {
        val emailValue = _email.value.trim().lowercase()

        viewModelScope.launch {
            _uiState.value = RecoveryUiState.Loading
            try {
                val data = hashMapOf("email" to emailValue, "newPassword" to newPassword)
                functions
                    .getHttpsCallable("confirmPasswordReset")
                    .call(data)
                    .await()
                
                _uiState.value = RecoveryUiState.Success
            } catch (e: Exception) {
                val message = if (e is FirebaseFunctionsException) e.message else e.localizedMessage
                _uiState.value = RecoveryUiState.Error(message ?: "Error al actualizar contraseña")
                delay(3000)
                if (_uiState.value is RecoveryUiState.Error) {
                    _uiState.value = RecoveryUiState.CodeVerified
                }
            }
        }
    }

    private fun startTimer(millis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                _timerText.value = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                _timerText.value = "00:00"
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
    
    fun clearError() {
        if (_uiState.value is RecoveryUiState.Error) {
            _uiState.value = RecoveryUiState.Idle
        }
    }
}
