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

data class SearchResult(
    val id: String = "",
    val nombre: String = "",
    val apellido_pa: String = "",
    val foto_url: String = "",
    val region: String = "",
    val comuna: String = "",
    val comunas: List<String> = emptyList() // For caregivers who might have multiple
)

sealed class SearchState {
    object Loading : SearchState()
    data class Success(val results: List<SearchResult>) : SearchState()
    data class Error(val message: String) : SearchState()
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow<SearchState>(SearchState.Loading)
    val state: StateFlow<SearchState> = _state

    private val _selectedRegion = MutableStateFlow("")
    val selectedRegion: StateFlow<String> = _selectedRegion

    private val _selectedCommunes = MutableStateFlow<List<String>>(emptyList())
    val selectedCommunes: StateFlow<List<String>> = _selectedCommunes

    private var allResults: List<SearchResult> = emptyList()
    private var targetCollection: String = ""

    fun initSearch(userRole: String) {
        targetCollection = if (userRole == "cuidador") "perfil_familiar" else "perfil_cuidador"
        fetchData()
    }

    private fun fetchData() {
        viewModelScope.launch {
            _state.value = SearchState.Loading
            try {
                if (targetCollection.isEmpty()) {
                    _state.value = SearchState.Error("Rol de usuario no definido")
                    return@launch
                }
                
                val query = db.collection(targetCollection).get().await()
                allResults = query.documents.map { doc ->
                    SearchResult(
                        id = doc.id,
                        nombre = doc.getString("nombre_completo")?.split(" ")?.firstOrNull() ?: doc.getString("nombre") ?: "N/A",
                        apellido_pa = doc.getString("apellido_pa") ?: doc.getString("nombre_completo")?.split(" ")?.getOrNull(1) ?: "",
                        foto_url = doc.getString("foto_url") ?: "",
                        region = doc.getString("region") ?: "",
                        comuna = doc.getString("comuna") ?: "",
                        comunas = doc.get("comunas") as? List<String> ?: emptyList()
                    )
                }
                applyFilters()
            } catch (e: Exception) {
                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "Acceso denegado: Verifica las reglas de Firestore para '$targetCollection'"
                } else {
                    e.message ?: "Error al cargar datos"
                }
                _state.value = SearchState.Error(errorMessage)
            }
        }
    }

    fun selectRegion(region: String) {
        _selectedRegion.value = region
        _selectedCommunes.value = emptyList()
        applyFilters()
    }

    fun toggleCommune(commune: String) {
        val current = _selectedCommunes.value.toMutableList()
        if (current.contains(commune)) {
            current.remove(commune)
        } else if (current.size < 3) {
            current.add(commune)
        }
        _selectedCommunes.value = current
        applyFilters()
    }

    private fun applyFilters() {
        val regionFilter = _selectedRegion.value
        val communeFilters = _selectedCommunes.value
        
        if (regionFilter.isEmpty() && communeFilters.isEmpty()) {
            _state.value = SearchState.Success(allResults)
            return
        }

        val filtered = allResults.filter { result ->
            val matchesRegion = regionFilter.isEmpty() || result.region == regionFilter
            
            val matchesCommune = if (communeFilters.isEmpty()) {
                true
            } else {
                if (targetCollection == "perfil_familiar") {
                    communeFilters.contains(result.comuna)
                } else {
                    result.comunas.any { communeFilters.contains(it) }
                }
            }
            
            matchesRegion && matchesCommune
        }
        _state.value = SearchState.Success(filtered)
    }
}
