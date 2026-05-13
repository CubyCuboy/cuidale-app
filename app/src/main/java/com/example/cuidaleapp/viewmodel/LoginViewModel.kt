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
    val Nombre: String = "",
    val Apellido_Pa: String = "",
    val correo: String = "",
    val rut: String = ""
)

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Transition(val userData: UserData) : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        // Verificar si ya hay una sesión activa al iniciar
        if (auth.currentUser != null && prefs.contains("user_nombre")) {
            _uiState.value = LoginUiState.Success
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

    private fun saveUserToCache(user: UserData) {
        prefs.edit().apply {
            putString("user_nombre", user.Nombre)
            putString("user_apellido", user.Apellido_Pa)
            putString("user_correo", user.correo)
            putString("user_rut", user.rut)
            apply()
        }
    }

    fun getCachedUser(): UserData {
        return UserData(
            Nombre = prefs.getString("user_nombre", "") ?: "",
            Apellido_Pa = prefs.getString("user_apellido", "") ?: "",
            correo = prefs.getString("user_correo", "") ?: "",
            rut = prefs.getString("user_rut", "") ?: ""
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
