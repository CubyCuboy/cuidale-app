package com.example.cuidaleapp.utils

import java.security.MessageDigest

object HashUtils {
    fun hashRut(rut: String): String {
        val cleanRut = rut.replace(".", "").replace("-", "").uppercase().trim()
        val bytes = MessageDigest.getInstance("SHA-256").digest(cleanRut.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
