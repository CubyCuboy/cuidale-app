package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.data.UserSession
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.HashUtils
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
    private val userSession = UserSession(application)

    init {
        com.example.cuidaleapp.ui.theme.ThemeManager.ensureInit(application)
    }

    val isDarkMode: StateFlow<Boolean> get() = com.example.cuidaleapp.ui.theme.ThemeManager.isDarkMode

    private val _userData = MutableStateFlow(userSession.getUser())
    val userData: StateFlow<UserData> = _userData.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _remoteState = MutableStateFlow<RemoteState>(RemoteState.Idle)
    val remoteState: StateFlow<RemoteState> = _remoteState.asStateFlow()

    private val _otherUserData = MutableStateFlow<UserData?>(null)

    private suspend fun updateFirestoreUser(uid: String, updates: Map<String, Any>) {
        val rut = userSession.getUser().rut
        if (rut.isNotBlank()) {
            val hashedRut = HashUtils.hashRut(rut)
            db.collection("usuarios").document(hashedRut).set(updates, SetOptions.merge()).await()
        } else {
            val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
            if (!query.isEmpty) {
                query.documents[0].reference.set(updates, SetOptions.merge()).await()
            }
        }
    }
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
    private var historyListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _accountHistory = MutableStateFlow<List<com.example.cuidaleapp.models.AccountHistoryEntry>>(emptyList())
    val accountHistory: StateFlow<List<com.example.cuidaleapp.models.AccountHistoryEntry>> = _accountHistory.asStateFlow()

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

    private fun applyLinkedAccountsMigration(user: UserData, snapshot: com.google.firebase.firestore.DocumentSnapshot): UserData {
        if (!user.linkedAccounts.isEmpty()) return user
        try {
            val rawMap = snapshot.get("providerTimestamps") as? Map<*, *> ?: return user
            val rawTimestamps = mutableMapOf<String, Long>()
            rawMap.forEach { (k, v) ->
                if (k is String && v != null) {
                    when (v) {
                        is Number -> rawTimestamps[k] = v.toLong()
                        is String -> v.toLongOrNull()?.let { rawTimestamps[k] = it }
                        is Map<*, *> -> {
                            val firstNum = v.values.firstOrNull { it is Number } as? Number
                            if (firstNum != null) rawTimestamps[k] = firstNum.toLong()
                        }
                    }
                }
            }
            if (rawTimestamps.isEmpty()) return user
            val migrated = com.example.cuidaleapp.utils.AccountUtils.migrateLegacyToLinkedAccounts(
                user.linkedAccounts,
                rawTimestamps,
                user.correo,
                user.verificado
            )
            return user.copy(linkedAccounts = migrated)
        } catch (e: Exception) {
            android.util.Log.e("ProfileViewModel", "Error migrating legacy linked accounts: ${e.message}")
            return user
        }
    }

    private fun startMyProfileListener() {
        val uid = auth.currentUser?.uid ?: return
        val rut = userSession.getUser().rut
        myProfileListener?.remove()

        if (rut.isNotBlank()) {
            val hashedRut = HashUtils.hashRut(rut)
            val userDocRef = db.collection("usuarios").document(hashedRut)
            myProfileListener = userDocRef.addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val user = snapshot.toObject(UserData::class.java)
                    ?.copy(uid = uid)
                    ?.let { applyLinkedAccountsMigration(it, snapshot) }
                if (user != null) {
                    val sortedHistory = user.accountHistory.sortedByDescending { it.timestamp }
                    _accountHistory.value = sortedHistory
                    val updatedUser = user.copy(accountHistory = sortedHistory)
                    userSession.saveUser(updatedUser)
                    _userData.value = updatedUser
                }
            }
        } else {
            myProfileListener = db.collection("usuarios").whereEqualTo("uid", uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                    val doc = snapshot.documents[0]
                    val user = doc.toObject(UserData::class.java)
                        ?.copy(uid = uid)
                        ?.let { applyLinkedAccountsMigration(it, doc) }
                    if (user != null) {
                        val sortedHistory = user.accountHistory.sortedByDescending { it.timestamp }
                        _accountHistory.value = sortedHistory
                        val updatedUser = user.copy(accountHistory = sortedHistory)
                        userSession.saveUser(updatedUser)
                        _userData.value = updatedUser
                    }
                }
        }
    }

    fun toggleTheme() {
        com.example.cuidaleapp.ui.theme.ThemeManager.toggleTheme()
    }

    fun resetRemoteState() {
        _remoteState.value = RemoteState.Idle
    }

    fun refreshFromFirestore() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val rut = userSession.getUser().rut
                val userDocRef = if (rut.isNotBlank()) {
                    val hashedRut = HashUtils.hashRut(rut)
                    db.collection("usuarios").document(hashedRut)
                } else {
                    val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                    if (!query.isEmpty) query.documents[0].reference else null
                }

                if (userDocRef != null) {
                    val document = kotlinx.coroutines.withTimeoutOrNull(8000L) { userDocRef.get().await() }
                    if (document != null) {
                        val remoteUser = document.toObject(UserData::class.java)
                            ?.copy(uid = uid)
                            ?.let { applyLinkedAccountsMigration(it, document) }
                        if (remoteUser != null) {
                            val sortedHistory = remoteUser.accountHistory.sortedByDescending { it.timestamp }
                            _accountHistory.value = sortedHistory
                            val finalUser = remoteUser.copy(accountHistory = sortedHistory)
                            userSession.saveUser(finalUser)
                            _userData.value = finalUser

                            if (document.get("linkedAccounts") == null && finalUser.linkedAccounts.isNotEmpty()) {
                                userDocRef.set(mapOf("linkedAccounts" to finalUser.linkedAccounts), com.google.firebase.firestore.SetOptions.merge()).await()
                            }
                        }
                    }
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
                
                val compressedBytes = com.example.cuidaleapp.utils.ImageUtils.getCompressedImage(context, uri) 
                    ?: throw Exception("No se pudo procesar la imagen")
                
                val filename = "profile_${System.currentTimeMillis()}.jpg"
                val ref = storage.reference.child("profiles/$uid/$filename")
                
                val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build()

                ref.putBytes(compressedBytes, metadata).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                updateFirestoreUser(uid, mapOf("fotoUrl" to downloadUrl))

                if (_userData.value.rol == "cuidador") {
                    try {
                        val fichaRef = db.collection("fichas").document(uid)
                        val fichaDoc = fichaRef.get().await()
                        if (fichaDoc.exists()) {
                            fichaRef.update("fotoUrl", downloadUrl).await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileVM", "Error syncing photo with Ficha: ${e.message}")
                    }
                }

                if (oldPhotoUrl.isNotEmpty() && oldPhotoUrl.contains("firebasestorage.googleapis.com")) {
                    try {
                        val oldRef = storage.getReferenceFromUrl(oldPhotoUrl)
                        oldRef.delete().await()
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "Error cleanup Storage: ${e.message}")
                    }
                }

                val updatedUser = _userData.value.copy(fotoUrl = downloadUrl)
                userSession.saveUser(updatedUser)
                _userData.value = updatedUser
                logAccountHistory("photo_upload", "Foto de Perfil Actualizada", "Has cambiado tu foto de perfil correctamente.")
                
                _remoteState.value = RemoteState.Success
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
                updateFirestoreUser(uid, mapOf("fotoUrl" to ""))

                if (_userData.value.rol == "cuidador") {
                    try {
                        val fichaRef = db.collection("fichas").document(uid)
                        if (fichaRef.get().await().exists()) {
                            fichaRef.update("fotoUrl", "").await()
                        }
                    } catch (e: Exception) {}
                }

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

                if (newPhotoUri != null) {
                    val context = getApplication<Application>()
                    val compressedBytes = com.example.cuidaleapp.utils.ImageUtils.getCompressedImage(context, newPhotoUri)
                        ?: throw Exception("No se pudo procesar la imagen")
                    
                    val filename = "profile_${System.currentTimeMillis()}.jpg"
                    val ref = storage.reference.child("profiles/$uid/$filename")
                    
                    val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build()

                    ref.putBytes(compressedBytes, metadata).await()
                    val newDownloadUrl = ref.downloadUrl.await().toString()

                    if (finalPhotoUrl.isNotEmpty() && finalPhotoUrl.contains("firebasestorage.googleapis.com")) {
                        try {
                            storage.getReferenceFromUrl(finalPhotoUrl).delete().await()
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileVM", "No se pudo borrar vieja: ${e.message}")
                        }
                    }
                    finalPhotoUrl = newDownloadUrl
                } else if (shouldDeleteOld) {
                    if (finalPhotoUrl.isNotEmpty() && finalPhotoUrl.contains("firebasestorage.googleapis.com")) {
                        try {
                            storage.getReferenceFromUrl(finalPhotoUrl).delete().await()
                        } catch (e: Exception) {
                            android.util.Log.e("ProfileVM", "Error al borrar foto: ${e.message}")
                        }
                    }
                    finalPhotoUrl = ""
                }

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

                updateFirestoreUser(uid, userMap)

                if (updatedUser.rol == "cuidador") {
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
                }
                
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
                    "descripcion" to finalUser.descripcion,
                    "fechaNacimiento" to finalUser.fechaNacimiento,
                    "discapacidad" to finalUser.discapacidad,
                    "tipoDiscapacidad" to finalUser.tipoDiscapacidad,
                    "tiempoDiscapacidad" to finalUser.tiempoDiscapacidad,
                    "valorHoraReferencial" to finalUser.valorHoraReferencial,
                    "verificacionOficial" to finalUser.verificacionOficial,
                    "formacionAcademica" to finalUser.formacionAcademica,
                    "experienciasLaborales" to finalUser.experienciasLaborales,
                    "certificados" to finalUser.certificados,
                    "referenciasLaborales" to finalUser.referenciasLaborales
                )
                updateFirestoreUser(uid, userMap)

                if (updatedUser.rol == "cuidador") {
                    try {
                        val fichaRef = db.collection("fichas").document(uid)
                        val fichaDoc = fichaRef.get().await()
                        if (fichaDoc.exists()) {
                            val fichaUpdate = mapOf(
                                "nombre" to finalUser.nombre,
                                "apellido_pa" to finalUser.apellido_pa,
                                "descripcion" to finalUser.descripcion,
                                "fechaNacimiento" to finalUser.fechaNacimiento,
                                "discapacidad" to finalUser.discapacidad,
                                "tipoDiscapacidad" to finalUser.tipoDiscapacidad,
                                "tiempoDiscapacidad" to finalUser.tiempoDiscapacidad,
                                "valorHoraReferencial" to finalUser.valorHoraReferencial,
                                "verificacionOficial" to finalUser.verificacionOficial,
                                "formacionAcademica" to finalUser.formacionAcademica,
                                "experienciasLaborales" to finalUser.experienciasLaborales,
                                "certificados" to finalUser.certificados,
                                "referenciasLaborales" to finalUser.referenciasLaborales
                            )
                            fichaRef.update(fichaUpdate).await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileVM", "Error syncing with Ficha: ${e.message}")
                    }
                }
                userSession.saveUser(finalUser)
                _userData.value = finalUser
                logAccountHistory("profile_update", "Actualización de Perfil", "Has modificado la información de tu perfil correctamente.")
                _remoteState.value = RemoteState.Success
                kotlinx.coroutines.delay(400)
                _remoteState.value = RemoteState.Idle
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

            // Escucha de estado en tiempo real: se registra de inmediato para que el perfil
            // muestre el estado real aunque la ficha tarde en cargar.
            statusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _otherUserStatus.value = snapshot.child("estado").getValue(String::class.java) ?: "desconectado"
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            rtdb.child("status").child(targetUid).addValueEventListener(statusListener!!)
            // Valor inicial rápido (one-shot) en caso de que el listener tarde en entregar datos.
            runCatching {
                val snap = rtdb.child("status").child(targetUid).get().await()
                _otherUserStatus.value = snap.child("estado").getValue(String::class.java) ?: "desconectado"
            }

            otherProfileListener = db.collection("usuarios").whereEqualTo("uid", targetUid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        _otherProfileState.value = RemoteState.Error(e.localizedMessage ?: "Error al cargar perfil")
                        return@addSnapshotListener
                    }

                    if (snapshot == null || snapshot.isEmpty) {
                        _otherProfileState.value = RemoteState.Error("El usuario no existe.")
                        return@addSnapshotListener
                    }

                    val doc = snapshot.documents[0]
                    val user = doc.toObject(UserData::class.java)?.copy(uid = targetUid)
                    
                    val targetBlockedList = (doc.get("bloqueados") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    if (targetBlockedList.contains(myUid)) {
                        _otherProfileState.value = RemoteState.Error("Este perfil es privado.")
                        _otherFichaState.value = FichaState.Error("Perfil Privado")
                        return@addSnapshotListener
                    }

                    _otherUserData.value = user
                    _otherProfileState.value = RemoteState.Success
                }

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
            } catch (e: Exception) {
                _otherFichaState.value = FichaState.Error("Error")
            }
        }
    }

    fun refreshUserData() {
        _userData.value = userSession.getUser()
    }

    fun logAccountHistory(action: String, title: String, description: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val entryId = java.util.UUID.randomUUID().toString()
                val entry = com.example.cuidaleapp.models.AccountHistoryEntry(
                    id = entryId,
                    action = action,
                    title = title,
                    description = description,
                    timestamp = System.currentTimeMillis()
                )

                val updatedLocalList = (listOf(entry) + _accountHistory.value).distinctBy { it.id }.sortedByDescending { it.timestamp }
                _accountHistory.value = updatedLocalList
                val currentUser = _userData.value
                val updatedUser = currentUser.copy(accountHistory = updatedLocalList)
                _userData.value = updatedUser
                userSession.saveUser(updatedUser)

                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                val userDocRef = if (!query.isEmpty) {
                    query.documents[0].reference
                } else {
                    val rut = userSession.getUser().rut
                    if (rut.isNotBlank()) {
                        val hashedRut = HashUtils.hashRut(rut)
                        db.collection("usuarios").document(hashedRut)
                    } else null
                }
                
                if (userDocRef != null) {
                    userDocRef.set(mapOf("accountHistory" to updatedLocalList), SetOptions.merge()).await()
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error logging history: ${e.message}")
            }
        }
    }

    fun deleteHistoryEntry(entryId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val updatedLocalList = _accountHistory.value.filter { it.id != entryId }
                _accountHistory.value = updatedLocalList
                val currentUser = _userData.value
                val updatedUser = currentUser.copy(accountHistory = updatedLocalList)
                _userData.value = updatedUser
                userSession.saveUser(updatedUser)

                val db = FirebaseFirestore.getInstance()
                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                val userDocRef = if (!query.isEmpty) query.documents[0].reference else {
                    val rut = userSession.getUser().rut
                    if (rut.isNotBlank()) {
                        val hashedRut = HashUtils.hashRut(rut)
                        db.collection("usuarios").document(hashedRut)
                    } else null
                }
                
                if (userDocRef != null) {
                    userDocRef.update("accountHistory", updatedLocalList).await()
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error deleting history entry: ${e.message}")
            }
        }
    }

    fun clearAllHistory() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                _accountHistory.value = emptyList()
                val currentUser = _userData.value
                val updatedUser = currentUser.copy(accountHistory = emptyList())
                _userData.value = updatedUser
                userSession.saveUser(updatedUser)

                val db = FirebaseFirestore.getInstance()
                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                val userDocRef = if (!query.isEmpty) query.documents[0].reference else {
                    val rut = userSession.getUser().rut
                    if (rut.isNotBlank()) {
                        val hashedRut = HashUtils.hashRut(rut)
                        db.collection("usuarios").document(hashedRut)
                    } else null
                }
                
                if (userDocRef != null) {
                    userDocRef.update("accountHistory", emptyList<Any>()).await()
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error clearing history: ${e.message}")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        auth.signOut()
        userSession.clear()
        com.example.cuidaleapp.utils.AppCacheManager.clearAllExceptTheme(getApplication())
        onComplete()
    }

    override fun onCleared() {
        super.onCleared()
        myProfileListener?.remove()
        otherProfileListener?.remove()
        historyListener?.remove()
        statusListener?.let { listener ->
            _otherUserData.value?.uid?.let { uid ->
                rtdb.child("status").child(uid).removeEventListener(listener)
            }
        }
    }
}
