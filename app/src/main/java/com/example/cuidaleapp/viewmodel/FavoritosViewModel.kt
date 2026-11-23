package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class FavoritosUiState {
    object Idle : FavoritosUiState()
    object Loading : FavoritosUiState()
    data class Success(val message: String) : FavoritosUiState()
    data class Error(val message: String) : FavoritosUiState()
}

class FavoritosViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<FavoritosUiState>(FavoritosUiState.Idle)
    val uiState: StateFlow<FavoritosUiState> = _uiState

    private val _favoritos = MutableStateFlow<List<String>>(emptyList())
    val favoritos: StateFlow<List<String>> = _favoritos

    private val _guardados = MutableStateFlow<List<String>>(emptyList())
    val guardados: StateFlow<List<String>> = _guardados

    private val _noDeseados = MutableStateFlow<List<String>>(emptyList())
    val noDeseados: StateFlow<List<String>> = _noDeseados

    private val _bloqueados = MutableStateFlow<List<String>>(emptyList())
    val bloqueados: StateFlow<List<String>> = _bloqueados

    private val _managedUsers = MutableStateFlow<List<UserData>>(emptyList())
    val managedUsers: StateFlow<List<UserData>> = _managedUsers

    init {
        loadSocialData()
    }

    private suspend fun getMyUserDocRef(uid: String): com.google.firebase.firestore.DocumentReference? {
        val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
        return if (!query.isEmpty) query.documents[0].reference else null
    }

    fun loadSocialData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userRef = getMyUserDocRef(uid) ?: return@launch
                val doc = userRef.get().await()
                val favs = (doc.get("favoritos") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val saved = (doc.get("guardados") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val hidden = (doc.get("no_deseados") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val blocked = (doc.get("bloqueados") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                
                _favoritos.value = favs
                _guardados.value = saved
                _noDeseados.value = hidden
                _bloqueados.value = blocked

                val allIds = (favs + saved + hidden + blocked).distinct()
                if (allIds.isNotEmpty()) {
                    val users = db.collection("usuarios")
                        .whereIn("uid", allIds)
                        .get().await()
                        .toObjects(UserData::class.java)
                    _managedUsers.value = users.distinctBy { it.uid }
                } else {
                    _managedUsers.value = emptyList()
                }
            } catch (e: Exception) {
            }
        }
    }

    fun toggleFavorito(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val isFav = _favoritos.value.contains(targetUid)

        viewModelScope.launch {
            _uiState.value = FavoritosUiState.Loading
            try {
                val userRef = getMyUserDocRef(myUid) ?: throw Exception("Usuario no encontrado")
                val action = if (isFav) FieldValue.arrayRemove(targetUid) else FieldValue.arrayUnion(targetUid)
                userRef.update("favoritos", action).await()
                loadSocialData()
                _uiState.value = FavoritosUiState.Success(if (isFav) "Eliminado de favoritos" else "Añadido a favoritos")
            } catch (e: Exception) {
                _uiState.value = FavoritosUiState.Error("Error al actualizar favoritos")
            }
        }
    }

    fun toggleGuardado(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val isSaved = _guardados.value.contains(targetUid)

        viewModelScope.launch {
            _uiState.value = FavoritosUiState.Loading
            try {
                val userRef = getMyUserDocRef(myUid) ?: throw Exception("Usuario no encontrado")
                val action = if (isSaved) FieldValue.arrayRemove(targetUid) else FieldValue.arrayUnion(targetUid)
                userRef.update("guardados", action).await()
                loadSocialData()
                _uiState.value = FavoritosUiState.Success(if (isSaved) "Eliminado de guardados" else "Guardado en contactos")
            } catch (e: Exception) {
                _uiState.value = FavoritosUiState.Error("Error al actualizar guardados")
            }
        }
    }

    fun toggleNoDeseado(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val isHidden = _noDeseados.value.contains(targetUid)

        if (!isHidden) {
            com.example.cuidaleapp.utils.OcultadosRecientes.marcar(targetUid)
        }

        viewModelScope.launch {
            _uiState.value = FavoritosUiState.Loading
            try {
                val userRef = getMyUserDocRef(myUid) ?: throw Exception("Usuario no encontrado")
                val action = if (isHidden) FieldValue.arrayRemove(targetUid) else FieldValue.arrayUnion(targetUid)
                userRef.update("no_deseados", action).await()
                loadSocialData()
                _uiState.value = FavoritosUiState.Success(if (isHidden) "Usuario restaurado en búsquedas" else "Usuario ocultado de búsquedas")
            } catch (e: Exception) {
                _uiState.value = FavoritosUiState.Error("Error al actualizar preferencias")
            }
        }
    }

    fun toggleBloqueo(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val isBlocked = _bloqueados.value.contains(targetUid)

        viewModelScope.launch {
            _uiState.value = FavoritosUiState.Loading
            try {
                val userRef = getMyUserDocRef(myUid) ?: throw Exception("Usuario no encontrado")
                if (!isBlocked) {
                    userRef.update(
                        mapOf(
                            "bloqueados" to FieldValue.arrayUnion(targetUid),
                            "favoritos" to FieldValue.arrayRemove(targetUid),
                            "guardados" to FieldValue.arrayRemove(targetUid)
                        )
                    ).await()
                    
                    val sols1 = db.collection("solicitudes")
                        .whereEqualTo("representanteUid", myUid)
                        .whereEqualTo("cuidadorUid", targetUid)
                        .get().await()
                    
                    val sols2 = db.collection("solicitudes")
                        .whereEqualTo("representanteUid", targetUid)
                        .whereEqualTo("cuidadorUid", myUid)
                        .get().await()
                    
                    (sols1.documents + sols2.documents).forEach { doc ->
                        doc.reference.delete()
                    }
                    
                    val vinculoId = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
                    db.collection("vinculos").document(vinculoId).delete()

                } else {
                    userRef.update("bloqueados", FieldValue.arrayRemove(targetUid)).await()
                }
                
                loadSocialData()
                _uiState.value = FavoritosUiState.Success(if (isBlocked) "Usuario desbloqueado" else "Usuario bloqueado y servicios cancelados")
            } catch (e: Exception) {
                _uiState.value = FavoritosUiState.Error("Error al actualizar bloqueo")
            }
        }
    }

    fun clearState() {
        _uiState.value = FavoritosUiState.Idle
    }
}
