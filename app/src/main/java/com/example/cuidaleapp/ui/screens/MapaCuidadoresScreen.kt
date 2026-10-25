package com.example.cuidaleapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.models.Cuidador
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.LocationUtils
import com.example.cuidaleapp.utils.SugerenciaUbicacion
import com.example.cuidaleapp.viewmodel.MapaCuidadoresViewModel
import com.example.cuidaleapp.viewmodel.MapaUiState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaCuidadoresScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: MapaCuidadoresViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsState()
    val isCargandoGps by viewModel.isCargandoGps.collectAsState()

    // Configurar la barra de estado completamente transparente
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color.Transparent,
            darkIcons = false
        )
    }

    var textoBusqueda by remember { mutableStateOf("") }
    var sugerenciasUbicacion by remember { mutableStateOf<List<SugerenciaUbicacion>>(emptyList()) }
    var resetNorthTrigger by remember { mutableLongStateOf(0L) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showBottomSheet by remember { mutableStateOf(false) }

    // Lista completa de comunas de Chile como respaldo local offline
    val todasLasComunas = remember {
        ChileData.regions.flatMap { it.comunas }.distinct().sorted()
    }

    // Consulta de sugerencias en vivo mediante Mapbox Places API (calles, avenidas, pasajes y lugares)
    LaunchedEffect(textoBusqueda) {
        val q = textoBusqueda.trim()
        if (q.length >= 2) {
            delay(250) // Debounce de 250 ms para optimizar peticiones
            val sugerenciasApi = LocationUtils.buscarSugerenciasMapbox(context, q)
            if (sugerenciasApi.isNotEmpty()) {
                sugerenciasUbicacion = sugerenciasApi
            } else {
                // Respaldar con comunas locales de ChileData si la API no devuelve resultados
                val comunasLocal = todasLasComunas.filter { comuna ->
                    comuna.lowercase().contains(q.lowercase())
                }.take(5).map { comuna ->
                    SugerenciaUbicacion(
                        titulo = comuna,
                        subtitulo = "Región Metropolitana, Chile",
                        punto = viewModel.puntoGeneralRM
                    )
                }
                sugerenciasUbicacion = comunasLocal
            }
        } else {
            sugerenciasUbicacion = emptyList()
        }
    }

    // Launcher de Permisos de Ubicación (UH1)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.buscarPorGps(context)
        } else {
            viewModel.limpiarFiltroUbicacion()
        }
    }

    fun ejecutarOPedirGps() {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            viewModel.buscarPorGps(context)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (val state = uiState) {
            is MapaUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CuidaleThemeExtras.customColors.panelBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando mapa de cuidadores...",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            is MapaUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CuidaleThemeExtras.customColors.panelBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.mensaje,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        HardShadowButton(
                            text = "REINTENTAR",
                            onClick = { viewModel.cargarCuidadoresFirestore() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    }
                }
            }

            is MapaUiState.Success -> {
                val cuidadores = state.cuidadores
                val geoJson = state.geoJsonData
                val centroBusqueda = state.centroBusqueda ?: viewModel.puntoGeneralRM
                val cameraEventId = state.cameraEventId

                // Render del mapa Mapbox con agrupamiento (clusters) y puntos sólidos
                MapboxHeatmapView(
                    geoJsonData = geoJson,
                    centro = centroBusqueda,
                    cameraEventId = cameraEventId,
                    resetNorthTrigger = resetNorthTrigger,
                    onMapClick = { punto ->
                        viewModel.filtrarPorZonaSeleccionada(punto, 1500.0)
                        showBottomSheet = true
                    }
                )

                // Barra Superior Flotante de Búsqueda y Sugerencias en Vivo (UH1, UH2, UH3)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Input para búsqueda manual por dirección / comuna / calles (UH2)
                        OutlinedTextField(
                            value = textoBusqueda,
                            onValueChange = { textoBusqueda = it },
                            placeholder = { Text("Buscar calle, dirección o comuna...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    sugerenciasUbicacion = emptyList()
                                    keyboardController?.hide()
                                    viewModel.buscarPorDireccionManual(context, textoBusqueda)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            trailingIcon = {
                                if (textoBusqueda.isNotEmpty()) {
                                    IconButton(onClick = {
                                        textoBusqueda = ""
                                        sugerenciasUbicacion = emptyList()
                                        viewModel.limpiarTextoBusquedaSinMoverCamara()
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                    }
                                } else {
                                    IconButton(onClick = {
                                        sugerenciasUbicacion = emptyList()
                                        keyboardController?.hide()
                                        viewModel.buscarPorDireccionManual(context, textoBusqueda)
                                    }) {
                                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        // Columna de Botones Flotantes: GPS y Orientación al Norte (debajo del GPS)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Botón de GPS rápido (UH1)
                            IconButton(
                                onClick = {
                                    sugerenciasUbicacion = emptyList()
                                    ejecutarOPedirGps()
                                },
                                enabled = !isCargandoGps,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                if (isCargandoGps) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "GPS Rápido",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Botón Orientar Mapa al Norte (Ubicación ajustada justo debajo del botón GPS)
                            IconButton(
                                onClick = { resetNorthTrigger = System.currentTimeMillis() },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = "Orientar al Norte",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Sugerencias de Calles/Lugares/Comunas en Vivo desde Mapbox Places API
                    AnimatedVisibility(
                        visible = sugerenciasUbicacion.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 48.dp, end = 56.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                sugerenciasUbicacion.forEach { sugerencia ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                textoBusqueda = sugerencia.titulo
                                                sugerenciasUbicacion = emptyList()
                                                keyboardController?.hide()
                                                viewModel.buscarPorCoordenadasDirectas(sugerencia.titulo, sugerencia.punto)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sugerencia.titulo,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = sugerencia.subtitulo,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (sugerencia != sugerenciasUbicacion.last()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Botón flotante para desplegar el BottomSheet con la lista de cuidadores
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    HardShadowButton(
                        text = "VER CUIDADORES (${cuidadores.size})",
                        onClick = { showBottomSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted
                    )
                }

                // BottomSheet desplegable (ModalBottomSheet Material 3)
                if (showBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = sheetState,
                        containerColor = CuidaleThemeExtras.customColors.panelBackground
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cuidadores por Zona (${cuidadores.size})",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { viewModel.limpiarFiltroUbicacion() }) {
                                    Text("Restablecer", color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (cuidadores.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay cuidadores en este sector.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp),
                                    modifier = Modifier.fillMaxHeight(0.6f)
                                ) {
                                    items(cuidadores, key = { it.id }) { cuidador ->
                                        CuidadorCardItem(
                                            cuidador = cuidador,
                                            centro = centroBusqueda,
                                            onClick = {
                                                showBottomSheet = false
                                                onUserClick(cuidador.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Componente ejecutor de Mapbox Maps SDK v11 que configura GeoJsonSource con agrupamiento (clustering)
 * de puntos sólidos y capas de conteo total por zona.
 */
@Composable
fun MapboxHeatmapView(
    geoJsonData: FeatureCollection?,
    centro: Point,
    cameraEventId: Long,
    resetNorthTrigger: Long,
    onMapClick: (Point) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    LaunchedEffect(mapView) {
        // Desactivar el compass por defecto de Mapbox para usar nuestro botón custom alineado
        mapView.compass.enabled = false

        mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            val sourceId = "cuidadores-geojson-source"

            // 1. Configuración de la fuente GeoJSON con Clustering activo
            if (style.getSource(sourceId) == null) {
                val source = geoJsonSource(sourceId) {
                    geoJsonData?.let { featureCollection(it) }
                    cluster(true)
                    clusterRadius(50L) // Radio en píxeles para agrupar puntos cercanos
                    clusterMaxZoom(14L) // Zoom máximo en el cual se agrupan los puntos
                }
                style.addSource(source)
            }

            // 2. Capa para puntos individuales de cuidadores (puntos sólidos sin degradados)
            if (style.getLayer("unclustered-caregivers-layer") == null) {
                val unclusteredPoints = circleLayer("unclustered-caregivers-layer", sourceId) {
                    filter(Expression.not(Expression.has("point_count")))
                    circleColor("#1E88E5") // Azul sólido vibrante
                    circleRadius(8.5)
                    circleStrokeWidth(2.5)
                    circleStrokeColor("#FFFFFF")
                }
                style.addLayer(unclusteredPoints)
            }

            // 3. Capa para círculos agrupados (clusters) con colores según cantidad
            if (style.getLayer("cluster-circles-layer") == null) {
                val clusterCircles = circleLayer("cluster-circles-layer", sourceId) {
                    filter(Expression.has("point_count"))
                    circleColor(
                        interpolate {
                            linear()
                            get { literal("point_count") }
                            stop {
                                literal(2.0)
                                color(Color(0xFF00897B).toArgb()) // Verde menta para agrupaciones pequeñas
                            }
                            stop {
                                literal(10.0)
                                color(Color(0xFF1E88E5).toArgb()) // Azul primario para agrupaciones medianas
                            }
                            stop {
                                literal(25.0)
                                color(Color(0xFF1565C0).toArgb()) // Azul marino para agrupaciones grandes
                            }
                        }
                    )
                    circleRadius(
                        interpolate {
                            linear()
                            get { literal("point_count") }
                            stop {
                                literal(2.0)
                                literal(18.0)
                            }
                            stop {
                                literal(10.0)
                                literal(24.0)
                            }
                            stop {
                                literal(50.0)
                                literal(32.0)
                            }
                        }
                    )
                    circleStrokeWidth(3.0)
                    circleStrokeColor("#FFFFFF")
                }
                style.addLayer(clusterCircles)
            }

            // 4. Capa para mostrar el número total de cuidadores dentro de la agrupación
            if (style.getLayer("cluster-count-layer") == null) {
                val clusterCountText = symbolLayer("cluster-count-layer", sourceId) {
                    filter(Expression.has("point_count"))
                    textField(Expression.toString(Expression.get("point_count")))
                    textSize(13.0)
                    textColor("#FFFFFF")
                    textIgnorePlacement(true)
                    textAllowOverlap(true)
                }
                style.addLayer(clusterCountText)
            }
        }

        mapView.mapboxMap.addOnMapClickListener { point ->
            onMapClick(point)
            true
        }
    }

    // Actualización reactiva de los datos del GeoJsonSource al cambiar geoJsonData
    LaunchedEffect(geoJsonData) {
        if (geoJsonData != null) {
            mapView.mapboxMap.getStyle { style ->
                val source = style.getSource("cuidadores-geojson-source") as? GeoJsonSource
                source?.featureCollection(geoJsonData)
            }
        }
    }

    // Centrado suave de la cámara al cambiar de coordenadas o re-disparar el evento de cámara
    LaunchedEffect(centro, cameraEventId) {
        mapView.mapboxMap.flyTo(
            CameraOptions.Builder()
                .center(centro)
                .zoom(13.0)
                .build(),
            MapAnimationOptions.mapAnimationOptions {
                duration(1000)
            }
        )
    }

    // Reset Orientación al Norte
    LaunchedEffect(resetNorthTrigger) {
        if (resetNorthTrigger > 0L) {
            mapView.mapboxMap.flyTo(
                CameraOptions.Builder()
                    .bearing(0.0)
                    .pitch(0.0)
                    .build(),
                MapAnimationOptions.mapAnimationOptions {
                    duration(800)
                }
            )
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Tarjeta individual de Cuidador para el ModalBottomSheet.
 */
@Composable
fun CuidadorCardItem(
    cuidador: Cuidador,
    centro: Point?,
    onClick: () -> Unit
) {
    val distanciaM = if (centro != null) {
        LocationUtils.calcularDistanciaMetros(centro.latitude(), centro.longitude(), cuidador.latitud, cuidador.longitud)
    } else null

    val distanciaTexto = if (distanciaM != null) {
        if (distanciaM >= 1000) {
            String.format(Locale.getDefault(), "%.1f km", distanciaM / 1000f)
        } else {
            "${distanciaM.toInt()} m"
        }
    } else ""

    HardShadowPanel(
        backgroundColor = MaterialTheme.colorScheme.surface,
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (cuidador.fotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = cuidador.fotoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cuidador.nombre,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = cuidador.especialidad,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = cuidador.comuna,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (distanciaTexto.isNotEmpty()) {
                        Text(
                            text = " • $distanciaTexto",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("VER PERFIL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
