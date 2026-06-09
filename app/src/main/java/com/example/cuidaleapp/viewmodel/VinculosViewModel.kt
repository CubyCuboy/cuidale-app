package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.cuidaleapp.models.UserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Vinculo(
    val id: String = "",
    val uid1: String = "",
    val uid2: String = "",
    val uids: List<String> = emptyList(),
    val estado: String = "pendiente",
    val solicitante: String = "",
    val fecha: Long = System.currentTimeMillis()
)

sealed class VinculosUiState {
    object Idle : VinculosUiState()
    object Loading : VinculosUiState()
    data class Success(val message: String) : VinculosUiState()
    data class Error(val message: String) : VinculosUiState()
}

class VinculosViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val realtime = FirebaseDatabase.getInstance().reference

    private val _uiState = MutableStateFlow<VinculosUiState>(VinculosUiState.Idle)
    val uiState: StateFlow<VinculosUiState> = _uiState

    val myUid: String get() = auth.currentUser?.uid ?: ""

    private val _misVinculos = MutableStateFlow<List<Vinculo>>(emptyList())
    val misVinculos: StateFlow<List<Vinculo>> = _misVinculos

    private val _vinculosUsers = MutableStateFlow<Map<String, UserData>>(emptyMap())
    val vinculosUsers: StateFlow<Map<String, UserData>> = _vinculosUsers

    private var vinculosListener: ListenerRegistration? = null

    init {
        observeVinculos()
    }

    fun observeVinculos() {
        val uid = auth.currentUser?.uid ?: return
        vinculosListener?.remove()
        
        vinculosListener = db.collection("vinculos")
            .whereArrayContains("uids", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.value = VinculosUiState.Error("Error al observar vínculos")
                    return@addSnapshotListener
                }
                
                snapshot?.let {
                    val list = it.toObjects(Vinculo::class.java)
                    _misVinculos.value = list
                    fetchUsersForVinculos(list, uid)
                }
            }
    }

    private fun fetchUsersForVinculos(vinculos: List<Vinculo>, myUid: String) {
        val uidsToFetch = vinculos.flatMap { listOf(it.uid1, it.uid2) }
            .filter { it != myUid && !_vinculosUsers.value.containsKey(it) }
            .distinct()

        if (uidsToFetch.isEmpty()) return

        viewModelScope.launch {
            try {
                // Firestore limit for 'whereIn' is 30. If more, we'd need chunks.
                val chunks = uidsToFetch.chunked(30)
                val fetchedUsers = mutableMapOf<String, UserData>()
                
                for (chunk in chunks) {
                    val snapshot = db.collection("usuarios")
                        .whereIn("uid", chunk)
                        .get()
                        .await()
                    snapshot.toObjects(UserData::class.java).forEach {
                        fetchedUsers[it.uid] = it
                    }
                }
                
                _vinculosUsers.value = _vinculosUsers.value + fetchedUsers
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    fun loadVinculos() {
        // Redundante con observeVinculos pero mantenida por compatibilidad
        observeVinculos()
    }

    fun enviarSolicitud(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (myUid == targetUid) return

        viewModelScope.launch {
            _uiState.value = VinculosUiState.Loading
            try {
                val id = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
                val doc = db.collection("vinculos").document(id).get().await()

                if (doc.exists()) {
                    _uiState.value = VinculosUiState.Error("Ya existe una solicitud o vínculo")
                    return@launch
                }

                val nuevoVinculo = Vinculo(
                    id = id,
                    uid1 = if (myUid < targetUid) myUid else targetUid,
                    uid2 = if (myUid < targetUid) targetUid else myUid,
                    uids = listOf(myUid, targetUid),
                    solicitante = myUid,
                    estado = "pendiente"
                )

                db.collection("vinculos").document(id).set(nuevoVinculo).await()

                // Notificar vía Realtime Database para aviso instantáneo
                realtime.child("notificaciones").child(targetUid).child(id).setValue(
                    mapOf(
                        "tipo" to "solicitud_vinculo",
                        "emisor" to myUid,
                        "timestamp" to System.currentTimeMillis()
                    )
                )

                _uiState.value = VinculosUiState.Success("Solicitud enviada")
                loadVinculos()
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error al enviar solicitud: ${e.message}")
            }
        }
    }

    // En VinculosViewModel.kt - Ajustar aceptarSolicitud
    fun aceptarSolicitud(vinculoId: String) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = VinculosUiState.Loading
            try {
                db.collection("vinculos").document(vinculoId).update("estado", "aceptado").await()

                // Limpiar notificaciones de solicitud
                realtime.child("notificaciones").child(myUid).child(vinculoId).removeValue()

                // Obtener el ID del otro usuario
                val snapshot = db.collection("vinculos").document(vinculoId).get().await()
                val uids = snapshot.get("uids") as? List<String> ?: emptyList()
                val targetUid = uids.firstOrNull { it != myUid } ?: ""

                // Notificar al otro que ya son contactos
                val aviso = mapOf(
                    "tipo" to "vinculo_aceptado",
                    "emisor" to myUid,
                    "timestamp" to System.currentTimeMillis(),
                    "mensaje" to "¡Ahora pueden chatear!"
                )
                realtime.child("notificaciones").child(targetUid).push().setValue(aviso)

                // Inicializar el chat en RTDB si es necesario
                realtime.child("messages").child(vinculoId).limitToLast(1).get().addOnSuccessListener {
                    if (!it.exists()) {
                        // Si no hay mensajes, enviamos uno de bienvenida invisible o sistema
                        // chatViewModel.sendMessage(vinculoId, "¡Solicitud aceptada! Ya pueden hablar.")
                    }
                }

                _uiState.value = VinculosUiState.Success("Solicitud aceptada")
                observeVinculos() // Refrescar
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun rechazarSolicitud(vinculoId: String) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = VinculosUiState.Loading
            try {
                db.collection("vinculos").document(vinculoId).delete().await()
                // Limpiar notificación
                realtime.child("notificaciones").child(myUid).child(vinculoId).removeValue()

                _uiState.value = VinculosUiState.Success("Solicitud rechazada/eliminada")
                loadVinculos()
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error al rechazar solicitud")
            }
        }
    }

    fun cancelarSolicitud(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val id = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
        viewModelScope.launch {
            _uiState.value = VinculosUiState.Loading
            try {
                db.collection("vinculos").document(id).delete().await()
                _uiState.value = VinculosUiState.Success("Solicitud cancelada")
                loadVinculos()
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error al cancelar la solicitud")
            }
        }
    }

    suspend fun getVinculo(targetUid: String): Vinculo? {
        val myUid = auth.currentUser?.uid ?: return null
        val id = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
        return try {
            val doc = db.collection("vinculos").document(id).get().await()
            doc.toObject(Vinculo::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sonContactos(uid1: String, uid2: String): Boolean {
        val id = if (uid1 < uid2) "${uid1}_$uid2" else "${uid2}_$uid1"
        return try {
            val doc = db.collection("vinculos").document(id).get().await()
            doc.exists() && doc.getString("estado") == "aceptado"
        } catch (e: Exception) {
            false
        }
    }

    fun clearState() {
        _uiState.value = VinculosUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        vinculosListener?.remove()
    }
}

