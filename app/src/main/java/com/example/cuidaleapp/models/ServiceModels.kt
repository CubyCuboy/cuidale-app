package com.example.cuidaleapp.models

import com.google.firebase.Timestamp

data class SolicitudCuidado(
    val id: String = "",
    val representanteUid: String = "",
    val pacienteFichaId: String = "",
    val nombrePaciente: String = "",
    val fotoPaciente: String = "",
    val fechaInicio: Timestamp? = null,
    val fechaFin: Timestamp? = null,
    val esIndefinido: Boolean = false,
    val horarioStr: String = "", // e.g. "De 09:30 a 11:30"
    val periodos: List<String> = emptyList(), // ["Mañana", "Tarde", "Noche"]
    val estado: String = "abierta", // "abierta", "cerrada", "finalizada"
    val cuidadorUid: String? = null,
    val cuidadorNombre: String = "",
    val cuidadorFoto: String = "",
    val createdAt: Timestamp? = null
)

data class PostulacionCuidado(
    val id: String = "",
    val solicitudId: String = "",
    val cuidadorUid: String = "",
    val cuidadorNombre: String = "",
    val cuidadorFoto: String = "",
    val estado: String = "pendiente", // "pendiente", "rechazada", "aceptada"
    val timestamp: Timestamp? = null
)
