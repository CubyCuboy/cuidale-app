package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.ChileData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.cuidaleapp.utils.ImageUtils
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class FichaState {
    object Loading : FichaState()
    object NotFound : FichaState()
    data class View(val data: Map<String, Any>) : FichaState()
    object Editing : FichaState()
    data class Error(val message: String) : FichaState()
}

class FichaViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val context = application.applicationContext

    private val _state = MutableStateFlow<FichaState>(FichaState.Loading)
    val state: StateFlow<FichaState> = _state

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
            _state.value = FichaState.Error("No hay sesión activa")
            return
        }

        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                val userDoc = db.collection("usuarios").document(user.uid).get().await()
                val currentRole = userDoc.getString("rol") ?: ""
                val currentRegion = userDoc.getString("region") ?: ""
                
                _role.value = currentRole
                _userRegion.value = currentRegion
                
                val regionData = ChileData.regions.find { it.name == currentRegion }
                _availableCommunes.value = regionData?.comunas?.sorted() ?: emptyList()

                val collectionName = if (currentRole == "cuidador") "perfil_cuidador" else "perfil_familiar"
                val profileDoc = db.collection(collectionName).document(user.uid).get().await()

                if (profileDoc.exists()) {
                    _state.value = FichaState.View(profileDoc.data ?: emptyMap())
                } else {
                    _state.value = FichaState.NotFound
                }
            } catch (e: Exception) {
                _state.value = FichaState.Error(e.message ?: "Error al cargar perfil")
            }
        }
    }

    fun refreshProfile() {
        checkProfile()
    }

    fun startEditing() {
        _state.value = FichaState.Editing
    }

    private suspend fun uploadFichaImages(userId: String, uris: List<Uri>): List<String> {
        val urls = mutableListOf<String>()
        val storageRef = storage.reference.child("fichas/$userId")

        uris.forEachIndexed { index, uri ->
            if (uri.toString().startsWith("http")) {
                urls.add(uri.toString())
            } else {
                // Validar cada imagen
                ImageUtils.validateImage(context, uri).getOrThrow()

                val fileName = "foto_${index + 1}.jpg"
                val fileRef = storageRef.child(fileName)
                val bytes = ImageUtils.getCompressedImage(context, uri) ?: return@forEachIndexed
                
                fileRef.putBytes(bytes).await()
                val downloadUrl = fileRef.downloadUrl.await().toString()
                urls.add(downloadUrl)
            }
        }
        return urls
    }

    fun saveCaregiverProfile(
        especializacion: String,
        experiencia: String,
        comunas: List<String>,
        horarios: List<String>,
        fotoUris: List<Uri>,
        telefono: String,
        correo: String,
        descripcion: String
    ) {
        val user = auth.currentUser ?: return
        if (especializacion.isBlank() || telefono.isBlank() || correo.isBlank() || descripcion.isBlank()) {
            _state.value = FichaState.Error("Por favor completa los campos obligatorios")
            return
        }
        
        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                val imageUrls = uploadFichaImages(user.uid, fotoUris)
                
                val data = hashMapOf(
                    "especializacion" to especializacion,
                    "experiencia" to if (experiencia.isBlank()) "Sin Especificar" else validateWordCount(experiencia, 300),
                    "comunas" to comunas,
                    "horarios" to horarios,
                    "fotos" to imageUrls,
                    "telefono" to telefono,
                    "correo" to correo,
                    "descripcion" to validateWordCount(descripcion, 300)
                )
                db.collection("perfil_cuidador").document(user.uid).set(data).await()
                _state.value = FichaState.View(data)
                // Opcional: refrescar para asegurar sincronía completa
                checkProfile()
            } catch (e: Exception) {
                _state.value = FichaState.Error("Error al guardar perfil: ${e.message}")
            }
        }
    }

    fun saveFamilyProfile(
        fotoUris: List<Uri>,
        nombreCompleto: String,
        edad: String,
        genero: String,
        comuna: String,
        direccion: String,
        discapacidad: String,
        enfermedad: String,
        dieta: String,
        medicacion: String,
        condiciones: String
    ) {
        val user = auth.currentUser ?: return
        if (nombreCompleto.isBlank() || edad.isBlank() || comuna.isBlank() || direccion.isBlank()) {
            _state.value = FichaState.Error("Por favor completa los campos obligatorios")
            return
        }

        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                val imageUrls = uploadFichaImages(user.uid, fotoUris)

                val data = hashMapOf(
                    "fotos" to imageUrls,
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
                _state.value = FichaState.View(data)
                // Opcional: refrescar para asegurar sincronía completa
                checkProfile()
            } catch (e: Exception) {
                _state.value = FichaState.Error("Error al guardar perfil: ${e.message}")
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
