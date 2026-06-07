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
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.SearchState
import com.example.cuidaleapp.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    userData: UserData,
    searchViewModel: SearchViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onViewProfile: () -> Unit,
    onViewFicha: () -> Unit,
    onUserClick: (String) -> Unit,
    onTabSelected: (String) -> Unit
) {
    val gradient = CuidaleThemeExtras.gradient
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val searchState by searchViewModel.state.collectAsState()
    val isRefreshing by searchViewModel.isRefreshing.collectAsState()
    val selectedRegion by searchViewModel.selectedRegion.collectAsState()
    val selectedCommunes by searchViewModel.selectedCommunes.collectAsState()
    
    val pullToRefreshState = rememberPullToRefreshState()
    val sheetState = rememberModalBottomSheetState()
    var showSearchSheet by remember { mutableStateOf(false) }
    
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
                        modifier = Modifier.fillMaxSize(),
                        drawerContainerColor = Color.Transparent,
                        drawerTonalElevation = 0.dp
                    ) {
                        CuidaleDrawerContent(
                            nombre = userData.nombre,
                            apellido = userData.apellido_pa,
                            fotoUrl = userData.fotoUrl,
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
                        CuidaleBottomBar(selectedTab = "home", onTabSelected = onTabSelected)
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
                                                        fotoUrl = result.foto_url,
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
