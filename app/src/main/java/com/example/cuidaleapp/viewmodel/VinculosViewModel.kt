package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Vinculo(
    val id: String = "",
    val uid1: String = "",
    val uid2: String = "",
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

    private val _uiState = MutableStateFlow<VinculosUiState>(VinculosUiState.Idle)
    val uiState: StateFlow<VinculosUiState> = _uiState

    private val _misVinculos = MutableStateFlow<List<Vinculo>>(emptyList())
    val misVinculos: StateFlow<List<Vinculo>> = _misVinculos

    init {
        loadVinculos()
    }

    fun loadVinculos() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {

                val q1 = db.collection("vinculos").whereEqualTo("uid1", uid).get().await()
                val q2 = db.collection("vinculos").whereEqualTo("uid2", uid).get().await()
                
                val list = (q1.toObjects(Vinculo::class.java) + q2.toObjects(Vinculo::class.java))
                    .distinctBy { it.id }
                
                _misVinculos.value = list
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error al cargar vínculos")
            }
        }
    }

    fun enviarSolicitud(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        if (myUid == targetUid) return

        viewModelScope.launch {
            _uiState.value = VinculosUiState.Loading
            try {

                val existing = db.collection("vinculos")
                    .whereIn("uid1", listOf(myUid, targetUid))
                    .get()
                    .await()
                
                val alreadyExists = existing.documents.any { doc ->
                    val v = doc.toObject(Vinculo::class.java)
                    (v?.uid1 == myUid && v?.uid2 == targetUid) || (v?.uid1 == targetUid && v?.uid2 == myUid)
                }

                if (alreadyExists) {
                    _uiState.value = VinculosUiState.Error("Ya existe una solicitud o vínculo")
                    return@launch
                }

                val id = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
                val nuevoVinculo = Vinculo(
                    id = id,
                    uid1 = if (myUid < targetUid) myUid else targetUid,
                    uid2 = if (myUid < targetUid) targetUid else myUid,
                    solicitante = myUid,
                    estado = "pendiente"
                )

                db.collection("vinculos").document(id).set(nuevoVinculo).await()
                _uiState.value = VinculosUiState.Success("Solicitud enviada")
                loadVinculos()
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error al enviar solicitud: ${e.message}")
            }
        }
    }

    fun aceptarSolicitud(vinculoId: String) {
        viewModelScope.launch {
            _uiState.value = VinculosUiState.Loading
            try {
                db.collection("vinculos").document(vinculoId).update("estado", "aceptado").await()
                _uiState.value = VinculosUiState.Success("Solicitud aceptada")
                loadVinculos()
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error al aceptar solicitud")
            }
        }
    }

    fun rechazarSolicitud(vinculoId: String) {
        viewModelScope.launch {
            _uiState.value = VinculosUiState.Loading
            try {
                db.collection("vinculos").document(vinculoId).delete().await()
                _uiState.value = VinculosUiState.Success("Solicitud rechazada/eliminada")
                loadVinculos()
            } catch (e: Exception) {
                _uiState.value = VinculosUiState.Error("Error al rechazar solicitud")
            }
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
}

