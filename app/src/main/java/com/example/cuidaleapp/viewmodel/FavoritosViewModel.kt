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

    private val _noDeseados = MutableStateFlow<List<String>>(emptyList())
    val noDeseados: StateFlow<List<String>> = _noDeseados

    private val _managedUsers = MutableStateFlow<List<UserData>>(emptyList())
    val managedUsers: StateFlow<List<UserData>> = _managedUsers

    init {
        loadSocialData()
    }

    private fun loadSocialData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("usuarios").document(uid).get().await()
                val favs = (doc.get("favoritos") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val hidden = (doc.get("no_deseados") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                
                _favoritos.value = favs
                _noDeseados.value = hidden

                // Load actual user data for these IDs
                val allIds = (favs + hidden).distinct()
                if (allIds.isNotEmpty()) {
                    val users = db.collection("usuarios")
                        .whereIn("uid", allIds)
                        .get().await()
                        .toObjects(UserData::class.java)
                    _managedUsers.value = users
                } else {
                    _managedUsers.value = emptyList()
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    fun toggleFavorito(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val isFav = _favoritos.value.contains(targetUid)

        viewModelScope.launch {
            _uiState.value = FavoritosUiState.Loading
            try {
                val action = if (isFav) FieldValue.arrayRemove(targetUid) else FieldValue.arrayUnion(targetUid)
                db.collection("usuarios").document(myUid).update("favoritos", action).await()
                loadSocialData()
                _uiState.value = FavoritosUiState.Success(if (isFav) "Eliminado de favoritos" else "Guardado en favoritos")
            } catch (e: Exception) {
                _uiState.value = FavoritosUiState.Error("Error al actualizar favoritos")
            }
        }
    }

    fun toggleNoDeseado(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val isHidden = _noDeseados.value.contains(targetUid)

        viewModelScope.launch {
            _uiState.value = FavoritosUiState.Loading
            try {
                val action = if (isHidden) FieldValue.arrayRemove(targetUid) else FieldValue.arrayUnion(targetUid)
                db.collection("usuarios").document(myUid).update("no_deseados", action).await()
                loadSocialData()
                _uiState.value = FavoritosUiState.Success(if (isHidden) "Usuario restaurado" else "Usuario marcado como no deseado")
            } catch (e: Exception) {
                _uiState.value = FavoritosUiState.Error("Error al actualizar preferencias")
            }
        }
    }

    fun clearState() {
        _uiState.value = FavoritosUiState.Idle
    }
}
