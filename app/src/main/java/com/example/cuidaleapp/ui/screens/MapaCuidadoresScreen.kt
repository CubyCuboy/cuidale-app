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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
import com.example.cuidaleapp.R
import com.example.cuidaleapp.utils.ErrorCodes
import com.example.cuidaleapp.utils.ErrorMessages
import com.example.cuidaleapp.utils.LocationUtils
import com.example.cuidaleapp.utils.ConnectivityObserver
import com.example.cuidaleapp.utils.SugerenciaUbicacion
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaCuidadoresScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onNavigateToFicha: () -> Unit = {},
    viewModel: MapaCuidadoresViewModel = viewModel(),
    favoritosViewModel: FavoritosViewModel = viewModel()
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

    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val favoritos by favoritosViewModel.favoritos.collectAsState()

    val hasActiveFilters = selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty() || useProfilePreferences

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
    var modoMapaDeCalor by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var cuidadorBurbuja by remember { mutableStateOf<Cuidador?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val filterSheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    val todasLasComunas = remember {
        ChileData.regions.flatMap { it.comunas }.distinct().sorted()
    }

    val subtituloSugerencia = stringResource(R.string.mapa_subtitulo_sugerencia)

    LaunchedEffect(textoBusqueda) {
        val q = textoBusqueda.trim()
        if (q.length >= 2) {
            delay(250)
            val sugerenciasApi = LocationUtils.buscarSugerenciasMapbox(context, q)
            if (sugerenciasApi.isNotEmpty()) {
                sugerenciasUbicacion = sugerenciasApi
            } else {
                val comunasLocal = todasLasComunas.filter { comuna ->
                    comuna.lowercase().contains(q.lowercase())
                }.take(5).map { comuna ->
                    SugerenciaUbicacion(
                        titulo = comuna,
                        subtitulo = subtituloSugerencia,
                        punto = viewModel.puntoGeneralRM
                    )
                }
                sugerenciasUbicacion = comunasLocal
            }
        } else {
            sugerenciasUbicacion = emptyList()
        }
    }

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
                            text = stringResource(R.string.mapa_cargando_mapa),
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
                            text = ErrorMessages.get(context, state.mensaje),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        HardShadowButton(
                            text = stringResource(R.string.action_retry),
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

                MapboxHeatmapView(
                    geoJsonData = geoJson,
                    centro = centroBusqueda,
                    cameraEventId = cameraEventId,
                    resetNorthTrigger = resetNorthTrigger,
                    modoMapaDeCalor = modoMapaDeCalor,
                    onMapClick = { punto ->
                        val latTocado = punto.latitude()
                        val lngTocado = punto.longitude()

                        val cuidadorCercano = cuidadores.minByOrNull { c ->
                            LocationUtils.calcularDistanciaMetros(latTocado, lngTocado, c.latitud, c.longitud)
                        }

                        if (cuidadorCercano != null && LocationUtils.calcularDistanciaMetros(latTocado, lngTocado, cuidadorCercano.latitud, cuidadorCercano.longitud) <= 800.0) {
                            cuidadorBurbuja = cuidadorCercano
                            showBottomSheet = false
                        } else {
                            cuidadorBurbuja = null
                            viewModel.filtrarPorZonaSeleccionada(punto, 1500.0)
                            showBottomSheet = true
                        }
                    }
                )

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
                                contentDescription = stringResource(R.string.mapa_volver),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedTextField(
                            value = textoBusqueda,
                            onValueChange = { textoBusqueda = it },
                            placeholder = { Text(stringResource(R.string.mapa_buscar_placeholder)) },
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (textoBusqueda.isNotEmpty()) {
                                        IconButton(onClick = {
                                            textoBusqueda = ""
                                            sugerenciasUbicacion = emptyList()
                                            viewModel.limpiarTextoBusquedaSinMoverCamara()
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.mapa_limpiar))
                                        }
                                    }

                                    Surface(
                                        onClick = { showFilterSheet = true },
                                        color = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = stringResource(R.string.mapa_filtros_desc),
                                                tint = if (hasActiveFilters) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            if (hasActiveFilters) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = {
                                    sugerenciasUbicacion = emptyList()
                                    cuidadorBurbuja = null
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
                                        contentDescription = stringResource(R.string.mapa_gps_desc),
                                        tint = Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = { resetNorthTrigger = System.currentTimeMillis() },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = stringResource(R.string.mapa_orientar_norte_desc),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

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
                                                cuidadorBurbuja = null
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

                AnimatedVisibility(
                    visible = cuidadorBurbuja != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                ) {
                    cuidadorBurbuja?.let { cuidador ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 14.dp,
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PersonPin,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.mapa_cuidador_en_ubicacion),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { cuidadorBurbuja = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.mapa_cerrar),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

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

                                        if (!cuidador.verificado) {
                                            Surface(
                                                color = Color(0xFFFFF3E0),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    stringResource(R.string.caregiver_profile_sin_verificacion),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFE65100),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = cuidador.comuna,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                HardShadowButton(
                                    text = stringResource(R.string.solicitudes_ver_perfil_completo),
                                    onClick = {
                                        cuidadorBurbuja = null
                                        onUserClick(cuidador.id)
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    HardShadowButton(
                        text = stringResource(R.string.mapa_ver_cuidadores, cuidadores.size),
                        onClick = {
                            cuidadorBurbuja = null
                            showBottomSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted
                    )
                }

                if (showFilterSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showFilterSheet = false },
                        sheetState = filterSheetState,
                        containerColor = CuidaleThemeExtras.customColors.panelBackground
                    ) {
                        Column {
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
                                                text = stringResource(R.string.mapa_comunas_ficha_title),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = stringResource(R.string.mapa_comunas_ficha_desc),
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
                                            Text(stringResource(R.string.mapa_configurar), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
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
                                    text = stringResource(R.string.mapa_cuidadores_zona, cuidadores.size),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { viewModel.limpiarFiltroUbicacion() }) {
                                    Text(stringResource(R.string.mapa_restablecer), color = MaterialTheme.colorScheme.primary)
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
                                        text = stringResource(R.string.mapa_sin_resultados),
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
 * Componente ejecutor de Mapbox Maps SDK v11 que configura el mapa interactivo con filtro gris
 * uniforme sobre todo el mapa y puntos de calor térmicos vibrantes de alto contraste.
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
                    fillColor("#424242")
                    fillOpacity(0.82)
                }
                style.addLayer(greyMaskLayer)
            }

            if (style.getSource(sourceId) == null) {
                val source = geoJsonSource(sourceId) {
                    geoJsonData?.let { featureCollection(it) }
                    cluster(true)
                    clusterRadius(50L)
                    clusterMaxZoom(14L)
                }
                style.addSource(source)
            }

            if (style.getLayer("cuidadores-heat-points-layer") == null) {
                val heatPoints = heatmapLayer("cuidadores-heat-points-layer", sourceId) {
                    heatmapWeight(1.0)
                    heatmapColor(
                        interpolate {
                            linear()
                            heatmapDensity()
                            stop {
                                literal(0.0)
                                color(Color(0x00000000).toArgb())
                            }
                            stop {
                                literal(0.15)
                                color(Color(0xDD00E5FF).toArgb())
                            }
                            stop {
                                literal(0.45)
                                color(Color(0xE6FFEB3B).toArgb())
                            }
                            stop {
                                literal(0.75)
                                color(Color(0xF1FF6D00).toArgb())
                            }
                            stop {
                                literal(1.00)
                                color(Color(0xFFD50000).toArgb())
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

    LaunchedEffect(modoMapaDeCalor) {
        mapView.mapboxMap.getStyle { style ->
            val heatVis = if (modoMapaDeCalor) Visibility.VISIBLE else Visibility.NONE
            val normalVis = if (!modoMapaDeCalor) Visibility.VISIBLE else Visibility.NONE

            style.getLayer("world-grey-mask-layer")?.visibility(heatVis)
            style.getLayer("cuidadores-heat-points-layer")?.visibility(heatVis)

            style.getLayer("unclustered-caregivers-layer")?.visibility(normalVis)
            style.getLayer("cluster-circles-layer")?.visibility(normalVis)
            style.getLayer("cluster-count-layer")?.visibility(normalVis)
        }
    }

    LaunchedEffect(geoJsonData) {
        if (geoJsonData != null) {
            mapView.mapboxMap.getStyle { style ->
                val source = style.getSource("cuidadores-geojson-source") as? GeoJsonSource
                source?.featureCollection(geoJsonData)
            }
        }
    }

    LaunchedEffect(centro) {
        mapView.mapboxMap.getStyle { style ->
            val userSource = style.getSource("user-location-geojson-source") as? GeoJsonSource
            val userFeatureCollection = FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(centro)))
            userSource?.featureCollection(userFeatureCollection)
        }
    }

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
 * Diseño vertical expandable: foto arriba, datos, acciones y área expandible.
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
            stringResource(R.string.mapa_distancia_km, distanciaM / 1000f)
        } else {
            stringResource(R.string.mapa_distancia_m, distanciaM.toInt())
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
                            text = stringResource(R.string.mapa_separador_distancia, distanciaTexto),
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
                Text(stringResource(R.string.solicitudes_ver_perfil), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
