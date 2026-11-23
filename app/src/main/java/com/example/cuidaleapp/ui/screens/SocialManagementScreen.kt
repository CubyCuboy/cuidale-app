package com.example.cuidaleapp.ui.screens

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import com.example.cuidaleapp.viewmodel.VinculosViewModel
import com.example.cuidaleapp.utils.OcultadosRecientes
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.cuidaleapp.viewmodel.Vinculo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialManagementScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    favoritosViewModel: FavoritosViewModel = viewModel(),
    vinculosViewModel: VinculosViewModel = viewModel()
) {
    val managedUsers by favoritosViewModel.managedUsers.collectAsState()
    val favoritos by favoritosViewModel.favoritos.collectAsState()
    val guardados by favoritosViewModel.guardados.collectAsState()
    val noDeseados by favoritosViewModel.noDeseados.collectAsState()
    val bloqueados by favoritosViewModel.bloqueados.collectAsState()
    val misVinculos by vinculosViewModel.misVinculos.collectAsState()
    val vinculosUsers by vinculosViewModel.vinculosUsers.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    // Paletas por pestaña: tokens semánticos del tema (sin colores literales).
    val custom = CuidaleThemeExtras.customColors
    val themeByTab = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.errorContainer           // Bloqueados
    )
    val themeOnByTab = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onErrorContainer
    )
    val accentByTab = listOf(
        MaterialTheme.colorScheme.primary,            // Contactos
        custom.favorite,                              // Favoritos
        custom.saved,                                 // Guardados
        custom.hidden,                                // No deseados
        MaterialTheme.colorScheme.error                // Bloqueados
    )
    val vividByTab = accentByTab

    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) selectedTab = pagerState.currentPage
    }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab)
    }

    LaunchedEffect(Unit) {
        vinculosViewModel.loadVinculos()
        favoritosViewModel.loadSocialData()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_social), fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 3.dp,
                            color = accentByTab[selectedTab]
                        )
                    }
                ) {
                    SocialTab(0, stringResource(R.string.social_tab_contactos), selectedTab == 0) { scope.launch { pagerState.animateScrollToPage(0) } }
                    SocialTab(1, stringResource(R.string.social_tab_favoritos), selectedTab == 1) { scope.launch { pagerState.animateScrollToPage(1) } }
                    SocialTab(2, stringResource(R.string.social_tab_guardados), selectedTab == 2) { scope.launch { pagerState.animateScrollToPage(2) } }
                    SocialTab(3, stringResource(R.string.social_tab_no_deseados), selectedTab == 3) { scope.launch { pagerState.animateScrollToPage(3) } }
                    SocialTab(4, stringResource(R.string.social_tab_bloqueados), selectedTab == 4) { scope.launch { pagerState.animateScrollToPage(4) } }
                }
            }
        }
    ) { paddingValues ->
        val fondoGradiente = Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = if (com.example.cuidaleapp.ui.theme.CuidaleThemeExtras.customColors.isDark) 0.26f else 0.16f
                ),
                MaterialTheme.colorScheme.surface
            )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fondoGradiente)
                .padding(paddingValues)
        ) {



            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> {
                        val contactosBase = misVinculos.filter { it.estado == "aceptado" }
                        fun otherUidDe(v: Vinculo): String =
                            if (v.uid1 == vinculosViewModel.myUid) v.uid2 else v.uid1
                        val visibles = contactosBase.filter { otherUidDe(it) !in noDeseados }
                        val fantasma = contactosBase.filter {
                            otherUidDe(it) in noDeseados && otherUidDe(it) in OcultadosRecientes.uids
                        }
                        val itemsLista = visibles + fantasma
                        if (itemsLista.isEmpty()) {
                            SocialEmptyState(
                                Icons.Default.People,
                                stringResource(R.string.social_empty_contactos_title),
                                stringResource(R.string.social_empty_contactos_subtitle),
                                tint = vividByTab[page]
                            )
                        } else {
                            val contactosSaliendo = remember { mutableStateListOf<String>() }
                            val scope = rememberCoroutineScope()
                            val contactosFantasmaUids = fantasma.map { otherUidDe(it) }
                            LaunchedEffect(contactosFantasmaUids) {
                                contactosFantasmaUids.forEach { uid ->
                                    if (uid !in contactosSaliendo) contactosSaliendo.add(uid)
                                }
                                delay(400)
                                contactosFantasmaUids.forEach { uid ->
                                    OcultadosRecientes.limpiar(uid)
                                }
                            }
                            val contactosBaseUids = contactosBase.map { otherUidDe(it) }.toSet()
                            LaunchedEffect(contactosBaseUids) {
                                contactosSaliendo.removeAll { it !in contactosBaseUids }
                            }
                            val contactosListState = rememberLazyListState()
                            val visiblesUsers = visibles.mapNotNull { vinculosUsers[otherUidDe(it)] }
                            val totalContactos = visiblesUsers.size
                            val verificadosCount =
                                visiblesUsers.count { it.verificacionOficial.equals("verificado", ignoreCase = true) }
                            LazyColumn(
                                state = contactosListState,
                                modifier = Modifier.fillMaxSize().boundaryShake(contactosListState),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item(key = "resumen_social") {
                                    SocialSummaryRow(totalContactos, verificadosCount)
                                }
                                items(itemsLista, key = { it.id }) { vinculo ->
                                    val otherUid = otherUidDe(vinculo)
                                    val user = vinculosUsers[otherUid]
                                    SalidaItemWrapper(
                                        modifier = Modifier.animateItem(fadeInSpec = tween(450), placementSpec = tween(450)),
                                        uid = otherUid,
                                        saliendo = contactosSaliendo
                                    ) {
                                        ContactCard(
                                            user,
                                            onUserClick = { user?.let { onUserClick(it.uid) } },
                                            themeColor = themeByTab[page],
                                            themeOnColor = themeOnByTab[page],
                                            accentColor = accentByTab[page],
                                            vivid = vividByTab[page]
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        val users = managedUsers.filter { favoritos.contains(it.uid) }
                        SocialUserList(
                            users,
                            Icons.Default.Favorite,
                            stringResource(R.string.social_empty_favoritos_title),
                            stringResource(R.string.social_empty_favoritos_subtitle),
                            onUserClick,
                            favoritosViewModel,
                            category = "favoritos",
                            themeColor = themeByTab[page],
                            themeOnColor = themeOnByTab[page],
                            vivid = vividByTab[page]
                        )
                    }
                    2 -> {
                        val users = managedUsers.filter { guardados.contains(it.uid) }
                        SocialUserList(
                            users,
                            Icons.Default.Bookmark,
                            stringResource(R.string.social_empty_guardados_title),
                            stringResource(R.string.social_empty_guardados_subtitle),
                            onUserClick,
                            favoritosViewModel,
                            category = "guardados",
                            themeColor = themeByTab[page],
                            themeOnColor = themeOnByTab[page],
                            vivid = vividByTab[page]
                        )
                    }
                    3 -> {
                        val users = managedUsers.filter { noDeseados.contains(it.uid) }
                        SocialUserList(
                            users,
                            Icons.Default.VisibilityOff,
                            stringResource(R.string.social_empty_no_deseados_title),
                            stringResource(R.string.social_empty_no_deseados_subtitle),
                            onUserClick,
                            favoritosViewModel,
                            category = "no_deseados",
                            themeColor = themeByTab[page],
                            themeOnColor = themeOnByTab[page],
                            vivid = vividByTab[page]
                        )
                    }
                    4 -> {
                        val users = managedUsers.filter { bloqueados.contains(it.uid) }
                        SocialUserList(
                            users,
                            Icons.Default.Block,
                            stringResource(R.string.social_empty_bloqueados_title),
                            stringResource(R.string.social_empty_bloqueados_subtitle),
                            onUserClick,
                            favoritosViewModel,
                            category = "bloqueados",
                            themeColor = themeByTab[page],
                            themeOnColor = themeOnByTab[page],
                            vivid = vividByTab[page]
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialTab(index: Int, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = isSelected,
        onClick = onClick,
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            )
        }
    )
}

@Composable
private fun SocialSummaryRow(total: Int, verificados: Int) {
    val resumen = buildString {
        append(stringResource(R.string.social_count_contactos, total))
        if (verificados > 0) {
            append(" · ")
            append(stringResource(R.string.social_count_verificados, verificados))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.People,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = resumen,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SocialUserList(
    users: List<UserData>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    onUserClick: (String) -> Unit,
    viewModel: FavoritosViewModel,
    category: String,
    themeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    themeOnColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    vivid: Color = themeColor
) {
    val saliendo = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(users.map { it.uid }) {
        val presentes = users.map { it.uid }.toSet()
        saliendo.removeAll { it !in presentes }
    }

    if (users.isEmpty()) {
        SocialEmptyState(icon, emptyTitle, emptySubtitle, tint = vivid)
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().boundaryShake(listState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users, key = { it.uid }) { user ->
                fun hideThen(action: () -> Unit) {
                    if (user.uid in saliendo) return
                    saliendo.add(user.uid)
                    scope.launch { delay(360); action() }
                }

                SalidaItemWrapper(
                    modifier = Modifier.animateItem(fadeInSpec = tween(450), placementSpec = tween(450)),
                    uid = user.uid,
                    saliendo = saliendo
                ) {
                    SocialManagementCard(
                        user = user,
                        onUserClick = { onUserClick(user.uid) },
                        themeColor = themeColor,
                        themeOnColor = themeOnColor,
                        vivid = vivid,
                        actions = {
                            val favs by viewModel.favoritos.collectAsState()
                            val saved by viewModel.guardados.collectAsState()
                            val unwanted by viewModel.noDeseados.collectAsState()
                            val blocked by viewModel.bloqueados.collectAsState()

                            SocialActionButtons(
                                isFavorito = favs.contains(user.uid),
                                isGuardado = saved.contains(user.uid),
                                isNoDeseado = unwanted.contains(user.uid),
                                isBloqueado = blocked.contains(user.uid),
                                onToggleFav = {
                                    if (category == "favoritos") hideThen { viewModel.toggleFavorito(user.uid) }
                                    else viewModel.toggleFavorito(user.uid)
                                },
                                onToggleSaved = {
                                    if (category == "guardados") hideThen { viewModel.toggleGuardado(user.uid) }
                                    else viewModel.toggleGuardado(user.uid)
                                },
                                onToggleHidden = {
                                    if (category == "no_deseados") hideThen { viewModel.toggleNoDeseado(user.uid) }
                                    else viewModel.toggleNoDeseado(user.uid)
                                },
                                onToggleBlocked = {
                                    if (category == "bloqueados") hideThen { viewModel.toggleBloqueo(user.uid) }
                                    else viewModel.toggleBloqueo(user.uid)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RolePill(
    rol: String,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = rol.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun VerifiedChip() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = stringResource(R.string.mapa_verificado),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun UserInfoLine(user: UserData) {
    val location = listOf(user.comuna, user.region).firstOrNull { !it.isBlank() }
    val esCuidador = user.rol.equals("cuidador", ignoreCase = true)
    val esVerificado = user.verificacionOficial.equals("verificado", ignoreCase = true)

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (location != null) {
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (esCuidador) {
            Text(
                text = " • " + if (user.valorHoraReferencial > 0) {
                    stringResource(R.string.mapa_valor_hora, formatearMiles(user.valorHoraReferencial))
                } else {
                    stringResource(R.string.mapa_valor_a_convenir)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (esVerificado) {
            Spacer(Modifier.width(8.dp))
            VerifiedChip()
        }
    }
}

private fun formatearMiles(valor: Int): String {
    val s = valor.toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}

@Composable
fun ContactCard(
    user: UserData?,
    onUserClick: () -> Unit,
    themeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    themeOnColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    accentColor: Color = themeColor,
    vivid: Color = accentColor
) {
    val shadowCard = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.25f)
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick() }
            .padding(bottom = 4.dp, end = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(cardShape)
                .background(shadowCard)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = cardShape,
            border = BorderStroke(1.dp, vivid.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(listOf(vivid.copy(alpha = 0.10f), Color.Transparent))
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user?.fotoUrl, size = 64.dp, ringColor = vivid, placeholderTint = vivid)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user?.let { "${it.nombre} ${it.apellido_pa}" } ?: stringResource(R.string.common_loading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    if (user != null) {
                        RolePill(user.rol, container = themeColor, content = themeOnColor)
                        Spacer(Modifier.height(6.dp))
                        UserInfoLine(user)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        onClick = onUserClick,
                        modifier = Modifier.fillMaxSize(),
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.social_cd_open_profile),
                            tint = accentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialManagementCard(
    user: UserData,
    onUserClick: () -> Unit,
    actions: @Composable () -> Unit,
    themeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    themeOnColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    vivid: Color = themeColor
) {
    val shadowCard = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.25f)
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp, end = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(cardShape)
                .background(shadowCard)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = cardShape,
            border = BorderStroke(1.dp, vivid.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(listOf(vivid.copy(alpha = 0.10f), Color.Transparent))
                        )
                        .clickable { onUserClick() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(user.fotoUrl, size = 56.dp, ringColor = vivid, placeholderTint = vivid)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${user.nombre} ${user.apellido_pa}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        RolePill(user.rol, container = themeColor, content = themeOnColor)
                        Spacer(Modifier.height(4.dp))
                        UserInfoLine(user)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = themeColor)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun SocialActionButtons(
    isFavorito: Boolean,
    isGuardado: Boolean,
    isNoDeseado: Boolean,
    isBloqueado: Boolean,
    onToggleFav: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleBlocked: () -> Unit
) {
    val custom = CuidaleThemeExtras.customColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = if (isFavorito) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = if (isFavorito) stringResource(R.string.social_action_favorito) else stringResource(R.string.social_action_marcar),
            color = custom.favorite,
            active = isFavorito,
            animType = "heart",
            onClick = onToggleFav
        )
        ActionButton(
            icon = if (isGuardado) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            label = if (isGuardado) stringResource(R.string.social_action_guardado) else stringResource(R.string.social_action_guardar),
            color = MaterialTheme.colorScheme.primary,
            active = isGuardado,
            animType = "bookmark",
            onClick = onToggleSaved
        )
        ActionButton(
            icon = if (isNoDeseado) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            label = if (isNoDeseado) stringResource(R.string.social_action_mostrar) else stringResource(R.string.social_action_ocultar),
            color = custom.hidden,
            active = isNoDeseado,
            animType = "visibility",
            onClick = onToggleHidden
        )
        ActionButton(
            icon = if (isBloqueado) Icons.Default.LockOpen else Icons.Default.Block,
            label = if (isBloqueado) stringResource(R.string.social_action_desbloquear) else stringResource(R.string.social_action_bloquear),
            color = MaterialTheme.colorScheme.error,
            active = isBloqueado,
            animType = "block",
            onClick = onToggleBlocked
        )
    }
}

@Composable
fun RowScope.ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    active: Boolean = false,
    animType: String = "pop",
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val textColor = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleBase = if (active) color else MaterialTheme.colorScheme.surfaceContainerHighest
    val bubbleOn = if (bubbleBase.luminance() > 0.55f) Color(0xFF141414) else Color.White
    val bubbleBrush = Brush.radialGradient(
        listOf(bubbleBase.copy(alpha = 0.95f), bubbleBase.copy(alpha = 0.55f))
    )

    val iconScale = remember { Animatable(1f) }
    val iconRotation = remember { Animatable(0f) }
    val iconShiftX = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(1f) }

    LaunchedEffect(active) {
        if (active) {
            when (animType) {
                "heart" -> {
                    iconScale.animateTo(1.35f, tween(120))
                    iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                "bookmark" -> {
                    launch {
                        iconScale.animateTo(1.25f, tween(150))
                        iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                    launch {
                        iconRotation.animateTo(-10f, tween(150))
                        iconRotation.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                }
                "visibility" -> {
                    iconAlpha.snapTo(0.6f)
                    launch { iconAlpha.animateTo(1f, tween(200)) }
                    launch {
                        iconScale.animateTo(1.2f, tween(150))
                        iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                }
                "block" -> {
                    launch {
                        iconShiftX.animateTo(-6f, tween(60))
                        iconShiftX.animateTo(6f, tween(60))
                        iconShiftX.animateTo(-4f, tween(60))
                        iconShiftX.animateTo(4f, tween(60))
                        iconShiftX.animateTo(0f, tween(60))
                    }
                }
                else -> {
                    iconScale.animateTo(1.18f, tween(120))
                    iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
            }
        } else {
            launch { iconScale.animateTo(1f, tween(200)) }
            launch { iconRotation.animateTo(0f, tween(200)) }
            launch { iconShiftX.animateTo(0f, tween(200)) }
            launch { iconAlpha.animateTo(1f, tween(200)) }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .pressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() }
            .padding(vertical = 6.dp)
    ) {
        val hardBubbleShadow = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.22f)
        Box(
            modifier = Modifier
                .size(56.dp)
                .padding(bottom = 3.dp, end = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(CircleShape)
                    .background(if (active) color.copy(alpha = 0.30f) else hardBubbleShadow)
            )
            Surface(
                shape = CircleShape,
                color = bubbleBase,
                border = BorderStroke(
                    1.dp,
                    if (active) bubbleOn.copy(alpha = 0.30f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bubbleBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = bubbleOn,
                        modifier = Modifier.size(26.dp).graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                            rotationZ = iconRotation.value
                            translationX = iconShiftX.value
                            alpha = iconAlpha.value
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun UserAvatar(url: String?, size: androidx.compose.ui.unit.Dp, ringColor: Color? = null, placeholderTint: Color? = null) {
    val ringModifier = if (ringColor != null) {
        Modifier.border(2.dp, Brush.verticalGradient(listOf(ringColor, ringColor.copy(alpha = 0.3f))), CircleShape)
    } else {
        Modifier
    }
    val placeholderColor = placeholderTint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.size(size).then(ringModifier),
        shape = CircleShape,
        color = placeholderColor.copy(alpha = 0.16f)
    ) {
        if (!url.isNullOrEmpty() && !url.startsWith("res:")) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Default.Person,
                null,
                modifier = Modifier.padding(size / 4),
                tint = placeholderColor
            )
        }
    }
}

@Composable
fun SocialEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = tint.copy(alpha = 0.12f)
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.verticalGradient(listOf(containerColor, containerColor.copy(alpha = 0.4f))),
                    CircleShape
                ),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(64.dp), tint = tint)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
