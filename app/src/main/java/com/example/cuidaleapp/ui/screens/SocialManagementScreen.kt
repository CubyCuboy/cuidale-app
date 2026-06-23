package com.example.cuidaleapp.ui.screens

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import com.example.cuidaleapp.viewmodel.VinculosViewModel

import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.utils.ConnectivityObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialManagementScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    favoritosViewModel: FavoritosViewModel = viewModel(),
    vinculosViewModel: VinculosViewModel = viewModel()
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val managedUsers by favoritosViewModel.managedUsers.collectAsState()
    val favoritos by favoritosViewModel.favoritos.collectAsState()
    val guardados by favoritosViewModel.guardados.collectAsState()
    val noDeseados by favoritosViewModel.noDeseados.collectAsState()
    val bloqueados by favoritosViewModel.bloqueados.collectAsState()
    val misVinculos by vinculosViewModel.misVinculos.collectAsState()
    val vinculosUsers by vinculosViewModel.vinculosUsers.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vinculosViewModel.loadVinculos()
        favoritosViewModel.loadSocialData()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                ConnectivityBanner()
                TopAppBar(
                    title = { Text("Gestión de Contactos", fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("FAVORITOS") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("GUARDADOS") })
                    Tab(
                        selected = selectedTab == 2, 
                        onClick = { selectedTab = 2 }, 
                        text = { 
                            val pending = misVinculos.count { it.estado == "pendiente" && it.solicitante != vinculosViewModel.myUid }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SOLICITUDES")
                                if (pending > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Badge { Text(pending.toString()) }
                                }
                            }
                        }
                    )
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("NO DESEADOS") })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("BLOQUEADOS") })
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    val users = managedUsers.filter { favoritos.contains(it.uid) }
                    SocialList(users, "No tienes usuarios favoritos.", onUserClick, favoritosViewModel)
                }
                1 -> {
                    val users = managedUsers.filter { guardados.contains(it.uid) }
                    SocialList(users, "No tienes usuarios guardados.", onUserClick, favoritosViewModel)
                }
                2 -> {
                    val requests = misVinculos.filter { it.estado == "pendiente" && it.solicitante != vinculosViewModel.myUid }
                    if (requests.isEmpty()) {
                        EmptyState("No tienes solicitudes pendientes.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(requests) { vinculo ->
                                val otherUid = if (vinculo.uid1 == vinculosViewModel.myUid) vinculo.uid2 else vinculo.uid1
                                val user = vinculosUsers[otherUid]
                                RequestItem(user, onAccept = { vinculosViewModel.aceptarSolicitud(vinculo.id) }, onDecline = { vinculosViewModel.rechazarSolicitud(vinculo.id) }, onUserClick = { user?.let { onUserClick(it.uid) } })
                            }
                        }
                    }
                }
                3 -> {
                    val users = managedUsers.filter { noDeseados.contains(it.uid) }
                    SocialList(users, "No tienes usuarios en no deseados.", onUserClick, favoritosViewModel)
                }
                4 -> {
                    val users = managedUsers.filter { bloqueados.contains(it.uid) }
                    SocialList(users, "No tienes usuarios bloqueados.", onUserClick, favoritosViewModel)
                }
            }
        }
    }
}

@Composable
fun SocialList(users: List<UserData>, emptyMsg: String, onUserClick: (String) -> Unit, viewModel: FavoritosViewModel) {
    if (users.isEmpty()) {
        EmptyState(emptyMsg)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users) { user ->
                val favs by viewModel.favoritos.collectAsState()
                val saved by viewModel.guardados.collectAsState()
                val unwanted by viewModel.noDeseados.collectAsState()
                val blocked by viewModel.bloqueados.collectAsState()

                SocialUserItem(
                    user = user,
                    isFavorito = favs.contains(user.uid),
                    isGuardado = saved.contains(user.uid),
                    isNoDeseado = unwanted.contains(user.uid),
                    isBloqueado = blocked.contains(user.uid),
                    onUserClick = { onUserClick(user.uid) },
                    onToggleFav = { viewModel.toggleFavorito(user.uid) },
                    onToggleSaved = { viewModel.toggleGuardado(user.uid) },
                    onToggleHidden = { viewModel.toggleNoDeseado(user.uid) },
                    onToggleBlocked = { viewModel.toggleBloqueo(user.uid) }
                )
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RequestItem(user: UserData?, onAccept: () -> Unit, onDecline: () -> Unit, onUserClick: () -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        modifier = Modifier.clickable { onUserClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f))) {
                if (user?.fotoUrl?.isNotEmpty() == true) {
                    AsyncImage(model = user.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user?.let { "${it.nombre} ${it.apellido_pa}" } ?: "Cargando...", fontWeight = FontWeight.Bold)
                Text("Quiere conectar contigo", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, "Aceptar", tint = Color(0xFF4CAF50))
            }
            IconButton(onClick = onDecline) {
                Icon(Icons.Default.Close, "Rechazar", tint = Color.Red)
            }
        }
    }
}

@Composable
fun SocialUserItem(
    user: UserData,
    isFavorito: Boolean,
    isGuardado: Boolean,
    isNoDeseado: Boolean,
    isBloqueado: Boolean,
    onUserClick: () -> Unit,
    onToggleFav: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleBlocked: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    
    HardShadowPanel(
        modifier = Modifier.clickable(onClick = onUserClick),
        backgroundColor = if (isBloqueado) customColors.panelBackground.copy(alpha = 0.5f) else customColors.panelBackground
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(customColors.avatarBackground)
            ) {
                if (user.fotoUrl.isNotEmpty()) {
                    AsyncImage(model = user.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
                }
                if (isBloqueado) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Block, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${user.nombre} ${user.apellido_pa}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isBloqueado) Color.Gray else Color.Unspecified
                    )
                    if (isFavorito) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = if (isBloqueado) "USUARIO BLOQUEADO" else user.rol.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBloqueado) Color.Red else MaterialTheme.colorScheme.primary
                )
            }

            Row {
                IconButton(onClick = onToggleFav) {
                    Icon(
                        imageVector = if (isFavorito) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorito) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        imageVector = if (isGuardado) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Guardado",
                        tint = if (isGuardado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }

                IconButton(onClick = onToggleBlocked) {
                    Icon(
                        imageVector = if (isBloqueado) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = "Bloquear",
                        tint = if (isBloqueado) Color(0xFF4CAF50) else Color.Red.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(onClick = onToggleHidden) {
                    Icon(
                        imageVector = if (isNoDeseado) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "No deseado",
                        tint = if (isNoDeseado) Color.Blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
