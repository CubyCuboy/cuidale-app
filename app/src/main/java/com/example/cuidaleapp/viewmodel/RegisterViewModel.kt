package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.ErrorMessages
import com.example.cuidaleapp.utils.HashUtils
import com.example.cuidaleapp.utils.PhoneUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class RegisterUiState {
    object Idle : RegisterUiState()
    object Loading : RegisterUiState()
    object Success : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var isExternalFlow = false
        private set
    private var externalUid = ""
    private var _externalPhotoUrl = ""

    fun initExternal(email: String, uid: String, provider: String, displayName: String = "", photoUrl: String = "") {
        isExternalFlow = true
        externalUid = uid
        _email.value = email
        if (photoUrl.isNotBlank()) _externalPhotoUrl = photoUrl

        val prefs = getApplication<Application>().getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
        val cachedEmail = prefs.getString("incomplete_email", "")
        val cachedProvider = prefs.getString("incomplete_provider", "")

        if (cachedEmail == email && cachedProvider == provider) {
            _role.value = prefs.getString("incomplete_role", "cuidador") ?: "cuidador"
            _nombre.value = prefs.getString("incomplete_nombre", "") ?: ""
            _nombreS.value = prefs.getString("incomplete_nombre_s", "") ?: ""
            _apellidoPa.value = prefs.getString("incomplete_apellido_pa", "") ?: ""
            _apellidoMa.value = prefs.getString("incomplete_apellido_ma", "") ?: ""
            _rut.value = prefs.getString("incomplete_rut", "") ?: ""
            _region.value = prefs.getString("incomplete_region", "") ?: ""
            _idRegion.value = prefs.getString("incomplete_id_region", "") ?: ""
            _comuna.value = prefs.getString("incomplete_comuna", "") ?: ""
            _telefono.value = prefs.getString("incomplete_telefono", "") ?: ""
            if (_externalPhotoUrl.isBlank()) {
                _externalPhotoUrl = prefs.getString("incomplete_fotoUrl", "") ?: ""
            }
        } else {
            clearIncompleteCache()

            if (displayName.isNotBlank()) {
                val parts = displayName.trim().split(Regex("\\s+"))
                val firstName = parts.getOrNull(0) ?: ""
                val secondName = if (parts.size >= 3) parts.getOrNull(1) ?: "" else ""
                val lastName = if (parts.size >= 3) parts.getOrNull(2) ?: (parts.getOrNull(1) ?: "") else if (parts.size == 2) parts[1] else ""
                val secondLastName = if (parts.size >= 4) parts.subList(3, parts.size).joinToString(" ") else ""

                _nombre.value = capitalizeWords(firstName)
                _nombreS.value = capitalizeWords(secondName)
                _apellidoPa.value = capitalizeWords(lastName)
                _apellidoMa.value = capitalizeWords(secondLastName)
            }

            prefs.edit()
                .putString("incomplete_email", email)
                .putString("incomplete_provider", provider)
                .putString("incomplete_nombre", _nombre.value)
                .putString("incomplete_nombre_s", _nombreS.value)
                .putString("incomplete_apellido_pa", _apellidoPa.value)
                .putString("incomplete_apellido_ma", _apellidoMa.value)
                .putString("incomplete_fotoUrl", _externalPhotoUrl)
                .apply()
        }

        viewModelScope.launch {
            try {
                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                if (query.isEmpty) {
                    val ghostData = hashMapOf(
                        "uid" to uid,
                        "correo" to email,
                        "verificado" to true,
                        "completado" to false,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("usuarios").document(uid).set(ghostData).await()
                }
            } catch (e: Exception) {}
        }
    }

    fun saveIncompleteCache() {
        val prefs = getApplication<Application>().getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("incomplete_role", _role.value)
            .putString("incomplete_nombre", _nombre.value)
            .putString("incomplete_nombre_s", _nombreS.value)
            .putString("incomplete_apellido_pa", _apellidoPa.value)
            .putString("incomplete_apellido_ma", _apellidoMa.value)
            .putString("incomplete_rut", _rut.value)
            .putString("incomplete_region", _region.value)
            .putString("incomplete_id_region", _idRegion.value)
            .putString("incomplete_comuna", _comuna.value)
            .putString("incomplete_telefono", _telefono.value)
            .apply()
    }

    fun clearIncompleteCache() {
        val prefs = getApplication<Application>().getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("incomplete_email")
            .remove("incomplete_provider")
            .remove("incomplete_role")
            .remove("incomplete_nombre")
            .remove("incomplete_nombre_s")
            .remove("incomplete_apellido_pa")
            .remove("incomplete_apellido_ma")
            .remove("incomplete_rut")
            .remove("incomplete_region")
            .remove("incomplete_id_region")
            .remove("incomplete_comuna")
            .remove("incomplete_telefono")
            .apply()
    }

    private fun providerIdFromPrefs(): String {
        val prefs = getApplication<Application>().getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
        return prefs.getString("incomplete_provider", "")?.ifBlank { null }
            ?: com.example.cuidaleapp.utils.AccountUtils.PROVIDER_GOOGLE
    }

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _role = MutableStateFlow("cuidador")
    val role: StateFlow<String> = _role.asStateFlow()

    private val _nombre = MutableStateFlow("")
    val nombre: StateFlow<String> = _nombre.asStateFlow()

    private val _nombreS = MutableStateFlow("")
    val nombreS: StateFlow<String> = _nombreS.asStateFlow()

    private val _apellidoPa = MutableStateFlow("")
    val apellidoPa: StateFlow<String> = _apellidoPa.asStateFlow()

    private val _apellidoMa = MutableStateFlow("")
    val apellidoMa: StateFlow<String> = _apellidoMa.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _rut = MutableStateFlow("")
    val rut: StateFlow<String> = _rut.asStateFlow()

    private val _region = MutableStateFlow("")
    val region: StateFlow<String> = _region.asStateFlow()

    private val _idRegion = MutableStateFlow("")
    val idRegion: StateFlow<String> = _idRegion.asStateFlow()

    private val _comuna = MutableStateFlow("")
    val comuna: StateFlow<String> = _comuna.asStateFlow()

    private val _telefono = MutableStateFlow("")
    val telefono: StateFlow<String> = _telefono.asStateFlow()

    val isEmailValid: StateFlow<Boolean> = _email
        .map { validateEmail(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isRutValid: StateFlow<Boolean> = _rut
        .map { validateRut(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isFormValid: StateFlow<Boolean> = combine(_nombre, _apellidoPa, _email, _rut, _telefono) { nombre, apellido, email, rut, telefono ->
        val phoneValid = if (telefono.isEmpty()) true else PhoneUtils.isValidChileanPhone(telefono)
        nombre.trim().length >= 2 && 
        apellido.trim().length >= 2 && 
        validateEmail(email) && 
        validateRut(rut) &&
        phoneValid
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun validateEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return false
        val pattern = android.util.Patterns.EMAIL_ADDRESS
        if (!pattern.matcher(trimmed).matches()) return false
        
        val parts = trimmed.split("@")
        if (parts.size != 2) return false
        val domain = parts[1]
        return domain.contains(".") && domain.substringAfterLast(".").length >= 2
    }

    fun onRoleChange(newRole: String) { 
        _role.value = newRole 
        saveIncompleteCache()
        clearError()
    }
    fun onNombreChange(newName: String) { 
        _nombre.value = capitalizeWords(newName)
        saveIncompleteCache()
        clearError()
    }
    fun onNombreSChange(newNameS: String) { 
        _nombreS.value = capitalizeWords(newNameS)
        saveIncompleteCache()
        clearError()
    }
    fun onApellidoPaChange(newApellidoPa: String) { 
        _apellidoPa.value = capitalizeWords(newApellidoPa)
        saveIncompleteCache()
        clearError()
    }
    fun onApellidoMaChange(newApellidoMa: String) { 
        _apellidoMa.value = capitalizeWords(newApellidoMa)
        saveIncompleteCache()
        clearError()
    }
    fun onEmailChange(newEmail: String) { 
        if (!isExternalFlow) {
            _email.value = newEmail.trim().lowercase()
            clearError()
        }
    }
    fun onPasswordChange(newPassword: String) { 
        _password.value = newPassword
        clearError()
    }
    fun onRutChange(newRut: String) { 
        _rut.value = newRut.uppercase().trim()
        saveIncompleteCache()
        clearError()
    }

    private fun capitalizeWords(text: String): String {
        return text.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
    fun onRegionChange(newRegion: String, newIdRegion: String = "99") { 
        _region.value = newRegion 
        _idRegion.value = newIdRegion
        saveIncompleteCache()
    }
    fun onComunaChange(newComuna: String) { 
        _comuna.value = newComuna 
        saveIncompleteCache()
    }

    fun onTelefonoChange(newTelefono: String) {
        val clean = newTelefono.replace(Regex("[^0-9]"), "")
        if (clean.length <= 9) {
            _telefono.value = clean
            saveIncompleteCache()
        }
    }

    private fun formatRutForSaving(rut: String): String {
        val clean = rut.replace(".", "").replace("-", "").uppercase()
        if (clean.length < 2) return clean
        val body = clean.substring(0, clean.length - 1)
        val dv = clean.substring(clean.length - 1)
        return "$body-$dv"
    }

    private fun validateRut(rut: String): Boolean {
        val cleanRut = rut.replace(".", "").replace("-", "").replace(" ", "").uppercase()
        if (cleanRut.length < 7 || cleanRut.length > 9) return false
        val body = cleanRut.substring(0, cleanRut.length - 1)
        val dv = cleanRut.substring(cleanRut.length - 1)
        
        return try {
            val mBody = body.toInt()
            calculateDV(mBody) == dv
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateDV(rut: Int): String {
        var mRut = rut
        var m = 0
        var s = 1
        while (mRut != 0) {
            s = (s + mRut % 10 * (9 - m++ % 6)) % 11
            mRut /= 10
        }
        return if (s != 0) (s + 47).toChar().toString() else "K"
    }

    private fun parseErrorMessage(e: Exception): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        val app = getApplication<Application>()
        return when {
            msg.contains("El RUT ya se encuentra registrado", ignoreCase = true) ->
                "El RUT ingresado ya se encuentra registrado por otro usuario"
            msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("already in use", ignoreCase = true) ->
                app.getString(com.example.cuidaleapp.R.string.error_email_in_use)
            msg.contains("network-request-failed", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true) ||
            msg.contains("host", ignoreCase = true) ||
            e is com.google.firebase.FirebaseNetworkException ||
            e is java.net.UnknownHostException ||
            e is java.io.IOException ->
                app.getString(com.example.cuidaleapp.R.string.error_network)
            msg.contains("weak-password", ignoreCase = true) ->
                app.getString(com.example.cuidaleapp.R.string.error_weak_password)
            msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                app.getString(com.example.cuidaleapp.R.string.error_permissions)
            else -> ErrorMessages.get(app, msg)
        }
    }

    suspend fun validateRutAvailability(): Boolean {
        val formattedRut = formatRutForSaving(_rut.value)
        if (formattedRut.isBlank()) {
            _uiState.value = RegisterUiState.Error("Por favor ingresa un RUT válido")
            return false
        }

        // Sin sesión activa (registro tradicional): se consulta la Cloud Function
        // pública resolveAccountByRut (busca por RUT sin requerir autenticación ni
        // que el cliente calcule el hash). Así no se lee Firestore directamente.
        if (auth.currentUser == null) {
            return try {
                val functions = FirebaseFunctions.getInstance("southamerica-west1")
                val result = functions.getHttpsCallable("resolveAccountByRut")
                    .call(mapOf("rut" to formattedRut))
                    .await()
                val data = result.data as? Map<*, *>
                if (data?.get("ok") == true) {
                    val verified = data["verified"] == true
                    _uiState.value = RegisterUiState.Error(
                        if (verified) "El RUT ingresado ya se encuentra registrado y verificado."
                        else "El RUT ingresado ya se encuentra registrado por otro usuario"
                    )
                    false
                } else {
                    true
                }
            } catch (e: Exception) {
                // No encontrado -> RUT disponible
                true
            }
        }

        // Con sesión activa (flujo externo): lectura normal sobre Firestore.
        val hashedRut = HashUtils.hashRut(formattedRut)
        return try {
            val docCheck = db.collection("usuarios").document(hashedRut).get().await()
            if (docCheck.exists()) {
                val existing = docCheck.toObject(UserData::class.java)
                if (isExternalFlow && existing?.uid == externalUid) {
                    true
                } else {
                    _uiState.value = RegisterUiState.Error("El RUT ingresado ya se encuentra registrado por otro usuario")
                    false
                }
            } else {
                true
            }
        } catch (e: Exception) {
            _uiState.value = RegisterUiState.Error(parseErrorMessage(e))
            false
        }
    }

    fun register() {
        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            try {
                val formattedRut = formatRutForSaving(_rut.value)

                // Primero se crea la cuenta de Auth para quedar autenticado y poder
                // leer/escribir en Firestore según las reglas de seguridad (isAuthenticated).
                val result = auth.createUserWithEmailAndPassword(_email.value, _password.value).await()
                val uid = result.user?.uid ?: throw Exception("No se pudo obtener el ID de usuario")

                // Verificación post-autenticación: el RUT no debe estar en uso.
                // Se busca por el campo "rut" (sin calcular hash en el cliente).
                val rutConflicts = db.collection("usuarios").whereEqualTo("rut", formattedRut).get().await()
                if (!rutConflicts.isEmpty) {
                    // El RUT ya pertenece a otra cuenta: se revierte la cuenta creada.
                    runCatching { result.user?.delete()?.await() }
                    throw Exception("El RUT ya se encuentra registrado por otro usuario")
                }

                val now = System.currentTimeMillis()
                val entryId = java.util.UUID.randomUUID().toString()
                val initialEntry = com.example.cuidaleapp.models.AccountHistoryEntry(
                    id = entryId,
                    action = "account_created",
                    title = "Cuenta Creada",
                    description = "Creación e inicio de cuenta con Correo y Contraseña (${_email.value.trim().lowercase()})",
                    timestamp = now
                )
                val initialHistory = listOf(initialEntry)

                val nowTimestamp = com.google.firebase.Timestamp(now / 1000, 0)
                val passwordEmail = _email.value.trim().lowercase()
                val initialLinked = listOf(
                    com.example.cuidaleapp.models.LinkedAccountDetail(
                        providerId = "email",
                        providerName = "Correo y Contraseña",
                        accountName = passwordEmail,
                        linkedAt = now,
                        verificado = false
                    )
                )
                val userData = hashMapOf(
                    "uid" to uid,
                    "rol" to _role.value,
                    "nombre" to _nombre.value.trim(),
                    "nombre_s" to _nombreS.value.trim(),
                    "apellido_pa" to _apellidoPa.value.trim(),
                    "apellido_ma" to _apellidoMa.value.trim(),
                    "correo" to _email.value.trim().lowercase(),
                    "rut" to formattedRut,
                    "telefono" to PhoneUtils.formatChileanPhone(_telefono.value),
                    "region" to _region.value,
                    "id_region" to _idRegion.value,
                    "comuna" to _comuna.value,
                    "verificado" to false,
                    "completado" to true,
                    "linkedAccounts" to initialLinked,
                    "accountHistory" to initialHistory,
                    "createdAt" to nowTimestamp,
                    "updatedAt" to nowTimestamp
                )

                // El documento canónico del usuario se guarda en usuarios/{hashedRut}
                val hashedRut = HashUtils.hashRut(formattedRut)
                val userDocRef = db.collection("usuarios").document(hashedRut)
                userDocRef.set(userData).await()

                val userObj = UserData(
                    uid = uid,
                    rol = _role.value,
                    nombre = _nombre.value.trim(),
                    nombre_s = _nombreS.value.trim(),
                    apellido_pa = _apellidoPa.value.trim(),
                    apellido_ma = _apellidoMa.value.trim(),
                    correo = _email.value.trim().lowercase(),
                    rut = formattedRut,
                    telefono = PhoneUtils.formatChileanPhone(_telefono.value),
                    region = _region.value,
                    id_region = _idRegion.value,
                    comuna = _comuna.value,
                    verificado = false,
                    completado = true,
                    createdAt = nowTimestamp,
                    updatedAt = nowTimestamp,
                    linkedAccounts = initialLinked,
                    accountHistory = initialHistory
                )
                UserSession(getApplication()).saveUser(userObj)
                auth.signOut()

                _uiState.value = RegisterUiState.Success
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(parseErrorMessage(e))
            }
        }
    }

    fun registerExternal() {
        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            try {
                val formattedRut = formatRutForSaving(_rut.value)
                val hashedRut = HashUtils.hashRut(formattedRut)
                val emailNorm = _email.value.trim().lowercase()

                // Anti-duplicación: si ya existe una cuenta canónica con este correo,
                // se le linkea el proveedor nuevo en lugar de crear una copia.
                val existingCanonical = com.example.cuidaleapp.utils.AccountUtils.findCanonicalUserDocument(
                    db = db,
                    email = emailNorm,
                    uid = externalUid
                )
                val existingUser = existingCanonical?.toObject(UserData::class.java)
                if (existingCanonical != null && existingUser?.completado == true) {
                    val providerId = providerIdFromPrefs()
                    val merged = com.example.cuidaleapp.utils.AccountUtils.attachProviderToExistingAccount(
                        doc = existingCanonical,
                        providerId = providerId,
                        accountName = emailNorm,
                        verified = true,
                        newUid = externalUid
                    ) ?: existingUser
                    com.example.cuidaleapp.utils.AccountUtils.cleanupDuplicateGhostDocs(
                        db = db,
                        canonicalRef = existingCanonical.reference,
                        canonicalId = existingCanonical.id,
                        email = emailNorm,
                        uid = externalUid
                    )
                    // Sincronizar foto de perfil externa si el canónico no tiene una.
                    if (_externalPhotoUrl.isNotBlank() && merged.fotoUrl.isBlank()) {
                        runCatching { existingCanonical.reference.update("fotoUrl", _externalPhotoUrl).await() }
                    }
                    val finalUser = merged.copy(uid = externalUid, fotoUrl = if (merged.fotoUrl.isNotBlank()) merged.fotoUrl else _externalPhotoUrl)
                    UserSession(getApplication()).saveUser(finalUser)
                    clearIncompleteCache()
                    _uiState.value = RegisterUiState.Success
                    return@launch
                }

                val docCheck = db.collection("usuarios").document(hashedRut).get().await()
                if (docCheck.exists()) {
                    val existing = docCheck.toObject(UserData::class.java)
                    if (existing?.uid != externalUid) {
                        throw Exception("El RUT ya se encuentra registrado por otro usuario")
                    }
                }

                val now = System.currentTimeMillis()
                val nowTimestamp = com.google.firebase.Timestamp(now / 1000, 0)
                val entryId = java.util.UUID.randomUUID().toString()
                val prefs = getApplication<Application>().getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
                val providerId = providerIdFromPrefs()
                val providerName = when (providerId) {
                    com.example.cuidaleapp.utils.AccountUtils.PROVIDER_MICROSOFT -> com.example.cuidaleapp.utils.AccountUtils.PROVIDER_DISPLAY_MICROSOFT
                    com.example.cuidaleapp.utils.AccountUtils.PROVIDER_GOOGLE -> com.example.cuidaleapp.utils.AccountUtils.PROVIDER_DISPLAY_GOOGLE
                    else -> providerId
                }

                val initialEntry = com.example.cuidaleapp.models.AccountHistoryEntry(
                    id = entryId,
                    action = "account_created",
                    title = "Cuenta Creada",
                    description = "Creación e inicio de cuenta con $providerName (${_email.value.trim().lowercase()})",
                    timestamp = now
                )
                val initialHistory = listOf(initialEntry)

                val providerIdCanonical = com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(providerId)
                val initialLinked = listOf(
                    com.example.cuidaleapp.models.LinkedAccountDetail(
                        providerId = providerIdCanonical,
                        providerName = providerName,
                        accountName = _email.value.trim().lowercase(),
                        linkedAt = now,
                        verificado = true
                    )
                )

                val userData = hashMapOf(
                    "uid" to externalUid,
                    "rol" to _role.value,
                    "nombre" to _nombre.value.trim(),
                    "nombre_s" to _nombreS.value.trim(),
                    "apellido_pa" to _apellidoPa.value.trim(),
                    "apellido_ma" to _apellidoMa.value.trim(),
                    "correo" to _email.value.trim().lowercase(),
                    "rut" to formattedRut,
                    "telefono" to PhoneUtils.formatChileanPhone(_telefono.value),
                    "region" to _region.value,
                    "id_region" to _idRegion.value,
                    "comuna" to _comuna.value,
                    "verificado" to true,
                    "completado" to true,
                    "linkedAccounts" to initialLinked,
                    "accountHistory" to initialHistory,
                    "createdAt" to nowTimestamp,
                    "updatedAt" to nowTimestamp
                ).apply {
                    if (_externalPhotoUrl.isNotBlank()) put("fotoUrl", _externalPhotoUrl)
                }

                val userDocRef = db.collection("usuarios").document(hashedRut)
                userDocRef.set(userData, com.google.firebase.firestore.SetOptions.merge()).await()

                try {
                    val tempDoc = db.collection("usuarios").document(externalUid).get().await()
                    if (tempDoc.exists() && externalUid != hashedRut) {
                        db.collection("usuarios").document(externalUid).delete().await()
                    }
                } catch (e: Exception) {}

                clearIncompleteCache()

                val userObj = UserData(
                    uid = externalUid,
                    rol = _role.value,
                    nombre = _nombre.value.trim(),
                    nombre_s = _nombreS.value.trim(),
                    apellido_pa = _apellidoPa.value.trim(),
                    apellido_ma = _apellidoMa.value.trim(),
                    correo = _email.value.trim().lowercase(),
                    rut = formattedRut,
                    telefono = PhoneUtils.formatChileanPhone(_telefono.value),
                    region = _region.value,
                    id_region = _idRegion.value,
                    comuna = _comuna.value,
                    fotoUrl = _externalPhotoUrl,
                    verificado = true,
                    completado = true,
                    createdAt = nowTimestamp,
                    updatedAt = nowTimestamp,
                    linkedAccounts = initialLinked,
                    accountHistory = initialHistory
                )
                UserSession(getApplication()).saveUser(userObj)

                _uiState.value = RegisterUiState.Success
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(parseErrorMessage(e))
            }
        }
    }

    fun clearError() {
        if (_uiState.value is RegisterUiState.Error) {
            _uiState.value = RegisterUiState.Idle
        }
    }
}
