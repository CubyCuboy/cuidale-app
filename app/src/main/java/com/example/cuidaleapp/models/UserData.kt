package com.example.cuidaleapp.models

data class UserData(
    val uid: String = "",
    val nombre: String = "",
    val nombre_s: String = "",
    val apellido_pa: String = "",
    val apellido_ma: String = "",
    val comuna: String = "",
    val correo: String = "",
    val correoAlternativo: String = "",
    val fotoUrl: String = "",
    val id_region: String = "",
    val mostrarCorreo: Boolean = false,
    val mostrarRut: Boolean = false,
    val mostrarTelefono: Boolean = false,
    val privacidadVinculos: String = "privado",
    val region: String = "",
    val rol: String = "",
    val rut: String = "",
    val telefono: String = "",
    val telefonoVerificado: Boolean = false,
    val verificado: Boolean = false,
    val descripcion: String = ""
)
