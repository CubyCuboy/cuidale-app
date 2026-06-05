package com.example.cuidaleapp.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.utils.ImageUtils
import com.example.cuidaleapp.utils.ProfileImageCacheManager
import com.example.cuidaleapp.models.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://cuidale-app.firebasestorage.app")
    private val prefs = application.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)

    sealed class RemoteState {
        object Idle : RemoteState()
        object Loading : RemoteState()
        object Success : RemoteState()
        data class Error(val message: String) : RemoteState()
    }

    private val _remoteState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val remoteState: StateFlow<RemoteState> = _remoteState.asStateFlow()

    private val _otherProfileState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val otherProfileState: StateFlow<RemoteState> = _otherProfileState.asStateFlow()

    private val _draftState = MutableStateFlow(EditProfileDraftState())
    val draftState: StateFlow<EditProfileDraftState> = _draftState.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _galleryState = MutableStateFlow(GalleryViewerState())
    val galleryState: StateFlow<GalleryViewerState> = _galleryState.asStateFlow()

    private val _otherFichaState = MutableStateFlow<FichaState>(FichaState.Loading)
    val otherFichaState: StateFlow<FichaState> = _otherFichaState.asStateFlow()

    private val _userData = MutableStateFlow(getCachedUser())
    val userData: StateFlow<UserData> = _userData.asStateFlow()

    private val _otherUserData = MutableStateFlow<UserData?>(null)
    val otherUserData: StateFlow<UserData?> = _otherUserData.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private var verificationId: String? = null

    init {
        loadUserProfile()
    }

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    private fun getCachedUser(): UserData {
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
            comuna = prefs.getString("user_comuna", "") ?: "",
            mostrarCorreo = prefs.getBoolean("user_mostrarCorreo", false),
            mostrarRut = prefs.getBoolean("user_mostrarRut", false),
            mostrarTelefono = prefs.getBoolean("user_mostrarTelefono", false),
            privacidadVinculos = prefs.getString("user_privacidadVinculos", "privado") ?: "privado",
            correoAlternativo = prefs.getString("user_correoAlternativo", "") ?: ""
        )
    }

    private suspend fun loadUserProfileInternal() {
        val uid = auth.currentUser?.uid ?: return
        _remoteState.value = RemoteState.Loading

        try {
            val doc = db.collection("usuarios").document(uid).get().await()
            val user = doc.toObject(UserData::class.java)?.copy(uid = uid)

            if (user != null) {
                saveUserToCache(user)
                _userData.value = user
                _remoteState.value = RemoteState.Success
            } else {
                _remoteState.value = RemoteState.Error("No se encontró el usuario")
            }
        } catch (e: Exception) {
            _remoteState.value = RemoteState.Error(e.localizedMessage ?: "Error al cargar perfil")
        }
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            loadUserProfileInternal()
        }
    }

    fun refreshUserData() {
        _userData.value = getCachedUser()
        loadUserProfile()
    }

    fun updateProfile(updatedUser: UserData) {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _remoteState.value = RemoteState.Loading
            try {
                val userMap = mapOf(
                    "nombre" to updatedUser.nombre,
                    "nombre_s" to updatedUser.nombre_s,
                    "apellido_pa" to updatedUser.apellido_pa,
                    "apellido_ma" to updatedUser.apellido_ma,
                    "telefono" to updatedUser.telefono,
                    "region" to updatedUser.region,
                    "comuna" to updatedUser.comuna,
                    "descripcion" to updatedUser.descripcion
                )
                db.collection("usuarios").document(uid).update(userMap).await()
                
                saveUserToCache(updatedUser)
                _userData.value = updatedUser
                
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

    private fun saveUserToCache(user: UserData) {
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
            putBoolean("user_mostrarCorreo", user.mostrarCorreo)
            putBoolean("user_mostrarRut", user.mostrarRut)
            putBoolean("user_mostrarTelefono", user.mostrarTelefono)
            putString("user_privacidadVinculos", user.privacidadVinculos)
            putString("user_correoAlternativo", user.correoAlternativo)
            apply()
        }
    }

    fun logout(onSuccess: () -> Unit) {
        auth.signOut()
        prefs.edit().clear().apply()
        onSuccess()
    }

    fun openGallery(index: Int, photos: List<ProfileImage>) {
        _galleryState.value = GalleryViewerState(
            isOpen = true,
            initialIndex = index,
            photos = photos
        )
    }

    fun closeGallery() {
        _galleryState.value = _galleryState.value.copy(isOpen = false)
    }
}
