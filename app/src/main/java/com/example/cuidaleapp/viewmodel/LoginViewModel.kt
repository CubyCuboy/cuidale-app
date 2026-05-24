package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserData(
    val nombre: String = "",
    val apellido_pa: String = "",
    val correo: String = "",
    val rut: String = "",
    val rol: String = "",
    val verificado: Boolean = false
)

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Transition(val userData: UserData) : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    data class Unverified(val email: String) : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    init {
        // Verificar si ya hay una sesión activa al iniciar
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Siempre verificamos con Firestore para asegurar el estado de 'verificado'
            viewModelScope.launch {
                try {
                    val query = db.collection("usuarios")
                        .whereEqualTo("correo", currentUser.email)
                        .get()
                        .await()
                    
                    if (!query.isEmpty) {
                        val userData = query.documents[0].toObject(UserData::class.java)
                        if (userData != null) {
                            if (!userData.verificado) {
                                // Si no está verificado, forzamos estado Unverified
                                _uiState.value = LoginUiState.Unverified(userData.correo)
                            } else {
                                // Solo si está verificado, guardamos en cache y marcamos éxito
                                saveUserToCache(userData)
                                _uiState.value = LoginUiState.Success
                            }
                        }
                    } else {
                        // Usuario de Auth existe pero no en Firestore? Caso raro, limpiar
                        auth.signOut()
                        clearCache()
                    }
                } catch (e: Exception) {
                    // Si falla la red, podemos intentar usar el cache si existe y marcaba verificado
                    val cachedUser = getCachedUser()
                    if (cachedUser.correo.isNotEmpty() && cachedUser.verificado) {
                        _uiState.value = LoginUiState.Success
                    } else {
                        _uiState.value = LoginUiState.Idle
                    }
                }
            }
        }
    }

    fun login(identifier: String, contrasena: String) {
        if (identifier.isBlank() || contrasena.isBlank()) {
            _uiState.value = LoginUiState.Error("Campos vacíos obligatorios")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val input = identifier.lowercase().trim()
                val isEmail = input.isNotEmpty() && input[0].isLetter() && input.contains("@")
                
                var targetEmail: String = ""
                var userData: UserData? = null

                if (isEmail) {
                    targetEmail = input
                    val query = db.collection("usuarios")
                        .whereEqualTo("correo", targetEmail)
                        .get()
                        .await()
                    
                    if (!query.isEmpty) {
                        userData = query.documents[0].toObject(UserData::class.java)
                    } else {
                        throw Exception("Usuario no registrado")
                    }
                } else {
                    // Es RUT
                    val formattedRut = formatRut(input)
                    val query = db.collection("usuarios")
                        .whereEqualTo("rut", formattedRut)
                        .get()
                        .await()

                    if (!query.isEmpty) {
                        userData = query.documents[0].toObject(UserData::class.java)
                        targetEmail = userData?.correo ?: throw Exception("El usuario no tiene un correo asociado")
                    } else {
                        throw Exception("Rut no encontrado")
                    }
                }

                // Verificar contraseña en Auth
                try {
                    auth.signInWithEmailAndPassword(targetEmail, contrasena).await()
                } catch (e: Exception) {
                    throw Exception("Credenciales incorrectas")
                }
                
                if (userData != null) {
                    // Verificar si la cuenta está verificada
                    val isVerified = userData.verificado
                    if (!isVerified) {
                        // Reenviar código automáticamente al intentar loguear sin verificar
                        resendVerificationCode(targetEmail)
                        _uiState.value = LoginUiState.Unverified(targetEmail)
                        return@launch
                    }
                    saveUserToCache(userData)
                    _uiState.value = LoginUiState.Transition(userData)
                } else {
                    _uiState.value = LoginUiState.Error("Error al recuperar datos")
                }

            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    private val functions = com.google.firebase.functions.FirebaseFunctions.getInstance("southamerica-west1")

    private fun resendVerificationCode(email: String) {
        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "email" to email,
                    "type" to "verification"
                )
                functions.getHttpsCallable("sendVerificationCode").call(data).await()
            } catch (e: Exception) {
                // Silencioso o log
            }
        }
    }

    private fun saveUserToCache(user: UserData) {
        prefs.edit().apply {
            putString("user_nombre", user.nombre)
            putString("user_apellido", user.apellido_pa)
            putString("user_correo", user.correo)
            putString("user_rut", user.rut)
            putString("user_rol", user.rol)
            putBoolean("user_verificado", user.verificado)
            apply()
        }
    }

    fun getCachedUser(): UserData {
        return UserData(
            nombre = prefs.getString("user_nombre", "") ?: "",
            apellido_pa = prefs.getString("user_apellido", "") ?: "",
            correo = prefs.getString("user_correo", "") ?: "",
            rut = prefs.getString("user_rut", "") ?: "",
            rol = prefs.getString("user_rol", "") ?: "",
            verificado = prefs.getBoolean("user_verificado", false)
        )
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }

    private fun formatRut(rut: String): String {
        val cleanRut = rut.replace(".", "").replace("-", "").uppercase()
        if (cleanRut.length < 2) return cleanRut
        val body = cleanRut.substring(0, cleanRut.length - 1)
        val dv = cleanRut.substring(cleanRut.length - 1)
        return "$body-$dv"
    }

    fun onTransitionFinished() {
        _uiState.value = LoginUiState.Success
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
