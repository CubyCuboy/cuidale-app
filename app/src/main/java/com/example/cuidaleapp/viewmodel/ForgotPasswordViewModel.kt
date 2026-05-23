package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
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

    companion object {
        private const val TIMER_SECONDS = 300
        private const val MAX_ATTEMPTS = 5
    }

    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val functions = FirebaseFunctions.getInstance("southamerica-west1")
    private val firestore = FirebaseFirestore.getInstance()

    private val _currentStep = MutableStateFlow(ForgotPasswordStep.EMAIL)
    val currentStep: StateFlow<ForgotPasswordStep> = _currentStep

    private val _status = MutableStateFlow<ForgotPasswordStatus>(ForgotPasswordStatus.Idle)
    val status: StateFlow<ForgotPasswordStatus> = _status

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _lastRequestedEmail = MutableStateFlow("")
    val lastRequestedEmail: StateFlow<String> = _lastRequestedEmail

    private val _timerSeconds = MutableStateFlow(TIMER_SECONDS)
    val timerSeconds: StateFlow<Int> = _timerSeconds

    private val _unmetRequirementsTrigger = MutableStateFlow<Set<Int>>(emptySet())
    val unmetRequirementsTrigger: StateFlow<Set<Int>> = _unmetRequirementsTrigger

    private var timerJob: Job? = null

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun sendVerificationCode(correo: String) {
        val normalizedEmail = correo.lowercase().trim()

        if (!isValidEmail(normalizedEmail)) {
            _status.value = ForgotPasswordStatus.Error(
                "Por favor, ingrese un correo electrónico válido."
            )
            return
        }

        viewModelScope.launch {
            _status.value = ForgotPasswordStatus.Loading

            try {
                val exists = isRegisteredEmail(normalizedEmail)
                if (!exists) {
                    _status.value = ForgotPasswordStatus.Error(
                        "La dirección de correo electrónico proporcionada no se encuentra registrada en nuestro sistema."
                    )
                    return@launch
                }

                val data = hashMapOf("email" to normalizedEmail)
                functions.getHttpsCallable("sendVerificationCode").call(data).await()

                _lastRequestedEmail.value = normalizedEmail
                _timerSeconds.value = TIMER_SECONDS
                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.CODE
                startTimer()
            } catch (e: Exception) {
                _status.value = ForgotPasswordStatus.Error(mapSendCodeError(e))
            }
        }
    }

    fun verifyCode(code: String) {
        val trimmedCode = code.trim()

        if (trimmedCode.length != 6 || !trimmedCode.all { it.isDigit() }) {
            _status.value = ForgotPasswordStatus.Error("El código de verificación debe constar de 6 dígitos numéricos.")
            return
        }

        if (_lastRequestedEmail.value.isBlank()) {
            handleFatalError("La sesión de recuperación ha expirado. Por favor, reinicie el proceso desde el inicio.")
            return
        }

        viewModelScope.launch {
            _status.value = ForgotPasswordStatus.Loading

            try {
                val data = hashMapOf(
                    "email" to _lastRequestedEmail.value,
                    "code" to trimmedCode
                )

                functions.getHttpsCallable("verifyCode").call(data).await()

                timerJob?.cancel()
                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.PASSWORD
            } catch (e: Exception) {
                handleVerifyCodeError(e)
            }
        }
    }

    fun updatePassword(password: String, confirm: String) {
        val hasMinLength = password.length >= 8
        val hasUppercase = password.any { it.isUpperCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }
        val match = password.isNotEmpty() && password == confirm

        val unmet = mutableSetOf<Int>()
        if (!hasMinLength) unmet.add(0)
        if (!hasUppercase) unmet.add(1)
        if (!hasSpecialChar) unmet.add(2)
        if (!match) unmet.add(3)

        if (unmet.isNotEmpty()) {
            viewModelScope.launch {
                _unmetRequirementsTrigger.value = unmet
                if (!match && unmet.size == 1) {
                    _status.value = ForgotPasswordStatus.Error("Las contraseñas ingresadas no coinciden. Por favor, verifíquelas e intente nuevamente.")
                }
                delay(2000)
                _unmetRequirementsTrigger.value = emptySet()
            }
            return
        }

        if (_lastRequestedEmail.value.isBlank()) {
            handleFatalError("La sesión de recuperación es inválida. Por favor, reinicie el proceso.")
            return
        }

        viewModelScope.launch {
            _status.value = ForgotPasswordStatus.Loading

            try {
                val data = hashMapOf(
                    "email" to _lastRequestedEmail.value,
                    "newPassword" to password
                )

                functions.getHttpsCallable("updatePassword").call(data).await()

                timerJob?.cancel()
                _timerSeconds.value = 0
                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.SUCCESS
            } catch (e: Exception) {
                val msg = mapUpdatePasswordError(e)
                if (
                    msg.contains("reiniciando", ignoreCase = true) ||
                    msg.contains("inválida", ignoreCase = true) ||
                    msg.contains("expirado", ignoreCase = true) ||
                    msg.contains("caducado", ignoreCase = true)
                ) {
                    handleFatalError(msg)
                } else {
                    _status.value = ForgotPasswordStatus.Error(msg)
                }
            }
        }
    }

    private suspend fun isRegisteredEmail(correo: String): Boolean {
        val result = firestore.collection("usuarios")
            .whereEqualTo("correo", correo)
            .limit(1)
            .get()
            .await()

        return !result.isEmpty
    }

    private fun handleVerifyCodeError(e: Exception) {
        var friendlyMsg = "No se pudo verificar el código ingresado. Por favor, inténtelo de nuevo."
        var isFatal = false

        if (e is FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.NOT_FOUND -> {
                    isFatal = true
                    friendlyMsg = "No se ha encontrado un código activo. Por favor, solicite uno nuevo."
                }

                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                    isFatal = true
                    friendlyMsg = when {
                        containsText(e, "expirado") ->
                            "El código ha expirado. Por favor, solicite uno nuevo para continuar."
                        containsText(e, "máximos") ->
                            "Se ha alcanzado el límite máximo de intentos fallidos. Por seguridad, el proceso debe reiniciarse."
                        else ->
                            "La sesión de verificación ya no es válida. Por favor, inicie el proceso nuevamente."
                    }
                }

                FirebaseFunctionsException.Code.PERMISSION_DENIED -> {
                    friendlyMsg = "El código ingresado es incorrecto. Por favor, verifíquelo e intente nuevamente."
                }

                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {
                    friendlyMsg = "El código debe constar de 6 dígitos numéricos. Por favor, corríjalo."
                }

                else -> {
                    friendlyMsg = "Ha ocurrido un error inesperado al verificar el código. Por favor, intente más tarde."
                }
            }
        } else {
            friendlyMsg = "Error de conexión con el servidor. Por favor, compruebe su conexión a internet."
        }

        if (isFatal) {
            handleFatalError(friendlyMsg)
        } else {
            _status.value = ForgotPasswordStatus.Error(friendlyMsg)
        }
    }

    private fun mapSendCodeError(e: Exception): String {
        return if (e is FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    "La solicitud es inválida. Por favor, revise los datos ingresados."
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    "El servicio no se encuentra disponible actualmente."
                FirebaseFunctionsException.Code.INTERNAL ->
                    "No se pudo enviar el correo de recuperación. Intente nuevamente."
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                    "Se han realizado demasiados intentos. Por favor, intente más tarde."
                FirebaseFunctionsException.Code.UNAVAILABLE ->
                    "El servicio de recuperación no está disponible en este momento."
                else ->
                    "No se pudo iniciar el proceso de recuperación de contraseña."
            }
        } else {
            "Error de conexión al servidor"
        }
    }

    private fun mapUpdatePasswordError(e: Exception): String {
        return if (e is FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    "La contraseña proporcionada no cumple con los requisitos mínimos de seguridad."
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    "La sesión de recuperación no es válida. Por favor, reinicie el proceso."
                FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                    "El código ha expirado. Por favor, inicie el proceso de recuperación nuevamente."
                FirebaseFunctionsException.Code.INTERNAL ->
                    "No se ha podido actualizar la contraseña debido a un error interno. Por favor, intente nuevamente."
                FirebaseFunctionsException.Code.UNAVAILABLE ->
                    "El servicio no se encuentra disponible en este momento. Por favor, intente más tarde."
                else ->
                    "Ha ocurrido un error al intentar actualizar su contraseña."
            }
        } else {
            "Error de conexión con el servidor. Por favor, verifique su conexión."
        }
    }

    private fun containsText(e: FirebaseFunctionsException, text: String): Boolean {
        val message = e.message ?: ""
        return message.contains(text, ignoreCase = true)
    }

    private fun handleFatalError(message: String) {
        viewModelScope.launch {
            _status.value = ForgotPasswordStatus.Error(message)
            delay(2500)
            resetToStart()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _timerSeconds.value = TIMER_SECONDS

        timerJob = viewModelScope.launch {
            while (_timerSeconds.value > 0) {
                delay(1000)
                _timerSeconds.value -= 1
            }

            if (_currentStep.value == ForgotPasswordStep.CODE) {
                cancelCurrentFlow()
                handleFatalError("El tiempo ha expirado. El código ya no es válido.")
            }
        }
    }

    private fun cancelCurrentFlow() {
        val email = _lastRequestedEmail.value
        if (email.isNotBlank()) {
            viewModelScope.launch {
                try {
                    functions.getHttpsCallable("cancelVerification")
                        .call(hashMapOf("email" to email))
                        .await()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                email.contains(".")
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
        cancelCurrentFlow()
        timerJob?.cancel()
        _lastRequestedEmail.value = ""
        _timerSeconds.value = TIMER_SECONDS
        _unmetRequirementsTrigger.value = emptySet()
        _status.value = ForgotPasswordStatus.Idle
        _currentStep.value = ForgotPasswordStep.EMAIL
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}