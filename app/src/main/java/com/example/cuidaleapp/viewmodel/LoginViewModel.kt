package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object CheckingAuth : LoginUiState()
    data class Transition(val userData: UserData) : LoginUiState()
    object Success : LoginUiState()
    data class Unverified(val email: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.CheckingAuth)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    init {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                try {
                    val query = db.collection("usuarios")
                        .whereEqualTo("correo", currentUser.email)
                        .get()
                        .await()
                    
                    if (!query.isEmpty) {
                        val userData = query.documents[0].toObject(UserData::class.java)
                        if (userData != null) {
                            if (userData.verificado) {
                                saveUserToCache(userData)
                                _uiState.value = LoginUiState.Success
                            } else {
                                auth.signOut()
                                clearCache()
                                _uiState.value = LoginUiState.Idle
                            }
                        } else {
                            _uiState.value = LoginUiState.Idle
                        }
                    } else {
                        auth.signOut()
                        clearCache()
                        _uiState.value = LoginUiState.Idle
                    }
                } catch (e: Exception) {
                    val cachedUser = getCachedUser()
                    if (cachedUser.correo.isNotEmpty()) {
                        _uiState.value = LoginUiState.Success
                    } else {
                        _uiState.value = LoginUiState.Idle
                    }
                }
            }
        } else {
            _uiState.value = LoginUiState.Idle
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
                        val queryRetry = db.collection("usuarios")
                            .whereEqualTo("correo", identifier.trim())
                            .get()
                            .await()
                        if (!queryRetry.isEmpty) {
                            userData = queryRetry.documents[0].toObject(UserData::class.java)
                            targetEmail = userData?.correo ?: identifier.trim()
                        } else {
                            throw Exception("Usuario no registrado")
                        }
                    }
                } else {
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

                try {
                    auth.signInWithEmailAndPassword(targetEmail, contrasena).await()
                } catch (e: Exception) {
                    throw Exception("Credenciales incorrectas")
                }
                
                if (userData != null) {
                    if (!userData.verificado) {
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

    private fun saveUserToCache(user: UserData) {
        prefs.edit().apply {
            putString("user_uid", user.uid)
            putString("user_nombre", user.nombre)
            putString("user_nombre_s", user.nombre_s)
            putString("user_apellido_pa", user.apellido_pa)
            putString("user_apellido_ma", user.apellido_ma)
            putString("user_correo", user.correo)
            putString("user_rut", user.rut)
            putString("user_rol", user.rol)
            putString("user_telefono", user.telefono)
            putString("user_fotoUrl", user.fotoUrl)
            putBoolean("user_telefonoVerificado", user.telefonoVerificado)
            putString("user_descripcion", user.descripcion)
            putString("user_id_region", user.id_region)
            putString("user_region", user.region)
            putString("user_comuna", user.comuna)
            apply()
        }
    }

    fun getCachedUser(): UserData {
        return UserData(
            uid = prefs.getString("user_uid", "") ?: "",
            nombre = prefs.getString("user_nombre", "") ?: "",
            nombre_s = prefs.getString("user_nombre_s", "") ?: "",
            apellido_pa = prefs.getString("user_apellido_pa", "") ?: "",
            apellido_ma = prefs.getString("user_apellido_ma", "") ?: "",
            correo = prefs.getString("user_correo", "") ?: "",
            rut = prefs.getString("user_rut", "") ?: "",
            rol = prefs.getString("user_rol", "") ?: "",
            telefono = prefs.getString("user_telefono", "") ?: "",
            fotoUrl = prefs.getString("user_fotoUrl", "") ?: "",
            telefonoVerificado = prefs.getBoolean("user_telefonoVerificado", false),
            descripcion = prefs.getString("user_descripcion", "") ?: "",
            id_region = prefs.getString("user_id_region", "") ?: "",
            region = prefs.getString("user_region", "") ?: "",
            comuna = prefs.getString("user_comuna", "") ?: ""
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
