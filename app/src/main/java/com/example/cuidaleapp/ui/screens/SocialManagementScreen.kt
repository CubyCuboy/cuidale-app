package com.example.cuidaleapp.ui.screens

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import com.example.cuidaleapp.viewmodel.VinculosViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver

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
    val customColors = CuidaleThemeExtras.customColors

    LaunchedEffect(Unit) {
        vinculosViewModel.loadVinculos()
        favoritosViewModel.loadSocialData()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(modifier = Modifier.background(customColors.panelBackground)) {
                TopAppBar(
                    title = { Text("Red de Contactos", fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    SocialTab(0, "CONTACTOS", selectedTab == 0) { selectedTab = 0 }
                    SocialTab(1, "FAVORITOS", selectedTab == 1) { selectedTab = 1 }
                    SocialTab(2, "GUARDADOS", selectedTab == 2) { selectedTab = 2 }
                    SocialTab(3, "NO DESEADOS", selectedTab == 3) { selectedTab = 3 }
                    SocialTab(4, "BLOQUEADOS", selectedTab == 4) { selectedTab = 4 }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {



            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "socialTabContent"
            ) { targetTab ->
                when (targetTab) {
                    0 -> {
                        val acceptedVinculos = misVinculos.filter { it.estado == "aceptado" }
                        if (acceptedVinculos.isEmpty()) {
                            SocialEmptyState(Icons.Default.People, "Aún no tienes contactos", "Conecta con cuidadores o familias para que aparezcan aquí.")
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(acceptedVinculos) { vinculo ->
                                    val otherUid = if (vinculo.uid1 == vinculosViewModel.myUid) vinculo.uid2 else vinculo.uid1
                                    val user = vinculosUsers[otherUid]
                                    ContactCard(user, onUserClick = { user?.let { onUserClick(it.uid) } })
                                }
                            }
                        }
                    }
                    1 -> {
                        val users = managedUsers.filter { favoritos.contains(it.uid) }
                        SocialUserList(users, Icons.Default.Star, "Sin favoritos", "Marca perfiles con una estrella para tenerlos siempre a mano.", onUserClick, favoritosViewModel)
                    }
                    2 -> {
                        val users = managedUsers.filter { guardados.contains(it.uid) }
                        SocialUserList(users, Icons.Default.Bookmark, "Nada guardado", "Guarda perfiles que te interesen para revisarlos después.", onUserClick, favoritosViewModel)
                    }
                    3 -> {
                        val users = managedUsers.filter { noDeseados.contains(it.uid) }
                        SocialUserList(users, Icons.Default.VisibilityOff, "Lista limpia", "Aquí aparecerán los perfiles que decidiste no volver a ver.", onUserClick, favoritosViewModel)
                    }
                    4 -> {
                        val users = managedUsers.filter { bloqueados.contains(it.uid) }
                        SocialUserList(users, Icons.Default.Block, "Sin bloqueos", "Usuarios bloqueados no podrán contactarte ni ver tu perfil.", onUserClick, favoritosViewModel)
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
fun SocialUserList(
    users: List<UserData>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    onUserClick: (String) -> Unit,
    viewModel: FavoritosViewModel
) {
    if (users.isEmpty()) {
        SocialEmptyState(icon, emptyTitle, emptySubtitle)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users, key = { it.uid }) { user ->
                Box(modifier = Modifier.animateItem(
                    fadeInSpec = tween(500),
                    placementSpec = tween(500),
                    fadeOutSpec = tween(500)
                )) {
                    SocialManagementCard(
                        user = user,
                        onUserClick = { onUserClick(user.uid) },
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
                                onToggleFav = { viewModel.toggleFavorito(user.uid) },
                                onToggleSaved = { viewModel.toggleGuardado(user.uid) },
                                onToggleHidden = { viewModel.toggleNoDeseado(user.uid) },
                                onToggleBlocked = { viewModel.toggleBloqueo(user.uid) }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactCard(user: UserData?, onUserClick: () -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        modifier = Modifier.clickable { onUserClick() },
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(user?.fotoUrl, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user?.let { "${it.nombre} ${it.apellido_pa}" } ?: "Cargando...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    user?.rol?.uppercase() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
            }
            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SocialManagementCard(
    user: UserData,
    onUserClick: () -> Unit,
    actions: @Composable () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { onUserClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user.fotoUrl, size = 50.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${user.nombre} ${user.apellido_pa}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.rol.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray.copy(alpha = 0.5f))
            }
            
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f))
            
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                actions()
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = if (isFavorito) Icons.Default.Star else Icons.Default.StarBorder,
            label = if (isFavorito) "Favorito" else "Marcar",
            color = if (isFavorito) Color(0xFFFFD700) else Color.Gray,
            onClick = onToggleFav
        )
        ActionButton(
            icon = if (isGuardado) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            label = if (isGuardado) "Guardado" else "Guardar",
            color = if (isGuardado) MaterialTheme.colorScheme.primary else Color.Gray,
            onClick = onToggleSaved
        )
        ActionButton(
            icon = if (isNoDeseado) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            label = if (isNoDeseado) "Mostrar" else "Ocultar",
            color = if (isNoDeseado) Color(0xFF2196F3) else Color.Gray,
            onClick = onToggleHidden
        )
        ActionButton(
            icon = if (isBloqueado) Icons.Default.LockOpen else Icons.Default.Block,
            label = if (isBloqueado) "Desbloquear" else "Bloquear",
            color = if (isBloqueado) Color(0xFF4CAF50) else Color.Red.copy(alpha = 0.7f),
            onClick = onToggleBlocked
        )
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UserAvatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = CuidaleThemeExtras.customColors.avatarBackground
    ) {
        if (!url.isNullOrEmpty() && !url.startsWith("res:")) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Person, null, modifier = Modifier.padding(size/4), tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun SocialEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.7f))
    }
}
