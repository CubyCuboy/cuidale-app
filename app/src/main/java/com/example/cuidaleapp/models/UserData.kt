package com.example.cuidaleapp.models

data class UserData(
    val uid: String = "",
    val nombre: String = "",
    val nombre_s: String = "",
    val apellido_pa: String = "",
    val apellido_ma: String = "",
    val comuna: String = "",
    val correo: String = "",
    val fotoUrl: String = "",
    val id_region: String = "",
    val region: String = "",
    val rol: String = "",
    val rut: String = "",
    val telefono: String = "",
    val telefonoVerificado: Boolean = false,
    val verificado: Boolean = false,
    val descripcion: String = ""
)
