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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference
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

    private val _otherUserData = MutableStateFlow<UserData?>(null)
    val otherUserData: StateFlow<UserData?> = _otherUserData.asStateFlow()

    private val _otherProfileState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val otherProfileState: StateFlow<RemoteState> = _otherProfileState.asStateFlow()

    private val _otherFichaState = MutableStateFlow<FichaState>(FichaState.Loading)
    val otherFichaState: StateFlow<FichaState> = _otherFichaState.asStateFlow()

    private val _otherUserStatus = MutableStateFlow("desconectado")
    val otherUserStatus: StateFlow<String> = _otherUserStatus.asStateFlow()

    private val _otherTareasPlantilla = MutableStateFlow<List<com.example.cuidaleapp.models.Tarea>>(emptyList())
    val otherTareasPlantilla: StateFlow<List<com.example.cuidaleapp.models.Tarea>> = _otherTareasPlantilla.asStateFlow()

    private var statusListener: ValueEventListener? = null
    private var otherProfileListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var myProfileListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _photoUpdateTrigger = MutableStateFlow(0L)
    val photoUpdateTrigger: StateFlow<Long> = _photoUpdateTrigger.asStateFlow()

    sealed class RemoteState {
        object Idle : RemoteState()
        object Loading : RemoteState()
        object Success : RemoteState()
        data class Error(val message: String) : RemoteState()
    }

    init {
        startMyProfileListener()
    }

    private fun startMyProfileListener() {
        val uid = auth.currentUser?.uid ?: return
        myProfileListener?.remove()
        myProfileListener = db.collection("usuarios").document(uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val user = snapshot.toObject(UserData::class.java)?.copy(uid = uid)
                android.util.Log.d("ProfileVM", "Snapshot received: fotoUrl=${user?.fotoUrl}")
                if (user != null) {
                    userSession.saveUser(user)
                    _userData.value = user
                }
            }
    }

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun resetRemoteState() {
        _remoteState.value = RemoteState.Idle
    }

    fun refreshFromFirestore() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val document = db.collection("usuarios").document(uid).get().await()
                val remoteUser = document.toObject(UserData::class.java)?.copy(uid = uid)
                if (remoteUser != null) {
                    userSession.saveUser(remoteUser)
                    _userData.value = remoteUser
                }
            } catch (e: Exception) {
                _userData.value = userSession.getUser()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun uploadProfilePicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val oldPhotoUrl = _userData.value.fotoUrl

        viewModelScope.launch {
            _remoteState.value = RemoteState.Loading
            try {
                val context = getApplication<Application>()
                
                // Compresión para asegurar los < 5MB de tus reglas
                val compressedBytes = com.example.cuidaleapp.utils.ImageUtils.getCompressedImage(context, uri) 
                    ?: throw Exception("No se pudo procesar la imagen")
                
                // Nombre único para evitar caché de navegador/app
                val filename = "profile_${System.currentTimeMillis()}.jpg"
                val ref = storage.reference.child("profiles/$uid/$filename")
                
                val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build()

                // Subida física
                ref.putBytes(compressedBytes, metadata).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                // Actualización en Firestore (esto disparará el myProfileListener automáticamente)
                db.collection("usuarios").document(uid).update("fotoUrl", downloadUrl).await()

                // Sincronizar con Ficha si existe
                try {
                    val fichaRef = db.collection("fichas").document(uid)
                    val fichaDoc = fichaRef.get().await()
                    if (fichaDoc.exists()) {
                        fichaRef.update("fotoUrl", downloadUrl).await()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileVM", "Error syncing photo with Ficha: ${e.message}")
                }

                // Borrar rastro de la anterior en Storage
                if (oldPhotoUrl.isNotEmpty() && oldPhotoUrl.contains("firebasestorage.googleapis.com")) {
                    try {
                        val oldRef = storage.getReferenceFromUrl(oldPhotoUrl)
                        oldRef.delete().await()
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "Error cleanup Storage: ${e.message}")
                    }
                }

                // Forzamos actualización local por si el listener tarda unos ms
                val updatedUser = _userData.value.copy(fotoUrl = downloadUrl)
                userSession.saveUser(updatedUser)
                _userData.value = updatedUser
                
                _remoteState.value = RemoteState.Success
                // Reset inmediato para permitir siguientes acciones
                kotlinx.coroutines.delay(500)
                _remoteState.value = RemoteState.Idle
            } catch (e: Exception) {
                _remoteState.value = RemoteState.Error(e.message ?: "Error al actualizar foto")
            }
        }
    }

    fun deleteProfilePicture() {
        val uid = auth.currentUser?.uid ?: return
        val oldPhotoUrl = _userData.value.fotoUrl

        viewModelScope.launch {
            _remoteState.value = RemoteState.Loading
            try {
                db.collection("usuarios").document(uid).update("fotoUrl", "").await()

                // Sincronizar con Ficha
                try {
                    val fichaRef = db.collection("fichas").document(uid)
                    if (fichaRef.get().await().exists()) {
                        fichaRef.update("fotoUrl", "").await()
                    }
                } catch (e: Exception) {}

                if (oldPhotoUrl.isNotEmpty() && oldPhotoUrl.contains("firebasestorage.googleapis.com")) {
                    try {
                        val oldRef = storage.getReferenceFromUrl(oldPhotoUrl)
                        oldRef.delete().await()
                    } catch (e: Exception) { }
                }

                val updatedUser = _userData.value.copy(fotoUrl = "")
                userSession.saveUser(updatedUser)
                _userData.value = updatedUser
                _photoUpdateTrigger.value = System.currentTimeMillis()
                _remoteState.value = RemoteState.Success
            } catch (e: Exception) {
                _remoteState.value = RemoteState.Error(e.message ?: "Error al eliminar imagen")
            }
        }
    }

    fun updateProfileWithPhoto(updatedUser: UserData, newPhotoUri: Uri?, shouldDeleteOld: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _remoteState.value = RemoteState.Loading
            try {
                var finalPhotoUrl = updatedUser.fotoUrl

                // 1. Manejo de foto (Subir nueva o Marcar para eliminar)
                if (newPhotoUri != null) {
                    val context = getApplication<Application>()
                    val compressedBytes = com.example.cuidaleapp.utils.ImageUtils.getCompressedImage(context, newPhotoUri)
                        ?: throw Exception("No se pudo procesar la imagen")
                    
                    val filename = "profile_${System.currentTimeMillis()}.jpg"
                    val ref = storage.reference.child("profiles/$uid/$filename")
                    
                    val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build()

                    // Subida física
                    ref.putBytes(compressedBytes, metadata).await()
                    val newDownloadUrl = ref.downloadUrl.await().toString()

                    // Intentar borrar la vieja (ignoramos error si las reglas lo bloquean)
                    if (finalPhotoUrl.isNotEmpty() && finalPhotoUrl.contains("firebasestorage.googleapis.com")) {
                        try {
                            storage.getReferenceFromUrl(finalPhotoUrl).delete().await()
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileVM", "No se pudo borrar vieja: ${e.message}")
                        }
                    }
                    finalPhotoUrl = newDownloadUrl
                } else if (shouldDeleteOld) {
                    // Borrado solicitado por el usuario
                    if (finalPhotoUrl.isNotEmpty() && finalPhotoUrl.contains("firebasestorage.googleapis.com")) {
                        try {
                            storage.getReferenceFromUrl(finalPhotoUrl).delete().await()
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileVM", "Error al borrar foto: ${e.message}")
                        }
                    }
                    finalPhotoUrl = ""
                }

                // 2. Actualizar Firestore
                val sanitizedPhone = PhoneUtils.formatChileanPhone(updatedUser.telefono)
                val userMap = mapOf(
                    "nombre" to updatedUser.nombre,
                    "nombre_s" to updatedUser.nombre_s,
                    "apellido_pa" to updatedUser.apellido_pa,
                    "apellido_ma" to updatedUser.apellido_ma,
                    "telefono" to sanitizedPhone,
                    "region" to updatedUser.region,
                    "comuna" to updatedUser.comuna,
                    "descripcion" to updatedUser.descripcion,
                    "fotoUrl" to finalPhotoUrl
                )

                db.collection("usuarios").document(uid).update(userMap).await()

                // Sincronizar con Ficha si existe
                try {
                    val fichaRef = db.collection("fichas").document(uid)
                    val fichaDoc = fichaRef.get().await()
                    if (fichaDoc.exists()) {
                        val fichaUpdate = mapOf(
                            "fotoUrl" to finalPhotoUrl,
                            "nombre" to updatedUser.nombre,
                            "apellido_pa" to updatedUser.apellido_pa
                        )
                        fichaRef.update(fichaUpdate).await()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileVM", "Error syncing with Ficha: ${e.message}")
                }
                
                // Forzar actualización local para que la UI no parpadee
                val finalUser = updatedUser.copy(telefono = sanitizedPhone, fotoUrl = finalPhotoUrl)
                userSession.saveUser(finalUser)
                _userData.value = finalUser
                
                _remoteState.value = RemoteState.Success
                kotlinx.coroutines.delay(300)
                _remoteState.value = RemoteState.Idle
            } catch (e: Exception) {
                android.util.Log.e("ProfileVM", "Error total: ${e.message}")
                _remoteState.value = RemoteState.Error(e.message ?: "Error al guardar cambios")
            }
        }
    }

    fun updateProfile(updatedUser: UserData) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _remoteState.value = RemoteState.Loading
            try {
                val sanitizedPhone = PhoneUtils.formatChileanPhone(updatedUser.telefono)
                val finalUser = updatedUser.copy(telefono = sanitizedPhone)

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

                // Sincronizar con Ficha si existe
                try {
                    val fichaRef = db.collection("fichas").document(uid)
                    val fichaDoc = fichaRef.get().await()
                    if (fichaDoc.exists()) {
                        val fichaUpdate = mapOf(
                            "nombre" to finalUser.nombre,
                            "apellido_pa" to finalUser.apellido_pa
                        )
                        fichaRef.update(fichaUpdate).await()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileVM", "Error syncing with Ficha: ${e.message}")
                }
                userSession.saveUser(finalUser)
                _userData.value = finalUser
                _remoteState.value = RemoteState.Success
            } catch (e: Exception) {
                _remoteState.value = RemoteState.Error(e.message ?: "Error al actualizar perfil")
            }
        }
    }

    fun loadOtherUserProfile(targetUid: String) {
        if (targetUid.isEmpty()) return
        val myUid = auth.currentUser?.uid ?: ""

        otherProfileListener?.remove()
        statusListener?.let { rtdb.child("status").child(targetUid).removeEventListener(it) }

        viewModelScope.launch {
            _otherProfileState.value = RemoteState.Loading
            _otherFichaState.value = FichaState.Loading
            
            // Listener en tiempo real para que los cambios de otros se reflejen al instante
            otherProfileListener = db.collection("usuarios").document(targetUid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        _otherProfileState.value = RemoteState.Error(e.localizedMessage ?: "Error al cargar perfil")
                        return@addSnapshotListener
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        _otherProfileState.value = RemoteState.Error("El usuario no existe.")
                        return@addSnapshotListener
                    }

                    val user = snapshot.toObject(UserData::class.java)?.copy(uid = targetUid)
                    
                    val targetBlockedList = (snapshot.get("bloqueados") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    if (targetBlockedList.contains(myUid)) {
                        _otherProfileState.value = RemoteState.Error("Este perfil es privado.")
                        _otherFichaState.value = FichaState.Error("Perfil Privado")
                        return@addSnapshotListener
                    }

                    _otherUserData.value = user
                    _otherProfileState.value = RemoteState.Success
                }

            // Carga de ficha y tareas (mantenemos lógica de carga inicial)
            try {
                val fichaDoc = db.collection("fichas").document(targetUid).get().await()
                if (fichaDoc.exists()) {
                    _otherFichaState.value = FichaState.View(fichaDoc.data ?: emptyMap())
                    val tareasSnapshot = db.collection("fichas").document(targetUid)
                        .collection("tareas_plantilla")
                        .whereEqualTo("activa", true)
                        .get().await()
                    _otherTareasPlantilla.value = tareasSnapshot.toObjects(com.example.cuidaleapp.models.Tarea::class.java)
                } else {
                    _otherFichaState.value = FichaState.NotFound
                }

                statusListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        _otherUserStatus.value = snapshot.child("estado").getValue(String::class.java) ?: "desconectado"
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                rtdb.child("status").child(targetUid).addValueEventListener(statusListener!!)
            } catch (e: Exception) {
                _otherFichaState.value = FichaState.Error("Error")
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

    override fun onCleared() {
        super.onCleared()
        myProfileListener?.remove()
        otherProfileListener?.remove()
        statusListener?.let { listener ->
            _otherUserData.value?.uid?.let { uid ->
                rtdb.child("status").child(uid).removeEventListener(listener)
            }
        }
    }
}
