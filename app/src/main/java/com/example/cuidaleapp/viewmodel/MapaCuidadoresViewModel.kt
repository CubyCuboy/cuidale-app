package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.Cuidador
import com.example.cuidaleapp.models.toFeatureCollection
import com.example.cuidaleapp.utils.LocationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

sealed interface MapaUiState {
    object Loading : MapaUiState
    data class Success(
        val cuidadores: List<Cuidador>,
        val geoJsonData: FeatureCollection?,
        val comunaGeoJsonData: FeatureCollection? = null,
        val centroBusqueda: Point?,
        val mensajeAviso: String?,
        val cameraEventId: Long = System.currentTimeMillis()
    ) : MapaUiState
    data class Error(val mensaje: String) : MapaUiState
}

class MapaCuidadoresViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<MapaUiState>(MapaUiState.Loading)
    val uiState: StateFlow<MapaUiState> = _uiState.asStateFlow()

    private val _isCargandoGps = MutableStateFlow(false)
    val isCargandoGps: StateFlow<Boolean> = _isCargandoGps.asStateFlow()

    // Filtros embebidos del mapa
    private val _selectedRegion = MutableStateFlow("")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _selectedCommunes = MutableStateFlow<List<String>>(emptyList())
    val selectedCommunes: StateFlow<List<String>> = _selectedCommunes.asStateFlow()

    // Preferencias de la Ficha Técnica del usuario
    private val _useProfilePreferences = MutableStateFlow(false)
    val useProfilePreferences: StateFlow<Boolean> = _useProfilePreferences.asStateFlow()

    private val _profileCommunes = MutableStateFlow<List<String>>(emptyList())
    val profileCommunes: StateFlow<List<String>> = _profileCommunes.asStateFlow()

    private val _sinComunasEnFicha = MutableStateFlow(false)
    val sinComunasEnFicha: StateFlow<Boolean> = _sinComunasEnFicha.asStateFlow()

    // Lista completa de todos los cuidadores cargados desde Firestore con su desfase de privacidad
    private var todosLosCuidadores = mutableListOf<Cuidador>()

    // Punto central por defecto (Región Metropolitana, Santiago de Chile: Lat -33.4489, Lng -70.6693)
    val puntoGeneralRM = Point.fromLngLat(-70.6693, -33.4489)

    init {
        cargarPreferenciasFicha()
        cargarCuidadoresFirestore()
    }

    /**
     * Carga las comunas de preferencia guardadas en la Ficha Técnica del usuario actual
     */
    fun cargarPreferenciasFicha() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("fichas").document(uid).get().await()
                if (doc.exists()) {
                    val tipo = doc.getString("tipo")
                    val comunas = if (tipo == "cuidador") {
                        @Suppress("UNCHECKED_CAST") (doc.get("comunas") as? List<String> ?: emptyList())
                    } else {
                        @Suppress("UNCHECKED_CAST") (doc.get("comunas_preferencia") as? List<String> ?: emptyList())
                    }
                    _profileCommunes.value = comunas
                } else {
                    _profileCommunes.value = emptyList()
                }
            } catch (e: Exception) {
                _profileCommunes.value = emptyList()
            }
        }
    }

    /**
     * Activa o desactiva la aplicación de preferencias de la Ficha del usuario
     */
    fun toggleProfilePreferences(enabled: Boolean) {
        _useProfilePreferences.value = enabled
        if (enabled) {
            if (_profileCommunes.value.isNotEmpty()) {
                _selectedCommunes.value = _profileCommunes.value.take(3)
                _sinComunasEnFicha.value = false
            } else {
                _sinComunasEnFicha.value = true
                _selectedCommunes.value = emptyList()
            }
        } else {
            _sinComunasEnFicha.value = false
            _selectedCommunes.value = emptyList()
        }
        aplicarFiltrosYActualizarUi(moverCamara = false)
    }


    /**
     * Consulta los cuidadores disponibles desde Firestore (colección "cuidadores" y fallback "usuarios"/"fichas").
     */
    fun cargarCuidadoresFirestore() {
        viewModelScope.launch {
            _uiState.value = MapaUiState.Loading
            try {
                val listaCuidadores = mutableListOf<Cuidador>()

                // 1. Intentar cargar desde colección "cuidadores"
                try {
                    val queryCuidadores = db.collection("cuidadores").get().await()
                    for (doc in queryCuidadores.documents) {
                        val uid = doc.getString("uid") ?: doc.id
                        val nombre = doc.getString("nombre") ?: doc.getString("nombre_completo") ?: "Cuidador"
                        val especialidad = doc.getString("especialidad") ?: doc.getString("especializacion") ?: "Cuidado General"
                        val comuna = doc.getString("comuna") ?: "Santiago"
                        val region = doc.getString("region") ?: "Metropolitana de Santiago"
                        val disponible = doc.getBoolean("disponible") ?: true
                        val fotoUrl = doc.getString("fotoUrl") ?: ""

                        val geoPoint = doc.getGeoPoint("ubicacionReferencial")
                        val lat = doc.getDouble("latitud") ?: geoPoint?.latitude
                        val lng = doc.getDouble("longitud") ?: geoPoint?.longitude

                        val (latFinal, lngFinal) = resolverCoordenadas(lat, lng, comuna)

                        listaCuidadores.add(
                            Cuidador(
                                id = uid,
                                nombre = nombre,
                                especialidad = especialidad,
                                comuna = comuna,
                                region = region,
                                disponible = disponible,
                                latitud = latFinal,
                                longitud = lngFinal,
                                fotoUrl = fotoUrl
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Complementar desde "usuarios" con rol == "cuidador"
                if (listaCuidadores.size < 5) {
                    try {
                        val queryUsuarios = db.collection("usuarios")
                            .whereEqualTo("rol", "cuidador")
                            .limit(30)
                            .get().await()

                        val idsExistentes = listaCuidadores.map { it.id }.toSet()

                        for (doc in queryUsuarios.documents) {
                            val uid = doc.getString("uid") ?: doc.id
                            if (uid in idsExistentes) continue

                            val nombre = doc.getString("nombre") ?: "Cuidador"
                            val apellido = doc.getString("apellido_pa") ?: ""
                            val nombreCompleto = "$nombre $apellido".trim()
                            val comuna = doc.getString("comuna") ?: "Santiago"
                            val region = doc.getString("region") ?: "Metropolitana de Santiago"
                            val fotoUrl = doc.getString("fotoUrl") ?: ""

                            var especialidad = "Cuidador Certificado"
                            try {
                                val fichaDoc = db.collection("fichas").document(uid).get().await()
                                if (fichaDoc.exists()) {
                                    especialidad = fichaDoc.getString("especializacion")
                                        ?.takeIf { it.isNotBlank() } ?: "Cuidador Certificado"
                                }
                            } catch (_: Exception) {}

                            val geoPoint = doc.getGeoPoint("ubicacionReferencial")
                            val lat = doc.getDouble("latitud") ?: geoPoint?.latitude
                            val lng = doc.getDouble("longitud") ?: geoPoint?.longitude

                            val (latFinal, lngFinal) = resolverCoordenadas(lat, lng, comuna)

                            listaCuidadores.add(
                                Cuidador(
                                    id = uid,
                                    nombre = nombreCompleto,
                                    especialidad = especialidad,
                                    comuna = comuna,
                                    region = region,
                                    disponible = true,
                                    latitud = latFinal,
                                    longitud = lngFinal,
                                    fotoUrl = fotoUrl
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                todosLosCuidadores = listaCuidadores
                aplicarFiltrosYActualizarUi(
                    nuevoCentro = puntoGeneralRM,
                    aviso = "Vista general: Cuidadores en la Región Metropolitana",
                    moverCamara = true
                )
            } catch (e: Exception) {
                _uiState.value = MapaUiState.Error(
                    mensaje = e.message ?: "Error al cargar los datos de los cuidadores"
                )
            }
        }
    }

    /**
     * Búsqueda por ubicación GPS rápida One-Shot (UH1)
     */
    fun buscarPorGps(context: Context) {
        viewModelScope.launch {
            _isCargandoGps.value = true
            try {
                val ubicacionGps = LocationUtils.obtenerUbicacionActualOneShot(context)
                if (ubicacionGps != null) {
                    val latGps = ubicacionGps.latitude()
                    val lngGps = ubicacionGps.longitude()

                    val baseList = obtenerListaFiltradaPorFiltros()
                    val filtrados = baseList.filter { c ->
                        val dist = LocationUtils.calcularDistanciaMetros(latGps, lngGps, c.latitud, c.longitud)
                        dist <= 3000.0
                    }

                    val resultado = if (filtrados.isNotEmpty()) filtrados else baseList
                    val mensaje = if (filtrados.isNotEmpty()) {
                        "Se encontraron ${filtrados.size} cuidadores a menos de 3 km de tu GPS."
                    } else {
                        "Ubicación centrada en tu GPS. Mostrando cuidadores disponibles."
                    }

                    _uiState.value = MapaUiState.Success(
                        cuidadores = resultado,
                        geoJsonData = resultado.toFeatureCollection(),
                        centroBusqueda = ubicacionGps,
                        mensajeAviso = mensaje,
                        cameraEventId = System.currentTimeMillis()
                    )
                } else {
                    val baseList = obtenerListaFiltradaPorFiltros()
                    _uiState.value = MapaUiState.Success(
                        cuidadores = baseList,
                        geoJsonData = baseList.toFeatureCollection(),
                        centroBusqueda = puntoGeneralRM,
                        mensajeAviso = "No se pudo obtener la ubicación GPS. Verifica tus permisos y el GPS.",
                        cameraEventId = System.currentTimeMillis()
                    )
                }
            } finally {
                _isCargandoGps.value = false
            }
        }
    }

    /**
     * Búsqueda directa por coordenadas de un punto de sugerencia
     */
    fun buscarPorCoordenadasDirectas(nombreLugar: String, punto: Point) {
        val latDir = punto.latitude()
        val lngDir = punto.longitude()

        val baseList = obtenerListaFiltradaPorFiltros()
        val filtrados = baseList.filter { c ->
            val dist = LocationUtils.calcularDistanciaMetros(latDir, lngDir, c.latitud, c.longitud)
            dist <= 3000.0
        }

        val listaResultado = if (filtrados.isNotEmpty()) filtrados else baseList
        val mensaje = if (filtrados.isNotEmpty()) {
            "Se encontraron ${filtrados.size} cuidadores cerca de '$nombreLugar'."
        } else {
            "Ubicación centrada en '$nombreLugar'. Mostrando cuidadores cercanos."
        }

        _uiState.value = MapaUiState.Success(
            cuidadores = listaResultado,
            geoJsonData = listaResultado.toFeatureCollection(),
            centroBusqueda = punto,
            mensajeAviso = mensaje,
            cameraEventId = System.currentTimeMillis()
        )
    }

    /**
     * Búsqueda manual por dirección/comuna mediante Geocoder nativo y fallback comunal local
     */
    fun buscarPorDireccionManual(context: Context, direccionTexto: String) {
        val textoLimpio = direccionTexto.trim()
        if (textoLimpio.isBlank()) {
            limpiarTextoBusquedaSinMoverCamara()
            return
        }

        viewModelScope.launch {
            val queryFormateada = if (!textoLimpio.lowercase().contains("chile")) "$textoLimpio, Chile" else textoLimpio
            var ubicacionDir = LocationUtils.geocodificarDireccion(context, queryFormateada)

            if (ubicacionDir == null) {
                val coords = resolverCoordenadas(null, null, textoLimpio)
                if (coords != Pair(-33.4489, -70.6693) || textoLimpio.lowercase().contains("santiago")) {
                    ubicacionDir = Point.fromLngLat(coords.second, coords.first)
                }
            }

            if (ubicacionDir != null) {
                buscarPorCoordenadasDirectas(textoLimpio, ubicacionDir)
            } else {
                val baseList = obtenerListaFiltradaPorFiltros()
                _uiState.value = MapaUiState.Success(
                    cuidadores = baseList,
                    geoJsonData = baseList.toFeatureCollection(),
                    centroBusqueda = puntoGeneralRM,
                    mensajeAviso = "No se encontró la ubicación para '$textoLimpio'. Mostrando vista general.",
                    cameraEventId = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Selecciona un cuidador específico al tocar su punto en el mapa y despliega la lista con ese único cuidador
     */
    fun seleccionarCuidadorEspecifico(cuidador: Cuidador) {
        val puntoCuidador = Point.fromLngLat(cuidador.longitud, cuidador.latitud)
        _uiState.value = MapaUiState.Success(
            cuidadores = listOf(cuidador),
            geoJsonData = listOf(cuidador).toFeatureCollection(),
            centroBusqueda = puntoCuidador,
            mensajeAviso = "Cuidador seleccionado: ${cuidador.nombre}",
            cameraEventId = System.currentTimeMillis()
        )
    }

    /**
     * Selección por toque de mapa: busca el cuidador más cercano al toque (si hay alguno a menos de 800m)
     */
    fun filtrarPorZonaSeleccionada(puntoTocado: Point, radioMetros: Double = 1500.0) {
        val latTocado = puntoTocado.latitude()
        val lngTocado = puntoTocado.longitude()

        val baseList = obtenerListaFiltradaPorFiltros()

        val cuidadorTocado = baseList.minByOrNull { c ->
            LocationUtils.calcularDistanciaMetros(latTocado, lngTocado, c.latitud, c.longitud)
        }

        if (cuidadorTocado != null && LocationUtils.calcularDistanciaMetros(latTocado, lngTocado, cuidadorTocado.latitud, cuidadorTocado.longitud) <= 800.0) {
            seleccionarCuidadorEspecifico(cuidadorTocado)
            return
        }

        val filtrados = baseList.filter { c ->
            val dist = LocationUtils.calcularDistanciaMetros(latTocado, lngTocado, c.latitud, c.longitud)
            dist <= radioMetros
        }

        val listaResultado = if (filtrados.isNotEmpty()) filtrados else baseList
        val radioKm = String.format("%.1f", radioMetros / 1000.0)

        val mensaje = if (filtrados.isNotEmpty()) {
            "${filtrados.size} cuidadores en el área seleccionada (radio de $radioKm km)."
        } else {
            "Mostrando cuidadores en la zona."
        }

        _uiState.value = MapaUiState.Success(
            cuidadores = listaResultado,
            geoJsonData = listaResultado.toFeatureCollection(),
            centroBusqueda = puntoTocado,
            mensajeAviso = mensaje,
            cameraEventId = System.currentTimeMillis()
        )
    }

    /**
     * Métodos de Filtro de Región y Comuna (MANTIENEN la cámara sin mover la posición)
     */
    fun selectRegion(region: String) {
        _useProfilePreferences.value = false
        _sinComunasEnFicha.value = false
        _selectedRegion.value = region
        _selectedCommunes.value = emptyList()
        aplicarFiltrosYActualizarUi(moverCamara = false)
    }

    fun toggleCommune(comuna: String) {
        _useProfilePreferences.value = false
        _sinComunasEnFicha.value = false
        val actuales = _selectedCommunes.value.toMutableList()
        if (actuales.contains(comuna)) {
            actuales.remove(comuna)
        } else if (actuales.size < 3) {
            actuales.add(comuna)
        }
        _selectedCommunes.value = actuales
        aplicarFiltrosYActualizarUi(moverCamara = false)
    }

    fun limpiarFiltros() {
        _useProfilePreferences.value = false
        _sinComunasEnFicha.value = false
        _selectedRegion.value = ""
        _selectedCommunes.value = emptyList()
        aplicarFiltrosYActualizarUi(moverCamara = false)
    }

    fun limpiarTextoBusquedaSinMoverCamara() {
        aplicarFiltrosYActualizarUi(moverCamara = false)
    }

    fun limpiarFiltroUbicacion() {
        limpiarFiltros()
        aplicarFiltrosYActualizarUi(nuevoCentro = puntoGeneralRM, aviso = "Filtros de ubicación restablecidos.", moverCamara = true)
    }

    private fun obtenerListaFiltradaPorFiltros(): List<Cuidador> {
        val reg = _selectedRegion.value
        val coms = _selectedCommunes.value

        return todosLosCuidadores.filter { c ->
            val cumpleRegion = reg.isEmpty() || c.region.contains(reg, ignoreCase = true) || reg.contains(c.region, ignoreCase = true)
            val cumpleComuna = coms.isEmpty() || coms.any { com -> c.comuna.equals(com, ignoreCase = true) }
            cumpleRegion && cumpleComuna
        }
    }

    private fun aplicarFiltrosYActualizarUi(
        nuevoCentro: Point? = null,
        aviso: String? = null,
        moverCamara: Boolean = false
    ) {
        val listaFiltrada = obtenerListaFiltradaPorFiltros()
        val currentState = _uiState.value as? MapaUiState.Success

        val centroActual = nuevoCentro ?: currentState?.centroBusqueda ?: puntoGeneralRM
        val cameraEventId = if (moverCamara) System.currentTimeMillis() else (currentState?.cameraEventId ?: System.currentTimeMillis())

        _uiState.value = MapaUiState.Success(
            cuidadores = listaFiltrada,
            geoJsonData = listaFiltrada.toFeatureCollection(),
            centroBusqueda = centroActual,
            mensajeAviso = aviso ?: "Cuidadores filtrados (${listaFiltrada.size})",
            cameraEventId = cameraEventId
        )
    }

    /**
     * Asigna coordenadas geográficas con desfase aleatorio de ~200m para resguardar privacidad de la dirección exacta
     */
    private fun resolverCoordenadas(lat: Double?, lng: Double?, comuna: String): Pair<Double, Double> {
        val coordsBase = if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            Pair(lat, lng)
        } else {
            mapCoordenadasComunas[comuna.trim().lowercase()] ?: Pair(-33.4489, -70.6693)
        }

        // Desfase aleatorio de privacidad de ~200m (±0.0018 grados de latitud/longitud)
        val jitterLat = (Random.nextDouble() - 0.5) * 0.0036
        val jitterLng = (Random.nextDouble() - 0.5) * 0.0036

        return Pair(coordsBase.first + jitterLat, coordsBase.second + jitterLng)
    }

    private val mapCoordenadasComunas = mapOf(
        "santiago" to Pair(-33.4489, -70.6693),
        "providencia" to Pair(-33.4326, -70.6149),
        "las condes" to Pair(-33.4117, -70.5804),
        "maipú" to Pair(-33.5111, -70.7523),
        "maipu" to Pair(-33.5111, -70.7523),
        "puente alto" to Pair(-33.6117, -70.5758),
        "la florida" to Pair(-33.5227, -70.5983),
        "san bernardo" to Pair(-33.5922, -70.7042),
        "ñuñoa" to Pair(-33.4569, -70.5975),
        "nunoa" to Pair(-33.4569, -70.5975),
        "peñalolén" to Pair(-33.4722, -70.5408),
        "penalolen" to Pair(-33.4722, -70.5408),
        "pudahuel" to Pair(-33.4403, -70.7558),
        "recoleta" to Pair(-33.4069, -70.6433),
        "vitacura" to Pair(-33.3828, -70.5728),
        "macul" to Pair(-33.4839, -70.6019),
        "quilicura" to Pair(-33.3639, -70.7303),
        "san miguel" to Pair(-33.4967, -70.6517),
        "la reina" to Pair(-33.4453, -70.5408),
        "estación central" to Pair(-33.4617, -70.6869),
        "estacion central" to Pair(-33.4617, -70.6869),
        "independencia" to Pair(-33.4167, -70.6500),
        "conchalí" to Pair(-33.3833, -70.6667),
        "conchali" to Pair(-33.3833, -70.6667),
        "ranca" to Pair(-34.1701, -70.7444),
        "valparaíso" to Pair(-33.0472, -71.6127),
        "viña del mar" to Pair(-33.0245, -71.5518)
    )
}
