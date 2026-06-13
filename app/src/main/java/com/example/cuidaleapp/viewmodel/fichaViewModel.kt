package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.models.Tarea
import com.example.cuidaleapp.models.FrecuenciaTarea
import com.example.cuidaleapp.models.TipoTarea
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    private val _isPartialLoading = MutableStateFlow(false)
    val isPartialLoading: StateFlow<Boolean> = _isPartialLoading

    private val _tareasPlantilla = MutableStateFlow<List<Tarea>>(emptyList())
    val tareasPlantilla: StateFlow<List<Tarea>> = _tareasPlantilla

    private val _tareasTemporales = MutableStateFlow<List<Tarea>>(emptyList())
    val tareasTemporales: StateFlow<List<Tarea>> = _tareasTemporales

    private var isEditingMode = false

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
                    listenToTareas(user.uid)
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
                isEditingMode = true
                _tareasTemporales.value = _tareasPlantilla.value
                _state.value = FichaState.Editing(currentState.data)
            }
            is FichaState.NotFound -> {
                isEditingMode = true
                _tareasTemporales.value = emptyList()
                _state.value = FichaState.Editing(emptyMap())
            }
            else -> {}
        }
    }

    fun cancelEditing() {
        isEditingMode = false
        checkProfile()
    }

    fun saveTareaPlantilla(tarea: Tarea) {
        if (isEditingMode) {
            val actuales = _tareasTemporales.value.toMutableList()
            val index = actuales.indexOfFirst { it.id == tarea.id }
            if (index != -1) {
                actuales[index] = tarea
            } else {
                actuales.add(tarea)
            }
            _tareasTemporales.value = actuales
        } else {
            val user = auth.currentUser ?: return
            viewModelScope.launch {
                try {
                    db.collection("fichas").document(user.uid)
                        .collection("tareas_plantilla").document(tarea.id)
                        .set(tarea).await()
                } catch (e: Exception) {}
            }
        }
    }

    fun deleteTareaPlantilla(tareaId: String) {
        if (isEditingMode) {
            _tareasTemporales.value = _tareasTemporales.value.filter { it.id != tareaId }
        } else {
            val user = auth.currentUser ?: return
            viewModelScope.launch {
                try {
                    db.collection("fichas").document(user.uid)
                        .collection("tareas_plantilla").document(tareaId)
                        .update("activa", false).await()
                } catch (e: Exception) {}
            }
        }
    }

    fun createCaregiverFicha(
        especializacion: String,
        experiencia: String,
        comunas: List<String>,
        horarios: List<String>,
        descripcion: String,
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
                    "activa" to true,
                    "ultima_actualizacion" to com.google.firebase.Timestamp.now()
                )
                db.collection("fichas").document(user.uid).set(data).await()
                
                isEditingMode = false
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
        onSuccess: () -> Unit = {}
    ) {
        val user = auth.currentUser ?: return

        viewModelScope.launch {
            _state.value = FichaState.Loading
            try {
                val data = hashMapOf(
                    "tipo" to "familiar",
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
                    "condiciones" to condicionesExtra.trim(),
                    "comunas_preferencia" to comunasPreferencia,
                    "horarios_preferencia" to horariosPreferencia,
                    "activa" to true,
                    "ultima_actualizacion" to com.google.firebase.Timestamp.now()
                )
                db.collection("fichas").document(user.uid).set(data).await()

                // Persistir tareas temporales
                val batch = db.batch()
                val tareasRef = db.collection("fichas").document(user.uid).collection("tareas_plantilla")
                
                // Marcar todas las anteriores como inactivas para "reemplazar" el set
                val oldTareas = db.collection("fichas").document(user.uid).collection("tareas_plantilla").get().await()
                oldTareas.documents.forEach { batch.update(it.reference, "activa", false) }

                _tareasTemporales.value.forEach { tarea ->
                    batch.set(tareasRef.document(tarea.id), tarea.copy(activa = true))
                }
                batch.commit().await()

                isEditingMode = false
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
                        "ultima_actualizacion" to com.google.firebase.Timestamp.now()
                    )
                } else {
                    mapOf(
                        "comunas_preferencia" to comunas,
                        "horarios_preferencia" to horarios,
                        "ultima_actualizacion" to com.google.firebase.Timestamp.now()
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

    private var tareasListener: ListenerRegistration? = null
    private fun listenToTareas(uid: String) {
        tareasListener?.remove()
        tareasListener = db.collection("fichas").document(uid)
            .collection("tareas_plantilla")
            .whereEqualTo("activa", true)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasPlantilla.value = snapshot.toObjects(Tarea::class.java)
                }
            }
    }



    override fun onCleared() {
        super.onCleared()
        tareasListener?.remove()
    }
}
