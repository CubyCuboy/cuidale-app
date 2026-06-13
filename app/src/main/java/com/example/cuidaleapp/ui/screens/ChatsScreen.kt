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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.draw.alpha
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
    unreadCount: Int = 0,
    pendingNotifs: Int = 0,
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
    val bloqueados by favoritosViewModel.bloqueados.collectAsState()
    val chatList by chatViewModel.chatList.collectAsState()
    val typingUsers by chatViewModel.typingUsers.collectAsState()
    val misVinculos by vinculosViewModel.misVinculos.collectAsState()
    val vinculosUsers by vinculosViewModel.vinculosUsers.collectAsState()
    val customColors = CuidaleThemeExtras.customColors

    var selectedTab by remember { mutableIntStateOf(0) }
    var chatSearchQuery by remember { mutableStateOf("") }
    
    var chatToMenu by remember { mutableStateOf<ChatViewModel.ChatPreview?>(null) }
    var showChatMenu by remember { mutableStateOf(false) }

    val filteredChatList = remember(chatList, chatSearchQuery) {
        if (chatSearchQuery.isBlank()) chatList
        else chatList.filter { 
            it.otherUser.nombre.contains(chatSearchQuery, ignoreCase = true) ||
            it.otherUser.apellido_pa.contains(chatSearchQuery, ignoreCase = true)
        }
    }

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
                        CuidaleBottomBar(
                            selectedTab = "chats", 
                            unreadCount = unreadCount, 
                            pendingNotifs = pendingNotifs,
                            onTabSelected = onTabSelected
                        )
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
                                    if (selectedTab < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = if (isDark) customColors.lime else customColors.successStrong
                                        )
                                    }
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
                                Tab(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    text = { Text("BLOQUEADOS", style = MaterialTheme.typography.labelLarge) }
                                )
                            }

                            if (selectedTab == 0 && chatList.isNotEmpty()) {
                                OutlinedTextField(
                                    value = chatSearchQuery,
                                    onValueChange = { chatSearchQuery = it },
                                    placeholder = { Text("Buscar conversación...", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = CircleShape,
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                                    trailingIcon = {
                                        if (chatSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { chatSearchQuery = "" }) {
                                                Icon(Icons.Default.Close, null, tint = Color.Gray)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.5f),
                                        unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.5f),
                                        focusedBorderColor = customColors.aqua,
                                        unfocusedBorderColor = Color.Transparent
                                    )
                                )
                            }

                            when (selectedTab) {
                                0 -> ChatListSection(
                                    chatList = filteredChatList,
                                    typingUsers = typingUsers,
                                    managedUsers = managedUsers,
                                    favoritos = favoritos,
                                    bloqueados = bloqueados,
                                    isDark = isDark,
                                    onChatClick = onChatClick,
                                    onUserClick = onUserClick,
                                    onLongClick = { 
                                        chatToMenu = it
                                        showChatMenu = true
                                    }
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
                                3 -> BlockedListSection(
                                    bloqueados = bloqueados,
                                    managedUsers = managedUsers,
                                    isDark = isDark,
                                    onRemove = { favoritosViewModel.toggleBloqueo(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChatMenu && chatToMenu != null) {
        val isFav = favoritos.contains(chatToMenu!!.otherUser.uid)
        val isBlocked = bloqueados.contains(chatToMenu!!.otherUser.uid)
        
        AlertDialog(
            onDismissRequest = { showChatMenu = false },
            confirmButton = {},
            title = { Text(chatToMenu!!.otherUser.nombre + " " + chatToMenu!!.otherUser.apellido_pa) },
            text = {
                Column {
                    TextButton(onClick = {
                        chatViewModel.markChatAsRead(chatToMenu!!.chatId)
                        showChatMenu = false
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DoneAll, null)
                            Spacer(Modifier.width(12.dp))
                            Text("Marcar como leído")
                        }
                    }
                    TextButton(onClick = {
                        favoritosViewModel.toggleFavorito(chatToMenu!!.otherUser.uid)
                        showChatMenu = false
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isFav) Icons.Default.StarOutline else Icons.Default.Star, null, tint = if (isFav) Color.Unspecified else Color.Yellow)
                            Spacer(Modifier.width(12.dp))
                            Text(if (isFav) "Quitar de favoritos" else "Añadir a favoritos")
                        }
                    }
                    TextButton(onClick = {
                        showChatMenu = false
                        onUserClick(chatToMenu!!.otherUser.uid)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null)
                            Spacer(Modifier.width(12.dp))
                            Text("Ver perfil")
                        }
                    }
                    TextButton(onClick = {
                        favoritosViewModel.toggleBloqueo(chatToMenu!!.otherUser.uid)
                        showChatMenu = false
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Block, null, tint = if (isBlocked) Color.Green else Color.Red)
                            Spacer(Modifier.width(12.dp))
                            Text(if (isBlocked) "Desbloquear usuario" else "Bloquear usuario", color = if (isBlocked) Color.Unspecified else Color.Red)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ChatListSection(
    chatList: List<ChatViewModel.ChatPreview>,
    typingUsers: Map<String, Boolean> = emptyMap(),
    managedUsers: List<UserData>,
    favoritos: List<String>,
    bloqueados: List<String>,
    isDark: Boolean,
    onChatClick: (String, UserData) -> Unit,
    onUserClick: (String) -> Unit,
    onLongClick: (ChatViewModel.ChatPreview) -> Unit = {}
) {
    val customColors = CuidaleThemeExtras.customColors
    val savedUsers = managedUsers.filter { favoritos.contains(it.uid) }
    
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
                val isBlocked = bloqueados.contains(chat.otherUser.uid)
                val isTyping = typingUsers[chat.chatId] ?: false

                RecentChatRow(
                    chat = chat, 
                    isDark = isDark, 
                    isFavorite = isFav,
                    isBlocked = isBlocked,
                    isTyping = isTyping,
                    onClick = { onChatClick(chat.chatId, chat.otherUser) },
                    onLongClick = { onLongClick(chat) }
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
                Box(
                    modifier = Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = if (isDark) Color.White.copy(alpha = 0.2f) else customColors.aqua.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Comienza a conectar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray
                        )
                        Text(
                            "Tus conversaciones aparecerán aquí",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.7f)
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

@Composable
fun BlockedListSection(
    bloqueados: List<String>,
    managedUsers: List<UserData>,
    isDark: Boolean,
    onRemove: (String) -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val blockedUsers = managedUsers.filter { bloqueados.contains(it.uid) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(blockedUsers) { user ->
            HardShadowPanel(
                backgroundColor = if (isDark) customColors.panelBackground else Color.White,
                shadowColor = Color.Red.copy(alpha = 0.2f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${user.nombre} ${user.apellido_pa}",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    TextButton(onClick = { onRemove(user.uid) }) {
                        Text("DESBLOQUEAR", color = Color.Red)
                    }
                }
            }
        }

        if (blockedUsers.isEmpty()) {
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
fun RecentChatRow(
    chat: ChatViewModel.ChatPreview, 
    isDark: Boolean, 
    isFavorite: Boolean = false, 
    isBlocked: Boolean = false, 
    isTyping: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val customColors = CuidaleThemeExtras.customColors
    val itemAccent = if (chat.otherUser.rol == "cuidador") customColors.aqua else customColors.mint
    
    HardShadowPanel(
        backgroundColor = if (isBlocked) (if (isDark) Color(0xFF2B2D31) else Color(0xFFF2F3F5)) else (if (isDark) customColors.panelBackground else Color.White),
        shadowColor = if (isBlocked) Color.Transparent else (if (isFavorite) Color.Yellow.copy(alpha = 0.3f) else itemAccent.copy(alpha = 0.3f)),
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).alpha(if (isBlocked) 0.6f else 1.0f),
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
                
                if (isBlocked) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Block, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else if (isFavorite) {
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
                        color = if (isBlocked) Color.Gray else (if (isFavorite) (if (isDark) customColors.lime else customColors.successStrong) else MaterialTheme.colorScheme.onSurface)
                    )
                    if (chat.lastMessage != null && !isBlocked) {
                        Text(
                            text = formatMessageDate(chat.lastMessage.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTyping && !isBlocked) {
                        Text(
                            text = "escribiendo...",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = customColors.aqua,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val msgText = when {
                            isBlocked -> "Usuario bloqueado"
                            chat.lastMessage?.deleted == true -> "Mensaje borrado"
                            else -> chat.lastMessage?.text ?: "Inicia una conversación"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (chat.lastMessage?.senderId != null && chat.lastMessage.senderId != chat.otherUser.uid && !isBlocked) {
                                val isRead = chat.lastMessage.readBy.size > 1
                                Icon(
                                    imageVector = if (isRead) Icons.Default.Visibility else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp),
                                    tint = if (isRead) Color(0xFF00B4D8) else Color.Gray
                                )
                            }
                            Text(
                                text = msgText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (chat.unreadCount > 0 && !isBlocked) (if (isDark) Color.White else Color.Black) else Color.Gray,
                                fontWeight = if (chat.unreadCount > 0 && !isBlocked) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                    if (chat.unreadCount > 0 && !isBlocked) {
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
