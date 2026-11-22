package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.models.Tarea
import com.example.cuidaleapp.models.FrecuenciaTarea
import com.example.cuidaleapp.models.TipoTarea
import com.example.cuidaleapp.models.FormacionAcademica
import com.example.cuidaleapp.models.ExperienciaLaboral
import com.example.cuidaleapp.models.ReferenciaLaboral
import com.example.cuidaleapp.models.Certificado
import com.example.cuidaleapp.utils.ImageUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class FichaState {
    object Loading : FichaState()
    object NotFound : FichaState()
    data class View(val data: Map<String, Any>) : FichaState()
    data class Editing(val data: Map<String, Any>) : FichaState()
    data class Error(val message: String) : FichaState()
}

data class Resena(
    val id: String = "",
    val autorId: String = "",
    val autorNombre: String = "",
    val comentario: String = "",
    val calificacion: Float = 0f,
    val fecha: Timestamp = Timestamp.now()
)

class FichaViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<FichaState>(FichaState.Loading)
    val state: StateFlow<FichaState> = _state.asStateFlow()

    private val _role = MutableStateFlow("")
    val role: StateFlow<String> = _role.asStateFlow()

    private val _userRegion = MutableStateFlow("")
    val userRegion: StateFlow<String> = _userRegion.asStateFlow()

    private val _availableCommunes = MutableStateFlow<List<String>>(emptyList())
    val availableCommunes: StateFlow<List<String>> = _availableCommunes.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isPartialLoading = MutableStateFlow(false)
    val isPartialLoading: StateFlow<Boolean> = _isPartialLoading.asStateFlow()

    private val _tareasPlantilla = MutableStateFlow<List<Tarea>>(emptyList())
    val tareasPlantilla: StateFlow<List<Tarea>> = _tareasPlantilla.asStateFlow()

    private val _tareasTemporales = MutableStateFlow<List<Tarea>>(emptyList())
    val tareasTemporales: StateFlow<List<Tarea>> = _tareasTemporales.asStateFlow()

    private val _pendingPatientPhotoUri = MutableStateFlow<Uri?>(null)
    val pendingPatientPhotoUri: StateFlow<Uri?> = _pendingPatientPhotoUri.asStateFlow()

    private val _resenas = MutableStateFlow<List<Resena>>(emptyList())
    val resenas: StateFlow<List<Resena>> = _resenas.asStateFlow()

    init {
        checkProfile()
    }

    fun checkProfile(isSilent: Boolean = false) {
        val user = auth.currentUser
        if (user == null) {
            _state.value = FichaState.Error("No hay sesión activa")
            return
        }

        viewModelScope.launch {
            val currentState = _state.value
            if (!isSilent && currentState !is FichaState.View) {
                _state.value = FichaState.Loading
            }
            
            _isRefreshing.value = true
            try {
                val query = db.collection("usuarios").whereEqualTo("uid", user.uid).get().await()
                val userDoc = if (!query.isEmpty) query.documents[0] else null
                val currentRole = userDoc?.getString("rol") ?: ""
                val currentRegion = userDoc?.getString("region") ?: ""
                
                _role.value = currentRole
                _userRegion.value = currentRegion
                
                val regionData = ChileData.regions.find { it.name == currentRegion }
                _availableCommunes.value = regionData?.comunas?.sorted() ?: emptyList()

                val profileDoc = db.collection("fichas").document(user.uid).get().await()

                if (profileDoc.exists()) {
                    val data = profileDoc.data?.toMutableMap() ?: mutableMapOf()
                    _state.value = FichaState.View(data)
                    
                    listenToTareas(user.uid)
                    if (currentRole == "cuidador") {
                        listenToResenas(user.uid)
                    }
                } else {
                    _state.value = FichaState.NotFound
                }
            } catch (e: Exception) {
                if (!isSilent) _state.value = FichaState.Error(e.message ?: "Error al cargar perfil")
            } finally {
                _isRefreshing.value = false
                _isPartialLoading.value = false
            }
        }
    }

    fun startEditing() {
        when (val currentState = _state.value) {
            is FichaState.View -> {
                _tareasTemporales.value = _tareasPlantilla.value
                _pendingPatientPhotoUri.value = null
                _state.value = FichaState.Editing(currentState.data)
            }
            is FichaState.NotFound -> {
                _tareasTemporales.value = emptyList()
                _pendingPatientPhotoUri.value = null
                _state.value = FichaState.Editing(emptyMap())
            }
            else -> {}
        }
    }

    fun setPendingPatientPhoto(uri: Uri?) {
        _pendingPatientPhotoUri.value = uri
    }

    fun cancelEditing() {
        checkProfile()
    }

    fun createCaregiverFicha(
        especializacion: String,
        experiencia: String,
        comunas: List<String>,
        horarios: List<String>,
        descripcion: String,
        fechaNacimiento: String = "",
        discapacidad: Boolean = false,
        tipoDiscapacidad: String = "",
        tiempoDiscapacidad: String = "",
        valorHoraReferencial: Int = 0,
        formacionAcademica: List<FormacionAcademica> = emptyList(),
        experienciasLaborales: List<ExperienciaLaboral> = emptyList(),
        referenciasLaborales: List<ReferenciaLaboral> = emptyList(),
        certificados: List<Certificado> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        val user = auth.currentUser ?: return
        
        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                val data = hashMapOf(
                    "tipo" to "cuidador",
                    "especializacion" to especializacion.trim(),
                    "experiencia" to experiencia.trim(),
                    "comunas" to comunas,
                    "horarios" to horarios,
                    "descripcion" to descripcion.trim(),
                    "fechaNacimiento" to fechaNacimiento.trim(),
                    "discapacidad" to discapacidad,
                    "tipoDiscapacidad" to tipoDiscapacidad.trim(),
                    "tiempoDiscapacidad" to tiempoDiscapacidad.trim(),
                    "valorHoraReferencial" to valorHoraReferencial,
                    "formacionAcademica" to formacionAcademica,
                    "experienciasLaborales" to experienciasLaborales,
                    "referenciasLaborales" to referenciasLaborales,
                    "certificados" to certificados,
                    "activa" to true,
                    "ultima_actualizacion" to Timestamp.now()
                )
                val userDocs = db.collection("usuarios").whereEqualTo("uid", user.uid).get().await()
                val estadoVerificacion = if (!userDocs.isEmpty) {
                    userDocs.documents[0].getString("verificacionOficial") ?: "pendiente"
                } else "pendiente"
                data["verificacionOficial"] = estadoVerificacion
                db.collection("fichas").document(user.uid).set(data).await()

                checkProfile()
                onSuccess()
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
        condicionesExtra: String,
        comunasPreferencia: List<String> = emptyList(),
        horariosPreferencia: List<String> = emptyList(),
        shouldDeletePhoto: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val user = auth.currentUser ?: return

        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                var finalPhotoUrl = ""
                val existingFicha = db.collection("fichas").document(user.uid).get().await()
                if (existingFicha.exists()) {
                    finalPhotoUrl = existingFicha.getString("fotoUrl") ?: ""
                }

                if (_pendingPatientPhotoUri.value != null) {
                    val uri = _pendingPatientPhotoUri.value!!
                    val storageRef = FirebaseStorage.getInstance().reference
                    val filename = "patient_${System.currentTimeMillis()}.jpg"
                    val ref = storageRef.child("families/${user.uid}/$filename")
                    
                    val context = getApplication<Application>()
                    val compressedBytes = ImageUtils.getCompressedImage(context, uri)
                        ?: throw Exception("Error al procesar imagen")

                    ref.putBytes(compressedBytes).await()
                    val newUrl = ref.downloadUrl.await().toString()
                    
                    if (finalPhotoUrl.isNotEmpty() && finalPhotoUrl.contains("firebasestorage")) {
                        try {
                            FirebaseStorage.getInstance().getReferenceFromUrl(finalPhotoUrl).delete().await()
                        } catch (e: Exception) {}
                    }
                    finalPhotoUrl = newUrl
                } else if (shouldDeletePhoto) {
                    if (finalPhotoUrl.isNotEmpty() && finalPhotoUrl.contains("firebasestorage")) {
                        try {
                            FirebaseStorage.getInstance().getReferenceFromUrl(finalPhotoUrl).delete().await()
                        } catch (e: Exception) {}
                    }
                    finalPhotoUrl = ""
                }

                val data = hashMapOf(
                    "tipo" to "familiar",
                    "nombre_completo" to nombreCompleto.trim(),
                    "fotoUrl" to finalPhotoUrl,
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
                    "condiciones" to condicionesExtra.trim(),
                    "comunas_preferencia" to comunasPreferencia,
                    "horarios_preferencia" to horariosPreferencia,
                    "activa" to true,
                    "ultima_actualizacion" to Timestamp.now()
                )
                db.collection("fichas").document(user.uid).set(data).await()

                val batch = db.batch()
                val tareasRef = db.collection("fichas").document(user.uid).collection("tareas_plantilla")
                
                val oldTareas = db.collection("fichas").document(user.uid).collection("tareas_plantilla").get().await()
                oldTareas.documents.forEach { batch.update(it.reference, "activa", false) }

                _tareasTemporales.value.forEach { tarea ->
                    batch.set(tareasRef.document(tarea.id), tarea.copy(activa = true))
                }
                batch.commit().await()

                checkProfile()
                onSuccess()
            } catch (e: Exception) {
                _state.value = FichaState.Error("Error al guardar perfil: ${e.message}")
            }
        }
    }

    fun updateSearchPreferences(
        comunas: List<String>,
        horarios: List<String>,
        onSuccess: () -> Unit = {}
    ) {
        val user = auth.currentUser ?: return
        val currentRole = _role.value

        viewModelScope.launch {
            _isPartialLoading.value = true
            try {
                val data = if (currentRole == "cuidador") {
                    mapOf(
                        "comunas" to comunas,
                        "horarios" to horarios,
                        "ultima_actualizacion" to Timestamp.now()
                    )
                } else {
                    mapOf(
                        "comunas_preferencia" to comunas,
                        "horarios_preferencia" to horarios,
                        "ultima_actualizacion" to Timestamp.now()
                    )
                }
                
                db.collection("fichas").document(user.uid).update(data).await()
                checkProfile(isSilent = true)
                onSuccess()
            } catch (e: Exception) {
                _isPartialLoading.value = false
                _state.value = FichaState.Error("Error al actualizar preferencias: ${e.message}")
            }
        }
    }

    private var resenasListener: ListenerRegistration? = null
    private fun listenToResenas(uid: String) {
        resenasListener?.remove()
        resenasListener = db.collection("fichas").document(uid)
            .collection("resenas")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Resena::class.java)?.copy(id = doc.id)
                    }
                    _resenas.value = list
                    
                    if (list.isNotEmpty()) {
                        val promedio = list.map { it.calificacion }.average().toFloat()
                        val total = list.size
                        
                        updateFichaRatings(uid, promedio, total)
                    }
                }
            }
    }

    private fun updateFichaRatings(uid: String, promedio: Float, total: Int) {
        viewModelScope.launch {
            try {
                db.collection("fichas").document(uid).update(
                    mapOf(
                        "rating_promedio" to promedio,
                        "total_resenas" to total
                    )
                ).await()
            } catch (e: Exception) {}
        }
    }

    private var tareasListener: ListenerRegistration? = null
    private fun listenToTareas(uid: String) {
        tareasListener?.remove()
        tareasListener = db.collection("fichas").document(uid)
            .collection("rutina_data").document("actual")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val list = snapshot.get("tareas") as? List<Map<String, Any>> ?: emptyList()
                    _tareasPlantilla.value = list.map { map ->
                        Tarea(
                            id = map["id"] as? String ?: "",
                            titulo = map["titulo"] as? String ?: "",
                            tipo = TipoTarea.valueOf(map["tipo"] as? String ?: "OTRO"),
                            horarioInicio = map["horarioInicio"] as? String ?: "",
                            frecuencia = FrecuenciaTarea.valueOf(map["frecuencia"] as? String ?: "DIARIA")
                        )
                    }.filter { it.id.isNotEmpty() }
                } else {
                    db.collection("fichas").document(uid).collection("tareas_plantilla")
                        .whereEqualTo("activa", true)
                        .get().addOnSuccessListener { subSnap ->
                            _tareasPlantilla.value = subSnap.toObjects(Tarea::class.java)
                        }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        tareasListener?.remove()
        resenasListener?.remove()
    }
}
