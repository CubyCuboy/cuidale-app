package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val userSession = UserSession(application)

    private val _userData = MutableStateFlow(userSession.getUser())
    val userData: StateFlow<UserData> = _userData.asStateFlow()

    private val _isLoggedOut = MutableStateFlow(false)
    val isLoggedOut: StateFlow<Boolean> = _isLoggedOut.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private var myProfileListener: ListenerRegistration? = null

    init {
        startMyProfileListener()
    }

    private fun startMyProfileListener() {
        val uid = auth.currentUser?.uid ?: return
        val rut = userSession.getUser().rut
        myProfileListener?.remove()

        if (rut.isNotBlank()) {
            val hashedRut = com.example.cuidaleapp.utils.HashUtils.hashRut(rut)
            myProfileListener = db.collection("usuarios").document(hashedRut)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val user = snapshot.toObject(UserData::class.java)?.copy(uid = uid)
                    if (user != null) {
                        userSession.saveUser(user)
                        _userData.value = user
                    }
                }
        } else {
            myProfileListener = db.collection("usuarios").whereEqualTo("uid", uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                    val user = snapshot.documents[0].toObject(UserData::class.java)?.copy(uid = uid)
                    if (user != null) {
                        userSession.saveUser(user)
                        _userData.value = user
                    }
                }
        }
    }

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun updateUserData() {
        val uid = auth.currentUser?.uid ?: return
        val rut = userSession.getUser().rut
        viewModelScope.launch {
            try {
                val remoteUser = if (rut.isNotBlank()) {
                    val hashedRut = com.example.cuidaleapp.utils.HashUtils.hashRut(rut)
                    val doc = db.collection("usuarios").document(hashedRut).get().await()
                    doc.toObject(UserData::class.java)?.copy(uid = uid)
                } else {
                    val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                    if (!query.isEmpty) query.documents[0].toObject(UserData::class.java)?.copy(uid = uid) else null
                }
                if (remoteUser != null) {
                    userSession.saveUser(remoteUser)
                    _userData.value = remoteUser
                } else {
                    _userData.value = userSession.getUser()
                }
            } catch (e: Exception) {
                _userData.value = userSession.getUser()
            }
        }
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

    override fun onCleared() {
        super.onCleared()
        myProfileListener?.remove()
    }
}
