package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.utils.HashUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class VerificationUiState {
    object Step1Email : VerificationUiState()
    object Step2Code : VerificationUiState()
    object Loading : VerificationUiState()
    object Success : VerificationUiState()
    object FailureExhausted : VerificationUiState()
    data class Error(val message: String) : VerificationUiState()
}

class EmailVerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val functions = FirebaseFunctions.getInstance("southamerica-west1")
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("verification_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<VerificationUiState>(VerificationUiState.Step1Email)
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _timeLeft = MutableStateFlow(0L)
    val timeLeft: StateFlow<Long> = _timeLeft.asStateFlow()

    private val _attemptsLeft = MutableStateFlow(5)
    val attemptsLeft: StateFlow<Int> = _attemptsLeft.asStateFlow()

    private var timerJob: Job? = null

    init {
        restoreState()
    }

    private fun restoreState() {
        val savedEmail = prefs.getString("email", "") ?: ""
        val expiryTime = prefs.getLong("expiry_time", 0L)
        val attempts = prefs.getInt("attempts", 5)
        val currentStep = prefs.getString("step", "step1")

        if (savedEmail.isNotEmpty()) {
            _email.value = savedEmail
            _attemptsLeft.value = attempts
            
            val currentTime = System.currentTimeMillis()
            if (currentTime < expiryTime) {
                _timeLeft.value = expiryTime - currentTime
                if (currentStep == "step2") {
                    _uiState.value = VerificationUiState.Step2Code
                    startTimer(expiryTime)
                }
            } else {
                clearSavedState()
            }
        }
    }

    private fun saveState(step: String) {
        val expiryTime = System.currentTimeMillis() + _timeLeft.value
        prefs.edit().apply {
            putString("email", _email.value)
            putLong("expiry_time", expiryTime)
            putInt("attempts", _attemptsLeft.value)
            putString("step", step)
            apply()
        }
    }

    private fun clearSavedState() {
        prefs.edit().clear().apply()
        _timeLeft.value = 0
        _attemptsLeft.value = 5
        _code.value = ""
    }

    fun onEmailChange(newEmail: String) {
        _email.value = newEmail
        if (_uiState.value is VerificationUiState.Error) {
            _uiState.value = VerificationUiState.Step1Email
        }
    }

    fun onCodeChange(newCode: String) {
        if (newCode.length <= 6 && newCode.all { it.isDigit() }) {
            _code.value = newCode
            if (_uiState.value is VerificationUiState.Error) {
                _uiState.value = VerificationUiState.Step2Code
            }
        }
    }

    fun sendVerificationCode() {
        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading
            try {
                val data = hashMapOf("email" to _email.value.trim().lowercase())
                
                functions
                    .getHttpsCallable("sendVerificationCode")
                    .call(data)
                    .await()

                val expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
                _timeLeft.value = TimeUnit.MINUTES.toMillis(30)
                _attemptsLeft.value = 5
                _code.value = ""
                _uiState.value = VerificationUiState.Step2Code
                saveState("step2")
                startTimer(expiry)
            } catch (e: Exception) {
                val message = if (e is FirebaseFunctionsException) {
                    e.message ?: "Error del servidor"
                } else {
                    e.localizedMessage ?: "Error al enviar código"
                }
                _uiState.value = VerificationUiState.Error(message)
                delay(3000)
                if (_uiState.value is VerificationUiState.Error) {
                    _uiState.value = VerificationUiState.Step1Email
                }
            }
        }
    }

    fun verifyCode() {
        if (_attemptsLeft.value <= 0) {
            _uiState.value = VerificationUiState.FailureExhausted
            return
        }

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading
            try {
                val data = hashMapOf(
                    "email" to _email.value.trim().lowercase(),
                    "code" to _code.value.trim()
                )

                functions
                    .getHttpsCallable("verifyVerificationCode")
                    .call(data)
                    .await()

                val authUser = FirebaseAuth.getInstance().currentUser
                val uid = authUser?.uid
                if (uid != null) {
                    val updates = mapOf(
                        "verificado" to true,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                    val userSession = UserSession(getApplication())
                    val cachedUser = userSession.getUser()
                    val updatedUser = cachedUser.copy(verificado = true)
                    userSession.saveUser(updatedUser)

                    if (updatedUser.rut.isNotBlank()) {
                        val hashedRut = HashUtils.hashRut(updatedUser.rut)
                        db.collection("usuarios").document(hashedRut).set(updates, SetOptions.merge()).await()
                    } else {
                        val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                        if (!query.isEmpty) {
                            query.documents[0].reference.set(updates, SetOptions.merge()).await()
                        }
                    }
                }

                clearSavedState()
                _uiState.value = VerificationUiState.Success
            } catch (e: Exception) {
                if (e is FirebaseFunctionsException) {
                    val remaining = e.details?.let { (it as Map<*, *>)["remaining"] as? Int }
                    if (remaining != null) {
                        _attemptsLeft.value = remaining
                        saveState("step2")
                    }

                    if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                        _uiState.value = VerificationUiState.FailureExhausted
                        return@launch
                    }
                }
                
                _uiState.value = VerificationUiState.Error(e.localizedMessage ?: "Error de verificación")
                delay(3000)
                if (_uiState.value is VerificationUiState.Error) {
                    _uiState.value = VerificationUiState.Step2Code
                }
            }
        }
    }

    private fun startTimer(expiryTime: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (System.currentTimeMillis() < expiryTime) {
                _timeLeft.value = expiryTime - System.currentTimeMillis()
                delay(1000)
            }
            _timeLeft.value = 0
            if (_uiState.value is VerificationUiState.Step2Code) {
                _uiState.value = VerificationUiState.Error("El código ha caducado")
                delay(3000)
                _uiState.value = VerificationUiState.Step1Email
            }
        }
    }

    fun resetToStep1() {
        _uiState.value = VerificationUiState.Step1Email
        _code.value = ""
    }
}
