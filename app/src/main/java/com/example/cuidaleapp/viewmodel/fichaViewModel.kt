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
    data class Editing(val data: Map<String, Any>) : FichaState()
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

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
            _isRefreshing.value = true
            try {
                val userDoc = db.collection("usuarios").document(user.uid).get().await()
                val currentRole = userDoc.getString("rol") ?: ""
                val currentRegion = userDoc.getString("region") ?: ""
                
                _role.value = currentRole
                _userRegion.value = currentRegion
                
                val regionData = ChileData.regions.find { it.name == currentRegion }
                _availableCommunes.value = regionData?.comunas?.sorted() ?: emptyList()

                val profileDoc = db.collection("fichas").document(user.uid).get().await()

                if (profileDoc.exists()) {
                    _state.value = FichaState.View(profileDoc.data ?: emptyMap())
                } else {
                    _state.value = FichaState.NotFound
                }
            } catch (e: Exception) {
                _state.value = FichaState.Error(e.message ?: "Error al cargar perfil")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun startEditing() {
        val currentState = _state.value
        if (currentState is FichaState.View) {
            _state.value = FichaState.Editing(currentState.data)
        }
    }

    fun cancelEditing() {
        checkProfile()
    }

    private suspend fun uploadFichaImages(userId: String, uris: List<Uri>): List<String> {
        val urls = mutableListOf<String>()
        val storageRef = storage.reference.child("fichas/$userId")

        uris.forEachIndexed { index, uri ->
            if (uri.toString().startsWith("http")) {
                urls.add(uri.toString())
            } else {
                try {
                    ImageUtils.validateImage(context, uri).getOrThrow()
                    val fileName = "foto_${System.currentTimeMillis()}_$index.jpg"
                    val fileRef = storageRef.child(fileName)
                    val bytes = ImageUtils.getCompressedImage(context, uri) ?: return@forEachIndexed
                    
                    fileRef.putBytes(bytes).await()
                    val downloadUrl = fileRef.downloadUrl.await().toString()
                    urls.add(downloadUrl)
                } catch (e: Exception) {
                }
            }
        }
        return urls
    }

    fun createCaregiverFicha(
        especializacion: String,
        experiencia: String,
        comunas: List<String>,
        descripcion: String,
        fotoUris: List<Uri>
    ) {
        val user = auth.currentUser ?: return
        
        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                val imageUrls = uploadFichaImages(user.uid, fotoUris)
                
                val data = hashMapOf(
                    "tipo" to "cuidador",
                    "especializacion" to especializacion.trim(),
                    "experiencia" to experiencia.trim(),
                    "comunas" to comunas,
                    "fotos" to imageUrls,
                    "descripcion" to descripcion.trim(),
                    "ultima_actualizacion" to com.google.firebase.Timestamp.now()
                )
                db.collection("fichas").document(user.uid).set(data).await()
                checkProfile()
            } catch (e: Exception) {
                _state.value = FichaState.Error("Error al guardar perfil: ${e.message}")
            }
        }
    }

    fun createFamilyFicha(
        nombreCompleto: String,
        edad: String,
        genero: String,
        vivesConFamiliar: Boolean,
        region: String,
        comuna: String,
        calle: String,
        numero: String,
        depto: String,
        nivelDependencia: String,
        movilidad: String,
        comunicacion: String,
        discapacidades: List<String>,
        discapacidadOtro: String,
        enfermedades: List<String>,
        enfermedadOtro: String,
        dieta: Map<String, String>,
        medicamentos: List<Map<String, Any>>,
        condicionesExtra: String,
        fotoUris: List<Uri>,
        comunasPreferencia: List<String> = emptyList(),
        horariosPreferencia: List<String> = emptyList()
    ) {
        val user = auth.currentUser ?: return

        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                val imageUrls = uploadFichaImages(user.uid, fotoUris)

                val data = hashMapOf(
                    "tipo" to "familiar",
                    "fotos" to imageUrls,
                    "nombre_completo" to nombreCompleto.trim(),
                    "edad" to edad.trim(),
                    "genero" to genero,
                    "vives_con_familiar" to vivesConFamiliar,
                    "region" to region,
                    "comuna" to comuna,
                    "calle" to calle.trim(),
                    "numero" to numero.trim(),
                    "depto" to depto.trim(),
                    "nivel_dependencia" to nivelDependencia,
                    "movilidad" to movilidad,
                    "comunicacion" to comunicacion,
                    "discapacidades" to discapacidades,
                    "discapacidad_otro" to discapacidadOtro.trim(),
                    "enfermedades" to enfermedades,
                    "enfermedad_otro" to enfermedadOtro.trim(),
                    "dieta" to dieta,
                    "medicamentos" to medicamentos,
                    "condiciones" to condicionesExtra.trim(),
                    "comunas_preferencia" to comunasPreferencia,
                    "horarios_preferencia" to horariosPreferencia,
                    "ultima_actualizacion" to com.google.firebase.Timestamp.now()
                )
                db.collection("fichas").document(user.uid).set(data).await()
                checkProfile()
            } catch (e: Exception) {
                _state.value = FichaState.Error("Error al guardar perfil: ${e.message}")
            }
        }
    }
}
