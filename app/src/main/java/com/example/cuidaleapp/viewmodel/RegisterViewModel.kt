package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class RegisterUserData(
    val rol: String = "",
    val nombre: String = "",
    val nombre_s: String = "",
    val apellido_pa: String = "",
    val apellido_ma: String = "",
    val correo: String = "",
    val rut: String = "",
    val region: String = "",
    val comuna: String = "",
    val id_region: String = ""
)

data class VerificationUiState(
    val attemptsUsed: Int = 0,
    val maxAttempts: Int = 5,
    val remainingAttempts: Int = 5,
    val verified: Boolean = false,
    val codeExists: Boolean = false,
    val createdAtMillis: Long? = null,
    val expiresAtMillis: Long? = null,
    val remainingSeconds: Int = 0,
    val isExpired: Boolean = false
)

sealed class RegisterStep {
    object PersonalData : RegisterStep()
    object GeographicData : RegisterStep()
    object Password : RegisterStep()
    object Verification : RegisterStep()
    object Success : RegisterStep()
}

sealed class RegisterStatus {
    object Idle : RegisterStatus()
    object Loading : RegisterStatus()
    object Success : RegisterStatus()
    data class Error(val message: String) : RegisterStatus()
}

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(
        "cuidale_registration_cache",
        Context.MODE_PRIVATE
    )

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("southamerica-west1")

    private val _currentStep = MutableStateFlow<RegisterStep>(RegisterStep.PersonalData)
    val currentStep: StateFlow<RegisterStep> = _currentStep

    private val _status = MutableStateFlow<RegisterStatus>(RegisterStatus.Idle)
    val status: StateFlow<RegisterStatus> = _status

    private val _userData = MutableStateFlow(loadUserData())
    val userData: StateFlow<RegisterUserData> = _userData

    private val _timerSeconds = MutableStateFlow(1800)
    val timerSeconds: StateFlow<Int> = _timerSeconds

    private val _verificationState = MutableStateFlow(loadVerificationCache())
    val verificationState: StateFlow<VerificationUiState> = _verificationState

    private val _unmetRequirementsTrigger = MutableStateFlow<Set<Int>>(emptySet())
    val unmetRequirementsTrigger: StateFlow<Set<Int>> = _unmetRequirementsTrigger

    private var timerJob: Job? = null

    init {
        val savedStep = prefs.getString("current_step", "")
        if (!savedStep.isNullOrEmpty() && _userData.value.rol.isNotEmpty()) {
            _currentStep.value = when (savedStep) {
                "GeographicData" -> RegisterStep.GeographicData
                "Password" -> RegisterStep.Password
                "Verification" -> RegisterStep.Verification
                "Success" -> RegisterStep.Success
                else -> RegisterStep.PersonalData
            }

            if (_currentStep.value == RegisterStep.Verification) {
                bootstrapVerificationState()
            }
        }
    }

    private fun loadUserData(): RegisterUserData {
        return RegisterUserData(
            rol = prefs.getString("temp_rol", "") ?: "",
            nombre = prefs.getString("temp_nombre", "") ?: "",
            nombre_s = prefs.getString("temp_nombre_s", "") ?: "",
            apellido_pa = prefs.getString("temp_apellido_pa", "") ?: "",
            apellido_ma = prefs.getString("temp_apellido_ma", "") ?: "",
            correo = prefs.getString("temp_correo", "") ?: "",
            rut = prefs.getString("temp_rut", "") ?: "",
            region = prefs.getString("temp_region", "") ?: "",
            comuna = prefs.getString("temp_comuna", "") ?: "",
            id_region = prefs.getString("temp_id_region", "") ?: ""
        )
    }

    private fun saveUserDataToCache(data: RegisterUserData) {
        prefs.edit().apply {
            putString("temp_rol", data.rol)
            putString("temp_nombre", data.nombre)
            putString("temp_nombre_s", data.nombre_s)
            putString("temp_apellido_pa", data.apellido_pa)
            putString("temp_apellido_ma", data.apellido_ma)
            putString("temp_correo", data.correo)
            putString("temp_rut", data.rut)
            putString("temp_region", data.region)
            putString("temp_comuna", data.comuna)
            putString("temp_id_region", data.id_region)
            apply()
        }
    }

    private fun loadVerificationCache(): VerificationUiState {
        val codeExists = prefs.getBoolean("verification_code_exists", false)
        val attemptsUsed = prefs.getInt("verification_attempts_used", 0)
        val maxAttempts = prefs.getInt("verification_max_attempts", 5)
        val verified = prefs.getBoolean("verification_verified", false)
        val createdAt = prefs.getLong("verification_created_at", 0L).takeIf { it > 0L }
        val expiresAt = prefs.getLong("verification_expires_at", 0L).takeIf { it > 0L }

        val remainingSeconds = expiresAt?.let {
            ((it - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        } ?: 0

        val isExpired = expiresAt?.let { System.currentTimeMillis() > it } ?: false

        return VerificationUiState(
            attemptsUsed = attemptsUsed,
            maxAttempts = maxAttempts,
            remainingAttempts = (maxAttempts - attemptsUsed).coerceAtLeast(0),
            verified = verified,
            codeExists = codeExists,
            createdAtMillis = createdAt,
            expiresAtMillis = expiresAt,
            remainingSeconds = remainingSeconds,
            isExpired = isExpired
        )
    }

    private fun saveVerificationCache(state: VerificationUiState) {
        prefs.edit().apply {
            putBoolean("verification_code_exists", state.codeExists)
            putInt("verification_attempts_used", state.attemptsUsed)
            putInt("verification_max_attempts", state.maxAttempts)
            putBoolean("verification_verified", state.verified)
            if (state.createdAtMillis != null) putLong("verification_created_at", state.createdAtMillis)
            else remove("verification_created_at")
            if (state.expiresAtMillis != null) putLong("verification_expires_at", state.expiresAtMillis)
            else remove("verification_expires_at")
            apply()
        }
    }

    private fun clearVerificationCache() {
        prefs.edit().apply {
            remove("verification_code_exists")
            remove("verification_attempts_used")
            remove("verification_max_attempts")
            remove("verification_verified")
            remove("verification_created_at")
            remove("verification_expires_at")
            apply()
        }
        _verificationState.value = VerificationUiState()
    }

    fun setRole(role: String) {
        val newData = _userData.value.copy(rol = role)
        _userData.value = newData
        saveUserDataToCache(newData)
    }

    fun prepareForVerification(email: String) {
        val normalizedEmail = email.trim().lowercase()
        // Actualizamos estado local inmediatamente para evitar UI vacía o saltos de paso
        if (_userData.value.correo != normalizedEmail) {
            _userData.value = _userData.value.copy(correo = normalizedEmail)
        }
        _currentStep.value = RegisterStep.Verification

        viewModelScope.launch {
            _status.value = RegisterStatus.Loading
            try {
                val query = db.collection("usuarios")
                    .whereEqualTo("correo", normalizedEmail)
                    .get()
                    .await()

                if (!query.isEmpty) {
                    val userDoc = query.documents.firstOrNull()
                    val data = userDoc?.toObject(RegisterUserData::class.java)
                    if (data != null) {
                        _userData.value = data
                        saveUserDataToCache(data)
                        saveCurrentStep(RegisterStep.Verification)
                    }
                }
                
                // Intentamos bootstrap siempre, ya que depende del correo que ya tenemos
                bootstrapVerificationState()
                _status.value = RegisterStatus.Idle
            } catch (e: Exception) {
                bootstrapVerificationState()
                _status.value = RegisterStatus.Idle // No bloqueamos por error de carga de perfil
            }
        }
    }

    fun updatePersonalData(
        fullNames: String,
        fullApellidos: String,
        email: String,
        rut: String,
        onSuccess: () -> Unit
    ) {
        if (fullNames.isBlank() || fullApellidos.isBlank() || email.isBlank() || rut.isBlank()) {
            _status.value = RegisterStatus.Error("Todos los campos son obligatorios")
            return
        }

        val apellidoWords = fullApellidos.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }

        if (apellidoWords.size < 2) {
            _status.value = RegisterStatus.Error("Debe ingresar sus dos apellidos")
            return
        }

        if (!isValidEmail(email)) {
            _status.value = RegisterStatus.Error("Use un correo válido (.com, .cl o .org)")
            return
        }

        if (!isValidRut(rut)) {
            _status.value = RegisterStatus.Error("RUT inválido")
            return
        }

        val normalizedEmail = email.lowercase().trim()
        val normalizedRut = formatRut(rut)

        viewModelScope.launch {
            _status.value = RegisterStatus.Loading
            try {
                val emailQuery = db.collection("usuarios")
                    .whereEqualTo("correo", normalizedEmail)
                    .get()
                    .await()

                if (!emailQuery.isEmpty) {
                    _status.value = RegisterStatus.Error("El correo ya se encuentra registrado")
                    return@launch
                }

                val rutQuery = db.collection("usuarios")
                    .whereEqualTo("rut", normalizedRut)
                    .get()
                    .await()

                if (!rutQuery.isEmpty) {
                    _status.value = RegisterStatus.Error("El RUT ya se encuentra registrado")
                    return@launch
                }

                val (n1, n2) = splitInput(capitalizeWords(fullNames))
                val (a1, a2) = splitInput(capitalizeWords(fullApellidos))

                val updatedData = _userData.value.copy(
                    nombre = n1,
                    nombre_s = if (n2.isEmpty()) "N/A" else n2,
                    apellido_pa = a1,
                    apellido_ma = if (a2.isEmpty()) "N/A" else a2,
                    correo = normalizedEmail,
                    rut = normalizedRut
                )

                _userData.value = updatedData
                saveUserDataToCache(updatedData)

                _status.value = RegisterStatus.Idle
                onSuccess()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al validar datos: ${e.message}")
            }
        }
    }

    fun onRutChanged(newRut: String) {
        val formatted = formatRutRealTime(newRut)
        _userData.value = _userData.value.copy(rut = formatted)
    }

    private fun formatRutRealTime(rut: String): String {
        val clean = rut.replace(".", "").replace("-", "").uppercase()
            .filter { it.isLetterOrDigit() }

        if (clean.isEmpty()) return ""

        val body = if (clean.length > 1) clean.substring(0, clean.length - 1) else ""
        val dv = clean.substring(clean.length - 1)

        if (body.isEmpty()) return dv

        val reversedBody = body.reversed()
        val sb = StringBuilder()
        for (i in reversedBody.indices) {
            if (i > 0 && i % 3 == 0) sb.append(".")
            sb.append(reversedBody[i])
        }
        return "${sb.reverse()}-$dv"
    }

    private fun capitalizeWords(input: String): String {
        if (input.isBlank()) return ""
        return input.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
            }
    }

    private fun formatRut(rut: String): String {
        val clean = rut.replace(".", "").replace("-", "").uppercase().trim()
        if (clean.length < 2) return clean
        val body = clean.substring(0, clean.length - 1)
        val dv = clean.substring(clean.length - 1)
        return "$body-$dv"
    }

    fun updateGeographicData(
        regionName: String,
        comunaName: String,
        regionId: String
    ): String? {
        if (regionName.isBlank() || comunaName.isBlank()) {
            return "Debe seleccionar una región y una comuna"
        }

        val updatedData = _userData.value.copy(
            region = regionName,
            comuna = comunaName,
            id_region = regionId
        )
        _userData.value = updatedData
        saveUserDataToCache(updatedData)

        return null
    }

    private fun splitInput(input: String): Pair<String, String> {
        val words = input.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> Pair(words[0], words.drop(1).joinToString(" "))
            words.size == 1 -> Pair(words[0], "N/A")
            else -> Pair("", "N/A")
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val pattern = Patterns.EMAIL_ADDRESS
        val isValidFormat = pattern.matcher(email).matches()
        val allowedDomains = listOf(".com", ".cl", ".org")
        val hasAllowedDomain = allowedDomains.any { email.lowercase().endsWith(it) }
        return isValidFormat && hasAllowedDomain
    }

    private fun isValidRut(rut: String): Boolean {
        val cleanRut = rut.replace(".", "").replace("-", "").trim()
        if (cleanRut.length < 8 || cleanRut.length > 9) return false

        val body = cleanRut.substring(0, cleanRut.length - 1)
        val dvInput = cleanRut.substring(cleanRut.length - 1).uppercase()

        return try {
            val expectedDv = calculateRutDv(body.toInt())
            dvInput == expectedDv
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateRutDv(rut: Int): String {
        var m = 0
        var s = 1
        var t = rut
        while (t != 0) {
            s = (s + t % 10 * (9 - m++ % 6)) % 11
            t /= 10
        }
        return if (s != 0) (s - 1).toString() else "K"
    }

    fun goToStep(step: RegisterStep) {
        _currentStep.value = step
        saveCurrentStep(step)

        if (step == RegisterStep.Verification) {
            bootstrapVerificationState()
        }
    }

    private fun saveCurrentStep(step: RegisterStep) {
        val stepName = when (step) {
            RegisterStep.GeographicData -> "GeographicData"
            RegisterStep.Password -> "Password"
            RegisterStep.Verification -> "Verification"
            RegisterStep.Success -> "Success"
            else -> "PersonalData"
        }
        // Usamos commit() para asegurar que el cambio de paso sea persistente inmediatamente
        prefs.edit().putString("current_step", stepName).commit()
    }

    fun finalizeRegistration(password: String) {
        val hasMinLength = password.length >= 8
        val hasUppercase = password.any { it.isUpperCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        val unmet = mutableSetOf<Int>()
        if (!hasMinLength) unmet.add(0)
        if (!hasUppercase) unmet.add(1)
        if (!hasSpecialChar) unmet.add(2)

        if (unmet.isNotEmpty()) {
            viewModelScope.launch {
                _unmetRequirementsTrigger.value = unmet
                _status.value = RegisterStatus.Error("La contraseña no cumple con todos los requisitos.")
                delay(2000)
                _unmetRequirementsTrigger.value = emptySet()
            }
            return
        }

        viewModelScope.launch {
            _status.value = RegisterStatus.Loading
            try {
                val data = _userData.value
                val authResult = auth.createUserWithEmailAndPassword(data.correo, password).await()
                val uid = authResult.user?.uid ?: throw Exception("Error al obtener ID de usuario")

                val userProfile = hashMapOf(
                    "nombre" to data.nombre,
                    "nombre_s" to data.nombre_s,
                    "apellido_pa" to data.apellido_pa,
                    "apellido_ma" to data.apellido_ma,
                    "correo" to data.correo,
                    "rut" to data.rut,
                    "region" to data.region,
                    "comuna" to data.comuna,
                    "id_region" to data.id_region,
                    "rol" to data.rol,
                    "verificado" to false
                )

                db.collection("usuarios").document(uid).set(userProfile).await()

                val functionData = hashMapOf(
                    "email" to data.correo,
                    "type" to "verification",
                    "uid" to uid
                )

                val result = functions.getHttpsCallable("sendVerificationCode").call(functionData).await()
                val resultMap = result.data as? Map<*, *>
                val expiresAtMillis = (resultMap?.get("expiresAt") as? Number)?.toLong()
                    ?: (System.currentTimeMillis() + 1800 * 1000L)
                val maxAttempts = (resultMap?.get("maxAttempts") as? Number)?.toInt() ?: 5

                val verificationState = VerificationUiState(
                    attemptsUsed = 0,
                    maxAttempts = maxAttempts,
                    remainingAttempts = maxAttempts,
                    verified = false,
                    codeExists = true,
                    createdAtMillis = System.currentTimeMillis(),
                    expiresAtMillis = expiresAtMillis,
                    remainingSeconds = ((expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0),
                    isExpired = false
                )

                _verificationState.value = verificationState
                saveVerificationCache(verificationState)

                // Aseguramos que el status sea Idle antes de cambiar el paso para evitar bloqueos visuales
                _status.value = RegisterStatus.Idle
                
                _currentStep.value = RegisterStep.Verification
                saveCurrentStep(RegisterStep.Verification)
                startTimerFromState(verificationState)
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al registrar: ${e.message}")
            }
        }
    }

    fun resendCode() {
        _status.value = RegisterStatus.Loading
        viewModelScope.launch {
            try {
                val email = currentEmail()
                val uid = auth.currentUser?.uid.orEmpty()

                val data = hashMapOf(
                    "email" to email,
                    "type" to "verification",
                    "uid" to uid
                )

                val result = functions.getHttpsCallable("sendVerificationCode").call(data).await()
                val resultMap = result.data as? Map<*, *>
                val expiresAtMillis = (resultMap?.get("expiresAt") as? Number)?.toLong()
                    ?: (System.currentTimeMillis() + 1800 * 1000L)
                val maxAttempts = (resultMap?.get("maxAttempts") as? Number)?.toInt() ?: 5

                val newState = VerificationUiState(
                    attemptsUsed = 0,
                    maxAttempts = maxAttempts,
                    remainingAttempts = maxAttempts,
                    verified = false,
                    codeExists = true,
                    createdAtMillis = System.currentTimeMillis(),
                    expiresAtMillis = expiresAtMillis,
                    remainingSeconds = ((expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0),
                    isExpired = false
                )

                _verificationState.value = newState
                saveVerificationCache(newState)
                _status.value = RegisterStatus.Idle
                startTimerFromState(newState)

                syncVerificationState()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("No se pudo reenviar el código: ${e.message}")
            }
        }
    }

    fun verifyCode(code: String) {
        if (!Regex("^\\d{6}$").matches(code)) {
            _status.value = RegisterStatus.Error("El código debe tener 6 dígitos.")
            return
        }

        viewModelScope.launch {
            _status.value = RegisterStatus.Loading
            try {
                val email = currentEmail()
                val data = hashMapOf(
                    "email" to email,
                    "code" to code
                )

                functions.getHttpsCallable("verifyCode").call(data).await()

                timerJob?.cancel()
                clearVerificationCache()
                
                // Cerramos sesión después de verificar para obligar login manual
                auth.signOut()
                
                _status.value = RegisterStatus.Success
                _currentStep.value = RegisterStep.Success
                saveCurrentStep(RegisterStep.Success)
            } catch (e: FirebaseFunctionsException) {
                syncVerificationState()
                _status.value = RegisterStatus.Error(buildVerifyErrorMessage(e))
            } catch (e: Exception) {
                syncVerificationState()
                _status.value = RegisterStatus.Error("Código inválido o expirado.")
            }
        }
    }

    fun bootstrapVerificationState() {
        val cachedState = loadVerificationCache()
        _verificationState.value = cachedState

        if (cachedState.codeExists && !cachedState.verified && !cachedState.isExpired) {
            startTimerFromState(cachedState)
        } else if (cachedState.isExpired) {
            _timerSeconds.value = 0
        }

        viewModelScope.launch {
            readVerificationFromFirestoreCache()
            readVerificationFromServer()
        }
    }

    private suspend fun readVerificationFromFirestoreCache() {
        val email = currentEmail()
        if (email.isBlank()) return

        try {
            val snapshot = db.collection("email_verifications")
                .document(email)
                .get(Source.CACHE)
                .await()

            applyVerificationSnapshot(snapshot.exists(), snapshot.data)
        } catch (_: Exception) {
        }
    }

    private suspend fun readVerificationFromServer() {
        val email = currentEmail()
        if (email.isBlank()) return

        try {
            val snapshot = db.collection("email_verifications")
                .document(email)
                .get(Source.SERVER)
                .await()

            applyVerificationSnapshot(snapshot.exists(), snapshot.data)
        } catch (_: Exception) {
        }
    }

    fun syncVerificationState() {
        viewModelScope.launch {
            readVerificationFromFirestoreCache()
            readVerificationFromServer()
        }
    }

    private fun applyVerificationSnapshot(exists: Boolean, data: Map<String, Any>?) {
        if (!exists || data == null) {
            timerJob?.cancel()

            val currentUserUid = auth.currentUser?.uid
            if (!currentUserUid.isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        val userSnapshot = db.collection("usuarios")
                            .document(currentUserUid)
                            .get(Source.SERVER)
                            .await()

                        val verified = userSnapshot.getBoolean("verificado") == true
                        if (verified) {
                            clearVerificationCache()
                            _status.value = RegisterStatus.Success
                            _currentStep.value = RegisterStep.Success
                            saveCurrentStep(RegisterStep.Success)
                        } else {
                            clearVerificationCache()
                            _timerSeconds.value = 0
                        }
                    } catch (_: Exception) {
                        clearVerificationCache()
                        _timerSeconds.value = 0
                    }
                }
            } else {
                clearVerificationCache()
                _timerSeconds.value = 0
            }
            return
        }

        val attemptsUsed = (data["attempts"] as? Number)?.toInt() ?: 0
        val maxAttempts = 5
        val verified = data["verified"] as? Boolean ?: false
        val createdAtMillis = (data["createdAt"] as? Number)?.toLong()
        val expiresAtMillis = (data["expiresAt"] as? Number)?.toLong()
        val remainingSeconds = expiresAtMillis?.let {
            ((it - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        } ?: 0
        val isExpired = expiresAtMillis?.let { System.currentTimeMillis() > it } ?: false

        val state = VerificationUiState(
            attemptsUsed = attemptsUsed,
            maxAttempts = maxAttempts,
            remainingAttempts = (maxAttempts - attemptsUsed).coerceAtLeast(0),
            verified = verified,
            codeExists = true,
            createdAtMillis = createdAtMillis,
            expiresAtMillis = expiresAtMillis,
            remainingSeconds = remainingSeconds,
            isExpired = isExpired
        )

        _verificationState.value = state
        saveVerificationCache(state)

        if (verified) {
            timerJob?.cancel()
            clearVerificationCache()
            _status.value = RegisterStatus.Success
            _currentStep.value = RegisterStep.Success
            saveCurrentStep(RegisterStep.Success)
            return
        }

        if (isExpired) {
            timerJob?.cancel()
            _timerSeconds.value = 0
            return
        }

        startTimerFromState(state)
    }

    private fun startTimerFromState(state: VerificationUiState) {
        val expiresAtMillis = state.expiresAtMillis ?: run {
            _timerSeconds.value = 0
            return
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = ((expiresAtMillis - System.currentTimeMillis()) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)

                _timerSeconds.value = remaining

                if (remaining <= 0) {
                    break
                }

                delay(1000)
            }
        }
    }

    private fun buildVerifyErrorMessage(e: FirebaseFunctionsException): String {
        val details = e.details as? Map<*, *>
        val remaining = (details?.get("remaining") as? Number)?.toInt()
        val attempts = (details?.get("attempts") as? Number)?.toInt()

        return when (e.code) {
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> {
                if (remaining != null && attempts != null) {
                    "Código incorrecto. Intento $attempts de 5. Le quedan $remaining intentos."
                } else {
                    "Código incorrecto."
                }
            }
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                e.message ?: "El código expiró o alcanzó el máximo de intentos."
            }
            FirebaseFunctionsException.Code.NOT_FOUND -> {
                "No hay un código activo para este correo."
            }
            else -> {
                e.message ?: "No se pudo verificar el código."
            }
        }
    }

    private fun currentEmail(): String {
        return auth.currentUser?.email?.trim()?.lowercase()
            ?: _userData.value.correo.trim().lowercase()
    }

    private fun clearRegistrationCacheButKeepVerification() {
        val verificationCodeExists = prefs.getBoolean("verification_code_exists", false)
        val verificationAttemptsUsed = prefs.getInt("verification_attempts_used", 0)
        val verificationMaxAttempts = prefs.getInt("verification_max_attempts", 5)
        val verificationVerified = prefs.getBoolean("verification_verified", false)
        val verificationCreatedAt = prefs.getLong("verification_created_at", 0L)
        val verificationExpiresAt = prefs.getLong("verification_expires_at", 0L)
        val savedStep = prefs.getString("current_step", "Verification")

        prefs.edit().clear().apply()

        prefs.edit().apply {
            putBoolean("verification_code_exists", verificationCodeExists)
            putInt("verification_attempts_used", verificationAttemptsUsed)
            putInt("verification_max_attempts", verificationMaxAttempts)
            putBoolean("verification_verified", verificationVerified)
            if (verificationCreatedAt > 0L) putLong("verification_created_at", verificationCreatedAt)
            if (verificationExpiresAt > 0L) putLong("verification_expires_at", verificationExpiresAt)
            putString("current_step", savedStep)
            apply()
        }
    }

    fun clearCache() {
        // Solo limpiamos la caché específica del registro en su propio SharedPreferences
        // No tocamos las preferencias globales del tema
        prefs.edit().clear().apply()
        _verificationState.value = VerificationUiState()
    }

    fun clearError() {
        if (_status.value is RegisterStatus.Error) {
            _status.value = RegisterStatus.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}