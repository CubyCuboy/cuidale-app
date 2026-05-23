package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    private val _userData = MutableStateFlow(getCachedUser())
    val userData: StateFlow<UserData> = _userData

    private val _isLoggedOut = MutableStateFlow(false)
    val isLoggedOut: StateFlow<Boolean> = _isLoggedOut

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    private fun getCachedUser(): UserData {
        return UserData(
            nombre = prefs.getString("user_nombre", "") ?: "",
            apellido_pa = prefs.getString("user_apellido", "") ?: "",
            correo = prefs.getString("user_correo", "") ?: "",
            rut = prefs.getString("user_rut", "") ?: "",
            rol = prefs.getString("user_rol", "") ?: ""
        )
    }

    fun logout() {
        auth.signOut()
        prefs.edit().clear().apply()
        _isLoggedOut.value = true
    }

    fun resetPassword(email: String) {
        if (email.isNotBlank()) {
            auth.sendPasswordResetEmail(email)
        }
    }
}
