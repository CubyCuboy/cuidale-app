package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.HashUtils
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
    data class ExternalRegistrationNeeded(val email: String, val uid: String, val providerId: String, val displayName: String? = null) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userSession = UserSession(application)
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
        val keepSession = prefs.getBoolean("keep_session", true)
        
        if (currentUser != null && !keepSession) {
            auth.signOut()
            userSession.clear()
            _uiState.value = LoginUiState.Idle
        } else if (currentUser != null) {
            viewModelScope.launch {
                try {
                    var loadedUser: UserData? = null

                    kotlinx.coroutines.withTimeoutOrNull(5000L) {
                        val cachedUser = userSession.getUser()
                        if (cachedUser.rut.isNotBlank()) {
                            val hashedRut = HashUtils.hashRut(cachedUser.rut)
                            val doc = db.collection("usuarios").document(hashedRut).get().await()
                            if (doc.exists()) {
                                val userData = doc.toObject(UserData::class.java)
                                if (userData != null) {
                                    loadedUser = userData
                                    return@withTimeoutOrNull
                                }
                            }
                        }

                        val query = db.collection("usuarios")
                            .whereEqualTo("uid", currentUser.uid)
                            .get()
                            .await()
                        
                        if (!query.isEmpty) {
                            val userData = query.documents[0].toObject(UserData::class.java)
                            if (userData != null) {
                                loadedUser = userData
                                return@withTimeoutOrNull
                            }
                        }

                        val email = currentUser.email ?: ""
                        if (email.isNotBlank()) {
                            val queryEmail = db.collection("usuarios")
                                .whereEqualTo("correo", email)
                                .get()
                                .await()
                            
                            if (!queryEmail.isEmpty) {
                                val userData = queryEmail.documents[0].toObject(UserData::class.java)
                                if (userData != null) {
                                    loadedUser = userData
                                    return@withTimeoutOrNull
                                }
                            }
                        }
                    }

                    val user = loadedUser ?: userSession.getUser()

                    // VALIDACIÓN DE SEGURIDAD ESTRICTA: El usuario DEBE haber completado el registro (RUT y Rol obligatorios)
                    if (user != null && user.completado && user.rut.isNotBlank() && user.rol.isNotBlank()) {
                        userSession.saveUser(user)
                        _uiState.value = LoginUiState.Success
                    } else {
                        // El usuario no ha completado el registro
                        val providerId = currentUser.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "google.com"
                        val email = currentUser.email ?: user?.correo ?: ""
                        
                        if (email.isNotBlank()) {
                            _uiState.value = LoginUiState.ExternalRegistrationNeeded(
                                email = email,
                                uid = currentUser.uid,
                                providerId = providerId,
                                displayName = currentUser.displayName
                            )
                        } else {
                            auth.signOut()
                            userSession.clear()
                            _uiState.value = LoginUiState.Idle
                        }
                    }
                } catch (e: Exception) {
                    val cachedUser = userSession.getUser()
                    if (cachedUser.completado && cachedUser.rut.isNotBlank() && cachedUser.rol.isNotBlank()) {
                        _uiState.value = LoginUiState.Success
                    } else {
                        val providerId = currentUser.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "google.com"
                        val email = currentUser.email ?: cachedUser.correo
                        if (email.isNotBlank()) {
                            _uiState.value = LoginUiState.ExternalRegistrationNeeded(
                                email = email,
                                uid = currentUser.uid,
                                providerId = providerId,
                                displayName = currentUser.displayName
                            )
                        } else {
                            auth.signOut()
                            userSession.clear()
                            _uiState.value = LoginUiState.Idle
                        }
                    }
                }
            }
        } else {
            _uiState.value = LoginUiState.Idle
        }
    }

    private fun parseLoginErrorMessage(e: Exception): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("network-request-failed", ignoreCase = true) || 
            msg.contains("network", ignoreCase = true) || 
            msg.contains("unavailable", ignoreCase = true) || 
            msg.contains("host", ignoreCase = true) || 
            e is com.google.firebase.FirebaseNetworkException || 
            e is java.net.UnknownHostException || 
            e is java.io.IOException -> 
                "Error de conexión a internet. Revisa tu red e intenta nuevamente"
            msg.contains("invalid-credential", ignoreCase = true) || 
            msg.contains("wrong-password", ignoreCase = true) || 
            msg.contains("user-not-found", ignoreCase = true) || 
            msg.contains("Credenciales incorrectas", ignoreCase = true) -> 
                "Credenciales incorrectas. Verifica tu correo/RUT y contraseña"
            msg.contains("user-disabled", ignoreCase = true) -> 
                "Esta cuenta ha sido deshabilitada"
            msg.contains("Usuario no registrado", ignoreCase = true) || 
            msg.contains("Rut no encontrado", ignoreCase = true) -> 
                msg
            else -> if (msg.isNotBlank()) msg else "Error al iniciar sesión"
        }
    }

    fun login(identifier: String, contrasena: String, keepSession: Boolean) {
        if (identifier.isBlank() || contrasena.isBlank()) {
            _uiState.value = LoginUiState.Error("Campos vacíos obligatorios")
            return
        }

        prefs.edit().putBoolean("keep_session", keepSession).apply()

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
                    val hashedRut = HashUtils.hashRut(formattedRut)
                    val doc = db.collection("usuarios").document(hashedRut).get().await()

                    if (doc.exists()) {
                        userData = doc.toObject(UserData::class.java)
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
                    userSession.saveUser(userData)
                    _uiState.value = LoginUiState.Success
                } else {
                    _uiState.value = LoginUiState.Error("Error al recuperar datos")
                }

            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(parseLoginErrorMessage(e))
            }
        }
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

    fun setError(message: String) {
        _uiState.value = LoginUiState.Error(message)
    }

    fun loginWithGoogle(idToken: String, email: String?, displayName: String?) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                if (email.isNullOrBlank()) {
                    throw Exception("El proveedor no proporcionó un correo electrónico")
                }
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user ?: throw Exception("Error al autenticar con Google")
                val uid = firebaseUser.uid

                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                if (!query.isEmpty) {
                    val userData = query.documents[0].toObject(UserData::class.java)
                    if (userData != null && userData.completado) {
                        userSession.saveUser(userData)
                        _uiState.value = LoginUiState.Success
                    } else {
                        _uiState.value = LoginUiState.ExternalRegistrationNeeded(email, uid, "google.com", displayName)
                    }
                } else {
                    val nameParts = (displayName ?: firebaseUser.displayName ?: "").trim().split(Regex("\\s+"))
                    val firstName = nameParts.getOrNull(0) ?: ""
                    val secondName = if (nameParts.size >= 3) nameParts.getOrNull(1) ?: "" else ""
                    val lastName = if (nameParts.size >= 3) nameParts.getOrNull(2) ?: (nameParts.getOrNull(1) ?: "") else if (nameParts.size == 2) nameParts[1] else ""
                    val secondLastName = if (nameParts.size >= 4) nameParts.subList(3, nameParts.size).joinToString(" ") else ""

                    val ghostData = hashMapOf(
                        "uid" to uid,
                        "correo" to email,
                        "nombre" to firstName,
                        "nombre_s" to secondName,
                        "apellido_pa" to lastName,
                        "apellido_ma" to secondLastName,
                        "verificado" to true,
                        "completado" to false,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("usuarios").document(uid).set(ghostData).await()
                    _uiState.value = LoginUiState.ExternalRegistrationNeeded(email, uid, "google.com", displayName)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(parseLoginErrorMessage(e))
            }
        }
    }

    fun loginWithProvider(activity: android.app.Activity, providerId: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val provider = com.google.firebase.auth.OAuthProvider.newBuilder(providerId)
                val result = auth.startActivityForSignInWithProvider(activity, provider.build()).await()
                val firebaseUser = result.user ?: throw Exception("Error al autenticar con proveedor")
                val email = firebaseUser.email ?: throw Exception("El proveedor no proporcionó un correo electrónico")
                val uid = firebaseUser.uid

                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                if (!query.isEmpty) {
                    val userData = query.documents[0].toObject(UserData::class.java)
                    if (userData != null && userData.completado) {
                        userSession.saveUser(userData)
                        _uiState.value = LoginUiState.Success
                    } else {
                        _uiState.value = LoginUiState.ExternalRegistrationNeeded(email, uid, providerId)
                    }
                } else {
                    val nameParts = (firebaseUser.displayName ?: "").split(" ")
                    val firstName = nameParts.getOrNull(0) ?: ""
                    val lastName = nameParts.getOrNull(1) ?: ""

                    val ghostData = hashMapOf(
                        "uid" to uid,
                        "correo" to email,
                        "nombre" to firstName,
                        "apellido_pa" to lastName,
                        "verificado" to true,
                        "completado" to false,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("usuarios").document(uid).set(ghostData).await()
                    _uiState.value = LoginUiState.ExternalRegistrationNeeded(email, uid, providerId)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(parseLoginErrorMessage(e))
            }
        }
    }
}
