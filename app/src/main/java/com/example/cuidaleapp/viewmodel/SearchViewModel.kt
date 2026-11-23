package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SearchResult(
    val id: String = "",
    val nombre: String = "",
    val apellido_pa: String = "",
    val fotoUrl: String = "",
    val representanteFotoUrl: String = "",
    val region: String = "",
    val comuna: String = "",
    val comunas: List<String> = emptyList(),
    val tipo: String = "",
    val edad: String = "",
    val nivel_dependencia: String = "",
    val especializacion: String = "",
    val horarios: List<String> = emptyList(),
    val isNew: Boolean = false,
    val calificacion: Double = 0.0,
    val nroCalificaciones: Int = 0,
    val valorHora: Int = 0
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

    private val _useProfilePreferences = MutableStateFlow(true)
    val useProfilePreferences: StateFlow<Boolean> = _useProfilePreferences.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null
    private var isLastPage = false
    private var currentResults = mutableListOf<SearchResult>()
    private val targetCollection = "fichas"
    private var targetTipo: String = ""
    private var noDeseados: List<String> = emptyList()

    private var userRegion = ""
    private var profileCommunes: List<String> = emptyList()

    private val PAGE_SIZE = 50L

    fun initSearch(userRole: String, residenceRegion: String) {
        val newTargetTipo = if (userRole == "cuidador") "familiar" else "cuidador"
        if (targetTipo == newTargetTipo && userRegion == residenceRegion && currentResults.isNotEmpty()) {
            return
        }

        targetTipo = newTargetTipo
        userRegion = residenceRegion

        viewModelScope.launch {
            loadNoDeseados()
            loadProfilePreferences()
            applyInitialFilters()
            resetAndFetch()
        }
    }

    private suspend fun loadProfilePreferences() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val doc = db.collection("fichas").document(uid).get().await()
            if (doc.exists()) {
                val tipo = doc.getString("tipo")
                profileCommunes = if (tipo == "cuidador") {
                    @Suppress("UNCHECKED_CAST") (doc.get("comunas") as? List<String> ?: emptyList())
                } else {
                    @Suppress("UNCHECKED_CAST") (doc.get("comunas_preferencia") as? List<String> ?: emptyList())
                }
            }
        } catch (e: Exception) {
            profileCommunes = emptyList()
        }
    }

    private fun applyInitialFilters() {
        if (_useProfilePreferences.value) {
            _selectedRegion.value = userRegion
            _selectedCommunes.value = profileCommunes.take(3)
        } else {
            _selectedRegion.value = userRegion
            _selectedCommunes.value = emptyList()
        }
    }

    fun toggleProfilePreferences(enabled: Boolean) {
        _useProfilePreferences.value = enabled
        applyInitialFilters()
        resetAndFetch()
    }

    private suspend fun loadNoDeseados() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
            if (!query.isEmpty) {
                noDeseados = (query.documents[0].get("no_deseados") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            }
        } catch (e: Exception) {
            noDeseados = emptyList()
        }
    }

    fun clearFilters() {
        _selectedRegion.value = ""
        _selectedCommunes.value = emptyList()
        resetAndFetch()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadNoDeseados()
            loadProfilePreferences()
            resetAndFetch()
        }
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
        val currentNoDeseados = noDeseados
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

                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _state.value = SearchState.Error("Usuario no autenticado")
                    _isRefreshing.value = false
                    return@launch
                }

                val collectionPath = if (targetTipo == "cuidador") "usuarios" else "fichas"
                val roleField = if (targetTipo == "cuidador") "rol" else "tipo"
                val orderField = if (targetTipo == "cuidador") "createdAt" else "ultima_actualizacion"

                var query = db.collection(collectionPath)
                    .whereEqualTo(roleField, targetTipo)

                val regionFilter = _selectedRegion.value
                val communeFilters = _selectedCommunes.value

                if (regionFilter.isNotEmpty()) {
                    query = query.whereEqualTo("region", regionFilter)
                }

                if (communeFilters.isNotEmpty()) {
                    query = query.whereIn("comuna", communeFilters)
                }

                query = query.orderBy(orderField, Query.Direction.DESCENDING)

                if (isLoadMore && lastDocument != null) {
                    query = query.startAfter(lastDocument!!)
                }

                val result = query.limit(PAGE_SIZE).get().await()

                val newItems = result.documents.map { doc ->
                    viewModelScope.async {
                        var nombre = ""
                        var apellido = ""
                        var foto = ""
                        var representanteFotoUrl = ""
                        var edad = ""
                        var nivel_dependencia = ""
                        var especializacion = ""
                        var horarios: List<String> = emptyList()
                        var comunas_trabajo: List<String> = emptyList()
                        var valorHora = 0
                        var promedio = 0.0
                        var nroCalif = 0
                        var region = doc.getString("region") ?: ""
                        var comuna = doc.getString("comuna") ?: ""

                        val actualId = doc.getString("uid") ?: doc.id

                        if (targetTipo == "cuidador") {
                            nombre = doc.getString("nombre") ?: ""
                            apellido = doc.getString("apellido_pa") ?: ""
                            foto = doc.getString("fotoUrl") ?: doc.getString("foto") ?: ""
                            try {
                                val fichaDoc = db.collection("fichas").document(actualId).get().await()
                                if (fichaDoc.exists()) {
                                    especializacion = fichaDoc.getString("especializacion") ?: ""
                                    horarios = @Suppress("UNCHECKED_CAST") (fichaDoc.get("horarios") as? List<String> ?: emptyList())
                                    comunas_trabajo = @Suppress("UNCHECKED_CAST") (fichaDoc.get("comunas") as? List<String> ?: emptyList())
                                    valorHora = fichaDoc.getLong("valorHoraReferencial")?.toInt() ?: 0
                                    promedio = (fichaDoc.get("rating_promedio") as? Number)?.toDouble() ?: 0.0
                                    nroCalif = (fichaDoc.get("total_resenas") as? Number)?.toInt() ?: 0
                                }
                            } catch (e: Exception) {
                            }
                        } else {
                            nombre = doc.getString("nombre_completo") ?: ""
                            apellido = ""

                            foto = doc.getString("fotoUrl") ?: (doc.get("fotos") as? List<String>)?.firstOrNull() ?: ""
                            edad = doc.get("edad")?.toString() ?: ""
                            nivel_dependencia = doc.getString("nivel_dependencia") ?: ""

                            // Intenta obtener la foto del representante (si existe)
                            val repUid = doc.getString("representanteUid")
                                ?: doc.getString("representante_uid")
                                ?: actualId
                            if (repUid.isNotBlank()) {
                                try {
                                    val repDocs = db.collection("usuarios")
                                        .whereEqualTo("uid", repUid)
                                        .get()
                                        .await()
                                    val repDoc = repDocs.documents.firstOrNull()
                                    representanteFotoUrl = repDoc?.getString("fotoUrl")
                                        ?: repDoc?.getString("foto")
                                        ?: ""
                                } catch (e: Exception) {
                                    representanteFotoUrl = ""
                                }
                            }
                        }

                        val timestamp = if (targetTipo == "cuidador") {
                            doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        } else {
                            doc.getTimestamp("ultima_actualizacion")?.toDate()?.time ?: 0L
                        }
                        val isNew = (System.currentTimeMillis() - timestamp) < (24 * 60 * 60 * 1000)

                        SearchResult(
                            id = actualId,
                            nombre = nombre.ifEmpty { "Usuario" },
                            apellido_pa = apellido,
                            fotoUrl = foto,
                            representanteFotoUrl = representanteFotoUrl,
                            region = region,
                            comuna = comuna,
                            comunas = if (targetTipo == "cuidador") comunas_trabajo else emptyList(),
                            tipo = targetTipo,
                            edad = edad,
                            nivel_dependencia = nivel_dependencia,
                            especializacion = especializacion,
                            horarios = horarios,
                            isNew = isNew,
                            calificacion = promedio,
                            nroCalificaciones = nroCalif,
                            valorHora = valorHora
                        )
                    }
                }.awaitAll().distinctBy { it.id }.filter { it.id !in currentNoDeseados }

                if (newItems.isEmpty() && !isLastPage && result.documents.isNotEmpty()) {
                    lastDocument = result.documents.last()
                    fetchData(isLoadMore = true)
                    return@launch
                }

                if (result.documents.size < PAGE_SIZE) {
                    isLastPage = true
                }

                if (result.documents.isNotEmpty()) {
                    lastDocument = result.documents.last()
                }

                val existingIds = currentResults.map { it.id }.toSet()
                val uniqueNewItems = newItems.filter { it.id !in existingIds }
                
                currentResults.addAll(uniqueNewItems)

                _state.value = SearchState.Success(
                    results = currentResults.toList(),
                    isLastPage = isLastPage,
                    isLoadingMore = false
                )
                _isRefreshing.value = false

            } catch (e: Exception) {
                _isRefreshing.value = false
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> {
                        "Error de permisos: No puedes acceder a las fichas. Verifica tu conexión o contacta soporte."
                    }
                    e.message?.contains("FAILED_PRECONDITION") == true -> {
                        "Error de índice: Esta búsqueda requiere un índice compuesto en Firestore."
                    }
                    e.message?.contains("UNAUTHENTICATED") == true -> {
                        "Sesión expirada. Por favor, inicia sesión nuevamente."
                    }
                    else -> e.message ?: "Error al cargar datos"
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
