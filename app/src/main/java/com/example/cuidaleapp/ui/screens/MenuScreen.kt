package com.example.cuidaleapp.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.utils.ConnectivityObserver
import com.example.cuidaleapp.utils.ErrorMessages
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.SearchState
import com.example.cuidaleapp.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    userData: UserData,
    searchViewModel: SearchViewModel,
    fichaState: FichaState,
    onViewFicha: () -> Unit,
    onUserClick: (String) -> Unit,
    onMapClick: () -> Unit = {}
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

    LaunchedEffect(userData.rol, userData.region, isOnline) {
        if (userData.rol.isNotEmpty() && isOnline) {
            searchViewModel.initSearch(userData.rol, userData.region)
        }
    }

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 10 }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {


        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {

            val listTitle = if (userData.rol == "cuidador") stringResource(R.string.menu_title_patients) else stringResource(R.string.menu_title_caregivers)

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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.connectivity_offline),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.menu_connect_discover, listTitle.lowercase()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        HardShadowButton(
                            text = stringResource(R.string.action_retry),
                            onClick = { searchViewModel.refresh() },
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White,
                            shadowColor = Color.Transparent
                        )
                    }
                }
            } else {
                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = { if (isOnline) searchViewModel.refresh() },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = searchState,
                            transitionSpec = {
                                (fadeIn(tween(500)) + scaleIn(initialScale = 0.96f, animationSpec = tween(500)))
                                    .togetherWith(fadeOut(tween(400)))
                            },
                            label = "searchContentTransition",
                            modifier = Modifier.fillMaxSize()
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
                                    val isLastPage = state.isLastPage

                                    if (resultsList.isEmpty()) {
                                        EmptySearchResults(
                                            title = if (selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty())
                                                stringResource(R.string.search_no_results_filters)
                                            else stringResource(R.string.menu_no_available, listTitle),
                                            subtitle = stringResource(R.string.search_try_changing_location),
                                            onClearFilters = { searchViewModel.clearFilters() },
                                            showClearButton = selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty()
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyColumn(
                                                state = listState,
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                                contentPadding = PaddingValues(bottom = 120.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(resultsList, key = { it.id }) { result ->
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
                                                    items(3) {
                                                        SkeletonUserCard(
                                                            modifier = Modifier.padding(vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                if (!isLastPage && !isLoadingMore && resultsList.size >= 50) {
                                                    item {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 24.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            HardShadowButton(
                                                                text = stringResource(R.string.menu_load_more, listTitle.uppercase()),
                                                                onClick = { searchViewModel.loadMore() },
                                                                containerColor = Color.White.copy(alpha = 0.15f),
                                                                contentColor = Color.White,
                                                                shadowColor = Color.Transparent,
                                                                icon = Icons.Default.Add
                                                            )
                                                        }
                                                    }
                                                }

                                                if (isLastPage && resultsList.isNotEmpty()) {
                                                    item {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(32.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Icon(
                                                                Icons.Default.CheckCircle,
                                                                null,
                                                                tint = Color.White.copy(alpha = 0.3f),
                                                                modifier = Modifier.size(40.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(16.dp))
                                                            Text(
                                                                stringResource(R.string.menu_no_more, listTitle.lowercase()),
                                                                color = Color.White.copy(alpha = 0.6f),
                                                                textAlign = TextAlign.Center,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                            Text(
                                                                stringResource(R.string.menu_try_other_config),
                                                                color = Color.White.copy(alpha = 0.4f),
                                                                textAlign = TextAlign.Center,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            CuidaleVerticalScrollbar(
                                                lazyListState = listState,
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(end = 4.dp, top = 16.dp, bottom = 120.dp)
                                            )
                                        }
                                    }
                                }
                                is SearchState.Error -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = ErrorMessages.get(context, state.message),
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
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        CuidaleCircularMenuButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            icon = {
                                Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White)
                            }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (userData.rol != "cuidador") {
                        CuidaleCircularMenuButton(
                            onClick = onMapClick,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = stringResource(R.string.cd_map_caregivers),
                                    tint = Color.White
                                )
                            }
                        )
                    }

                    CuidaleCircularMenuButton(
                        onClick = { if (isOnline) showSearchSheet = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_filters),
                                tint = Color.White
                            )
                        }
                    )
                }
            }
        }

        if (showFichaReminder) {
            AlertDialog(
                onDismissRequest = { showFichaReminder = false },
                icon = { Icon(Icons.Default.Warning, null, tint = Color.Yellow) },
                title = { Text(stringResource(R.string.menu_ficha_incomplete_title)) },
                text = {
                    Text(
                        if (userData.rol == "cuidador")
                            stringResource(R.string.menu_ficha_incomplete_caregiver)
                        else
                            stringResource(R.string.menu_ficha_incomplete_rep)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showFichaReminder = false
                        onViewFicha()
                    }) {
                        Text(stringResource(R.string.menu_create_ficha_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFichaReminder = false }) {
                        Text(stringResource(R.string.menu_later))
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
fun CuidaleVerticalScrollbar(
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val isScrolling = lazyListState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 0 else 500),
        label = "scrollbarAlpha"
    )

    if (alpha > 0f) {
        val layoutInfo = lazyListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return

        val totalItems = layoutInfo.totalItemsCount
        if (totalItems <= 0) return

        val firstItem = visibleItems.firstOrNull() ?: return
        
        val scrollbarHeightFraction = (visibleItems.size.toFloat() / totalItems).coerceIn(0.1f, 1f)
        val scrollPositionFraction = (firstItem.index.toFloat() / totalItems).coerceIn(0f, 1f)

        if (scrollbarHeightFraction.isNaN() || scrollPositionFraction.isNaN()) return

        BoxWithConstraints(modifier = modifier.width(4.dp).fillMaxHeight().alpha(alpha)) {
            val trackHeight = this.maxHeight
            val thumbHeight = trackHeight * scrollbarHeightFraction
            val thumbOffset = trackHeight * scrollPositionFraction

            Box(
                modifier = Modifier
                    .offset(y = thumbOffset)
                    .height(thumbHeight)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f))
            )
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.White.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            if (showClearButton) {
                Spacer(modifier = Modifier.height(32.dp))
                HardShadowButton(
                    text = stringResource(R.string.menu_clear_filters),
                    onClick = onClearFilters,
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White,
                    shadowColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
fun FichaPlaceholder(
    rol: String,
    onClick: () -> Unit
) {
    val accentColor = if (rol == "cuidador") MaterialTheme.colorScheme.primary else CuidaleThemeExtras.customColors.successStrong
    
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.15f),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (rol == "cuidador") Icons.Default.Description else Icons.Default.FamilyRestroom,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = accentColor.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (rol == "cuidador") 
                    stringResource(R.string.menu_ficha_placeholder_title_caregiver)
                else 
                    stringResource(R.string.menu_ficha_placeholder_title_rep),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (rol == "cuidador")
                    stringResource(R.string.menu_ficha_placeholder_desc_caregiver)
                else
                    stringResource(R.string.menu_ficha_placeholder_desc_rep),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            HardShadowButton(
                text = stringResource(R.string.menu_create_ficha_technical),
                onClick = onClick,
                containerColor = accentColor,
                contentColor = Color.White,
                shadowColor = accentColor.copy(alpha = 0.3f),
                icon = Icons.Default.Add
            )
        }
    }
}
