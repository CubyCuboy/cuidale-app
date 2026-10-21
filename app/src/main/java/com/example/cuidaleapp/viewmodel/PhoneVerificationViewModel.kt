package com.example.cuidaleapp.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.utils.PhoneUtils
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class PhoneVerificationUiState {
    object Step1Phone : PhoneVerificationUiState()
    object Step2Code : PhoneVerificationUiState()
    object Loading : PhoneVerificationUiState()
    object Success : PhoneVerificationUiState()
    data class Error(val message: String) : PhoneVerificationUiState()
}

class PhoneVerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userSession = UserSession(application)

    private val _uiState = MutableStateFlow<PhoneVerificationUiState>(PhoneVerificationUiState.Step1Phone)
    val uiState: StateFlow<PhoneVerificationUiState> = _uiState.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _timeLeft = MutableStateFlow(0L)
    val timeLeft: StateFlow<Long> = _timeLeft.asStateFlow()

    private val _attemptsLeft = MutableStateFlow(5)
    val attemptsLeft: StateFlow<Int> = _attemptsLeft.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var timerJob: Job? = null

    init {
        val user = userSession.getUser()
        if (user.telefono.isNotEmpty()) {
            _phoneNumber.value = user.telefono
        }
    }

    fun onPhoneChange(newPhone: String) {
        val clean = newPhone.replace(Regex("[^0-9]"), "")
        if (clean.length <= 9) {
            _phoneNumber.value = clean
        } else if (clean.startsWith("56") && clean.length <= 11) {
            _phoneNumber.value = clean.takeLast(9)
        }

        if (_uiState.value is PhoneVerificationUiState.Error) {
            _uiState.value = PhoneVerificationUiState.Step1Phone
        }
    }

    fun onCodeChange(newCode: String) {
        if (newCode.length <= 6 && newCode.all { it.isDigit() }) {
            _code.value = newCode
            if (_uiState.value is PhoneVerificationUiState.Error) {
                _uiState.value = PhoneVerificationUiState.Step2Code
            }
        }
    }

    fun sendVerificationCode(activity: Activity) {
        val phone = _phoneNumber.value.trim()
        if (phone.isEmpty()) {
            _uiState.value = PhoneVerificationUiState.Error("Ingresa un número de teléfono válido")
            return
        }

        val formattedPhone = PhoneUtils.formatForFirebase(phone)

        _uiState.value = PhoneVerificationUiState.Loading

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    signInWithPhoneAuthCredential(credential)
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = PhoneVerificationUiState.Error(e.localizedMessage ?: "Fallo en la verificación")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                this@PhoneVerificationViewModel.verificationId = verificationId
                this@PhoneVerificationViewModel.resendToken = token
                _attemptsLeft.value = 5
                _code.value = ""
                _uiState.value = PhoneVerificationUiState.Step2Code
                startTimer(60000)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode() {
        if (_attemptsLeft.value <= 0) {
            _uiState.value = PhoneVerificationUiState.Error("Has agotado los intentos permitidos.")
            return
        }

        if (_timeLeft.value <= 0L) {
            _uiState.value = PhoneVerificationUiState.Error("El código ha caducado. Solicita uno nuevo.")
            return
        }

        val codeStr = _code.value
        val id = verificationId

        if (codeStr.length != 6 || id == null) {
            _uiState.value = PhoneVerificationUiState.Error("Código inválido")
            return
        }

        _uiState.value = PhoneVerificationUiState.Loading
        val credential = PhoneAuthProvider.getCredential(id, codeStr)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user != null) {
                    val formattedPhone = PhoneUtils.formatChileanPhone(_phoneNumber.value)
                    val updates = mapOf(
                        "telefonoVerificado" to true,
                        "telefono" to formattedPhone
                    )

                    val currentUserData = userSession.getUser()
                    val userDocRef = if (currentUserData.rut.isNotBlank()) {
                        val hashedRut = com.example.cuidaleapp.utils.HashUtils.hashRut(currentUserData.rut)
                        db.collection("usuarios").document(hashedRut)
                    } else {
                        val query = db.collection("usuarios").whereEqualTo("uid", user.uid).get().await()
                        if (!query.isEmpty) query.documents[0].reference else null
                    }

                    userDocRef?.set(updates, com.google.firebase.firestore.SetOptions.merge())?.await()

                    val notifRef = userDocRef?.collection("notificaciones")?.document()
                    if (notifRef != null) {
                        val notif = mapOf(
                            "id" to notifRef.id,
                            "tipo" to "configuracion_cuenta",
                            "emisor" to user.uid,
                            "receptor" to user.uid,
                            "timestamp" to System.currentTimeMillis(),
                            "leida" to false,
                            "data" to mapOf("title" to "Verificación de Teléfono", "text" to "Tu número de teléfono ha sido verificado con éxito vía SMS.")
                        )
                        notifRef.set(notif).await()
                    }
                    
                    userSession.saveUser(currentUserData.copy(telefono = formattedPhone, telefonoVerificado = true))
                    
                    _uiState.value = PhoneVerificationUiState.Success
                } else {
                    _uiState.value = PhoneVerificationUiState.Error("Usuario no autenticado")
                }
            } catch (e: Exception) {
                _attemptsLeft.value = (_attemptsLeft.value - 1).coerceAtLeast(0)
                val message = if (_attemptsLeft.value <= 0) {
                    "Has agotado los intentos permitidos. Solicita un nuevo código."
                } else {
                    e.localizedMessage ?: "Error al verificar código"
                }
                _uiState.value = PhoneVerificationUiState.Error(message)
            }
        }
    }

    private fun startTimer(duration: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = duration
            while (remaining > 0) {
                _timeLeft.value = remaining
                delay(1000)
                remaining -= 1000
            }
            _timeLeft.value = 0
            _code.value = ""
        }
    }

    fun resetToStep1() {
        _uiState.value = PhoneVerificationUiState.Step1Phone
        _code.value = ""
        _attemptsLeft.value = 5
        verificationId = null
    }
}
