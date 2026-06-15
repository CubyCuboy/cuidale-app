package com.example.cuidaleapp.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    userData: UserData,
    searchViewModel: SearchViewModel,
    fichaState: FichaState,
    isDark: Boolean,
    unreadCount: Int = 0,
    pendingNotifs: Int = 0,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onViewProfile: () -> Unit,
    onViewFicha: () -> Unit,
    onViewSettings: () -> Unit,
    onUserClick: (String) -> Unit,
    onTabSelected: (String) -> Unit,
    solicitudViewModel: com.example.cuidaleapp.viewmodel.SolicitudViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val gradient = CuidaleThemeExtras.gradient
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val searchState by searchViewModel.state.collectAsState()
    val isRefreshing by searchViewModel.isRefreshing.collectAsState()
    val selectedRegion by searchViewModel.selectedRegion.collectAsState()
    val selectedCommunes by searchViewModel.selectedCommunes.collectAsState()
    
    val pullToRefreshState = rememberPullToRefreshState()
    val sheetState = rememberModalBottomSheetState()
    var showSearchSheet by remember { mutableStateOf(false) }
    
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

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            searchViewModel.loadMore()
        }
    }

    LaunchedEffect(userData.rol) {
        if (userData.rol.isNotEmpty()) {
            searchViewModel.initSearch(userData.rol)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxHeight().width(320.dp),
                        drawerContainerColor = Color.Transparent,
                        drawerTonalElevation = 0.dp
                    ) {
                        CuidaleDrawerContent(
                            nombre = userData.nombre,
                            apellido = userData.apellido_pa,
                            fotoUrl = userData.fotoUrl,
                            rol = userData.rol,
                            isDark = isDark,
                            onToggleTheme = onToggleTheme,
                            onProfileClick = {
                                scope.launch { drawerState.close() }
                                onViewProfile()
                            },
                            onFichaClick = {
                                scope.launch { drawerState.close() }
                                onViewFicha()
                            },
                            onSettingsClick = {
                                scope.launch { drawerState.close() }
                                onViewSettings()
                            },
                            onLogoutClick = {
                                scope.launch { drawerState.close() }
                                onLogout()
                            },
                            onCloseClick = {
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            },
            gesturesEnabled = true
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            CuidaleCircularMenuButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    },
                    floatingActionButton = {
                        CuidaleCircularMenuButton(
                            onClick = { showSearchSheet = true },
                            modifier = Modifier.padding(bottom = 16.dp, end = 16.dp),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        )
                    },
                    bottomBar = {
                        val misPostulaciones = solicitudViewModel.misPostulaciones.collectAsState().value
                        val postulacionesRecibidas = solicitudViewModel.postulacionesRecibidas.collectAsState().value
                        val pendingReqCount = if (userData.rol == "cuidador") misPostulaciones.size 
                                              else postulacionesRecibidas.values.flatten().count { it.estado == "pendiente" }

                        CuidaleBottomBar(
                            selectedTab = "home", 
                            unreadCount = unreadCount, 
                            pendingNotifs = pendingNotifs,
                            pendingRequests = pendingReqCount,
                            onTabSelected = onTabSelected
                        )
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
                            .padding(innerPadding)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                        ) {
                            val listTitle = if (userData.rol == "cuidador") "Pacientes" else "Cuidadores"

                            Text(
                                text = listTitle,
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )

                            if (fichaState is FichaState.NotFound) {
                                FichaPlaceholder(
                                    rol = userData.rol,
                                    onClick = onViewFicha
                                )
                            } else {
                                PullToRefreshBox(
                                    state = pullToRefreshState,
                                    isRefreshing = isRefreshing,
                                    onRefresh = { searchViewModel.refresh() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    when (searchState) {
                                        is SearchState.Loading -> {
                                            if (!isRefreshing) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(color = Color.White)
                                                }
                                            }
                                        }
                                        is SearchState.Success -> {
                                            val resultsList = (searchState as SearchState.Success).results
                                            val isLoadingMore = (searchState as SearchState.Success).isLoadingMore
                                            
                                            if (resultsList.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "No se encontraron resultados",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            } else {
                                                LazyColumn(
                                                    state = listState,
                                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                                    contentPadding = PaddingValues(bottom = 80.dp),
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
                                                    text = (searchState as SearchState.Error).message,
                                                    color = MaterialTheme.colorScheme.error,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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

                        if (showSearchSheet) {
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
                                    onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { showSearchSheet = false } }
                                )
                            }
                        }
                    }
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
