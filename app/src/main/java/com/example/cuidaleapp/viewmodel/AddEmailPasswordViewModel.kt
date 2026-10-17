package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.HashUtils
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AddEmailPasswordState {
    object Idle : AddEmailPasswordState()
    object Loading : AddEmailPasswordState()
    object Success : AddEmailPasswordState()
    data class Error(val message: String) : AddEmailPasswordState()
}

class AddEmailPasswordViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userSession = UserSession(application)

    private val _uiState = MutableStateFlow<AddEmailPasswordState>(AddEmailPasswordState.Idle)
    val uiState: StateFlow<AddEmailPasswordState> = _uiState.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _email.value = newEmail.trim().lowercase()
        clearError()
    }

    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
        clearError()
    }

    fun validateEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return false
        val pattern = android.util.Patterns.EMAIL_ADDRESS
        if (!pattern.matcher(trimmed).matches()) return false
        val parts = trimmed.split("@")
        if (parts.size != 2) return false
        val domain = parts[1]
        return domain.contains(".") && domain.substringAfterLast(".").length >= 2
    }

    fun linkEmailPassword() {
        val emailVal = _email.value.trim().lowercase()
        val passVal = _password.value

        if (!validateEmail(emailVal)) {
            _uiState.value = AddEmailPasswordState.Error("Correo electrónico no válido")
            return
        }

        if (passVal.length < 8 || !passVal.any { it.isUpperCase() } || !passVal.any { !it.isLetterOrDigit() }) {
            _uiState.value = AddEmailPasswordState.Error("La contraseña no cumple con los requisitos de seguridad")
            return
        }

        viewModelScope.launch {
            _uiState.value = AddEmailPasswordState.Loading
            try {
                val currentUser = auth.currentUser ?: throw Exception("Usuario no autenticado")
                val uid = currentUser.uid

                val credential = EmailAuthProvider.getCredential(emailVal, passVal)
                if (currentUser.providerData.any { it.providerId == "password" }) {
                    try {
                        currentUser.updateEmail(emailVal).await()
                        currentUser.updatePassword(passVal).await()
                    } catch (e: Exception) {
                        currentUser.linkWithCredential(credential).await()
                    }
                } else {
                    currentUser.linkWithCredential(credential).await()
                }
                currentUser.reload().await()

                val updates = mapOf(
                    "correoAlternativo" to emailVal,
                    "verificado" to false,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )

                val currentUserData = userSession.getUser()
                val rut = currentUserData.rut
                if (rut.isNotBlank()) {
                    val hashedRut = HashUtils.hashRut(rut)
                    db.collection("usuarios").document(hashedRut).set(updates, SetOptions.merge()).await()
                } else {
                    val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                    if (!query.isEmpty) {
                        query.documents[0].reference.set(updates, SetOptions.merge()).await()
                    }
                }

                val updatedSessionUser = currentUserData.copy(
                    correoAlternativo = emailVal,
                    verificado = false
                )
                userSession.saveUser(updatedSessionUser)

                _uiState.value = AddEmailPasswordState.Success
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("email-already-in-use") == true -> "El correo ya se encuentra registrado"
                    e.message?.contains("requires-recent-login") == true -> "Por seguridad, vuelve a iniciar sesión antes de agregar contraseña"
                    else -> e.localizedMessage ?: "Error al agregar correo y contraseña"
                }
                _uiState.value = AddEmailPasswordState.Error(msg)
            }
        }
    }

    fun clearError() {
        if (_uiState.value is AddEmailPasswordState.Error) {
            _uiState.value = AddEmailPasswordState.Idle
        }
    }
}
