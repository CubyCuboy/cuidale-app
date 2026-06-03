package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.PhoneUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val userSession = UserSession(application)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _userData = MutableStateFlow(userSession.getUser())
    val userData: StateFlow<UserData> = _userData.asStateFlow()

    private val _remoteState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val remoteState: StateFlow<RemoteState> = _remoteState.asStateFlow()

    sealed class RemoteState {
        object Idle : RemoteState()
        object Loading : RemoteState()
        object Success : RemoteState()
        data class Error(val message: String) : RemoteState()
    }

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun updateProfile(updatedUser: UserData) {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _remoteState.value = RemoteState.Loading
            try {
                val sanitizedPhone = PhoneUtils.formatChileanPhone(updatedUser.telefono)
                val finalUser = updatedUser.copy(telefono = sanitizedPhone)

                // Update in Firestore
                val userMap = mapOf(
                    "nombre" to finalUser.nombre,
                    "nombre_s" to finalUser.nombre_s,
                    "apellido_pa" to finalUser.apellido_pa,
                    "apellido_ma" to finalUser.apellido_ma,
                    "telefono" to finalUser.telefono,
                    "region" to finalUser.region,
                    "comuna" to finalUser.comuna,
                    "descripcion" to finalUser.descripcion
                )
                db.collection("usuarios").document(uid).update(userMap).await()
                
                // Update in Session
                userSession.saveUser(finalUser)
                _userData.value = finalUser
                
                _remoteState.value = RemoteState.Success
            } catch (e: Exception) {
                _remoteState.value = RemoteState.Error(e.message ?: "Error al actualizar perfil")
            }
        }
    }

    fun refreshUserData() {
        _userData.value = userSession.getUser()
    }

    fun logout(onComplete: () -> Unit) {
        auth.signOut()
        userSession.clear()
        onComplete()
    }
}
