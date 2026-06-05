package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val userSession = UserSession(application)

    private val _userData = MutableStateFlow(userSession.getUser())
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

    fun updateUserData() {
        _userData.value = userSession.getUser()
    }

    fun logout() {
        auth.signOut()
        userSession.clear()
        _isLoggedOut.value = true
    }

    fun resetPassword(email: String) {
        if (email.isNotBlank()) {
            auth.sendPasswordResetEmail(email)
        }
    }
}
