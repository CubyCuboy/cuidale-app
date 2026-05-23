package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    private val _currentStep = MutableStateFlow<RegisterStep>(RegisterStep.PersonalData)
    val currentStep: StateFlow<RegisterStep> = _currentStep

    private val _status = MutableStateFlow<RegisterStatus>(RegisterStatus.Idle)
    val status: StateFlow<RegisterStatus> = _status

    private val _userData = MutableStateFlow(RegisterUserData(role = prefs.getString("temp_role", "") ?: ""))
    val userData: StateFlow<RegisterUserData> = _userData

    fun setRole(role: String) {
        prefs.edit().putString("temp_role", role).apply()
        _userData.value = _userData.value.copy(role = role)
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

        if (nameWords.size > 2) {
            _status.value = RegisterStatus.Error("Máximo dos nombres permitidos")
            return
        }

        if (apellidoWords.size > 2) {
            _status.value = RegisterStatus.Error("Máximo dos apellidos permitidos")
            return
        }

        if (!isValidEmail(email)) {
            _status.value = RegisterStatus.Error("Formato de correo inválido")
            return
        }

        if (!isValidRut(rut)) {
            _status.value = RegisterStatus.Error("RUT inválido (use formato 12345678-9)")
            return
        }

        val normalizedEmail = email.lowercase().trim()

        viewModelScope.launch {
            _status.value = RegisterStatus.Loading
            try {
                val query = db.collection("usuarios")
                    .whereEqualTo("correo", normalizedEmail)
                    .get()
                    .await()

                if (!query.isEmpty) {
                    _status.value = RegisterStatus.Error("El correo ya se encuentra registrado")
                    return@launch
                }

                val (n1, n2) = splitInput(fullNames)
                val (a1, a2) = splitInput(fullApellidos)

                _userData.value = _userData.value.copy(
                    nombre = n1,
                    nombre_s = n2,
                    apellido_pa = a1,
                    apellido_ma = a2,
                    correo = normalizedEmail,
                    rut = rut.trim()
                )
                
                _status.value = RegisterStatus.Idle
                onSuccess()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al verificar el correo: ${e.message}")
            }
        }
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
        val cleanRut = rut.replace(".", "").trim()
        if (!cleanRut.contains("-")) return false
        
        val parts = cleanRut.split("-")
        if (parts.size != 2) return false
        
        val body = parts[0]
        val dvInput = parts[1].uppercase()
        
        if (body.length < 7 || body.length > 8) return false
        
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
                    "rol" to data.role
                )

                db.collection("usuarios").document(uid).set(userProfile).await()

                _status.value = RegisterStatus.Success
                _currentStep.value = RegisterStep.Success
                clearCache()
            } catch (e: Exception) {
                _status.value = RegisterStatus.Error("Error al registrar: ${e.message}")
            }
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
