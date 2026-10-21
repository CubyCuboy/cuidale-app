package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.AccountHistoryEntry
import com.example.cuidaleapp.utils.AccountUtils
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

    fun validateEmail(email: String): Boolean = AccountUtils.validateEmail(email)

    fun linkEmailPassword() {
        val emailVal = _email.value.trim().lowercase()
        val passVal = _password.value

        if (!AccountUtils.validateEmail(emailVal)) {
            _uiState.value = AddEmailPasswordState.Error("Correo electrónico no válido")
            return
        }

        val passwordError = AccountUtils.validatePassword(passVal)
        if (passwordError != null) {
            _uiState.value = AddEmailPasswordState.Error(passwordError)
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
                    } catch (_: Exception) {
                        currentUser.linkWithCredential(credential).await()
                    }
                } else {
                    currentUser.linkWithCredential(credential).await()
                }
                currentUser.reload().await()

                val now = System.currentTimeMillis()
                val currentUserData = userSession.getUser()

                val entry = AccountHistoryEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    action = "provider_linked",
                    title = "Cuenta de Correo y Contraseña Vinculada",
                    description = "Has vinculado tu correo ($emailVal) y contraseña",
                    timestamp = now
                )

                val newHistory = (listOf(entry) + currentUserData.accountHistory).distinctBy { it.id }.sortedByDescending { it.timestamp }
                val newLinked = AccountUtils.mergeLinkedAccount(
                    currentUserData.linkedAccounts,
                    "email",
                    "",
                    emailVal,
                    verificado = false,
                    timestamp = now
                )

                val updates = mapOf(
                    "verificado" to false,
                    "linkedAccounts" to newLinked,
                    "accountHistory" to newHistory,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )

                val rut = currentUserData.rut
                val userDocRef = if (rut.isNotBlank()) {
                    val hashedRut = HashUtils.hashRut(rut)
                    db.collection("usuarios").document(hashedRut)
                } else {
                    val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                    if (!query.isEmpty) query.documents[0].reference else null
                }

                if (userDocRef != null) {
                    userDocRef.set(updates, SetOptions.merge()).await()
                }

                val updatedSessionUser = currentUserData.copy(
                    verificado = false,
                    accountHistory = newHistory,
                    linkedAccounts = newLinked
                )
                userSession.saveUser(updatedSessionUser)

                _uiState.value = AddEmailPasswordState.Success
            } catch (e: Exception) {
                _uiState.value = AddEmailPasswordState.Error(AccountUtils.parseAccountError(e, getApplication()))
            }
        }
    }

    fun clearError() {
        if (_uiState.value is AddEmailPasswordState.Error) {
            _uiState.value = AddEmailPasswordState.Idle
        }
    }
}
