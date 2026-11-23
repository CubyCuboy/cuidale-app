package com.example.cuidaleapp.models

import com.google.firebase.Timestamp
import java.util.UUID

enum class TipoTarea {
    COMIDA, MEDICACION, HIGIENE, ACTIVIDAD, INYECCION, CAMBIO_SUERO, CAMBIO_POSTURA, OTRO
}

enum class FrecuenciaTarea {
    UNA_VEZ, DIARIA, SEMANAL, MENSUAL
}

data class Tarea(
    val id: String = UUID.randomUUID().toString(),
    val solicitudId: String = "",
    val titulo: String = "",
    val descripcion: String = "",
    val tipo: TipoTarea = TipoTarea.OTRO,
    val horarioInicio: String = "",
    val horarioFin: String? = null,
    val frecuencia: FrecuenciaTarea = FrecuenciaTarea.DIARIA,
    val diasSeleccionados: List<Int> = emptyList(),
    val mesesSeleccionados: List<Int> = emptyList(),
    val dosis: String = "",
    val unidades: String = "",
    val viaAdministracion: String = "",
    val fechaEspecifica: String? = null,
    val exigirFoto: Boolean = false,
    val activa: Boolean = true,
    val creadaPor: String = "",
    val fechaCreacion: Timestamp = Timestamp.now()
)

data class RegistroTarea(
    val id: String = UUID.randomUUID().toString(),
    val tareaId: String = "",
    val solicitudId: String = "",
    val fecha: String = "",
    val completada: Boolean = false,
    val completadaPor: String = "",
    val fechaCompletado: Timestamp? = null,
    val postergada: Boolean = false,
    val motivoPostergacion: String = "",
    val fechaPostergacion: Timestamp? = null,
    val notas: String = "",
    val fotoUrl: String = "",
    val fotoStoragePath: String = "",
    val exigirFoto: Boolean = false,
    val sinEvidenciaFotografica: Boolean = false,
    val descripcion: String = "",
    val frecuencia: FrecuenciaTarea = FrecuenciaTarea.DIARIA,
    val horarioPrevisto: String = "",
    val titulo: String = "",
    val tipo: TipoTarea = TipoTarea.OTRO,
    val dosis: String = "",
    val unidades: String = "",
    val viaAdministracion: String = ""
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
