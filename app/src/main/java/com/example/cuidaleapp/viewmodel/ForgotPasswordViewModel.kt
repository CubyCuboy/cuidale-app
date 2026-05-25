package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
    data class Unverified(val email: String) : ForgotPasswordStatus()
}

data class ForgotVerificationUiState(
    val attemptsUsed: Int = 0,
    val maxAttempts: Int = 5,
    val remainingAttempts: Int = 5,
    val verified: Boolean = false,
    val codeExists: Boolean = false,
    val createdAtMillis: Long? = null,
    val expiresAtMillis: Long? = null,
    val remainingSeconds: Int = 0,
    val isExpired: Boolean = false
)

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TIMER_SECONDS = 300
        private const val MAX_ATTEMPTS = 5
    }

    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val functions = FirebaseFunctions.getInstance("southamerica-west1")
    private val firestore = FirebaseFirestore.getInstance()

    private val _currentStep = MutableStateFlow(loadSavedStep())
    val currentStep: StateFlow<ForgotPasswordStep> = _currentStep

    private val _status = MutableStateFlow<ForgotPasswordStatus>(ForgotPasswordStatus.Idle)
    val status: StateFlow<ForgotPasswordStatus> = _status

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _lastRequestedEmail = MutableStateFlow(
        prefs.getString("forgot_email", "")?.trim()?.lowercase() ?: ""
    )
    val lastRequestedEmail: StateFlow<String> = _lastRequestedEmail

    private val _timerSeconds = MutableStateFlow(TIMER_SECONDS)
    val timerSeconds: StateFlow<Int> = _timerSeconds

    private val _verificationState = MutableStateFlow(loadVerificationCache())
    val verificationState: StateFlow<ForgotVerificationUiState> = _verificationState

    private val _unmetRequirementsTrigger = MutableStateFlow<Set<Int>>(emptySet())
    val unmetRequirementsTrigger: StateFlow<Set<Int>> = _unmetRequirementsTrigger

    private var timerJob: Job? = null

    init {
        if (_currentStep.value == ForgotPasswordStep.CODE || _currentStep.value == ForgotPasswordStep.PASSWORD) {
            bootstrapVerificationState()
        }
    }

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
                val userQuery = firestore.collection("usuarios")
                    .whereEqualTo("correo", normalizedEmail)
                    .get()
                    .await()

                if (userQuery.isEmpty) {
                    _status.value = ForgotPasswordStatus.Error(
                        "La dirección de correo electrónico proporcionada no se encuentra registrada en nuestro sistema."
                    )
                    return@launch
                }

                val userData = userQuery.documents[0]
                val isVerified = userData.getBoolean("verificado") ?: false

                if (!isVerified) {
                    val sendData = hashMapOf(
                        "email" to normalizedEmail,
                        "type" to "verification"
                    )
                    functions.getHttpsCallable("sendVerificationCode").call(sendData).await()

                    _status.value = ForgotPasswordStatus.Unverified(normalizedEmail)
                    return@launch
                }

                val data = hashMapOf(
                    "email" to normalizedEmail,
                    "type" to "recovery"
                )

                val result = functions.getHttpsCallable("sendVerificationCode").call(data).await()
                val resultMap = result.data as? Map<*, *>
                val expiresAtMillis = (resultMap?.get("expiresAt") as? Number)?.toLong()
                    ?: (System.currentTimeMillis() + TIMER_SECONDS * 1000L)
                val maxAttempts = (resultMap?.get("maxAttempts") as? Number)?.toInt() ?: MAX_ATTEMPTS

                _lastRequestedEmail.value = normalizedEmail
                prefs.edit().putString("forgot_email", normalizedEmail).apply()

                val state = ForgotVerificationUiState(
                    attemptsUsed = 0,
                    maxAttempts = maxAttempts,
                    remainingAttempts = maxAttempts,
                    verified = false,
                    codeExists = true,
                    createdAtMillis = System.currentTimeMillis(),
                    expiresAtMillis = expiresAtMillis,
                    remainingSeconds = ((expiresAtMillis - System.currentTimeMillis()) / 1000L)
                        .toInt()
                        .coerceAtLeast(0),
                    isExpired = false
                )

                _verificationState.value = state
                saveVerificationCache(state)

                _timerSeconds.value = state.remainingSeconds
                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.CODE
                saveCurrentStep(ForgotPasswordStep.CODE)
                startTimerFromState(state)
            } catch (e: Exception) {
                _status.value = ForgotPasswordStatus.Error(mapSendCodeError(e))
            }
        }
    }

    fun verifyCode(code: String) {
        val trimmedCode = code.trim()

        if (trimmedCode.length != 6 || !trimmedCode.all { it.isDigit() }) {
            _status.value = ForgotPasswordStatus.Error(
                "El código de verificación debe constar de 6 dígitos numéricos."
            )
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

                val updatedState = _verificationState.value.copy(
                    verified = true
                )
                _verificationState.value = updatedState
                saveVerificationCache(updatedState)

                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.PASSWORD
                saveCurrentStep(ForgotPasswordStep.PASSWORD)

                syncVerificationState()
            } catch (e: Exception) {
                syncVerificationState()
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
                    _status.value = ForgotPasswordStatus.Error(
                        "Las contraseñas ingresadas no coinciden. Por favor, verifíquelas e intente nuevamente."
                    )
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
                clearForgotRecoveryCache()

                _status.value = ForgotPasswordStatus.Idle
                _currentStep.value = ForgotPasswordStep.SUCCESS
                saveCurrentStep(ForgotPasswordStep.SUCCESS)
            } catch (e: Exception) {
                syncVerificationState()
                val msg = mapUpdatePasswordError(e)
                if (
                    msg.contains("reinicie", ignoreCase = true) ||
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

    fun bootstrapVerificationState() {
        val cachedState = loadVerificationCache()
        _verificationState.value = cachedState
        _timerSeconds.value = cachedState.remainingSeconds

        if (cachedState.codeExists && !cachedState.isExpired) {
            startTimerFromState(cachedState)
        } else if (cachedState.isExpired) {
            _timerSeconds.value = 0
        }

        viewModelScope.launch {
            readVerificationFromFirestoreCache()
            readVerificationFromServer()
        }
    }

    private suspend fun readVerificationFromFirestoreCache() {
        val email = _lastRequestedEmail.value
        if (email.isBlank()) return

        try {
            val snapshot = firestore.collection("email_verifications")
                .document(email)
                .get(Source.CACHE)
                .await()

            applyVerificationSnapshot(snapshot.exists(), snapshot.data)
        } catch (_: Exception) {
        }
    }

    private suspend fun readVerificationFromServer() {
        val email = _lastRequestedEmail.value
        if (email.isBlank()) return

        try {
            val snapshot = firestore.collection("email_verifications")
                .document(email)
                .get(Source.SERVER)
                .await()

            applyVerificationSnapshot(snapshot.exists(), snapshot.data)
        } catch (_: Exception) {
        }
    }

    fun syncVerificationState() {
        viewModelScope.launch {
            readVerificationFromFirestoreCache()
            readVerificationFromServer()
        }
    }

    private fun applyVerificationSnapshot(exists: Boolean, data: Map<String, Any>?) {
        if (!exists || data == null) {
            timerJob?.cancel()
            clearForgotVerificationStateOnly()

            if (_currentStep.value == ForgotPasswordStep.CODE || _currentStep.value == ForgotPasswordStep.PASSWORD) {
                _timerSeconds.value = 0
            }
            return
        }

        val attemptsUsed = (data["attempts"] as? Number)?.toInt() ?: 0
        val maxAttempts = (data["maxAttempts"] as? Number)?.toInt() ?: MAX_ATTEMPTS
        val verified = data["verified"] as? Boolean ?: false
        val createdAtMillis = (data["createdAt"] as? Number)?.toLong()
        val expiresAtMillis = (data["expiresAt"] as? Number)?.toLong()

        val remainingSeconds = expiresAtMillis?.let {
            ((it - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        } ?: 0

        val isExpired = expiresAtMillis?.let {
            System.currentTimeMillis() > it
        } ?: false

        val state = ForgotVerificationUiState(
            attemptsUsed = attemptsUsed,
            maxAttempts = maxAttempts,
            remainingAttempts = (maxAttempts - attemptsUsed).coerceAtLeast(0),
            verified = verified,
            codeExists = true,
            createdAtMillis = createdAtMillis,
            expiresAtMillis = expiresAtMillis,
            remainingSeconds = remainingSeconds,
            isExpired = isExpired
        )

        _verificationState.value = state
        saveVerificationCache(state)
        _timerSeconds.value = remainingSeconds

        if (isExpired) {
            timerJob?.cancel()
            _timerSeconds.value = 0
            return
        }

        startTimerFromState(state)
    }

    private fun startTimerFromState(state: ForgotVerificationUiState) {
        val expiresAtMillis = state.expiresAtMillis ?: run {
            _timerSeconds.value = 0
            return
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = ((expiresAtMillis - System.currentTimeMillis()) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)

                _timerSeconds.value = remaining

                if (remaining <= 0) {
                    if (_currentStep.value == ForgotPasswordStep.CODE || _currentStep.value == ForgotPasswordStep.PASSWORD) {
                        clearForgotVerificationStateOnly()
                    }
                    break
                }

                delay(1000)
            }
        }
    }

    private fun handleVerifyCodeError(e: Exception) {
        var friendlyMsg = "No se pudo verificar el código ingresado. Por favor, inténtelo de nuevo."
        var isFatal = false

        if (e is FirebaseFunctionsException) {
            val details = e.details as? Map<*, *>
            val remaining = (details?.get("remaining") as? Number)?.toInt()
            val attempts = (details?.get("attempts") as? Number)?.toInt()

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
                    friendlyMsg = if (remaining != null && attempts != null) {
                        "El código ingresado es incorrecto. Intento $attempts de $MAX_ATTEMPTS. Le quedan $remaining intentos."
                    } else {
                        "El código ingresado es incorrecto. Por favor, verifíquelo e intente nuevamente."
                    }
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

    private fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                email.contains(".")
    }

    fun goToStep(step: ForgotPasswordStep) {
        _currentStep.value = step
        saveCurrentStep(step)

        if (step == ForgotPasswordStep.CODE || step == ForgotPasswordStep.PASSWORD) {
            bootstrapVerificationState()
        }
    }

    fun clearError() {
        if (_status.value is ForgotPasswordStatus.Error) {
            _status.value = ForgotPasswordStatus.Idle
        }
    }

    fun resetToStart() {
        cancelCurrentFlow()
        timerJob?.cancel()
        clearForgotRecoveryCache()
        _unmetRequirementsTrigger.value = emptySet()
        _status.value = ForgotPasswordStatus.Idle
        _currentStep.value = ForgotPasswordStep.EMAIL
        saveCurrentStep(ForgotPasswordStep.EMAIL)
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

    private fun loadSavedStep(): ForgotPasswordStep {
        return when (prefs.getString("forgot_step", "EMAIL")) {
            "CODE" -> ForgotPasswordStep.CODE
            "PASSWORD" -> ForgotPasswordStep.PASSWORD
            "SUCCESS" -> ForgotPasswordStep.SUCCESS
            else -> ForgotPasswordStep.EMAIL
        }
    }

    private fun saveCurrentStep(step: ForgotPasswordStep) {
        prefs.edit().putString("forgot_step", step.name).apply()
    }

    private fun loadVerificationCache(): ForgotVerificationUiState {
        val codeExists = prefs.getBoolean("forgot_code_exists", false)
        val attemptsUsed = prefs.getInt("forgot_attempts_used", 0)
        val maxAttempts = prefs.getInt("forgot_max_attempts", MAX_ATTEMPTS)
        val verified = prefs.getBoolean("forgot_verified", false)
        val createdAt = prefs.getLong("forgot_created_at", 0L).takeIf { it > 0L }
        val expiresAt = prefs.getLong("forgot_expires_at", 0L).takeIf { it > 0L }

        val remainingSeconds = expiresAt?.let {
            ((it - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        } ?: 0

        val isExpired = expiresAt?.let { System.currentTimeMillis() > it } ?: false

        return ForgotVerificationUiState(
            attemptsUsed = attemptsUsed,
            maxAttempts = maxAttempts,
            remainingAttempts = (maxAttempts - attemptsUsed).coerceAtLeast(0),
            verified = verified,
            codeExists = codeExists,
            createdAtMillis = createdAt,
            expiresAtMillis = expiresAt,
            remainingSeconds = remainingSeconds,
            isExpired = isExpired
        )
    }

    private fun saveVerificationCache(state: ForgotVerificationUiState) {
        prefs.edit().apply {
            putBoolean("forgot_code_exists", state.codeExists)
            putInt("forgot_attempts_used", state.attemptsUsed)
            putInt("forgot_max_attempts", state.maxAttempts)
            putBoolean("forgot_verified", state.verified)
            if (state.createdAtMillis != null) putLong("forgot_created_at", state.createdAtMillis)
            else remove("forgot_created_at")
            if (state.expiresAtMillis != null) putLong("forgot_expires_at", state.expiresAtMillis)
            else remove("forgot_expires_at")
            apply()
        }
    }

    private fun clearForgotVerificationStateOnly() {
        prefs.edit().apply {
            remove("forgot_code_exists")
            remove("forgot_attempts_used")
            remove("forgot_max_attempts")
            remove("forgot_verified")
            remove("forgot_created_at")
            remove("forgot_expires_at")
            apply()
        }
        _verificationState.value = ForgotVerificationUiState()
        _timerSeconds.value = TIMER_SECONDS
    }

    private fun clearForgotRecoveryCache() {
        prefs.edit().apply {
            remove("forgot_email")
            remove("forgot_step")
            remove("forgot_code_exists")
            remove("forgot_attempts_used")
            remove("forgot_max_attempts")
            remove("forgot_verified")
            remove("forgot_created_at")
            remove("forgot_expires_at")
            apply()
        }

        _lastRequestedEmail.value = ""
        _verificationState.value = ForgotVerificationUiState()
        _timerSeconds.value = TIMER_SECONDS
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}