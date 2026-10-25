package com.example.cuidaleapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.Cuidador
import com.example.cuidaleapp.models.toFeatureCollection
import com.example.cuidaleapp.utils.LocationUtils
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
        val centroBusqueda: Point?,
        val mensajeAviso: String?,
        val cameraEventId: Long = System.currentTimeMillis()
    ) : MapaUiState
    data class Error(val mensaje: String) : MapaUiState
}

class MapaCuidadoresViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<MapaUiState>(MapaUiState.Loading)
    val uiState: StateFlow<MapaUiState> = _uiState.asStateFlow()

    private val _isCargandoGps = MutableStateFlow(false)
    val isCargandoGps: StateFlow<Boolean> = _isCargandoGps.asStateFlow()

    // Todos los cuidadores obtenidos de Firestore sin filtrar
    private var todosLosCuidadores = mutableListOf<Cuidador>()

    // Punto central por defecto (Región Metropolitana, Santiago de Chile: Lat -33.4489, Lng -70.6693)
    val puntoGeneralRM = Point.fromLngLat(-70.6693, -33.4489)

    init {
        cargarCuidadoresFirestore()
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

                // 2. Si "cuidadores" tiene pocos registros, complementar desde "usuarios" con rol == "cuidador"
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
                val geoJson = todosLosCuidadores.toFeatureCollection()

                _uiState.value = MapaUiState.Success(
                    cuidadores = todosLosCuidadores.toList(),
                    geoJsonData = geoJson,
                    centroBusqueda = puntoGeneralRM,
                    mensajeAviso = "Vista general: Cuidadores en la Región Metropolitana",
                    cameraEventId = System.currentTimeMillis()
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

                    val filtrados = todosLosCuidadores.filter { c ->
                        val dist = LocationUtils.calcularDistanciaMetros(latGps, lngGps, c.latitud, c.longitud)
                        dist <= 3000.0 // 3 km
                    }

                    val geoJson = (if (filtrados.isNotEmpty()) filtrados else todosLosCuidadores).toFeatureCollection()
                    val mensaje = if (filtrados.isNotEmpty()) {
                        "Se encontraron ${filtrados.size} cuidadores a menos de 3 km de tu ubicación GPS."
                    } else {
                        "Ubicación centrada en tu GPS. Sin cuidadores a menos de 3 km (mostrando vista general)."
                    }

                    _uiState.value = MapaUiState.Success(
                        cuidadores = if (filtrados.isNotEmpty()) filtrados else todosLosCuidadores,
                        geoJsonData = geoJson,
                        centroBusqueda = ubicacionGps,
                        mensajeAviso = mensaje,
                        cameraEventId = System.currentTimeMillis()
                    )
                } else {
                    // Estado de fallback/error de geolocalización (UH4)
                    val currentState = _uiState.value
                    val cuidadoresActuales = if (currentState is MapaUiState.Success) currentState.cuidadores else todosLosCuidadores
                    _uiState.value = MapaUiState.Success(
                        cuidadores = cuidadoresActuales,
                        geoJsonData = cuidadoresActuales.toFeatureCollection(),
                        centroBusqueda = puntoGeneralRM,
                        mensajeAviso = "No se pudo obtener la ubicación GPS. Verifica tus permisos y el GPS activado.",
                        cameraEventId = System.currentTimeMillis()
                    )
                }
            } finally {
                _isCargandoGps.value = false
            }
        }
    }

    /**
     * Búsqueda directa por coordenadas de un punto seleccionado de sugerencia (calle, avenida, dirección)
     */
    fun buscarPorCoordenadasDirectas(nombreLugar: String, punto: Point) {
        val latDir = punto.latitude()
        val lngDir = punto.longitude()

        val filtrados = todosLosCuidadores.filter { c ->
            val dist = LocationUtils.calcularDistanciaMetros(latDir, lngDir, c.latitud, c.longitud)
            dist <= 3000.0 // 3 km
        }

        val listaResultado = if (filtrados.isNotEmpty()) filtrados else todosLosCuidadores
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
     * Búsqueda manual por dirección/comuna mediante Geocoder nativo y fallback comunal local (UH2)
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

            // Fallback directo por coordenadas de comunas conocidas de Chile si Geocoder no responde
            if (ubicacionDir == null) {
                val coords = resolverCoordenadas(null, null, textoLimpio)
                if (coords != Pair(-33.4489, -70.6693) || textoLimpio.lowercase().contains("santiago")) {
                    ubicacionDir = Point.fromLngLat(coords.second, coords.first)
                }
            }

            if (ubicacionDir != null) {
                buscarPorCoordenadasDirectas(textoLimpio, ubicacionDir)
            } else {
                // Manejo de error de geocodificación (UH4)
                val currentState = _uiState.value
                val cuidadoresActuales = if (currentState is MapaUiState.Success) currentState.cuidadores else todosLosCuidadores
                _uiState.value = MapaUiState.Success(
                    cuidadores = cuidadoresActuales,
                    geoJsonData = cuidadoresActuales.toFeatureCollection(),
                    centroBusqueda = puntoGeneralRM,
                    mensajeAviso = "No se encontró la ubicación para '$textoLimpio'. Mostrando vista general.",
                    cameraEventId = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Limpia el texto de búsqueda y restablece los cuidadores MANTENIENDO la posición actual de la cámara sin mover el mapa.
     */
    fun limpiarTextoBusquedaSinMoverCamara() {
        val currentState = _uiState.value
        if (currentState is MapaUiState.Success) {
            _uiState.value = currentState.copy(
                cuidadores = todosLosCuidadores.toList(),
                geoJsonData = todosLosCuidadores.toFeatureCollection(),
                mensajeAviso = null
                // centroBusqueda y cameraEventId NO se modifican para evitar movimientos no deseados de la cámara
            )
        }
    }

    /**
     * Estado fallback sin GPS ni dirección manual (UH3). Restablece la vista al centro de la RM.
     */
    fun limpiarFiltroUbicacion() {
        _uiState.value = MapaUiState.Success(
            cuidadores = todosLosCuidadores.toList(),
            geoJsonData = todosLosCuidadores.toFeatureCollection(),
            centroBusqueda = puntoGeneralRM,
            mensajeAviso = "Vista general de Región Metropolitana. Filtros de ubicación restablecidos.",
            cameraEventId = System.currentTimeMillis()
        )
    }

    /**
     * Filtrar cuidadores por una zona seleccionada en el mapa al tocar una coordenada.
     */
    fun filtrarPorZonaSeleccionada(puntoTocado: Point, radioMetros: Double = 1500.0) {
        val latTocado = puntoTocado.latitude()
        val lngTocado = puntoTocado.longitude()

        val filtrados = todosLosCuidadores.filter { c ->
            val dist = LocationUtils.calcularDistanciaMetros(latTocado, lngTocado, c.latitud, c.longitud)
            dist <= radioMetros
        }

        val listaResultado = if (filtrados.isNotEmpty()) filtrados else todosLosCuidadores
        val radioKm = String.format("%.1f", radioMetros / 1000.0)

        val mensaje = if (filtrados.isNotEmpty()) {
            "${filtrados.size} cuidadores en el área seleccionada (radio de $radioKm km)."
        } else {
            "No hay cuidadores en el área directa tocada. Mostrando vista general."
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
     * Asigna coordenadas geográficas a partir de lat/lng recibidos o mediante las coordenadas base de la comuna.
     */
    private fun resolverCoordenadas(lat: Double?, lng: Double?, comuna: String): Pair<Double, Double> {
        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            return Pair(lat, lng)
        }

        val coordenadasComuna = mapCoordenadasComunas[comuna.trim().lowercase()]
            ?: Pair(-33.4489, -70.6693) // Santiago Centro por defecto

        // Añadir una pequeña variación aleatoria (jitter) para evitar superposición perfecta
        val jitterLat = (Random.nextDouble() - 0.5) * 0.015
        val jitterLng = (Random.nextDouble() - 0.5) * 0.015

        return Pair(coordenadasComuna.first + jitterLat, coordenadasComuna.second + jitterLng)
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
