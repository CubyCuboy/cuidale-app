package com.example.cuidaleapp.models

import com.google.firebase.Timestamp
import java.util.UUID

enum class TipoTarea {
    COMIDA, MEDICACION, HIGIENE, ACTIVIDAD, OTRO
}

enum class FrecuenciaTarea {
    UNA_VEZ, DIARIA, SEMANAL, MENSUAL
}

data class Tarea(
    val id: String = UUID.randomUUID().toString(),
    val solicitudId: String = "", // O pacienteFichaId si es una plantilla
    val titulo: String = "",
    val descripcion: String = "",
    val tipo: TipoTarea = TipoTarea.OTRO,
    val horarioInicio: String = "", // Formato "HH:mm"
    val horarioFin: String? = null, // Opcional, para actividades con duración
    val frecuencia: FrecuenciaTarea = FrecuenciaTarea.DIARIA,
    val diasSeleccionados: List<Int> = emptyList(), // 1-7 para SEMANAL, 1-31 para MENSUAL
    val mesesSeleccionados: List<Int> = emptyList(), // 1-12 para MENSUAL
    val fechaEspecifica: String? = null, // "yyyy-MM-dd" para UNA_VEZ
    val activa: Boolean = true,
    val creadaPor: String = "",
    val fechaCreacion: Timestamp = Timestamp.now()
)

data class RegistroTarea(
    val id: String = UUID.randomUUID().toString(),
    val tareaId: String = "",
    val solicitudId: String = "",
    val fecha: String = "", // Formato "yyyy-MM-dd"
    val completada: Boolean = false,
    val completadaPor: String = "",
    val fechaCompletado: Timestamp? = null,
    val postergada: Boolean = false,
    val motivoPostergacion: String = "",
    val fechaPostergacion: Timestamp? = null,
    val notas: String = "",
    val descripcion: String = "", // Indicaciones permanentes de la plantilla
    val frecuencia: FrecuenciaTarea = FrecuenciaTarea.DIARIA,
    val horarioPrevisto: String = "",
    val titulo: String = "",
    val tipo: TipoTarea = TipoTarea.OTRO
)

data class InformeTurno(
    val id: String = UUID.randomUUID().toString(),
    val solicitudId: String = "",
    val fecha: String = "",
    val cuidadorUid: String = "",
    val resumenTareas: List<RegistroTarea> = emptyList(),
    val observacionesGenerales: String = "",
    val generadoAt: Timestamp = Timestamp.now()
)
