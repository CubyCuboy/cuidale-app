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
import com.example.cuidaleapp.ui.components.CuidaleSearchContent
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.LocationUtils
import com.example.cuidaleapp.utils.SugerenciaUbicacion
import com.example.cuidaleapp.viewmodel.MapaCuidadoresViewModel
import com.example.cuidaleapp.viewmodel.MapaUiState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerAt
import com.mapbox.maps.extension.style.layers.generated.backgroundLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.heatmapLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaCuidadoresScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onNavigateToFicha: () -> Unit = {},
    viewModel: MapaCuidadoresViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val isCargandoGps by viewModel.isCargandoGps.collectAsState()

    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val selectedCommunes by viewModel.selectedCommunes.collectAsState()
    val useProfilePreferences by viewModel.useProfilePreferences.collectAsState()
    val sinComunasEnFicha by viewModel.sinComunasEnFicha.collectAsState()

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
    var modoMapaDeCalor by remember { mutableStateOf(true) } // Por defecto modo mapa de calor
    var showFilterSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val filterSheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    // Lista completa de comunas de Chile como respaldo local offline
    val todasLasComunas = remember {
        ChileData.regions.flatMap { it.comunas }.distinct().sorted()
    }

    // Consulta de sugerencias en vivo mediante Mapbox Places API
    LaunchedEffect(textoBusqueda) {
        val q = textoBusqueda.trim()
        if (q.length >= 2) {
            delay(250) // Debounce 250 ms
            val sugerenciasApi = LocationUtils.buscarSugerenciasMapbox(context, q)
            if (sugerenciasApi.isNotEmpty()) {
                sugerenciasUbicacion = sugerenciasApi
            } else {
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

                // Mapbox HeatmapView con máscara gris global sobre carreteras, verdes y todo el mapa
                MapboxHeatmapView(
                    geoJsonData = geoJson,
                    centro = centroBusqueda,
                    cameraEventId = cameraEventId,
                    resetNorthTrigger = resetNorthTrigger,
                    modoMapaDeCalor = modoMapaDeCalor,
                    onMapClick = { punto ->
                        viewModel.filtrarPorZonaSeleccionada(punto, 1500.0)
                        showBottomSheet = true
                    }
                )

                // Barra Superior Flotante de Búsqueda y Sugerencias en Vivo
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

                        // Input para búsqueda manual por dirección / comuna / calles
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (textoBusqueda.isNotEmpty()) {
                                        IconButton(onClick = {
                                            textoBusqueda = ""
                                            sugerenciasUbicacion = emptyList()
                                            viewModel.limpiarTextoBusquedaSinMoverCamara()
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                        }
                                    }

                                    // Botón para abrir el panel de Filtros embebido
                                    IconButton(onClick = { showFilterSheet = true }) {
                                        BadgedBox(
                                            badge = {
                                                if (selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty() || useProfilePreferences) {
                                                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = "Filtros de Búsqueda",
                                                tint = if (selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty() || useProfilePreferences)
                                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        // Columna de Botones Flotantes de Acción: GPS, Norte y Modo Zonas de Calor (Ícono de Fuego)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. Botón de GPS rápido
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

                            // 2. Botón Orientar Mapa al Norte (debajo del GPS)
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

                            // 3. Botón para Alternar Modo Zonas de Calor (Ícono de Fuego)
                            IconButton(
                                onClick = { modoMapaDeCalor = !modoMapaDeCalor },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (modoMapaDeCalor) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Modo Zona de Calor",
                                    tint = if (modoMapaDeCalor) Color(0xFFFF6D00) else MaterialTheme.colorScheme.onSurfaceVariant
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

                // ModalBottomSheet con los Filtros de Búsqueda Embebidos
                if (showFilterSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showFilterSheet = false },
                        sheetState = filterSheetState,
                        containerColor = CuidaleThemeExtras.customColors.panelBackground
                    ) {
                        Column {
                            // Aviso si el usuario activó la preferencia pero no tiene comunas guardadas en la ficha
                            if (sinComunasEnFicha) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Aún no tienes comunas en tu ficha técnica",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Asigna tus comunas preferidas para aplicar este filtro.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                showFilterSheet = false
                                                onNavigateToFicha()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("CONFIGURAR", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }

                            CuidaleSearchContent(
                                allRegions = ChileData.regions.map { it.name },
                                selectedRegion = selectedRegion,
                                onRegionSelected = { viewModel.selectRegion(it) },
                                allCommunes = ChileData.regions.find { it.name == selectedRegion }?.comunas?.sorted() ?: emptyList(),
                                selectedCommunes = selectedCommunes,
                                onCommuneToggle = { viewModel.toggleCommune(it) },
                                onClearFilters = { viewModel.limpiarFiltros() },
                                onClose = { scope.launch { filterSheetState.hide() }.invokeOnCompletion { showFilterSheet = false } },
                                useProfilePreferences = useProfilePreferences,
                                onTogglePreferences = { viewModel.toggleProfilePreferences(it) }
                            )
                        }
                    }
                }

                // BottomSheet desplegable con la lista de Cuidadores Filtrados
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
                                        text = "No hay cuidadores en este sector con los filtros actuales.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
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
 * Componente ejecutor de Mapbox Maps SDK v11 que configura el mapa interactivo con máscara gris
 * completa sobre carreteras, áreas verdes y aguas, destacando los puntos de calor térmicos.
 */
@Composable
fun MapboxHeatmapView(
    geoJsonData: FeatureCollection?,
    centro: Point,
    cameraEventId: Long,
    resetNorthTrigger: Long,
    modoMapaDeCalor: Boolean,
    onMapClick: (Point) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    LaunchedEffect(mapView) {
        mapView.compass.enabled = false

        mapView.mapboxMap.loadStyle(Style.MAPBOX_STREETS) { style ->
            val sourceId = "cuidadores-geojson-source"
            val userSourceId = "user-location-geojson-source"
            val worldSourceId = "world-grey-mask-source"

            // 1. Fuente y Capa de Máscara Gris Global sobre Todo el Mapa (Carreteras, Verdes, Aguas)
            val worldRing = listOf(
                Point.fromLngLat(-180.0, -85.0),
                Point.fromLngLat(180.0, -85.0),
                Point.fromLngLat(180.0, 85.0),
                Point.fromLngLat(-180.0, 85.0),
                Point.fromLngLat(-180.0, -85.0)
            )
            val worldPolygon = Polygon.fromLngLats(listOf(worldRing))
            val worldFeatureCollection = FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(worldPolygon)))

            if (style.getSource(worldSourceId) == null) {
                val worldSource = geoJsonSource(worldSourceId) {
                    featureCollection(worldFeatureCollection)
                }
                style.addSource(worldSource)
            }

            if (style.getLayer("world-grey-mask-layer") == null) {
                val greyMaskLayer = fillLayer("world-grey-mask-layer", worldSourceId) {
                    fillColor("#424242") // Gris neutro uniforme que cubre carreteras, áreas verdes y mares
                    fillOpacity(0.82) // 82% de opacidad para que todo el mapa sea un lienzo gris
                }
                style.addLayer(greyMaskLayer)
            }

            // 2. Fuente GeoJSON de Cuidadores
            if (style.getSource(sourceId) == null) {
                val source = geoJsonSource(sourceId) {
                    geoJsonData?.let { featureCollection(it) }
                    cluster(true)
                    clusterRadius(50L)
                    clusterMaxZoom(14L)
                }
                style.addSource(source)
            }

            // 3. Capa de Puntos de Calor Térmicos Vibrantes (Cyan -> Amarillo -> Naranja -> Rojo Fuego)
            if (style.getLayer("cuidadores-heat-points-layer") == null) {
                val heatPoints = heatmapLayer("cuidadores-heat-points-layer", sourceId) {
                    heatmapWeight(1.0)
                    heatmapColor(
                        interpolate {
                            linear()
                            heatmapDensity()
                            stop {
                                literal(0.0)
                                color(Color(0x00000000).toArgb()) // Transparente afuera
                            }
                            stop {
                                literal(0.15)
                                color(Color(0xDD00E5FF).toArgb()) // Cyan brillante de alto impacto
                            }
                            stop {
                                literal(0.45)
                                color(Color(0xE6FFEB3B).toArgb()) // Amarillo brillante
                            }
                            stop {
                                literal(0.75)
                                color(Color(0xF1FF6D00).toArgb()) // Naranja fuego
                            }
                            stop {
                                literal(1.00)
                                color(Color(0xFFD50000).toArgb()) // Rojo intenso
                            }
                        }
                    )
                    heatmapRadius(
                        interpolate {
                            exponential { literal(1.5) }
                            zoom()
                            stop {
                                literal(0.0)
                                literal(10.0)
                            }
                            stop {
                                literal(9.0)
                                literal(28.0)
                            }
                            stop {
                                literal(15.0)
                                literal(65.0)
                            }
                        }
                    )
                    heatmapOpacity(0.92)
                }
                style.addLayer(heatPoints)
            }

            // 4. Capa Puntos Individuales Sólidos de Cuidadores (para Modo Normal)
            if (style.getLayer("unclustered-caregivers-layer") == null) {
                val unclusteredPoints = circleLayer("unclustered-caregivers-layer", sourceId) {
                    filter(Expression.not(Expression.has("point_count")))
                    circleColor("#1E88E5")
                    circleRadius(8.5)
                    circleStrokeWidth(2.5)
                    circleStrokeColor("#FFFFFF")
                }
                style.addLayer(unclusteredPoints)
            }

            // 5. Capa Círculos Agrupados (Clusters para Modo Normal)
            if (style.getLayer("cluster-circles-layer") == null) {
                val clusterCircles = circleLayer("cluster-circles-layer", sourceId) {
                    filter(Expression.has("point_count"))
                    circleColor(
                        interpolate {
                            linear()
                            get { literal("point_count") }
                            stop {
                                literal(2.0)
                                color(Color(0xFF00897B).toArgb())
                            }
                            stop {
                                literal(10.0)
                                color(Color(0xFF1E88E5).toArgb())
                            }
                            stop {
                                literal(25.0)
                                color(Color(0xFF1565C0).toArgb())
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

            // 6. Capa Conteo Numérico de Agrupaciones
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

            // 7. Indicación de Posición Actual del Usuario / Centro de Búsqueda
            val userPointFeature = Feature.fromGeometry(centro)
            val userFeatureCollection = FeatureCollection.fromFeatures(listOf(userPointFeature))

            if (style.getSource(userSourceId) == null) {
                val userSource = geoJsonSource(userSourceId) {
                    featureCollection(userFeatureCollection)
                }
                style.addSource(userSource)
            }

            if (style.getLayer("user-location-outer-pulse") == null) {
                val outerPulse = circleLayer("user-location-outer-pulse", userSourceId) {
                    circleColor("#2196F3")
                    circleOpacity(0.25)
                    circleRadius(20.0)
                }
                style.addLayer(outerPulse)
            }

            if (style.getLayer("user-location-inner-circle") == null) {
                val innerCircle = circleLayer("user-location-inner-circle", userSourceId) {
                    circleColor("#1976D2")
                    circleRadius(8.0)
                    circleStrokeWidth(3.0)
                    circleStrokeColor("#FFFFFF")
                }
                style.addLayer(innerCircle)
            }
        }

        mapView.mapboxMap.addOnMapClickListener { point ->
            onMapClick(point)
            true
        }
    }

    // Alternar Visibilidad entre Modo Zona de Calor (Máscara Gris Global + Puntos de Calor) y Modo Estándar
    LaunchedEffect(modoMapaDeCalor) {
        mapView.mapboxMap.getStyle { style ->
            val heatVis = if (modoMapaDeCalor) Visibility.VISIBLE else Visibility.NONE
            val normalVis = if (!modoMapaDeCalor) Visibility.VISIBLE else Visibility.NONE

            // Máscara gris global y puntos de calor
            style.getLayer("world-grey-mask-layer")?.visibility(heatVis)
            style.getLayer("cuidadores-heat-points-layer")?.visibility(heatVis)

            // Puntos sólidos y clusters estándar
            style.getLayer("unclustered-caregivers-layer")?.visibility(normalVis)
            style.getLayer("cluster-circles-layer")?.visibility(normalVis)
            style.getLayer("cluster-count-layer")?.visibility(normalVis)
        }
    }

    // Actualizar reactivamente la fuente GeoJSON de cuidadores
    LaunchedEffect(geoJsonData) {
        if (geoJsonData != null) {
            mapView.mapboxMap.getStyle { style ->
                val source = style.getSource("cuidadores-geojson-source") as? GeoJsonSource
                source?.featureCollection(geoJsonData)
            }
        }
    }

    // Actualizar reactivamente el punto indicador de la posición del usuario
    LaunchedEffect(centro) {
        mapView.mapboxMap.getStyle { style ->
            val userSource = style.getSource("user-location-geojson-source") as? GeoJsonSource
            val userFeatureCollection = FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(centro)))
            userSource?.featureCollection(userFeatureCollection)
        }
    }

    // Centrado suave de la cámara sólo cuando cameraEventId se modifica
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
