package com.example.cuidaleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class TareasViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    private val _tareasPlantilla = MutableStateFlow<List<Tarea>>(emptyList())
    val tareasPlantilla: StateFlow<List<Tarea>> = _tareasPlantilla

    private val _tareasHoy = MutableStateFlow<List<RegistroTarea>>(emptyList())
    val tareasHoy: StateFlow<List<RegistroTarea>> = _tareasHoy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var tareasListener: ListenerRegistration? = null
    private var registrosListener: ListenerRegistration? = null

    fun cargarTareasFicha(pacienteFichaId: String) {
        tareasListener?.remove()
        tareasListener = db.collection("fichas").document(pacienteFichaId)
            .collection("tareas_plantilla")
            .whereEqualTo("activa", true)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasPlantilla.value = snapshot.toObjects(Tarea::class.java)
                }
            }
    }

    fun cargarTareasDelDia(solicitudId: String, fecha: String = getTodayDate()) {
        registrosListener?.remove()
        registrosListener = db.collection("solicitudes").document(solicitudId)
            .collection("registros_tareas")
            .whereEqualTo("fecha", fecha)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasHoy.value = snapshot.toObjects(RegistroTarea::class.java)
                        .sortedBy { it.horarioPrevisto }
                }
            }
    }

    fun agregarTareaAPlantilla(pacienteFichaId: String, tarea: Tarea) {
        viewModelScope.launch {
            try {
                db.collection("fichas").document(pacienteFichaId)
                    .collection("tareas_plantilla").document(tarea.id)
                    .set(tarea).await()
            } catch (e: Exception) {}
        }
    }

    fun agregarTareaARegistroHoy(solicitudId: String, tarea: Tarea) {
        viewModelScope.launch {
            try {
                val today = getTodayDate()
                val regId = UUID.randomUUID().toString()
                val registro = RegistroTarea(
                    id = regId,
                    tareaId = tarea.id,
                    solicitudId = solicitudId,
                    fecha = today,
                    horarioPrevisto = tarea.horarioInicio,
                    titulo = tarea.titulo,
                    tipo = tarea.tipo
                )
                db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas").document(regId)
                    .set(registro).await()
            } catch (e: Exception) {}
        }
    }

    fun marcarTareaCompletada(solicitudId: String, registroId: String, completada: Boolean, userId: String, notas: String = "") {
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "completada" to completada,
                    "completadaPor" to if (completada) userId else "",
                    "fechaCompletado" to if (completada) Timestamp.now() else null,
                    "notas" to notas
                )
                db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas").document(registroId)
                    .update(updates).await()
            } catch (e: Exception) {}
        }
    }

    fun generarInstanciasParaHoy(solicitudId: String, pacienteFichaId: String) {
        val today = getTodayDate()
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

        viewModelScope.launch {
            try {
                val existing = db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas")
                    .whereEqualTo("fecha", today)
                    .get().await()
                
                if (!existing.isEmpty) return@launch

                val plantillas = db.collection("solicitudes").document(solicitudId)
                    .collection("tareas_servicio")
                    .get().await().toObjects(Tarea::class.java)

                val tareasParaHoy = plantillas.filter { tarea ->
                    when (tarea.frecuencia) {
                        FrecuenciaTarea.DIARIA, FrecuenciaTarea.INDEFINIDA -> true
                        FrecuenciaTarea.SEMANAL -> {
                            val adjustDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
                            tarea.diasSeleccionados.contains(adjustDay)
                        }
                        FrecuenciaTarea.MENSUAL -> tarea.diasSeleccionados.contains(dayOfMonth)
                        FrecuenciaTarea.UNA_VEZ -> tarea.fechaEspecifica == today
                    }
                }

                val batch = db.batch()
                tareasParaHoy.forEach { tarea ->
                    val regId = UUID.randomUUID().toString()
                    val registro = RegistroTarea(
                        id = regId,
                        tareaId = tarea.id,
                        solicitudId = solicitudId,
                        fecha = today,
                        horarioPrevisto = tarea.horarioInicio,
                        titulo = tarea.titulo,
                        tipo = tarea.tipo
                    )
                    val ref = db.collection("solicitudes").document(solicitudId)
                        .collection("registros_tareas").document(regId)
                    batch.set(ref, registro)
                }
                batch.commit().await()
            } catch (e: Exception) {}
        }
    }

    fun cargarTareasServicio(solicitudId: String) {
        tareasListener?.remove()
        tareasListener = db.collection("solicitudes").document(solicitudId)
            .collection("tareas_servicio")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasPlantilla.value = snapshot.toObjects(Tarea::class.java)
                }
            }
    }

    fun agregarTareaAServicio(solicitudId: String, tarea: Tarea) {
        viewModelScope.launch {
            try {
                db.collection("solicitudes").document(solicitudId)
                    .collection("tareas_servicio").document(tarea.id)
                    .set(tarea).await()
                
                // If it applies today, also add to today's registry
                if (debeEjecutarseHoy(tarea)) {
                    agregarTareaARegistroHoy(solicitudId, tarea)
                }
            } catch (e: Exception) {}
        }
    }

    fun eliminarTareaServicio(solicitudId: String, tareaId: String) {
        viewModelScope.launch {
            try {
                // Delete from service template
                db.collection("solicitudes").document(solicitudId)
                    .collection("tareas_servicio").document(tareaId)
                    .delete().await()
                
                // Also delete today's instance if not completed
                val snapshot = db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas")
                    .whereEqualTo("tareaId", tareaId)
                    .whereEqualTo("fecha", getTodayDate())
                    .get().await()
                
                for (doc in snapshot.documents) {
                    if (doc.getBoolean("completada") != true) {
                        doc.reference.delete().await()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error al eliminar tarea: ${e.localizedMessage}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun debeEjecutarseHoy(tarea: Tarea): Boolean {
        val today = getTodayDate()
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        
        return when (tarea.frecuencia) {
            FrecuenciaTarea.DIARIA, FrecuenciaTarea.INDEFINIDA -> true
            FrecuenciaTarea.SEMANAL -> {
                val adjustDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
                tarea.diasSeleccionados.contains(adjustDay)
            }
            FrecuenciaTarea.MENSUAL -> tarea.diasSeleccionados.contains(dayOfMonth)
            FrecuenciaTarea.UNA_VEZ -> tarea.fechaEspecifica == today
        }
    }

    fun generarInformeFinal(solicitudId: String, cuidadorUid: String, observaciones: String, onComplete: (InformeTurno) -> Unit) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas")
                    .get().await()
                
                val registros = snapshot.toObjects(RegistroTarea::class.java)
                val informe = InformeTurno(
                    solicitudId = solicitudId,
                    fecha = getTodayDate(),
                    cuidadorUid = cuidadorUid,
                    resumenTareas = registros,
                    observacionesGenerales = observaciones
                )
                
                db.collection("informes_servicios").document(informe.id).set(informe).await()
                onComplete(informe)
            } catch (e: Exception) {}
        }
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        tareasListener?.remove()
        registrosListener?.remove()
    }
}
