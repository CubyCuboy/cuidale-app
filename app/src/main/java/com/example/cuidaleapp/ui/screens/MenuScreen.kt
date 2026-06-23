package com.example.cuidaleapp.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.SearchState
import com.example.cuidaleapp.viewmodel.SearchViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.ui.platform.LocalContext

import com.example.cuidaleapp.utils.ConnectivityObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    userData: UserData,
    searchViewModel: SearchViewModel,
    fichaState: FichaState,
    onViewFicha: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val context = LocalContext.current

    val searchState by searchViewModel.state.collectAsState()
    val isRefreshing by searchViewModel.isRefreshing.collectAsState()
    val selectedRegion by searchViewModel.selectedRegion.collectAsState()
    val selectedCommunes by searchViewModel.selectedCommunes.collectAsState()
    val useProfilePreferences by searchViewModel.useProfilePreferences.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    val sheetState = rememberModalBottomSheetState()
    var showSearchSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var showFichaReminder by remember { mutableStateOf(false) }

    LaunchedEffect(fichaState) {
        if (fichaState is FichaState.NotFound) {
            val prefs = context.getSharedPreferences("cuidale_reminders", Context.MODE_PRIVATE)
            val lastReminder = prefs.getLong("last_ficha_reminder", 0L)
            val now = System.currentTimeMillis()

            if (now - lastReminder > 2 * 60 * 60 * 1000) {
                showFichaReminder = true
                prefs.edit().putLong("last_ficha_reminder", now).apply()
            }
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore.value, isOnline) {
        if (shouldLoadMore.value && isOnline) {
            searchViewModel.loadMore()
        }
    }

    LaunchedEffect(userData.rol, userData.region, isOnline) {
        if (userData.rol.isNotEmpty() && isOnline) {
            searchViewModel.initSearch(userData.rol, userData.region)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            val listTitle = if (userData.rol == "cuidador") "Pacientes" else "Cuidadores"

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = listTitle,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            if (fichaState is FichaState.NotFound) {
                FichaPlaceholder(
                    rol = userData.rol,
                    onClick = onViewFicha
                )
            } else if (!isOnline && searchState !is SearchState.Success) {
                CuidalePlaceholder(
                    title = if (userData.rol == "cuidador") "¡Aún no hay pacientes cerca!" else "Buscando cuidadores...",
                    subtitle = if (userData.rol == "cuidador")
                        "Activa tu conexión para encontrar personas que necesitan tu ayuda hoy mismo."
                        else "Necesitas internet para descubrir nuevos perfiles que puedan cuidar a tus seres queridos.",
                    icon = if (userData.rol == "cuidador") Icons.Default.AccessibilityNew else Icons.Default.Diversity3,
                    iconColor = if (userData.rol == "cuidador") MaterialTheme.colorScheme.primary else CuidaleThemeExtras.customColors.mint
                )
            } else {
                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = { if (isOnline) searchViewModel.refresh() },
                    modifier = Modifier.weight(1f)
                ) {
                    AnimatedContent(
                        targetState = searchState,
                        transitionSpec = {
                            (fadeIn(tween(500)) + scaleIn(initialScale = 0.96f, animationSpec = tween(500)))
                                .togetherWith(fadeOut(tween(400)))
                        },
                        label = "searchContentTransition"
                    ) { state ->
                        when (state) {
                            is SearchState.Loading -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(5) { SkeletonUserCard() }
                                }
                            }
                            is SearchState.Success -> {
                                val resultsList = state.results
                                val isLoadingMore = state.isLoadingMore

                                if (resultsList.isEmpty()) {
                                    EmptySearchResults(
                                        title = if (selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty())
                                            "Sin resultados para estos filtros"
                                        else "No hay $listTitle disponibles",
                                        subtitle = "Prueba cambiando la ubicación o los filtros de búsqueda.",
                                        onClearFilters = { searchViewModel.clearFilters() },
                                        showClearButton = selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty()
                                    )
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(bottom = 100.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(resultsList) { result ->
                                            CuidaleUserCard(
                                                nombre = result.nombre,
                                                apellido = result.apellido_pa,
                                                fotoUrl = result.fotoUrl,
                                                comuna = result.comuna,
                                                comunas = result.comunas,
                                                tipo = result.tipo,
                                                edad = result.edad,
                                                nivelDependencia = result.nivel_dependencia,
                                                especializacion = result.especializacion,
                                                horarios = result.horarios,
                                                isNew = result.isNew,
                                                onClick = { onUserClick(result.id) }
                                            )
                                        }

                                        if (isLoadingMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is SearchState.Error -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isOnline) {
            CuidaleCircularMenuButton(
                onClick = { showSearchSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 24.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            )
        }

        if (showFichaReminder) {
            AlertDialog(
                onDismissRequest = { showFichaReminder = false },
                icon = { Icon(Icons.Default.Warning, null, tint = Color.Yellow) },
                title = { Text("Ficha incompleta") },
                text = {
                    Text(
                        if (userData.rol == "cuidador")
                            "Aún no tienes una ficha creada. Recuerda que no puedes ofrecer tus servicios ni aparecer en búsquedas sin ella."
                        else
                            "Aún no conocemos nada de el familiar que debe ser cuidado. Debes hacer una ficha para poder buscar cuidadores."
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showFichaReminder = false
                        onViewFicha()
                    }) {
                        Text("CREAR FICHA AHORA")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFichaReminder = false }) {
                        Text("MÁS TARDE")
                    }
                }
            )
        }

        if (showSearchSheet && isOnline) {
            ModalBottomSheet(
                onDismissRequest = { showSearchSheet = false },
                sheetState = sheetState,
                containerColor = CuidaleThemeExtras.customColors.panelBackground
            ) {
                CuidaleSearchContent(
                    allRegions = ChileData.regions.map { it.name },
                    selectedRegion = selectedRegion,
                    onRegionSelected = { searchViewModel.selectRegion(it) },
                    allCommunes = ChileData.regions.find { it.name == selectedRegion }?.comunas?.sorted() ?: emptyList(),
                    selectedCommunes = selectedCommunes,
                    onCommuneToggle = { searchViewModel.toggleCommune(it) },
                    onClearFilters = { searchViewModel.clearFilters() },
                    onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { showSearchSheet = false } },
                    useProfilePreferences = useProfilePreferences,
                    onTogglePreferences = { searchViewModel.toggleProfilePreferences(it) }
                )
            }
        }
    }
}

@Composable
fun EmptySearchResults(
    title: String,
    subtitle: String,
    onClearFilters: () -> Unit,
    showClearButton: Boolean
) {
    val customColors = CuidaleThemeExtras.customColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HardShadowPanel(
            backgroundColor = customColors.panelBackground.copy(alpha = 0.95f),
            shadowColor = Color.Black.copy(alpha = 0.15f),
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showClearButton) {
                    Spacer(modifier = Modifier.height(32.dp))
                    HardShadowButton(
                        text = "LIMPIAR FILTROS",
                        onClick = onClearFilters,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        isDark = customColors.isDark,
                        modifier = Modifier.height(50.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FichaPlaceholder(rol: String, onClick: () -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = Color.Black.copy(alpha = 0.1f),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (rol == "cuidador")
                        "Aun no puedes buscar a quien cuidar sin una ficha"
                    else
                        "Aun no conocemos nada de el familiar que debe ser cuidado debes hacer una ficha",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Completa tu ficha técnica para acceder a todos los servicios de Cuidale.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                HardShadowButton(
                    text = "CREAR MI FICHA",
                    onClick = onClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    isDark = customColors.isDark
                )
            }
        }
    }
}
