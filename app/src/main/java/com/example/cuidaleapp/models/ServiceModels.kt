package com.example.cuidaleapp.models

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@Immutable
@IgnoreExtraProperties
data class SolicitudCuidado(
    val id: String = "",
    val representanteUid: String = "",
    val representanteNombre: String = "",
    val representanteRut: String = "",
    val representanteTelefono: String = "",
    val representanteFoto: String = "",
    val pacienteFichaId: String = "",
    val nombrePaciente: String = "",
    val fotoPaciente: String = "",
    val fechaInicio: Timestamp? = null,
    val fechaFin: Timestamp? = null,
    val esIndefinido: Boolean = false,
    val horarioStr: String = "",
    val periodos: List<String> = emptyList(),
    val estado: String = "abierta",
    val cuidadorUid: String? = null,
    val cuidadorNombre: String = "",
    val cuidadorFoto: String = "",
    val aceptadoAt: Timestamp? = null,
    val exigirFotoTareas: Boolean = false,
    val tarifaHora: Long = 0L,
    val tarifaHoraReferencia: Long? = null,
    val estadoAcuerdo: String = "acordado",
    val propuestaPrecio: Long? = null,
    val acuerdoAceptadoRepresentante: Boolean = false,
    val acuerdoAceptadoCuidador: Boolean = false,
    val horarioAcuerdo: Map<String, Boolean> = emptyMap(),
    val cobroEstado: String? = null,
    val createdAt: Timestamp? = null
)

@Immutable
data class PostulacionCuidado(
    val id: String = "",
    val solicitudId: String = "",
    val cuidadorUid: String = "",
    val cuidadorNombre: String = "",
    val cuidadorFoto: String = "",
    val estado: String = "pendiente",
    val timestamp: Timestamp? = null
)

@Immutable
data class HistorialServicio(
    val id: String = "",
    val solicitudId: String = "",
    val representanteUid: String = "",
    val representanteNombre: String = "",
    val representanteTelefono: String = "",
    val pacienteNombre: String = "",
    val pacienteEdad: String = "",
    val cuidadorUid: String = "",
    val cuidadorNombre: String = "",
    val cuidadorEspecialidad: String = "",
    val fechaInicio: Timestamp? = null,
    val fechaTermino: Timestamp? = null,
    val aceptadoAt: Timestamp? = null,
    val calificacion: Double? = null,
    val observacionCalificacion: String = "",
    val montoPagado: Long? = null,
    val cobroId: String? = null,
    val turnoId: String? = null,
    val eliminadoPorRepresentante: Boolean = false,
    val eliminadoPorCuidador: Boolean = false
)
