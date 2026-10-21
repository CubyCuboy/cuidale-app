package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.ErrorMessages
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

    private fun parseDocumentToUserData(doc: com.google.firebase.firestore.DocumentSnapshot, fallbackUid: String = ""): UserData? {
        val user = doc.toObject(UserData::class.java) ?: return null
        val targetUid = if (user.uid.isNotBlank()) user.uid else fallbackUid
        var result = user.copy(uid = targetUid)
        if (result.linkedAccounts.isEmpty()) {
            result = applyLinkedAccountsMigration(result, doc)
        }
        return result
    }

    private fun applyLinkedAccountsMigration(user: UserData, snapshot: com.google.firebase.firestore.DocumentSnapshot): UserData {
        if (!user.linkedAccounts.isEmpty()) return user
        try {
            val rawMap = snapshot.get("providerTimestamps") as? Map<*, *> ?: return user
            val rawTimestamps = mutableMapOf<String, Long>()
            rawMap.forEach { (k, v) ->
                if (k is String && v != null) {
                    when (v) {
                        is Number -> rawTimestamps[k] = v.toLong()
                        is String -> v.toLongOrNull()?.let { rawTimestamps[k] = it }
                        is Map<*, *> -> {
                            val num = v.values.firstOrNull { it is Number } as? Number
                            if (num != null) rawTimestamps[k] = num.toLong()
                        }
                    }
                }
            }
            if (rawTimestamps.isEmpty()) return user
            val migrated = com.example.cuidaleapp.utils.AccountUtils.migrateLegacyToLinkedAccounts(
                user.linkedAccounts,
                rawTimestamps,
                user.correo,
                user.verificado
            )
            return user.copy(linkedAccounts = migrated)
        } catch (e: Exception) {
            android.util.Log.e("LoginViewModel", "Error migrating legacy linked accounts: ${e.message}")
            return user
        }
    }

    init {
        val pendingTask = auth.pendingAuthResult
        val currentUser = auth.currentUser
        val keepSession = prefs.getBoolean("keep_session", true)
        
        if (pendingTask != null) {
            viewModelScope.launch {
                _uiState.value = LoginUiState.Loading
                try {
                    val result = pendingTask.await()
                    val firebaseUser = result.user
                    if (firebaseUser != null) {
                        val providerId = firebaseUser.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "microsoft.com"
                        val email = extractEmailFromAuthResult(firebaseUser, result)
                        val uid = firebaseUser.uid
                        
                        var query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                        if (query.isEmpty && email.isNotBlank()) {
                            query = db.collection("usuarios").whereEqualTo("correo", email).get().await()
                        }
                        
                        if (!query.isEmpty) {
                            val doc = query.documents[0]
                            val userData = parseDocumentToUserData(doc, uid)
                            if (userData != null && userData.completado) {
                                val now = System.currentTimeMillis()
                                doc.reference.update(
                                    "uid", uid,
                                    "linkedAccounts" to com.example.cuidaleapp.utils.AccountUtils.mergeLinkedAccount(
                                        userData.linkedAccounts,
                                        providerId,
                                        "",
                                        email,
                                        verificado = true,
                                        timestamp = now
                                    )
                                )
                                val updatedUserData = userData.copy(uid = uid)
                                userSession.saveUser(updatedUserData)
                                _uiState.value = LoginUiState.Success
                            } else {
                                _uiState.value = LoginUiState.ExternalRegistrationNeeded(
                                    email = email,
                                    uid = uid,
                                    providerId = providerId,
                                    displayName = firebaseUser.displayName
                                )
                            }
                        } else {
                            _uiState.value = LoginUiState.ExternalRegistrationNeeded(
                                email = email,
                                uid = uid,
                                providerId = providerId,
                                displayName = firebaseUser.displayName
                            )
                        }
                    } else {
                        _uiState.value = LoginUiState.Idle
                    }
                } catch (e: Exception) {
                    _uiState.value = LoginUiState.Error(parseLoginErrorMessage(e))
                }
            }
        } else if (currentUser != null && !keepSession) {
            auth.signOut()
            userSession.clear()
            _uiState.value = LoginUiState.Idle
        } else if (currentUser != null) {
            viewModelScope.launch {
                try {
                    val cachedUser = userSession.getUser()
                    if (cachedUser.uid.isNotBlank() && currentUser.uid != cachedUser.uid) {
                        userSession.clear()
                    } else if (cachedUser.correo.isNotBlank() && currentUser.email != null && cachedUser.correo != currentUser.email) {
                        userSession.clear()
                    }

                    var loadedUser: UserData? = null

                    kotlinx.coroutines.withTimeoutOrNull(5000L) {
                        val query = db.collection("usuarios")
                            .whereEqualTo("uid", currentUser.uid)
                            .get()
                            .await()
                        
                        if (!query.isEmpty) {
                            val userData = parseDocumentToUserData(query.documents[0], currentUser.uid)
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
                                val userData = parseDocumentToUserData(queryEmail.documents[0], currentUser.uid)
                                if (userData != null) {
                                    loadedUser = userData
                                    return@withTimeoutOrNull
                                }
                            }
                        }

                        val freshCachedUser = userSession.getUser()
                        if (freshCachedUser.rut.isNotBlank() && (freshCachedUser.uid == currentUser.uid || freshCachedUser.correo == currentUser.email)) {
                            val hashedRut = HashUtils.hashRut(freshCachedUser.rut)
                            val doc = db.collection("usuarios").document(hashedRut).get().await()
                            if (doc.exists()) {
                                val userData = parseDocumentToUserData(doc, currentUser.uid)
                                if (userData != null) {
                                    loadedUser = userData
                                    return@withTimeoutOrNull
                                }
                            }
                        }
                    }

                    val user = loadedUser ?: userSession.getUser()

                    if (user != null && user.completado && user.rut.isNotBlank() && user.rol.isNotBlank() && (user.uid.isBlank() || user.uid == currentUser.uid)) {
                        userSession.saveUser(user)
                        _uiState.value = LoginUiState.Success
                    } else {
                        val providerId = currentUser.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "google.com"
                        val email = currentUser.email ?: user?.correo ?: currentUser.providerData.firstOrNull { !it.email.isNullOrBlank() }?.email ?: ""
                        
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
                    if (cachedUser.completado && cachedUser.rut.isNotBlank() && cachedUser.rol.isNotBlank() && cachedUser.uid == currentUser.uid) {
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
        val app = getApplication<Application>()
        return when {
            msg.contains("network-request-failed", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true) ||
            msg.contains("host", ignoreCase = true) ||
            e is com.google.firebase.FirebaseNetworkException ||
            e is java.net.UnknownHostException ||
            e is java.io.IOException ->
                app.getString(com.example.cuidaleapp.R.string.error_network)
            msg.contains("invalid-credential", ignoreCase = true) ||
            msg.contains("wrong-password", ignoreCase = true) ||
            msg.contains("user-not-found", ignoreCase = true) ||
            msg.contains("Credenciales incorrectas", ignoreCase = true) ->
                app.getString(com.example.cuidaleapp.R.string.error_credentials)
            msg.contains("user-disabled", ignoreCase = true) ->
                "Esta cuenta ha sido deshabilitada"
            msg.contains("Usuario no registrado", ignoreCase = true) ||
            msg.contains("Rut no encontrado", ignoreCase = true) ->
                msg
            else -> ErrorMessages.get(app, msg)
        }
    }

    fun login(identifier: String, contrasena: String, keepSession: Boolean) {
        if (identifier.isBlank() || contrasena.isBlank()) {
            _uiState.value = LoginUiState.Error("Campos vacíos obligatorios")
            return
        }

        userSession.clear()
        prefs.edit().putBoolean("keep_session", keepSession).apply()

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                // Tiempo máximo de espera: 25 s.
                val result = kotlinx.coroutines.withTimeoutOrNull(25_000L) {
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
                            userData = parseDocumentToUserData(query.documents[0])
                        } else {
                            val queryRetry = db.collection("usuarios")
                                .whereEqualTo("correo", identifier.trim())
                                .get()
                                .await()
                            if (!queryRetry.isEmpty) {
                                userData = parseDocumentToUserData(queryRetry.documents[0])
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
                            userData = parseDocumentToUserData(doc)
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
                        val passwordAccount = userData.findLinkedAccount("email")
                        val isVerified = passwordAccount?.verificado ?: userData.verificado
                        if (!isVerified) {
                            throw LoginNeedsVerification(targetEmail)
                        }
                        userSession.saveUser(userData)
                    } else {
                        throw Exception("Error al recuperar datos")
                    }
                    userData
                }

                if (result == null) {
                    _uiState.value = LoginUiState.Error("La conexión está tardando demasiado. Inténtalo nuevamente.")
                } else {
                    _uiState.value = LoginUiState.Success
                }
            } catch (e: LoginNeedsVerification) {
                _uiState.value = LoginUiState.Unverified(e.email)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(parseLoginErrorMessage(e))
            }
        }
    }

    private class LoginNeedsVerification(val email: String) : Exception()

    private fun formatRut(rut: String): String {
        val cleanRut = rut.replace(".", "").replace("-", "").uppercase()
        if (cleanRut.length < 2) return cleanRut
        val body = cleanRut.substring(0, cleanRut.length - 1)
        val dv = cleanRut.substring(cleanRut.length - 1)
        return "$body-$dv"
    }

    fun resolveEmailForVerification(identifier: String, onResolved: (String) -> Unit) {
        val input = identifier.trim().lowercase()
        if (input.contains("@")) {
            onResolved(input)
            return
        }
        viewModelScope.launch {
            try {
                val formattedRut = formatRut(input)
                val hashedRut = HashUtils.hashRut(formattedRut)
                val doc = db.collection("usuarios").document(hashedRut).get().await()
                if (doc.exists()) {
                    val email = doc.getString("correo") ?: ""
                    if (email.isNotBlank()) {
                        onResolved(email)
                        return@launch
                    }
                }
                onResolved(input)
            } catch (e: Exception) {
                onResolved(input)
            }
        }
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
        userSession.clear()
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

                var query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                if (query.isEmpty && email.isNotBlank()) {
                    query = db.collection("usuarios").whereEqualTo("correo", email).get().await()
                }

                if (!query.isEmpty) {
                    val doc = query.documents[0]
                    val userData = parseDocumentToUserData(doc, uid)
                    if (userData != null && userData.completado) {
                        val now = System.currentTimeMillis()
                        val updatedLinked = com.example.cuidaleapp.utils.AccountUtils.mergeLinkedAccount(
                            userData.linkedAccounts,
                            "google.com",
                            "Google",
                            email,
                            verificado = true,
                            timestamp = now
                        )
                        doc.reference.set(
                            mapOf(
                                "uid" to uid,
                                "correo" to email,
                                "linkedAccounts" to updatedLinked
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        val updatedUserData = userData.copy(uid = uid, correo = email, linkedAccounts = updatedLinked)
                        userSession.saveUser(updatedUserData)
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

                    val now = System.currentTimeMillis()
                    val initialLinked = com.example.cuidaleapp.utils.AccountUtils.mergeLinkedAccount(
                        emptyList(),
                        "google.com",
                        "Google",
                        email,
                        verificado = true,
                        timestamp = now
                    )
                    val ghostData = hashMapOf(
                        "uid" to uid,
                        "correo" to email,
                        "nombre" to firstName,
                        "nombre_s" to secondName,
                        "apellido_pa" to lastName,
                        "apellido_ma" to secondLastName,
                        "verificado" to true,
                        "completado" to false,
                        "linkedAccounts" to initialLinked,
                        "createdAt" to com.google.firebase.Timestamp(now / 1000, 0)
                    )
                    db.collection("usuarios").document(uid).set(ghostData).await()
                    _uiState.value = LoginUiState.ExternalRegistrationNeeded(email, uid, "google.com", displayName)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(parseLoginErrorMessage(e))
            }
        }
    }

    private fun extractEmailFromAuthResult(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        result: com.google.firebase.auth.AuthResult?
    ): String {
        if (!firebaseUser.email.isNullOrBlank()) {
            return firebaseUser.email!!.trim().lowercase()
        }

        val providerEmail = firebaseUser.providerData.firstOrNull { !it.email.isNullOrBlank() }?.email
        if (!providerEmail.isNullOrBlank()) {
            return providerEmail.trim().lowercase()
        }

        val profile = result?.additionalUserInfo?.profile
        if (profile != null) {
            val mail = profile["mail"] as? String
            if (!mail.isNullOrBlank()) return mail.trim().lowercase()

            val preferredUsername = profile["preferred_username"] as? String
            if (!preferredUsername.isNullOrBlank() && preferredUsername.contains("@")) {
                return preferredUsername.trim().lowercase()
            }

            val userPrincipalName = profile["userPrincipalName"] as? String
            if (!userPrincipalName.isNullOrBlank() && userPrincipalName.contains("@")) {
                return userPrincipalName.trim().lowercase()
            }

            val emailClaim = profile["email"] as? String
            if (!emailClaim.isNullOrBlank()) return emailClaim.trim().lowercase()
        }

        val username = result?.additionalUserInfo?.username
        if (!username.isNullOrBlank() && username.contains("@")) {
            return username.trim().lowercase()
        }

        return ""
    }

    fun loginWithProvider(activity: android.app.Activity, providerId: String) {
        userSession.clear()
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val provider = com.google.firebase.auth.OAuthProvider.newBuilder(providerId)
                if (providerId == "microsoft.com") {
                    provider.addCustomParameter("prompt", "select_account")
                    provider.scopes = listOf("openid", "email", "profile", "User.Read")
                }

                val pendingTask = auth.pendingAuthResult
                val result = if (pendingTask != null) {
                    pendingTask.await()
                } else {
                    auth.startActivityForSignInWithProvider(activity, provider.build()).await()
                }

                val firebaseUser = result.user ?: throw Exception("Error al autenticar con proveedor")
                val email = extractEmailFromAuthResult(firebaseUser, result)

                if (email.isBlank()) {
                    throw Exception("El proveedor no proporcionó un correo electrónico válido")
                }

                val uid = firebaseUser.uid
                val displayName = firebaseUser.displayName
                    ?: (result.additionalUserInfo?.profile?.get("displayName") as? String)
                    ?: (result.additionalUserInfo?.profile?.get("name") as? String)
                    ?: ""

                var query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                if (query.isEmpty && email.isNotBlank()) {
                    query = db.collection("usuarios").whereEqualTo("correo", email).get().await()
                }

                if (!query.isEmpty) {
                    val doc = query.documents[0]
                    val userData = parseDocumentToUserData(doc, uid)
                    if (userData != null && userData.completado) {
                        val now = System.currentTimeMillis()
                        val updatedLinked = com.example.cuidaleapp.utils.AccountUtils.mergeLinkedAccount(
                            userData.linkedAccounts,
                            providerId,
                            "",
                            email,
                            verificado = true,
                            timestamp = now
                        )
                        doc.reference.update(
                            "uid", uid,
                            "correo", email,
                            "linkedAccounts" to updatedLinked
                        )
                        val updatedUserData = userData.copy(uid = uid, correo = email, linkedAccounts = updatedLinked)
                        userSession.saveUser(updatedUserData)
                        _uiState.value = LoginUiState.Success
                    } else {
                        _uiState.value = LoginUiState.ExternalRegistrationNeeded(
                            email = email,
                            uid = uid,
                            providerId = providerId,
                            displayName = displayName.ifBlank { null }
                        )
                    }
                } else {
                    val nameParts = displayName.trim().split(Regex("\\s+"))
                    val firstName = nameParts.getOrNull(0) ?: ""
                    val secondName = if (nameParts.size >= 3) nameParts.getOrNull(1) ?: "" else ""
                    val lastName = if (nameParts.size >= 3) nameParts.getOrNull(2) ?: (nameParts.getOrNull(1) ?: "") else if (nameParts.size == 2) nameParts[1] else ""
                    val secondLastName = if (nameParts.size >= 4) nameParts.subList(3, nameParts.size).joinToString(" ") else ""

                    val now = System.currentTimeMillis()
                    val initialLinked = com.example.cuidaleapp.utils.AccountUtils.mergeLinkedAccount(
                        emptyList(),
                        providerId,
                        "",
                        email,
                        verificado = true,
                        timestamp = now
                    )
                    val ghostData = hashMapOf(
                        "uid" to uid,
                        "correo" to email,
                        "nombre" to firstName,
                        "nombre_s" to secondName,
                        "apellido_pa" to lastName,
                        "apellido_ma" to secondLastName,
                        "verificado" to true,
                        "completado" to false,
                        "linkedAccounts" to initialLinked,
                        "createdAt" to com.google.firebase.Timestamp(now / 1000, 0)
                    )
                    db.collection("usuarios").document(uid).set(ghostData).await()
                    _uiState.value = LoginUiState.ExternalRegistrationNeeded(
                        email = email,
                        uid = uid,
                        providerId = providerId,
                        displayName = displayName.ifBlank { null }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(parseLoginErrorMessage(e))
            }
        }
    }
}
