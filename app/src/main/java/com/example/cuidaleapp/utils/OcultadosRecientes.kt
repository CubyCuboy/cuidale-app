package com.example.cuidaleapp.utils

object OcultadosRecientes {
    val uids = mutableSetOf<String>()
    fun marcar(uid: String) { uids.add(uid) }
    fun limpiar(uid: String) { uids.remove(uid) }
}
