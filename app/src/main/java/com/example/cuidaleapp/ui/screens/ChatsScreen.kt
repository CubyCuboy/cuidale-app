package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.components.CuidaleBottomBar
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.CuidaleCircularMenuButton
import com.example.cuidaleapp.ui.components.CuidaleDrawerContent
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.viewmodel.ChatViewModel
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import com.example.cuidaleapp.viewmodel.Vinculo
import com.example.cuidaleapp.viewmodel.VinculosViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    userData: UserData,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onViewProfile: () -> Unit,
    onViewFicha: () -> Unit,
    onViewSettings: () -> Unit,
    onTabSelected: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onChatClick: (String, UserData) -> Unit,
    favoritosViewModel: FavoritosViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    vinculosViewModel: VinculosViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val gradient = CuidaleThemeExtras.gradient
    val managedUsers by favoritosViewModel.managedUsers.collectAsState()
    val favoritos by favoritosViewModel.favoritos.collectAsState()
    val noDeseados by favoritosViewModel.noDeseados.collectAsState()
    val chatList by chatViewModel.chatList.collectAsState()
    val misVinculos by vinculosViewModel.misVinculos.collectAsState()
    val vinculosUsers by vinculosViewModel.vinculosUsers.collectAsState()
    val customColors = CuidaleThemeExtras.customColors

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        chatViewModel.listenChats()
        vinculosViewModel.loadVinculos()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
            }
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
                    bottomBar = {
                        CuidaleBottomBar(selectedTab = "chats", onTabSelected = onTabSelected)
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(
                                if (isDark) 
                                    listOf(customColors.panelBackground, Color(0xFF0D1B2A))
                                else 
                                    listOf(customColors.aqua.copy(alpha = 0.8f), customColors.mint.copy(alpha = 0.6f))
                            ))
                            .padding(innerPadding)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Mensajes",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = if (isDark) Color.White else customColors.successStrong,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
                            )

                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color.Transparent,
                                contentColor = if (isDark) customColors.lime else customColors.successStrong,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                        color = if (isDark) customColors.lime else customColors.successStrong
                                    )
                                },
                                divider = {}
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("CHATS", style = MaterialTheme.typography.labelLarge) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { 
                                        val pendingCount = misVinculos.count { it.estado == "pendiente" && it.solicitante != userData.uid }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("SOLICITUDES", style = MaterialTheme.typography.labelLarge)
                                            if (pendingCount > 0) {
                                                Spacer(Modifier.width(4.dp))
                                                Badge { Text(pendingCount.toString()) }
                                            }
                                        }
                                    }
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    text = { Text("NO DESEADOS", style = MaterialTheme.typography.labelLarge) }
                                )
                            }

                            when (selectedTab) {
                                0 -> ChatListSection(
                                    chatList = chatList,
                                    managedUsers = managedUsers,
                                    favoritos = favoritos,
                                    isDark = isDark,
                                    onChatClick = onChatClick,
                                    onUserClick = onUserClick
                                )
                                1 -> RequestListSection(
                                    vinculos = misVinculos,
                                    vinculosUsers = vinculosUsers,
                                    myUid = userData.uid,
                                    isDark = isDark,
                                    onAccept = { vinculosViewModel.aceptarSolicitud(it) },
                                    onDecline = { vinculosViewModel.rechazarSolicitud(it) }
                                )
                                2 -> UnwantedListSection(
                                    noDeseados = noDeseados,
                                    managedUsers = managedUsers,
                                    isDark = isDark,
                                    onRemove = { favoritosViewModel.toggleNoDeseado(it) }
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
fun ChatListSection(
    chatList: List<ChatViewModel.ChatPreview>,
    managedUsers: List<UserData>,
    favoritos: List<String>,
    isDark: Boolean,
    onChatClick: (String, UserData) -> Unit,
    onUserClick: (String) -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val savedUsers = managedUsers.filter { favoritos.contains(it.uid) }
    
    // Separamos los chats: favoritos primero, luego el resto
    val sortedChats = chatList.sortedWith(compareByDescending<ChatViewModel.ChatPreview> { 
        favoritos.contains(it.otherUser.uid) 
    }.thenByDescending { 
        it.lastMessage?.timestamp ?: 0L 
    })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sortedChats.isNotEmpty()) {
            item {
                Text(
                    text = "CHATS RECIENTES",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = if (isDark) customColors.lime.copy(alpha = 0.8f) else customColors.successStrong.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(sortedChats) { chat ->
                val isFav = favoritos.contains(chat.otherUser.uid)
                RecentChatRow(
                    chat = chat, 
                    isDark = isDark, 
                    isFavorite = isFav,
                    onClick = { onChatClick(chat.chatId, chat.otherUser) }
                )
            }
        }

        if (savedUsers.isNotEmpty()) {
            item {
                Text(
                    text = "USUARIOS GUARDADOS",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = if (isDark) customColors.lime.copy(alpha = 0.8f) else customColors.successStrong.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            items(savedUsers) { user ->
                val itemAccent = if (user.rol == "cuidador") customColors.aqua else customColors.mint
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = itemAccent.copy(alpha = 0.2f),
                    modifier = Modifier.clickable { onUserClick(user.uid) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                .background(itemAccent.copy(alpha = 0.1f))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                color = Color.White
                            ) {
                                if (user.fotoUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = user.fotoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null, tint = itemAccent.copy(alpha = 0.5f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${user.nombre} ${user.apellido_pa}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (user.rol == "cuidador") "Cuidador disponible" else "Familiar/Paciente",
                                style = MaterialTheme.typography.bodySmall,
                                color = itemAccent.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight, 
                            null, 
                            tint = if (isDark) customColors.lime else itemAccent
                        )
                    }
                }
            }
        }

        if (chatList.isEmpty() && savedUsers.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No tienes chats activos ni guardados",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RequestListSection(
    vinculos: List<Vinculo>,
    vinculosUsers: Map<String, UserData>,
    myUid: String,
    isDark: Boolean,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val requests = vinculos.filter { it.estado == "pendiente" && it.solicitante != myUid }
    val myPending = vinculos.filter { it.estado == "pendiente" && it.solicitante == myUid }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (requests.isNotEmpty()) {
            item {
                Text(
                    "POR RESPONDER",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) customColors.lime else customColors.successStrong,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(requests) { vinculo ->
                val otherUid = if (vinculo.uid1 == myUid) vinculo.uid2 else vinculo.uid1
                val user = vinculosUsers[otherUid]
                
                HardShadowPanel(
                    backgroundColor = if (isDark) customColors.panelBackground else Color.White,
                    shadowColor = customColors.aqua.copy(alpha = 0.3f)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f))) {
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
                        IconButton(onClick = { onAccept(vinculo.id) }) {
                            Icon(Icons.Default.Check, "Aceptar", tint = customColors.successStrong)
                        }
                        IconButton(onClick = { onDecline(vinculo.id) }) {
                            Icon(Icons.Default.Close, "Rechazar", tint = Color.Red)
                        }
                    }
                }
            }
        }

        if (myPending.isNotEmpty()) {
            item {
                Text(
                    "ENVIADAS (PENDIENTES)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            items(myPending) { vinculo ->
                val otherUid = if (vinculo.uid1 == myUid) vinculo.uid2 else vinculo.uid1
                val user = vinculosUsers[otherUid]
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            user?.let { "${it.nombre} ${it.apellido_pa}" } ?: "Cargando...",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("Esperando respuesta...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }

        if (requests.isEmpty() && myPending.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                    Text("No tienes solicitudes pendientes", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun UnwantedListSection(
    noDeseados: List<String>,
    managedUsers: List<UserData>,
    isDark: Boolean,
    onRemove: (String) -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val unwantedUsers = managedUsers.filter { noDeseados.contains(it.uid) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(unwantedUsers) { user ->
            HardShadowPanel(
                backgroundColor = if (isDark) customColors.panelBackground else Color.White,
                shadowColor = Color.Red.copy(alpha = 0.1f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${user.nombre} ${user.apellido_pa}",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    TextButton(onClick = { onRemove(user.uid) }) {
                        Text("RESTAURAR", color = customColors.aqua)
                    }
                }
            }
        }

        if (unwantedUsers.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                    Text("No tienes usuarios bloqueados", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

private fun formatMessageDate(timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val msgDate = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(java.util.Calendar.YEAR) == msgDate.get(java.util.Calendar.YEAR) &&
                now.get(java.util.Calendar.DAY_OF_YEAR) == msgDate.get(java.util.Calendar.DAY_OF_YEAR) -> {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
        else -> {
            java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    }
}

@Composable
fun RecentChatRow(chat: ChatViewModel.ChatPreview, isDark: Boolean, isFavorite: Boolean = false, onClick: () -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    val itemAccent = if (chat.otherUser.rol == "cuidador") customColors.aqua else customColors.mint
    
    HardShadowPanel(
        backgroundColor = if (isDark) customColors.panelBackground else Color.White,
        shadowColor = if (isFavorite) Color.Yellow.copy(alpha = 0.3f) else itemAccent.copy(alpha = 0.3f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(itemAccent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (chat.otherUser.fotoUrl.isNotEmpty()) {
                    AsyncImage(model = chat.otherUser.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, tint = itemAccent)
                }
                
                if (isFavorite) {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint = Color.Yellow,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${chat.otherUser.nombre} ${chat.otherUser.apellido_pa}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        color = if (isFavorite) (if (isDark) customColors.lime else customColors.successStrong) else MaterialTheme.colorScheme.onSurface
                    )
                    if (chat.lastMessage != null) {
                        Text(
                            text = formatMessageDate(chat.lastMessage.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val msgText = if (chat.lastMessage?.isDeleted == true) "Mensaje borrado" else chat.lastMessage?.text ?: "Inicia una conversación"
                    Text(
                        text = msgText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chat.unreadCount > 0) (if (isDark) Color.White else Color.Black) else Color.Gray,
                        fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.unreadCount > 0) {
                        Box(
                            modifier = Modifier.size(20.dp).background(customColors.lime, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(chat.unreadCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
