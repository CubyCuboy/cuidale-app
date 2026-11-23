package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
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



        TopAppBar(
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.chats_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.chats_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90E0EF)
                    )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)
                    .padding(end = 4.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.28f))
                )
                OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = { chatSearchQuery = it },
                    placeholder = { Text(stringResource(R.string.chats_search_placeholder), color = Color(0xFF546E7A)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (chatSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { chatSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.White.copy(alpha = 0.92f),
                        focusedContainerColor = Color.White.copy(alpha = 0.96f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color(0xFF0D1B2A),
                        unfocusedTextColor = Color(0xFF0D1B2A),
                        unfocusedBorderColor = Color(0xFF90E0EF),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = Color(0xFF546E7A),
                        focusedPlaceholderColor = Color(0xFF546E7A)
                    ),
                    singleLine = true
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ChatListSection(
                    chatList = filteredChatList,
                    typingUsers = typingUsers,
                    favoritos = favoritos,
                    bloqueados = bloqueados,
                    isDark = isDark,
                    searchQuery = chatSearchQuery,
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
                            Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Icon(if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Block, null, tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(if (isBlocked) stringResource(R.string.chat_unblock) else stringResource(R.string.chat_block), color = if (isBlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
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
    searchQuery: String = "",
    onChatClick: (String, UserData) -> Unit,
    onLongClick: (ChatViewModel.ChatPreview) -> Unit,
    onMarkRead: (String) -> Unit
) {
    if (chatList.isEmpty()) {
        EmptyState(
            text = if (searchQuery.isNotBlank()) stringResource(R.string.chats_no_results, searchQuery)
            else stringResource(R.string.chats_empty),
            icon = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.ChatBubbleOutline
        )
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
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.28f),
        cornerRadius = 20.dp,
        shadowOffset = 4.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)
                .alpha(if (isBlocked) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when {
                isBlocked -> MaterialTheme.colorScheme.error
                chat.unreadCount > 0 -> MaterialTheme.colorScheme.primary
                isFavorite -> Color(0xFFF9A825)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(statusColor)
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.size(56.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = when {
                        isBlocked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        isFavorite -> Color(0xFFF9A825).copy(alpha = 0.22f)
                        chat.unreadCount > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else -> if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFECEFF1)
                    },
                    border = if (isFavorite && !isBlocked) {
                        BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFFF9A825), Color(0xFFE53935))))
                    } else null
                ) {
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
                        Icon(
                            Icons.Default.Person,
                            null,
                            modifier = Modifier.align(Alignment.Center),
                            tint = when {
                                isBlocked -> MaterialTheme.colorScheme.error
                                isFavorite -> Color(0xFFB8860B)
                                else -> if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF546E7A)
                            }
                        )
                    }
                }
                if (isFavorite && !isBlocked) {
                    Icon(
                        Icons.Default.Favorite, null, tint = Color(0xFFE53935),
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .background(if (isDark) Color(0xFF1B263B) else Color.White, CircleShape)
                            .padding(2.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val fullName = listOf(chat.otherUser.nombre, chat.otherUser.apellido_pa)
                        .filter { it.isNotBlank() && it != "n/a" }.joinToString(" ")

                    Text(
                        text = fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (chat.unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).fadingEdge(
                            Brush.horizontalGradient(0.85f to Color.Black, 1f to Color.Transparent)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isDark) Color.White else Color.Black
                    )
                    if (chat.lastMessage != null && !isBlocked) {
                        Text(
                            text = formatMessageDate(context, chat.lastMessage.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

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
                    val textColor = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

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
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.graphicsLayer { scaleX = badgeScale; scaleY = badgeScale }
                        ) {
                            Text(chat.unreadCount.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onMarkRead(chat.chatId) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.MarkEmailUnread,
                                contentDescription = stringResource(R.string.chats_unread_cd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.25f)
            )
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
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.28f),
        cornerRadius = 20.dp,
        shadowOffset = 4.dp,
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
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = onAccept,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.chats_accept))
            }
            Spacer(Modifier.width(4.dp))
            FilledIconButton(
                onClick = onDecline,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chats_decline))
            }
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
                HardShadowPanel(
                    backgroundColor = if (isDark) Color(0xFF1B263B) else Color.White,
                    shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.28f),
                    cornerRadius = 20.dp,
                    shadowOffset = 4.dp,
                    contentPadding = PaddingValues(0.dp)
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
                            TextButton(onClick = { onRemove(user.uid) }) { Text(stringResource(R.string.chats_unblock_upper), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black) }
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
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.chats_chat_upper), fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onUserClick(user.uid) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
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
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF48CAE4).copy(alpha = 0.30f),
                                Color(0xFF0077B6).copy(alpha = 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(48.dp), tint = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
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
