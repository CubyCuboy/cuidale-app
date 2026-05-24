package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.ChileData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class ProfileState {
    object Loading : ProfileState()
    object NotFound : ProfileState()
    data class View(val data: Map<String, Any>) : ProfileState()
    object Editing : ProfileState()
    data class Error(val message: String) : ProfileState()
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val state: StateFlow<ProfileState> = _state

    private val _role = MutableStateFlow("")
    val role: StateFlow<String> = _role

    private val _userRegion = MutableStateFlow("")
    val userRegion: StateFlow<String> = _userRegion

    private val _availableCommunes = MutableStateFlow<List<String>>(emptyList())
    val availableCommunes: StateFlow<List<String>> = _availableCommunes

    init {
        checkProfile()
    }

    fun checkProfile() {
        val user = auth.currentUser
        if (user == null) {
            _state.value = ProfileState.Error("No hay sesión activa")
            return
        }

        viewModelScope.launch {
            _state.value = ProfileState.Loading
            try {
                // First get the user role and region from 'usuarios' collection
                val userDoc = db.collection("usuarios").document(user.uid).get().await()
                val currentRole = userDoc.getString("rol") ?: ""
                val currentRegion = userDoc.getString("region") ?: ""
                
                _role.value = currentRole
                _userRegion.value = currentRegion
                
                // Load communes for that region
                val regionData = ChileData.regions.find { it.name == currentRegion }
                _availableCommunes.value = regionData?.comunas?.sorted() ?: emptyList()

                val collectionName = if (currentRole == "cuidador") "perfil_cuidador" else "perfil_familiar"
                val profileDoc = db.collection(collectionName).document(user.uid).get().await()

                if (profileDoc.exists()) {
                    _state.value = ProfileState.View(profileDoc.data ?: emptyMap())
                } else {
                    _state.value = ProfileState.NotFound
                }
            } catch (e: Exception) {
                _state.value = ProfileState.Error(e.message ?: "Error al cargar perfil")
            }
        }
    }

    fun startEditing() {
        _state.value = ProfileState.Editing
    }

    fun saveCaregiverProfile(
        especializacion: String,
        experiencia: String,
        comunas: List<String>,
        horarios: List<String>,
        fotoUri: Uri?,
        telefono: String,
        correo: String,
        descripcion: String
    ) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _state.value = ProfileState.Loading
            try {
                val data = hashMapOf(
                    "especializacion" to especializacion,
                    "experiencia" to if (experiencia.isBlank()) "Sin Especificar" else validateWordCount(experiencia, 300),
                    "comunas" to comunas,
                    "horarios" to horarios,
                    "foto_url" to (fotoUri?.toString() ?: ""), // Simple logic for now
                    "telefono" to telefono,
                    "correo" to correo,
                    "descripcion" to validateWordCount(descripcion, 300)
                )
                db.collection("perfil_cuidador").document(user.uid).set(data).await()
                _state.value = ProfileState.View(data)
            } catch (e: Exception) {
                _state.value = ProfileState.Error("Error al guardar perfil: ${e.message}")
            }
        }
    }

    fun saveFamilyProfile(
        fotoUri: Uri?,
        nombreCompleto: String,
        edad: String,
        genero: String,
        comuna: String,
        direccion: String,
        discapacidad: String, // "No" or the text
        enfermedad: String, // "No" or the text
        dieta: String, // "No" or the text
        medicacion: String, // "Si" or "No"
        condiciones: String
    ) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _state.value = ProfileState.Loading
            try {
                val data = hashMapOf(
                    "foto_url" to (fotoUri?.toString() ?: ""),
                    "nombre_completo" to nombreCompleto,
                    "edad" to edad,
                    "genero" to genero,
                    "comuna" to comuna,
                    "direccion" to direccion,
                    "discapacidad" to discapacidad,
                    "enfermedad_cronica" to enfermedad,
                    "dieta_especial" to dieta,
                    "usa_medicacion" to medicacion,
                    "condiciones" to validateWordCount(condiciones, 300)
                )
                db.collection("perfil_familiar").document(user.uid).set(data).await()
                _state.value = ProfileState.View(data)
            } catch (e: Exception) {
                _state.value = ProfileState.Error("Error al guardar perfil: ${e.message}")
            }
        }
    }

    private fun validateWordCount(text: String, max: Int): String {
        val words = text.trim().split("\\s+".toRegex())
        return if (words.size > max) {
            words.take(max).joinToString(" ")
        } else {
            text
        }
    }
}
