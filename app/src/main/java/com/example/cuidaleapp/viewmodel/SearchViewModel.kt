package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SearchResult(
    val id: String = "",
    val nombre: String = "",
    val apellido_pa: String = "",
    val foto_url: String = "",
    val region: String = "",
    val comuna: String = "",
    val comunas: List<String> = emptyList()
)

sealed class SearchState {
    object Loading : SearchState()
    data class Success(
        val results: List<SearchResult>,
        val isLastPage: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : SearchState()
    data class Error(val message: String) : SearchState()
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow<SearchState>(SearchState.Loading)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _selectedRegion = MutableStateFlow("")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _selectedCommunes = MutableStateFlow<List<String>>(emptyList())
    val selectedCommunes: StateFlow<List<String>> = _selectedCommunes.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null
    private var isLastPage = false
    private var currentResults = mutableListOf<SearchResult>()
    private val targetCollection = "fichas"
    private var targetTipo: String = ""
    
    private val PAGE_SIZE = 10L

    fun initSearch(userRole: String) {
        val newTargetTipo = if (userRole == "cuidador") "familiar" else "cuidador"
        if (targetTipo == newTargetTipo && currentResults.isNotEmpty()) {
            return 
        }
        
        targetTipo = newTargetTipo
        resetAndFetch()
    }

    fun clearFilters() {
        _selectedRegion.value = ""
        _selectedCommunes.value = emptyList()
        resetAndFetch()
    }

    fun refresh() {
        resetAndFetch()
    }

    private fun resetAndFetch() {
        lastDocument = null
        isLastPage = false
        currentResults.clear()
        fetchData(isLoadMore = false)
    }

    fun loadMore() {
        if (isLastPage || _state.value !is SearchState.Success || (_state.value as SearchState.Success).isLoadingMore) {
            return
        }
        fetchData(isLoadMore = true)
    }

    private fun fetchData(isLoadMore: Boolean) {
        viewModelScope.launch {
            if (!isLoadMore) {
                _state.value = SearchState.Loading
                _isRefreshing.value = true
            } else {
                val currentState = _state.value as? SearchState.Success
                if (currentState != null) {
                    _state.value = currentState.copy(isLoadingMore = true)
                }
            }

            try {
                if (targetTipo.isEmpty()) {
                    _state.value = SearchState.Error("Rol de usuario no definido")
                    _isRefreshing.value = false
                    return@launch
                }

                var query = db.collection(targetCollection)
                    .whereEqualTo("tipo", targetTipo)
                    .orderBy("ultima_actualizacion", Query.Direction.DESCENDING)

                val regionFilter = _selectedRegion.value
                val communeFilters = _selectedCommunes.value

                if (regionFilter.isNotEmpty()) {
                    query = query.whereEqualTo("region", regionFilter)
                }

                if (communeFilters.isNotEmpty()) {
                    if (targetTipo == "familiar") {
                        query = query.whereIn("comuna", communeFilters)
                    } else {
                        query = query.whereArrayContainsAny("comunas", communeFilters)
                    }
                }

                if (isLoadMore && lastDocument != null) {
                    query = query.startAfter(lastDocument!!)
                }

                val result = query.limit(PAGE_SIZE).get().await()
                
                val newItems = result.documents.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val fotos = doc.get("fotos") as? List<String> ?: emptyList()
                    
                    SearchResult(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: doc.getString("nombre_completo")?.split(" ")?.firstOrNull() ?: "N/A",
                        apellido_pa = doc.getString("apellido_pa") ?: doc.getString("nombre_completo")?.split(" ")?.getOrNull(1) ?: "",
                        foto_url = fotos.firstOrNull() ?: "",
                        region = doc.getString("region") ?: "",
                        comuna = doc.getString("comuna") ?: "",
                        comunas = @Suppress("UNCHECKED_CAST") (doc.get("comunas") as? List<String> ?: emptyList())
                    )
                }

                if (newItems.size < PAGE_SIZE) {
                    isLastPage = true
                }

                if (result.documents.isNotEmpty()) {
                    lastDocument = result.documents.last()
                }

                currentResults.addAll(newItems)
                
                _state.value = SearchState.Success(
                    results = currentResults.toList(),
                    isLastPage = isLastPage,
                    isLoadingMore = false
                )
                _isRefreshing.value = false

            } catch (e: Exception) {
                _isRefreshing.value = false
                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "Acceso denegado: Verifica las reglas de Firestore para '$targetCollection'"
                } else if (e.message?.contains("FAILED_PRECONDITION") == true) {
                    "Error de índice: Esta búsqueda requiere un índice compuesto en Firestore."
                } else {
                    e.message ?: "Error al cargar datos"
                }
                _state.value = SearchState.Error(errorMessage)
            }
        }
    }

    fun selectRegion(region: String) {
        if (_selectedRegion.value == region) return
        _selectedRegion.value = region
        _selectedCommunes.value = emptyList()
        resetAndFetch()
    }

    fun toggleCommune(commune: String) {
        val current = _selectedCommunes.value.toMutableList()
        if (current.contains(commune)) {
            current.remove(commune)
        } else if (current.size < 3) {
            current.add(commune)
        }
        _selectedCommunes.value = current
        resetAndFetch()
    }
}
