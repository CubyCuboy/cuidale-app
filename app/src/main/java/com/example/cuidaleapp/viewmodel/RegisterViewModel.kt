package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.functions.FirebaseFunctions

data class RegisterUserData(
    val role: String = "",
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
    private val prefs = application.getSharedPreferences("cuidale_registration_cache", Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = com.google.firebase.functions.FirebaseFunctions.getInstance("southamerica-west1")

    private val _currentStep = MutableStateFlow<RegisterStep>(RegisterStep.PersonalData)
    val currentStep: StateFlow<RegisterStep> = _currentStep

    private val _status = MutableStateFlow<RegisterStatus>(RegisterStatus.Idle)
    val status: StateFlow<RegisterStatus> = _status

    private val _userData = MutableStateFlow(RegisterUserData(role = prefs.getString("temp_role", "") ?: ""))
    val userData: StateFlow<RegisterUserData> = _userData

    private val _timerSeconds = MutableStateFlow(1800)
    val timerSeconds: StateFlow<Int> = _timerSeconds

    private var timerJob: kotlinx.coroutines.Job? = null

    private val _unmetRequirementsTrigger = MutableStateFlow<Set<Int>>(emptySet())
    val unmetRequirementsTrigger: StateFlow<Set<Int>> = _unmetRequirementsTrigger

    fun setRole(role: String) {
        prefs?.edit()?.putString("temp_role", role)?.apply()
        _userData.value = _userData.value.copy(role = role)
    }

    fun prepareForVerification(email: String) {
        viewModelScope.launch {
            _status.value = RegisterStatus.Loading
            try {
                val query = db.collection("usuarios")
                    .whereEqualTo("correo", email)
                    .get()
                    .await()

                if (!query.isEmpty) {
                    val userDoc = query.documents[0]
                    val data = userDoc.toObject(RegisterUserData::class.java)
                    if (data != null) {
                        _userData.value = data
                        _currentStep.value = RegisterStep.Verification
                        startTimer()
                    }
                }
                _status.value = RegisterStatus.Idle
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al cargar datos de verificación")
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

        val nameWords = fullNames.trim().split("\\s+".toRegex())
        val apellidoWords = fullApellidos.trim().split("\\s+".toRegex())

        if (!isValidEmail(email)) {
            _status.value = RegisterStatus.Error("Formato de correo inválido")
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
                // Verificar si correo existe
                val emailQuery = db.collection("usuarios")
                    .whereEqualTo("correo", normalizedEmail)
                    .get()
                    .await()

                if (!emailQuery.isEmpty) {
                    _status.value = RegisterStatus.Error("El correo ya se encuentra registrado")
                    return@launch
                }

                // Verificar si RUT existe
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

                _userData.value = _userData.value.copy(
                    nombre = n1,
                    nombre_s = n2,
                    apellido_pa = a1,
                    apellido_ma = a2,
                    correo = normalizedEmail,
                    rut = normalizedRut
                )
                
                _status.value = RegisterStatus.Idle
                onSuccess()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al validar datos: ${e.message}")
            }
        }
    }

    private fun capitalizeWords(input: String): String {
        return input.trim().split("\\s+".toRegex()).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
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

        _userData.value = _userData.value.copy(
            region = regionName,
            comuna = comunaName,
            id_region = regionId
        )

        return null
    }

    private fun splitInput(input: String): Pair<String, String> {
        val words = input.trim().split("\\s+".toRegex())
        return when {
            words.size >= 2 -> Pair(words[0], words[1])
            words.size == 1 -> Pair(words[0], "N/A")
            else -> Pair("", "N/A")
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches() && 
               (email.endsWith(".com") || email.endsWith(".cl"))
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
    }
    
    fun finalizeRegistration(password: String) {
        val hasMinLength = password.length >= 8
        val hasUppercase = password.any { it.isUpperCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        val unmet = mutableSetOf<Int>()
        if (!hasMinLength) unmet.add(0)
        if (!hasUppercase) unmet.add(1)
        if (!hasSpecialChar) unmet.add(2)
        // La coincidencia de contraseña se maneja en el UI layer (NewPasswordStep)
        // pero podemos agregarla aquí si fuera necesario.
        // Aquí asumimos que si llega aquí es porque NewPasswordStep validó canUpdate.

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
                    "rol" to data.role,
                    "verificado" to false
                )

                db.collection("usuarios").document(uid).set(userProfile).await()

                // Enviar código de verificación
                sendVerificationCode()
                
                _status.value = RegisterStatus.Idle
                // Cambiamos el paso pero no navegamos a una nueva actividad automáticamente
                // El Screen de RegisterActivity mostrará el RegisterVerificationStep
                _currentStep.value = RegisterStep.Verification
                clearCache()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al registrar: ${e.message}")
            }
        }
    }

    private fun sendVerificationCode() {
        val email = _userData.value.correo
        if (email.isBlank()) return

        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "email" to email,
                    "type" to "verification"
                )
                functions.getHttpsCallable("sendVerificationCode").call(data).await()
                startTimer()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al enviar código: ${e.message}")
            }
        }
    }

    fun resendCode() {
        _status.value = RegisterStatus.Loading
        viewModelScope.launch {
            try {
                val email = auth.currentUser?.email ?: _userData.value.correo
                val data = hashMapOf(
                    "email" to email,
                    "type" to "verification"
                )
                functions.getHttpsCallable("sendVerificationCode").call(data).await()
                _status.value = RegisterStatus.Idle
                startTimer()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("No se pudo reenviar el código: ${e.message}")
            }
        }
    }

    fun verifyCode(code: String) {
        if (code.length != 6) {
            _status.value = RegisterStatus.Error("El código debe tener 6 dígitos.")
            return
        }

        viewModelScope.launch {
            _status.value = RegisterStatus.Loading
            try {
                val email = auth.currentUser?.email ?: _userData.value.correo
                val data = hashMapOf(
                    "email" to email,
                    "code" to code
                )

                functions.getHttpsCallable("verifyCode").call(data).await()

                // Marcar como verificado en Firestore
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    db.collection("usuarios").document(uid).update("verificado", true).await()
                }

                timerJob?.cancel()
                _status.value = RegisterStatus.Success
                _currentStep.value = RegisterStep.Success
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Código inválido o expirado.")
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _timerSeconds.value = 1800
        timerJob = viewModelScope.launch {
            while (_timerSeconds.value > 0) {
                delay(1000)
                _timerSeconds.value -= 1
            }
            // Cuando expira, podemos limpiar el código en backend
            try {
                val email = auth.currentUser?.email ?: _userData.value.correo
                if (email.isNotBlank()) {
                    val data = hashMapOf("email" to email)
                    functions.getHttpsCallable("cancelVerification").call(data).await()
                }
            } catch (e: Exception) {}
        }
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }

    fun clearError() {
        if (_status.value is RegisterStatus.Error) {
            _status.value = RegisterStatus.Idle
        }
    }
}
