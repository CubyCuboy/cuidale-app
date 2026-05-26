package com.example.cuidaleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

class RegisterViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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

    val isEmailValid: StateFlow<Boolean> = _email.combine(_email) { email, _ ->
        validateEmail(email)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isRutValid: StateFlow<Boolean> = _rut.combine(_rut) { rut, _ ->
        validateRut(rut)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isFormValid: StateFlow<Boolean> = combine(_nombre, _apellidoPa, _email, _rut) { nombre, apellido, email, rut ->
        nombre.trim().length >= 2 && 
        apellido.trim().length >= 2 && 
        validateEmail(email) && 
        validateRut(rut)
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

    fun onRoleChange(newRole: String) { _role.value = newRole }
    fun onNombreChange(newName: String) { _nombre.value = capitalizeWords(newName) }
    fun onNombreSChange(newNameS: String) { _nombreS.value = capitalizeWords(newNameS) }
    fun onApellidoPaChange(newApellidoPa: String) { _apellidoPa.value = capitalizeWords(newApellidoPa) }
    fun onApellidoMaChange(newApellidoMa: String) { _apellidoMa.value = capitalizeWords(newApellidoMa) }
    fun onEmailChange(newEmail: String) { _email.value = newEmail.trim().lowercase() }
    fun onPasswordChange(newPassword: String) { _password.value = newPassword }
    fun onRutChange(newRut: String) { _rut.value = newRut.uppercase() }

    private fun capitalizeWords(text: String): String {
        return text.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
    fun onRegionChange(newRegion: String, newIdRegion: String = "99") { 
        _region.value = newRegion 
        _idRegion.value = newIdRegion
    }
    fun onComunaChange(newComuna: String) { _comuna.value = newComuna }

    private fun formatRutForSaving(rut: String): String {
        val clean = rut.replace(".", "").replace("-", "").uppercase()
        if (clean.length < 2) return clean
        val body = clean.substring(0, clean.length - 1)
        val dv = clean.substring(clean.length - 1)
        return "$body-$dv"
    }

    private fun validateRut(rut: String): Boolean {
        val cleanRut = rut.replace(".", "").replace("-", "").uppercase()
        if (cleanRut.length < 8) return false
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

    fun register() {
        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            try {
                val formattedRut = formatRutForSaving(_rut.value)
                
                // Usamos la colección "usuarios" para coincidir con las reglas
                val rutQuery = db.collection("usuarios")
                    .whereEqualTo("rut", formattedRut)
                    .get()
                    .await()
                
                if (!rutQuery.isEmpty) {
                    throw Exception("El RUT ya se encuentra registrado")
                }

                val result = auth.createUserWithEmailAndPassword(_email.value, _password.value).await()
                val uid = result.user?.uid ?: throw Exception("No se pudo obtener el ID de usuario")

                val userData = hashMapOf(
                    "uid" to uid,
                    "rol" to _role.value,
                    "nombre" to _nombre.value.trim(),
                    "nombre_s" to _nombreS.value.trim(),
                    "apellido_pa" to _apellidoPa.value.trim(),
                    "apellido_ma" to _apellidoMa.value.trim(),
                    "correo" to _email.value.trim().lowercase(),
                    "rut" to formattedRut,
                    "region" to _region.value,
                    "id_region" to _idRegion.value,
                    "comuna" to _comuna.value,
                    "verificado" to false,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                db.collection("usuarios").document(uid).set(userData).await()
                _uiState.value = RegisterUiState.Success
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("email-already-in-use") == true -> "El correo electrónico ya se encuentra registrado"
                    e.message?.contains("PERMISSION_DENIED") == true -> "Error de permisos: Asegúrate de que las reglas de Firestore permitan 'list' en 'usuarios'"
                    else -> e.localizedMessage ?: "Error al registrar"
                }
                _uiState.value = RegisterUiState.Error(errorMessage)
            }
        }
    }

    fun clearError() {
        if (_uiState.value is RegisterUiState.Error) {
            _uiState.value = RegisterUiState.Idle
        }
    }
}
