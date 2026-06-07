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
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
    private val userSession = UserSession(application)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _userData = MutableStateFlow(userSession.getUser())
    val userData: StateFlow<UserData> = _userData.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _remoteState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val remoteState: StateFlow<RemoteState> = _remoteState.asStateFlow()

    // Temporary photo URL for edit session - only committed on save
    private var _tempPhotoUrl: String? = null
    private var _pendingPhotoOperation: PhotoOperation? = null
    
    enum class PhotoOperation { UPLOAD, DELETE }

    private val _otherUserData = MutableStateFlow<UserData?>(null)
    val otherUserData: StateFlow<UserData?> = _otherUserData.asStateFlow()

    private val _otherProfileState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val otherProfileState: StateFlow<RemoteState> = _otherProfileState.asStateFlow()


    private val _otherFichaState = MutableStateFlow<FichaState>(FichaState.Loading)
    val otherFichaState: StateFlow<FichaState> = _otherFichaState.asStateFlow()


    sealed class RemoteState {
        object Idle : RemoteState()
        object Loading : RemoteState()
        object Success : RemoteState()
        data class Error(val message: String) : RemoteState()
    }

    init {
        refreshFromFirestore()
    }

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    // Revert pending photo changes and restore original
    fun revertPendingPhotoChanges() {
        val originalUrl = _userData.value.fotoUrl
        _tempPhotoUrl = null
        _pendingPhotoOperation = null
        
        // Restore UI to original state
        val restoredUser = _userData.value.copy(fotoUrl = originalUrl)
        userSession.saveUser(restoredUser)
        _userData.value = restoredUser
    }
    
    fun refreshFromFirestore() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val document = db.collection("usuarios").document(uid).get().await()
                val remoteUser = document.toObject(UserData::class.java)
                if (remoteUser != null) {
                    userSession.saveUser(remoteUser)
                    _userData.value = remoteUser
                }
            } catch (e: Exception) {
                // Fallback to local session if firestore fails
                _userData.value = userSession.getUser()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun resetRemoteState() {
        _remoteState.value = RemoteState.Idle
    }
    
    // Get the display photo URL (committed or pending)
    fun getDisplayPhotoUrl(): String {
        return _tempPhotoUrl ?: _userData.value.fotoUrl
    }
    
    // Check if there's a pending photo change
    fun hasPendingPhotoChange(): Boolean = _tempPhotoUrl != null || _pendingPhotoOperation != null
    
    // Reset pending photo changes
    fun resetPendingPhotoChanges() {
        _tempPhotoUrl = null
        _pendingPhotoOperation = null
    }

    fun uploadProfilePicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val oldPhotoUrl = _userData.value.fotoUrl

        viewModelScope.launch {
            try {
                // 1. Upload new photo first with unique name
                val filename = \"profile_${System.currentTimeMillis()}.jpg\"
                val ref = storage.reference.child(\"profiles/$uid/$filename\")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString().replace("\"", "")

                // Store temporarily - don't update Firestore yet
                _tempPhotoUrl = downloadUrl
                _pendingPhotoOperation = PhotoOperation.UPLOAD
                
                // Update UI state immediately for preview
                val updatedUser = _userData.value.copy(fotoUrl = downloadUrl)
                userSession.saveUser(updatedUser)
                _userData.value = updatedUser
                
            } catch (e: Exception) {
                _remoteState.value = RemoteState.Error(e.message ?: "Error al subir imagen")
            }
        }
    }

    fun deleteProfilePicture() {
        val uid = auth.currentUser?.uid ?: return
        val oldPhotoUrl = _userData.value.fotoUrl

        viewModelScope.launch {
            try {
                // Mark for deletion - will be processed on save
                _tempPhotoUrl = ""
                _pendingPhotoOperation = PhotoOperation.DELETE
                
                // Update UI state immediately for preview
                val updatedUser = _userData.value.copy(fotoUrl = "")
                userSession.saveUser(updatedUser)
                _userData.value = updatedUser
                
            } catch (e: Exception) {
                _remoteState.value = RemoteState.Error(e.message ?: "Error al eliminar imagen")
            }
        }
    }
    
    // Commit pending photo changes to Firestore
    private suspend fun commitPhotoChanges(uid: String) {
        val operation = _pendingPhotoOperation ?: return
        val oldPhotoUrl = _userData.value.fotoUrl
        
        when (operation) {
            PhotoOperation.UPLOAD -> {
                // Update Firestore with new photo URL
                db.collection("usuarios").document(uid).update("fotoUrl", _tempPhotoUrl ?: "").await()
                
                // Delete old photo from storage if exists
                if (oldPhotoUrl.isNotEmpty() && oldPhotoUrl.contains("firebasestorage.googleapis.com")) {
                    try {
                        val oldRef = storage.getReferenceFromUrl(oldPhotoUrl)
                        oldRef.delete().await()
                    } catch (e: Exception) {
                        // Ignore storage deletion errors
                    }
                }
            }
            PhotoOperation.DELETE -> {
                // Update Firestore to remove photo URL
                db.collection("usuarios").document(uid).update("fotoUrl", "").await()
                
                // Delete photo from storage if exists
                if (oldPhotoUrl.isNotEmpty() && oldPhotoUrl.contains("firebasestorage.googleapis.com")) {
                    try {
                        val oldRef = storage.getReferenceFromUrl(oldPhotoUrl)
                        oldRef.delete().await()
                    } catch (e: Exception) {
                        // Ignore storage deletion errors
                    }
                }
            }
        }
        
        // Clear pending state
        _tempPhotoUrl = null
        _pendingPhotoOperation = null
    }

    fun updateProfile(updatedUser: UserData) {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _remoteState.value = RemoteState.Loading
            try {
                val sanitizedPhone = PhoneUtils.formatChileanPhone(updatedUser.telefono)
                var finalUser = updatedUser.copy(telefono = sanitizedPhone)

                // Commit pending photo changes first if any
                if (hasPendingPhotoChange()) {
                    commitPhotoChanges(uid)
                    // Update finalUser with the new photo URL
                    finalUser = finalUser.copy(fotoUrl = getDisplayPhotoUrl())
                }

                // Update in Firestore
                val userMap = mutableMapOf(
                    "nombre" to finalUser.nombre,
                    "nombre_s" to finalUser.nombre_s,
                    "apellido_pa" to finalUser.apellido_pa,
                    "apellido_ma" to finalUser.apellido_ma,
                    "telefono" to finalUser.telefono,
                    "region" to finalUser.region,
                    "comuna" to finalUser.comuna,
                    "descripcion" to finalUser.descripcion
                )
                
                // Add fotoUrl to update if there was a photo change
                if (hasPendingPhotoChange()) {
                    userMap["fotoUrl"] = getDisplayPhotoUrl()
                }
                
                db.collection("usuarios").document(uid).update(userMap).await()
                
                // Update in Session
                userSession.saveUser(finalUser)
                _userData.value = finalUser
                
                // Reset pending photo changes after successful save
                resetPendingPhotoChanges()
                
                _remoteState.value = RemoteState.Success
            } catch (e: Exception) {
                _remoteState.value = RemoteState.Error(e.message ?: "Error al actualizar perfil")
            }
        }
    }

    fun loadOtherUserProfile(targetUid: String) {
        if (targetUid.isEmpty()) return

        viewModelScope.launch {
            _otherProfileState.value = RemoteState.Loading
            _otherFichaState.value = FichaState.Loading
            _otherUserData.value = null // Clear previous data

            try {
                // 1. Fetch User Profile
                val userDoc = db.collection("usuarios").document(targetUid).get().await()

                if (userDoc.exists()) {
                    val user = userDoc.toObject(UserData::class.java)?.copy(uid = targetUid)
                    _otherUserData.value = user
                    _otherProfileState.value = RemoteState.Success

                    // 2. Fetch Ficha (Technical File)
                    val fichaDoc = db.collection("fichas").document(targetUid).get().await()
                    if (fichaDoc.exists()) {
                        _otherFichaState.value = FichaState.View(fichaDoc.data ?: emptyMap())
                    } else {
                        _otherFichaState.value = FichaState.NotFound
                    }
                } else {
                    _otherProfileState.value = RemoteState.Error("El usuario con ID $targetUid no existe en la base de datos.")
                    _otherFichaState.value = FichaState.Error("Usuario no encontrado")
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Error desconocido al cargar perfil"
                _otherProfileState.value = RemoteState.Error(errorMsg)
                _otherFichaState.value = FichaState.Error(errorMsg)
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

