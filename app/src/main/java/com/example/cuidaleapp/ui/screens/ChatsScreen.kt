package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.components.ConnectivityBanner
import com.example.cuidaleapp.ui.components.CuidaleBottomBar
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.fadingEdge
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.CuidaleCircularMenuButton
import com.example.cuidaleapp.ui.components.CuidaleDrawerContent
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.viewmodel.ChatViewModel
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import com.example.cuidaleapp.viewmodel.Vinculo
import com.example.cuidaleapp.viewmodel.VinculosViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    userData: UserData,
    isDark: Boolean,
    onUserClick: (String) -> Unit,
    onChatClick: (String, UserData) -> Unit,
    favoritosViewModel: FavoritosViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    vinculosViewModel: VinculosViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val chatList by chatViewModel.chatList.collectAsState()
    val typingUsers by chatViewModel.typingUsers.collectAsState()
    val misVinculos by vinculosViewModel.misVinculos.collectAsState()
    val vinculosUsers by vinculosViewModel.vinculosUsers.collectAsState()
    val favoritos by favoritosViewModel.favoritos.collectAsState()
    val bloqueados by favoritosViewModel.bloqueados.collectAsState()
    val managedUsers by favoritosViewModel.managedUsers.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    var selectedTab by remember { mutableIntStateOf(0) }
    var chatSearchQuery by remember { mutableStateOf("") }
    
    var showChatMenu by remember { mutableStateOf(false) }
    var chatToMenu by remember { mutableStateOf<ChatViewModel.ChatPreview?>(null) }

    val filteredChatList = remember(chatList, chatSearchQuery, favoritos, bloqueados) {
        val baseList = chatList.filter { !bloqueados.contains(it.otherUser.uid) }
            .filter { 
                chatSearchQuery.isBlank() || 
                it.otherUser.nombre.contains(chatSearchQuery, ignoreCase = true) ||
                it.otherUser.apellido_pa.contains(chatSearchQuery, ignoreCase = true)
            }
        baseList.sortedWith(compareByDescending<ChatViewModel.ChatPreview> { favoritos.contains(it.otherUser.uid) }.thenByDescending { it.lastMessage?.timestamp ?: 0L })
    }

    LaunchedEffect(Unit) {
        chatViewModel.listenChats()
        vinculosViewModel.loadVinculos()
    }

    Column(modifier = Modifier.fillMaxSize()) {



        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.chats_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                Box(
                    Modifier
                        .tabIndicatorOffset(tabPositions[selectedTab])
                        .height(3.dp)
                        .padding(horizontal = 24.dp)
                        .background(Color.White, CircleShape)
                )
            }
        ) {
            val tabs = listOf(stringResource(R.string.chats_tab_chats), stringResource(R.string.chats_tab_requests), stringResource(R.string.chats_tab_blocked))
            tabs.forEachIndexed { index, title ->
                val badgeCount = if (index == 1) {
                    misVinculos.count { it.estado == "pendiente" && it.solicitante != userData.uid }
                } else 0
                
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title, 
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                            if (badgeCount > 0) {
                                Spacer(Modifier.width(6.dp))
                                Badge { Text(badgeCount.toString()) }
                            }
                        }
                    }
                )
            }
        }

        if (selectedTab == 0) {
            OutlinedTextField(
                value = chatSearchQuery,
                onValueChange = { chatSearchQuery = it },
                placeholder = { Text(stringResource(R.string.chats_search_placeholder)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White,
                    focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = primaryColor.copy(alpha = 0.5f)
                ),
                singleLine = true
            )
        }

        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ChatListSection(
                    chatList = filteredChatList,
                    typingUsers = typingUsers,
                    favoritos = favoritos,
                    bloqueados = bloqueados,
                    isDark = isDark,
                    onChatClick = onChatClick,
                    onLongClick = {
                        chatToMenu = it
                        showChatMenu = true
                    },
                    onMarkRead = { chatId -> chatViewModel.clearChatNotifications(chatId) }
                )
                1 -> RequestListSection(
                    vinculos = misVinculos,
                    vinculosUsers = vinculosUsers,
                    myUid = userData.uid,
                    isDark = isDark,
                    onAccept = { vinculosViewModel.aceptarSolicitud(it) },
                    onDecline = { vinculosViewModel.rechazarSolicitud(it) }
                )
                2 -> BlockedListSection(
                    bloqueados = bloqueados,
                    managedUsers = managedUsers,
                    isDark = isDark,
                    onRemove = { favoritosViewModel.toggleBloqueo(it) },
                    onChatClick = onChatClick,
                    onUserClick = onUserClick
                )
            }
        }
    }

    if (showChatMenu && chatToMenu != null) {
        val isFav = favoritos.contains(chatToMenu!!.otherUser.uid)
        val isBlocked = bloqueados.contains(chatToMenu!!.otherUser.uid)
        
        AlertDialog(
            onDismissRequest = { showChatMenu = false },
            confirmButton = {},
            title = { Text(chatToMenu!!.otherUser.nombre) },
            text = {
                Column {
                    TextButton(onClick = {
                        chatViewModel.markChatAsRead(chatToMenu!!.chatId)
                        showChatMenu = false
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MarkEmailUnread, null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.chats_mark_read))
                        }
                    }
                    TextButton(onClick = {
                        favoritosViewModel.toggleFavorito(chatToMenu!!.otherUser.uid)
                        showChatMenu = false
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isFav) Icons.Default.StarOutline else Icons.Default.Star, null, tint = if (isFav) Color.Unspecified else Color.Yellow)
                            Spacer(Modifier.width(12.dp))
                            Text(if (isFav) stringResource(R.string.chats_remove_favorite) else stringResource(R.string.chats_add_favorite))
                        }
                    }
                    TextButton(onClick = {
                        showChatMenu = false
                        onUserClick(chatToMenu!!.otherUser.uid)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.chats_view_profile))
                        }
                    }
                    TextButton(onClick = {
                        favoritosViewModel.toggleBloqueo(chatToMenu!!.otherUser.uid)
                        showChatMenu = false
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Block, null, tint = if (isBlocked) Color.Green else Color.Red)
                            Spacer(Modifier.width(12.dp))
                            Text(if (isBlocked) stringResource(R.string.chat_unblock) else stringResource(R.string.chat_block), color = if (isBlocked) Color.Unspecified else Color.Red)
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
    typingUsers: Map<String, Boolean>,
    favoritos: List<String>,
    bloqueados: List<String>,
    isDark: Boolean,
    onChatClick: (String, UserData) -> Unit,
    onLongClick: (ChatViewModel.ChatPreview) -> Unit,
    onMarkRead: (String) -> Unit
) {
    if (chatList.isEmpty()) {
        EmptyState(stringResource(R.string.chats_empty), Icons.Default.ChatBubbleOutline)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatList, key = { it.chatId }) { chat ->
                ChatCard(
                    chat = chat,
                    isTyping = typingUsers[chat.chatId] ?: false,
                    isFavorite = favoritos.contains(chat.otherUser.uid),
                    isBlocked = bloqueados.contains(chat.otherUser.uid),
                    isDark = isDark,
                    onClick = { onChatClick(chat.chatId, chat.otherUser) },
                    onLongClick = { onLongClick(chat) },
                    onMarkRead = onMarkRead,
                    context = LocalContext.current
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatCard(
    chat: ChatViewModel.ChatPreview,
    isTyping: Boolean,
    isFavorite: Boolean,
    isBlocked: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkRead: (String) -> Unit,
    context: android.content.Context
) {
    val customColors = CuidaleThemeExtras.customColors
    HardShadowPanel(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        backgroundColor = if (isDark) (if (chat.unreadCount > 0) Color(0xFF2B2D31) else Color(0xFF1B263B)) 
                else (if (chat.unreadCount > 0) Color(0xFFE3E5E8) else Color.White),
        shadowColor = Color.Black.copy(alpha = 0.05f),
        cornerRadius = 16.dp,
        shadowOffset = 2.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).alpha(if (isBlocked) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                    if (chat.otherUser.fotoUrl.isNotBlank() && chat.otherUser.fotoUrl != "n/a") {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(chat.otherUser.fotoUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(customColors.avatarBackground)
                        )
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
                    }
                }
                
                if (isFavorite && !isBlocked) {
                    Icon(
                        Icons.Default.Star, null, tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).background(if (isDark) Color(0xFF1B263B) else Color.White, CircleShape).padding(1.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val fullName = listOf(chat.otherUser.nombre, chat.otherUser.apellido_pa).filter { it.isNotBlank() && it != "n/a" }.joinToString(" ")
                    
                    Text(
                        text = fullName, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = if (chat.unreadCount > 0) FontWeight.ExtraBold else FontWeight.Bold,
                        modifier = Modifier.weight(1f).fadingEdge(
                            Brush.horizontalGradient(
                                0.85f to Color.Black,
                                1f to Color.Transparent
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isDark) Color.White else Color.Black
                    )
                    if (chat.lastMessage != null && !isBlocked) {
                        Text(
                            text = formatMessageDate(context, chat.lastMessage.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
                
                Spacer(Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val lastMsgText = when {
                        isBlocked -> stringResource(R.string.chats_user_blocked)
                        isTyping -> stringResource(R.string.chats_typing)
                        chat.lastMessage?.deleted == true -> stringResource(R.string.chat_message_deleted)
                        else -> {
                            val lastMsg = chat.lastMessage
                            val batchItems = runCatching {
                                com.example.cuidaleapp.ui.components.parseMediaBatch(lastMsg?.metadata ?: emptyMap())
                            }.getOrDefault(emptyList())
                            val isMultiBatch = lastMsg?.type == "media_group" || batchItems.size > 1
                            val content = when {
                                isMultiBatch && batchItems.isNotEmpty() ->
                                    stringResource(R.string.chats_sent_multiple, batchItems.size)
                                else -> {
                                    val icon = when {
                                        lastMsg?.videoUrl != null -> stringResource(R.string.chat_media_video)
                                        lastMsg?.imageUrl != null -> stringResource(R.string.chat_media_photo)
                                        lastMsg?.fileUrl != null -> stringResource(R.string.chat_media_file, lastMsg.fileName ?: stringResource(R.string.chat_document))
                                        else -> ""
                                    }
                                    val text = lastMsg?.text?.takeIf { it.isNotBlank() } ?: ""
                                    when {
                                        text.isNotBlank() && icon.isNotBlank() -> stringResource(R.string.chats_text_with_icon, text, icon)
                                        icon.isNotBlank() -> icon
                                        else -> text.ifBlank { stringResource(R.string.chats_start_chat) }
                                    }
                                }
                            }
                            val fromMe = lastMsg != null && lastMsg.senderId != chat.otherUser.uid && lastMsg.senderId != "system"
                            when {
                                lastMsg == null -> content
                                lastMsg.senderId == "system" -> content
                                fromMe -> stringResource(R.string.chats_from_me, content)
                                else -> stringResource(R.string.chats_from_user, chat.otherUser.nombre, content)
                            }
                        }
                    }
                    val textColor = if (isTyping) MaterialTheme.colorScheme.primary else Color.Gray
                    
                    Text(
                        text = lastMsgText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chat.unreadCount > 0 && !isBlocked) (if (isDark) Color.White else Color.Black) else textColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (chat.unreadCount > 0 && !isBlocked) FontWeight.Bold else FontWeight.Normal
                    )
                    
                    if (chat.unreadCount > 0 && !isBlocked) {
                        val badgeScale by animateFloatAsState(
                            targetValue = if (chat.unreadCount > 0) 1f else 0.7f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                            label = "unreadBadge"
                        )
                        Box(
                            modifier = Modifier.size(20.dp)
                                .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale }
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(chat.unreadCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onMarkRead(chat.chatId) }) {
                            Icon(
                                Icons.Default.MarkEmailUnread,
                                contentDescription = stringResource(R.string.chats_unread_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
    val requests = vinculos.filter { it.estado == "pendiente" && it.solicitante != myUid }
    if (requests.isEmpty()) {
        EmptyState(stringResource(R.string.chats_no_requests), Icons.Default.GroupAdd)
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(requests) { vinculo ->
                val otherUid = if (vinculo.uid1 == myUid) vinculo.uid2 else vinculo.uid1
                val user = vinculosUsers[otherUid]
                ChatCardLikeRequest(user, isDark = isDark, onAccept = { onAccept(vinculo.id) }, onDecline = { onDecline(vinculo.id) }, context = LocalContext.current)
            }
        }
    }
}

@Composable
fun ChatCardLikeRequest(user: UserData?, isDark: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, context: android.content.Context) {
    val customColors = CuidaleThemeExtras.customColors
    HardShadowPanel(
        backgroundColor = if (isDark) Color(0xFF1B263B) else Color.White,
        shadowColor = Color.Black.copy(alpha = 0.05f),
        cornerRadius = 16.dp,
        shadowOffset = 2.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                    if (user?.fotoUrl?.isNotEmpty() == true) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(user.fotoUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null, 
                            contentScale = ContentScale.Crop,
                            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(customColors.avatarBackground)
                        )
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val fullName = listOf(user?.nombre, user?.apellido_pa).filter { it?.isNotBlank() == true && it != "n/a" }.joinToString(" ")
                Text(
                    text = if (fullName.isBlank()) stringResource(R.string.chat_user_fallback) else fullName, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fadingEdge(Brush.horizontalGradient(0.85f to Color.Black, 1f to Color.Transparent))
                )
                Text(stringResource(R.string.chats_wants_connect), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onAccept) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50)) }
            IconButton(onClick = onDecline) { Icon(Icons.Default.Close, null, tint = Color.Red) }
        }
    }
}

@Composable
fun BlockedListSection(bloqueados: List<String>, managedUsers: List<UserData>, isDark: Boolean, onRemove: (String) -> Unit, onChatClick: (String, UserData) -> Unit, onUserClick: (String) -> Unit) {
    val users = managedUsers.filter { bloqueados.contains(it.uid) }
    if (users.isEmpty()) {
        EmptyState(stringResource(R.string.chats_no_blocked), Icons.Default.Block)
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(users) { user ->
                Surface(
                    shape = RoundedCornerShape(12.dp), 
                    color = if (isDark) Color(0xFF1B263B) else Color.White,
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(user.fotoUrl)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = null, 
                                modifier = Modifier.size(40.dp).clip(CircleShape), 
                                contentScale = ContentScale.Crop,
                                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(CuidaleThemeExtras.customColors.avatarBackground)
                            )
                            Spacer(Modifier.width(12.dp))
                            val fullName = listOf(user.nombre, user.apellido_pa).filter { it.isNotBlank() && it != "n/a" }.joinToString(" ")
                            Text(
                                text = fullName, 
                                Modifier.weight(1f).fadingEdge(Brush.horizontalGradient(0.85f to Color.Black, 1f to Color.Transparent)), 
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { onRemove(user.uid) }) { Text(stringResource(R.string.chats_unblock_upper), color = Color.Red, fontWeight = FontWeight.Black) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { 
                                    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                    val chatId = if (myUid < user.uid) "${myUid}_${user.uid}" else "${user.uid}_$myUid"
                                    onChatClick(chatId, user)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.chats_chat_upper), fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onUserClick(user.uid) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.chats_profile_upper), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(64.dp), tint = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Color.White)
        }
    }
}

private fun formatMessageDate(context: android.content.Context, timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val msgDate = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return if (now.get(java.util.Calendar.DATE) == msgDate.get(java.util.Calendar.DATE)) {
        java.text.SimpleDateFormat(context.getString(R.string.chats_date_format_time), java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    } else {
        java.text.SimpleDateFormat(context.getString(R.string.chats_date_format_short), java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}
