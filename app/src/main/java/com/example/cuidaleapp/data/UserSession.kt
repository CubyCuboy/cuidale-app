package com.example.cuidaleapp.data

import android.content.Context
import android.content.SharedPreferences
import com.example.cuidaleapp.models.UserData

class UserSession(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    fun saveUser(user: UserData) {
        prefs.edit().apply {
            putString("user_uid", user.uid)
            putString("user_nombre", user.nombre)
            putString("user_nombre_s", user.nombre_s)
            putString("user_apellido_pa", user.apellido_pa)
            putString("user_apellido_ma", user.apellido_ma)
            putString("user_correo", user.correo)
            putString("user_rut", user.rut)
            putString("user_rol", user.rol)
            putString("user_telefono", user.telefono)
            putString("user_fotoUrl", user.fotoUrl)
            putBoolean("user_telefonoVerificado", user.telefonoVerificado)
            putString("user_descripcion", user.descripcion)
            putString("user_id_region", user.id_region)
            putString("user_region", user.region)
            putString("user_comuna", user.comuna)
            apply()
        }
    }

    fun getUser(): UserData {
        return UserData(
            uid = prefs.getString("user_uid", "") ?: "",
            nombre = prefs.getString("user_nombre", "") ?: "",
            nombre_s = prefs.getString("user_nombre_s", "") ?: "",
            apellido_pa = prefs.getString("user_apellido_pa", "") ?: "",
            apellido_ma = prefs.getString("user_apellido_ma", "") ?: "",
            correo = prefs.getString("user_correo", "") ?: "",
            rut = prefs.getString("user_rut", "") ?: "",
            rol = prefs.getString("user_rol", "") ?: "",
            telefono = prefs.getString("user_telefono", "") ?: "",
            fotoUrl = prefs.getString("user_fotoUrl", "") ?: "",
            telefonoVerificado = prefs.getBoolean("user_telefonoVerificado", false),
            descripcion = prefs.getString("user_descripcion", "") ?: "",
            id_region = prefs.getString("user_id_region", "") ?: "",
            region = prefs.getString("user_region", "") ?: "",
            comuna = prefs.getString("user_comuna", "") ?: ""
        )
    }

    fun clear() {
        // Keep theme settings but clear user data
        val isDark = prefs.getBoolean("dark_mode", false)
        prefs.edit().clear().apply()
        prefs.edit().putBoolean("dark_mode", isDark).apply()
    }
}
