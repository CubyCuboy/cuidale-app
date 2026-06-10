package com.example.cuidaleapp.models

data class Notification(
    val id: String = "",
    val tipo: String = "", // "solicitud_vinculo", "vinculo_aceptado"
    val emisor: String = "",
    val receptor: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val leida: Boolean = false,
    val data: Map<String, String> = emptyMap()
)
