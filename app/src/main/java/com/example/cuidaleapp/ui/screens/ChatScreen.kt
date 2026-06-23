package com.example.cuidaleapp.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.ui.components.fadingEdge
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.models.ChatMessage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ChatViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUser: UserData,
    myUid: String,
    myName: String,
    myRole: String = "",
    isDark: Boolean = false,
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onViewOtherProfile: (String) -> Unit,
    onRedirectToRequests: () -> Unit,
    onViewRequest: ((String) -> Unit)? = null,
    favoritosViewModel: com.example.cuidaleapp.viewmodel.FavoritosViewModel? = null,
    initialMessage: String? = null
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val otherStatus by viewModel.otherUserStatus.collectAsState()
    val isOtherTyping by viewModel.isOtherTyping.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages 
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
    
    val pinnedMessages = remember(messages) {
        messages.filter { it.pinned && !it.deleted }
    }

    val favs by favoritosViewModel?.favoritos?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val bloqueados by favoritosViewModel?.bloqueados?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val noDeseados by favoritosViewModel?.noDeseados?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    
    var showShareRequests by remember { mutableStateOf(false) }
    var showShareTasks by remember { mutableStateOf(false) }
    var myRequests by remember { mutableStateOf<List<SolicitudCuidado>>(emptyList()) }
    var activeTasks by remember { mutableStateOf<List<com.example.cuidaleapp.models.RegistroTarea>>(emptyList()) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val customColors = CuidaleThemeExtras.customColors
    val primaryColor = MaterialTheme.colorScheme.primary

    var inputText by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showPinnedMessages by remember { mutableStateOf(false) }
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val selectedMessages = remember { mutableStateListOf<ChatMessage>() }
    
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    
    val isFavorite = favs.contains(otherUser.uid)
    val iHaveBlocked = bloqueados.contains(otherUser.uid)
    val iHaveUnwanted = noDeseados.contains(otherUser.uid)

    val isScrolledUp by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var hasNewMessageBelow by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (isScrolledUp) hasNewMessageBelow = true
        else if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }
    
    LaunchedEffect(isScrolledUp) {
        if (!isScrolledUp) hasNewMessageBelow = false
    }

    LaunchedEffect(chatId, initialMessage) {
        AppNavigationState.setActiveChat(chatId)
        viewModel.listenMessages(chatId)
        viewModel.listenUserStatus(otherUser.uid)
        viewModel.listenTyping(chatId, otherUser.uid)
        viewModel.setUserStatus(true)
        viewModel.archiveOldMessages(chatId)
        viewModel.clearChatNotifications(chatId)
        
        if (initialMessage != null) {
            viewModel.sendMessage(chatId, initialMessage)
        }
    }

    LaunchedEffect(showShareRequests) {
        if (showShareRequests && myRole == "representante") {
            try {
                val snapshot = FirebaseFirestore.getInstance().collection("solicitudes")
                    .whereEqualTo("representanteUid", myUid)
                    .whereEqualTo("estado", "abierta")
                    .get().await()
                myRequests = snapshot.toObjects(SolicitudCuidado::class.java)
            } catch (e: Exception) {}
        }
    }
    
    LaunchedEffect(showShareTasks) {
        if (showShareTasks && myRole == "cuidador") {
             try {
                val snapshot = FirebaseFirestore.getInstance().collection("solicitudes")
                    .whereIn("representanteUid", listOf(myUid, otherUser.uid))
                    .whereIn("cuidadorUid", listOf(myUid, otherUser.uid))
                    .whereEqualTo("estado", "cerrada")
                    .limit(1)
                    .get().await()
                
                val sol = snapshot.toObjects(SolicitudCuidado::class.java).firstOrNull()
                sol?.let { s ->
                    val tasksSnapshot = FirebaseFirestore.getInstance()
                        .collection("solicitudes").document(s.id)
                        .collection("registros_tareas")
                        .whereEqualTo("fecha", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                        .get().await()
                    activeTasks = tasksSnapshot.toObjects(com.example.cuidaleapp.models.RegistroTarea::class.java)
                }
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AppNavigationState.setActiveChat(null)
            viewModel.setUserStatus(false)
            viewModel.setTyping(chatId, false)
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPinConfirmDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ConnectivityBanner()
        
        // Custom Top Bar
        Surface(
            shadowElevation = 4.dp,
            color = if (isDark) Color(0xFF1B263B) else Color.White
        ) {
            Box(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = {
                        if (selectedMessages.isNotEmpty()) {
                            val allFromMe = selectedMessages.all { it.senderId == myUid }
                            Column {
                                Text("${selectedMessages.size} seleccionados", fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (allFromMe) "Puedes eliminar o fijar estos mensajes" else "Solo puedes fijar estos mensajes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        } else if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(if (isOnline) "Buscar..." else "Sin conexión", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = isOnline,
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                )
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onViewOtherProfile(otherUser.uid) }
                            ) {
                                Box(modifier = Modifier.size(42.dp)) {
                                    Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color.Gray.copy(alpha = 0.1f)) {
                                        if (otherUser.fotoUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(otherUser.fotoUrl)
                                                    .crossfade(true)
                                                    .diskCachePolicy(CachePolicy.ENABLED)
                                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                                    .build(),
                                                contentDescription = null, 
                                                contentScale = ContentScale.Crop, 
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (otherStatus == "en línea" && isOnline) Color(0xFF4CAF50) else Color.Gray)
                                            .align(Alignment.BottomEnd)
                                            .border(2.dp, if (isDark) Color(0xFF1B263B) else Color.White, CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = otherUser.nombre, 
                                            style = MaterialTheme.typography.titleMedium, 
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.fadingEdge(Brush.horizontalGradient(0.95f to Color.Black, 1f to Color.Transparent))
                                        )
                                        if (isFavorite) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Text(
                                        text = if (!isOnline) "OFFLINE" else if (isBlocked) "Cerrado" else otherStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!isOnline) Color.Gray else if (isBlocked) Color.Red else (if (otherStatus == "en línea") Color(0xFF4CAF50) else Color.Gray),
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (selectedMessages.isNotEmpty()) selectedMessages.clear() else onNavigateBack() }) {
                            Icon(if (selectedMessages.isNotEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        if (selectedMessages.isNotEmpty()) {
                            val allFromMe = selectedMessages.all { it.senderId == myUid }
                            IconButton(onClick = {
                                val textToCopy = selectedMessages.joinToString("\n") { it.text }
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("chat", textToCopy))
                                selectedMessages.clear()
                            }) { Icon(Icons.Default.ContentCopy, null, tint = Color.Gray) }
                            if (selectedMessages.size == 1 && selectedMessages.first().senderId == myUid && !selectedMessages.first().deleted && isOnline) {
                                IconButton(onClick = {
                                    val msg = selectedMessages.first()
                                    messageToEdit = msg
                                    inputText = msg.text
                                    selectedMessages.clear()
                                }) { Icon(Icons.Default.Edit, null, tint = primaryColor) }
                            }
                            if (allFromMe && isOnline) {
                                IconButton(onClick = { showDeleteConfirmDialog = true }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                            if (isOnline) {
                                IconButton(onClick = { showPinConfirmDialog = true }) { 
                                    Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary) 
                                }
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) { Icon(Icons.Default.Search, null) }
                            IconButton(onClick = { showPinnedMessages = true }) { 
                                BadgedBox(badge = { if (pinnedMessages.isNotEmpty()) Badge { Text(pinnedMessages.size.toString()) } }) {
                                    Icon(Icons.Default.PushPin, null)
                                }
                            }
                            var showTopMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                                DropdownMenuItem(text = { Text("Ver Perfil") }, onClick = { showTopMenu = false; onViewOtherProfile(otherUser.uid) }, leadingIcon = { Icon(Icons.Default.Person, null) })
                                DropdownMenuItem(text = { Text(if (isFavorite) "Quitar de Favoritos" else "Añadir a Favoritos") }, onClick = { showTopMenu = false; favoritosViewModel?.toggleFavorito(otherUser.uid) }, enabled = isOnline, leadingIcon = { Icon(Icons.Default.Star, null, tint = if (isFavorite) Color.Yellow else Color.Gray) })
                                DropdownMenuItem(text = { Text(if (iHaveBlocked) "Desbloquear" else "Bloquear", color = if (iHaveBlocked) Color.Unspecified else Color.Red) }, onClick = { showTopMenu = false; favoritosViewModel?.toggleBloqueo(otherUser.uid) }, enabled = isOnline, leadingIcon = { Icon(if (iHaveBlocked) Icons.Default.CheckCircle else Icons.Default.Block, null, tint = if (iHaveBlocked) Color(0xFF4CAF50) else Color.Red) })
                                DropdownMenuItem(text = { Text(if (iHaveUnwanted) "Mostrar en búsquedas" else "Ocultar de búsquedas") }, onClick = { showTopMenu = false; favoritosViewModel?.toggleNoDeseado(otherUser.uid) }, enabled = isOnline, leadingIcon = { Icon(if (iHaveUnwanted) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) })
                            }
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0xFF0D1B2A) else Color(0xFFF1F4F9))
            ) {
                if (pinnedMessages.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { showPinnedMessages = true },
                        color = primaryColor.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp), tint = primaryColor)
                            Spacer(Modifier.width(12.dp))
                            Text(pinnedMessages.last().text, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!isOnline && filteredMessages.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                                Text("Sin conexión a internet", fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("No se pueden cargar los mensajes", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    } else {
                        val reversedMessages = remember(filteredMessages) { filteredMessages.reversed() }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp, 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            reverseLayout = true
                        ) {
                            item {
                                Column {
                                    androidx.compose.animation.AnimatedVisibility(visible = isOtherTyping && isOnline, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${otherUser.nombre} está escribiendo...", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                        }
                                    }
                                }
                            }
                            items(items = reversedMessages, key = { it.id }) { msg ->
                                val index = reversedMessages.indexOf(msg)
                                
                                if (index < reversedMessages.size - 1) {
                                    val nextMsg = reversedMessages[index + 1]
                                    if (!isSameDay(msg.timestamp, nextMsg.timestamp)) {
                                        DayDivider(msg.timestamp)
                                    }
                                } else if (index == reversedMessages.size - 1) {
                                    DayDivider(msg.timestamp)
                                }

                                if (msg.senderId == "system") {
                                    SystemMessage(msg.text)
                                } else {
                                    val isHighlighted = highlightedMessageId == msg.id
                                    ChatBubble(
                                        msg = msg,
                                        isMe = msg.senderId == myUid,
                                        isDark = isDark,
                                        isSelected = selectedMessages.any { it.id == msg.id },
                                        isHighlighted = isHighlighted,
                                        onClick = {
                                            if (selectedMessages.isNotEmpty()) {
                                                if (selectedMessages.any { it.id == msg.id }) selectedMessages.removeAll { it.id == msg.id }
                                                else selectedMessages.add(msg)
                                            }
                                        },
                                        onLongClick = { if (selectedMessages.isEmpty()) selectedMessages.add(msg) },
                                        onSwipeRight = { if (!isBlocked && !iHaveBlocked && isOnline) replyingToMessage = msg },
                                        onViewPostulacion = { solId -> onViewRequest?.invoke(solId) ?: onRedirectToRequests() },
                                        onDoubleClick = {
                                            if (msg.senderId == myUid && !msg.deleted && isOnline) {
                                                messageToEdit = msg
                                                inputText = msg.text
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    tonalElevation = 8.dp,
                    color = if (isDark) Color(0xFF1B263B) else Color.White,
                    shape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    if (!isOnline) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF2B2D31) else Color(0xFFF2F3F5))
                                .padding(top = 20.dp, bottom = 40.dp, start = 20.dp, end = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WifiOff, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "MODO SOLO LECTURA", 
                                        color = Color.Gray, 
                                        style = MaterialTheme.typography.labelLarge, 
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Conéctate a internet para enviar mensajes.", 
                                    color = Color.Gray.copy(alpha = 0.7f), 
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else if (isBlocked || iHaveBlocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF2B2D31) else Color(0xFFF2F3F5))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (iHaveBlocked) Icons.Default.Block else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (iHaveBlocked) Color.Red else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = if (iHaveBlocked) "Has bloqueado a ${otherUser.nombre}." 
                                          else "No puedes enviar mensajes a este usuario.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (iHaveBlocked) 
                                        "No podrás recibir mensajes de este usuario y no aparecerá en tus búsquedas. Puedes desbloquearlo para retomar el contacto."
                                        else "Este usuario ha restringido quién puede enviarle mensajes. No puedes contactar con él por ahora.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                if (iHaveBlocked) {
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = { favoritosViewModel?.toggleBloqueo(otherUser.uid) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("Desbloquear")
                                    }
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
                            if (replyingToMessage != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    color = Color.Gray.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.width(4.dp).height(32.dp).background(primaryColor, CircleShape))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (replyingToMessage!!.senderId == myUid) "Tú" else otherUser.nombre, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = primaryColor)
                                            Text(replyingToMessage!!.text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        }
                                        IconButton(onClick = { replyingToMessage = null }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                IconButton(onClick = { if (myRole == "representante") showShareRequests = true else showShareTasks = true }) {
                                    Icon(if (myRole == "representante") Icons.Default.AddCircle else Icons.AutoMirrored.Filled.Assignment, null, tint = primaryColor)
                                }
                                
                                TextField(
                                    value = inputText,
                                    onValueChange = { inputText = it; viewModel.setTyping(chatId, it.isNotEmpty()) },
                                    placeholder = { Text(if (messageToEdit != null) "Editando..." else "Escribe un mensaje...") },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 4,
                                    colors = TextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    trailingIcon = {
                                        if (messageToEdit != null) {
                                            IconButton(onClick = { messageToEdit = null; inputText = "" }) { Icon(Icons.Default.Close, null) }
                                        }
                                    }
                                )

                                FloatingActionButton(
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            viewModel.setTyping(chatId, false)
                                            if (messageToEdit != null) {
                                                viewModel.editMessage(chatId, messageToEdit!!.id, inputText)
                                                messageToEdit = null
                                            } else {
                                                viewModel.sendMessage(
                                                    chatId = chatId,
                                                    text = inputText,
                                                    replyToId = replyingToMessage?.id,
                                                    replyToText = replyingToMessage?.text,
                                                    replyToName = if (replyingToMessage?.senderId == myUid) "Tú" else otherUser.nombre
                                                )
                                                replyingToMessage = null
                                            }
                                            inputText = ""
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    containerColor = primaryColor,
                                    contentColor = Color.White,
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                                ) {
                                    Icon(if (messageToEdit != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isScrolledUp,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
            ) {
                Surface(
                    color = if (hasNewMessageBelow) MaterialTheme.colorScheme.secondary else primaryColor,
                    shape = CircleShape,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable { scope.launch { listState.animateScrollToItem(0) } }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasNewMessageBelow) Icons.Default.NewReleases else Icons.Default.ArrowDownward, 
                            contentDescription = null, 
                            tint = Color.White, 
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasNewMessageBelow) "Nuevos mensajes" else "Volver al final", 
                            color = Color.White, 
                            style = MaterialTheme.typography.labelMedium, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = if (isDark) Color(0xFF1B263B) else Color.White,
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("¿Eliminar mensajes?", fontWeight = FontWeight.Black)
                    }
                    Text(
                        "Esta acción borrará permanentemente los mensajes seleccionados para todos los participantes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            text = {
                Surface(
                    color = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedMessages) { msg ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(4.dp).background(Color.Red, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedMessages.forEach { viewModel.deleteMessage(chatId, it.id) }
                        selectedMessages.clear()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("ELIMINAR PARA TODOS", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("CANCELAR") }
            }
        )
    }

    if (showPinConfirmDialog) {
        val allPinned = selectedMessages.all { it.pinned }
        AlertDialog(
            onDismissRequest = { showPinConfirmDialog = false },
            containerColor = if (isDark) Color(0xFF1B263B) else Color.White,
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(if (allPinned) "¿Desfijar mensajes?" else "¿Fijar mensajes?", fontWeight = FontWeight.Black)
                    }
                    Text(
                        if (allPinned) "Los mensajes seleccionados ya no aparecerán en la lista de mensajes destacados."
                        else "Los mensajes seleccionados se guardarán en la lista de fijados para que todos puedan verlos fácilmente.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            text = {
                Surface(
                    color = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedMessages) { msg ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PushPin, null, modifier = Modifier.size(12.dp), tint = if (msg.pinned) Color.Red else Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedMessages.forEach { viewModel.togglePin(chatId, it.id, it.pinned, myName) }
                        selectedMessages.clear()
                        showPinConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (allPinned) "ACEPTAR" else "FIJAR", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPinConfirmDialog = false }) { Text("CANCELAR") }
            }
        )
    }
    
    if (showShareRequests) {
        ModalBottomSheet(onDismissRequest = { showShareRequests = false }) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Compartir solicitud", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                if (myRequests.isEmpty()) {
                    Text("No tienes solicitudes abiertas", color = Color.Gray)
                } else {
                    myRequests.forEach { sol ->
                        Card(
                            onClick = {
                                viewModel.sendMessage(chatId, "Revisa mi solicitud para ${sol.nombrePaciente}", type = "solicitud_compartida", metadata = mapOf("solicitudId" to sol.id))
                                showShareRequests = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = sol.fotoPaciente, contentDescription = null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(12.dp))
                                Text(sol.nombrePaciente, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showShareTasks) {
        ModalBottomSheet(onDismissRequest = { showShareTasks = false }) {
            Column(Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(0.7f)) {
                Text("Compartir tarea", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Selecciona una tarea para compartir su estado actual.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                
                if (activeTasks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AssignmentLate, null, modifier = Modifier.size(48.dp), tint = Color.Gray.copy(alpha = 0.3f))
                            Spacer(Modifier.height(12.dp))
                            Text("No hay tareas registradas hoy", color = Color.Gray)
                        }
                    }
                } else {
                    val completadas = activeTasks.filter { it.completada }
                    val pospuestas = activeTasks.filter { it.postergada && !it.completada }
                    val pendientes = activeTasks.filter { !it.completada && !it.postergada }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            TaskShareSection(
                                title = "PENDIENTES",
                                tasks = pendientes,
                                color = MaterialTheme.colorScheme.primary,
                                onTaskClick = { reg: com.example.cuidaleapp.models.RegistroTarea ->
                                    viewModel.sendMessage(chatId, "Sobre: ${reg.titulo} (${reg.horarioPrevisto}) - ESTADO: PENDIENTE", type = "tarea_compartida", metadata = mapOf("tareaId" to reg.id, "solicitudId" to reg.solicitudId))
                                    showShareTasks = false
                                }
                            )
                        }
                        item {
                            TaskShareSection(
                                title = "COMPLETADAS",
                                tasks = completadas,
                                color = Color(0xFF4CAF50),
                                onTaskClick = { reg: com.example.cuidaleapp.models.RegistroTarea ->
                                    viewModel.sendMessage(chatId, "Sobre: ${reg.titulo} (${reg.horarioPrevisto}) - ESTADO: COMPLETADA", type = "tarea_compartida", metadata = mapOf("tareaId" to reg.id, "solicitudId" to reg.solicitudId))
                                    showShareTasks = false
                                }
                            )
                        }
                        item {
                            TaskShareSection(
                                title = "POSPUESTAS",
                                tasks = pospuestas,
                                color = Color.Red,
                                onTaskClick = { reg: com.example.cuidaleapp.models.RegistroTarea ->
                                    viewModel.sendMessage(chatId, "Sobre: ${reg.titulo} (${reg.horarioPrevisto}) - ESTADO: POSPUESTA", type = "tarea_compartida", metadata = mapOf("tareaId" to reg.id, "solicitudId" to reg.solicitudId))
                                    showShareTasks = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    
    if (showPinnedMessages) {
        ModalBottomSheet(onDismissRequest = { showPinnedMessages = false }) {
            Column(Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(0.6f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, null, tint = primaryColor)
                    Spacer(Modifier.width(12.dp))
                    Text("Mensajes fijados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn {
                    items(pinnedMessages) { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                            color = Color.Gray.copy(alpha = 0.05f), 
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { 
                                            showPinnedMessages = false
                                            val index = filteredMessages.reversed().indexOfFirst { it.id == msg.id }
                                            if (index != -1) {
                                                scope.launch {
                                                    listState.animateScrollToItem(index)
                                                    highlightedMessageId = msg.id
                                                    kotlinx.coroutines.delay(2000)
                                                    highlightedMessageId = null
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("IR AL MENSAJE", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.togglePin(chatId, msg.id, true, myName) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.PushPin, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("DESFIJAR", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskShareSection(
    title: String,
    tasks: List<com.example.cuidaleapp.models.RegistroTarea>,
    color: Color,
    onTaskClick: (com.example.cuidaleapp.models.RegistroTarea) -> Unit
) {
    var expanded by remember { mutableStateOf(tasks.isNotEmpty()) }
    
    Column {
        Surface(
            onClick = { expanded = !expanded },
            color = Color.Black.copy(alpha = 0.03f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.weight(1f))
                Badge(containerColor = color) { Text(tasks.size.toString(), color = Color.White) }
            }
        }
        
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tasks.isEmpty()) {
                    Text("Sin tareas", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(12.dp))
                } else {
                    tasks.forEach { reg ->
                        Card(
                            onClick = { onTaskClick(reg) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, color.copy(alpha = 0.1f))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RadioButtonUnchecked, null, tint = color, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(reg.titulo, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(reg.horarioPrevisto, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    msg: ChatMessage,
    isMe: Boolean,
    isDark: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeRight: () -> Unit,
    onViewPostulacion: (String) -> Unit,
    onDoubleClick: () -> Unit
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    
    val highlightColor = if (isDark) Color(0xFFF1C40F).copy(alpha = 0.3f) else Color(0xFFF1C40F).copy(alpha = 0.15f)
    
    val bubbleColor = when {
        isSelected -> if (isDark) Color(0xFF32353B) else Color(0xFFD3E3FD)
        msg.deleted -> Color(0xFFD32F2F)
        isMe -> MaterialTheme.colorScheme.primary 
        else -> if (isDark) Color(0xFF1B263B) else Color.White
    }
    
    val finalBubbleColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isHighlighted) highlightColor else bubbleColor,
        animationSpec = if (isHighlighted) infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ) else tween(500),
        label = "highlightAnimation"
    )
    
    val textColor = if (isMe || msg.deleted) Color.White else (if (isDark) Color.White else Color.Black)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHighlighted) highlightColor.copy(alpha = 0.1f) else Color.Transparent)
            .graphicsLayer {
                translationX = swipeOffset
                scaleX = if (isSelected || isHighlighted) 0.98f else 1f
                scaleY = if (isSelected || isHighlighted) 0.98f else 1f
            }.pointerInput(msg.id) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() }, onDoubleTap = { onDoubleClick() })
            }.pointerInput(msg.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount -> if (dragAmount > 0) swipeOffset = (swipeOffset + dragAmount).coerceAtMost(100f) },
                    onDragEnd = { if (swipeOffset > 80f) onSwipeRight(); swipeOffset = 0f },
                    onDragCancel = { swipeOffset = 0f }
                )
            },
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (swipeOffset > 0) {
            Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).alpha(swipeOffset / 100f))
        }

        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                color = finalBubbleColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                tonalElevation = if (isMe) 2.dp else 1.dp,
                shadowElevation = if (isSelected) 4.dp else 0.dp
            ) {
                Column(modifier = Modifier.padding(12.dp, 8.dp).widthIn(max = 280.dp)) {
                    if (msg.replyToId != null) {
                        Surface(Modifier.fillMaxWidth().padding(bottom = 4.dp), color = Color.Black.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Text(msg.replyToName ?: "Usuario", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary)
                                Text(msg.replyToText ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = textColor.copy(alpha = 0.7f))
                            }
                        }
                    }
                    
                    if (msg.deleted) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.width(4.dp))
                            Text("Mensaje eliminado", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Color.White.copy(alpha = 0.9f))
                        }
                    } else {
                        if (msg.imageUrl != null) {
                            AsyncImage(model = msg.imageUrl, contentDescription = null, Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.videoUrl != null) {
                            VideoPlayer(msg.videoUrl, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.fileUrl != null) {
                            FileBubble(msg.fileName ?: "Archivo", msg.fileUrl)
                            Spacer(Modifier.height(4.dp))
                        }
                        
                        when (msg.type) {
                            "aviso_postulacion", "solicitud_compartida" -> {
                                RequestAlertBubble(msg, onViewClick = { onViewPostulacion(msg.metadata["solicitudId"] ?: "") })
                            }
                            "tarea_compartida" -> {
                                TaskSharedBubble(msg.text, isMe)
                            }
                            else -> {
                                Text(
                                    text = buildAnnotatedString {
                                        append(msg.text)
                                        if (msg.edited) {
                                            withStyle(SpanStyle(fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))) {
                                                append(" (editado)")
                                            }
                                        }
                                    },
                                    color = textColor, 
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(formatMessageDate(msg.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                if (isMe) {
                    Spacer(Modifier.width(4.dp))
                    val isRead = msg.readBy.size > 1
                    Icon(imageVector = if (isRead) Icons.Default.Visibility else Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (isRead) Color(0xFF00B4D8) else Color.Gray)
                }
            }
        }
    }
}

@Composable
fun RequestAlertBubble(msg: ChatMessage, onViewClick: () -> Unit) {
    var status by remember(msg.id) { mutableStateOf<String?>(null) }
    val solicitudId = msg.metadata["solicitudId"] ?: ""
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(solicitudId) {
        if (solicitudId.isNotEmpty()) {
            db.collection("solicitudes").document(solicitudId).addSnapshotListener { snap, _ ->
                status = snap?.getString("estado")
            }
        }
    }

    val statusText = when(status) {
        "abierta" -> "DISPONIBLE"
        "cerrada" -> "YA TOMADA"
        else -> "..."
    }

    Surface(color = Color.Black.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg.text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = Color.Gray.copy(alpha = 0.1f), shape = CircleShape) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onViewClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text("VER DETALLES", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun TaskSharedBubble(text: String, isMe: Boolean) {
    val accentColor = if (isMe) Color.White else MaterialTheme.colorScheme.primary
    val isDark = CuidaleThemeExtras.customColors.isDark
    
    Surface(
        color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "REPORTE DE TAREA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (isMe) Color.White else (if (isDark) Color.LightGray else Color.DarkGray)
            )
        }
    }
}

@Composable
fun FileBubble(fileName: String, fileUrl: String) {
    val context = LocalContext.current
    Surface(color = Color.Black.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(fileUrl))
        context.startActivity(intent)
    }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
        }
    }
    DisposableEffect(videoUrl) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(factory = { PlayerView(it).apply { player = exoPlayer } }, modifier = modifier)
}

@Composable
fun SystemMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.Gray.copy(alpha = 0.1f), shape = CircleShape) {
            Text(text, modifier = Modifier.padding(12.dp, 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun DayDivider(timestamp: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.2f))
        Text(
            text = formatDividerDate(timestamp),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.2f))
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun formatDividerDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    return when {
        now.get(Calendar.YEAR) == date.get(Calendar.YEAR) && 
        now.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR) -> "HOY"
        
        now.get(Calendar.YEAR) == date.get(Calendar.YEAR) && 
        now.get(Calendar.DAY_OF_YEAR) - date.get(Calendar.DAY_OF_YEAR) == 1 -> "AYER"
        
        else -> SimpleDateFormat("d 'de' MMMM", Locale("es", "ES")).format(Date(timestamp)).uppercase()
    }
}

private fun formatMessageDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - msgDate.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Ayer " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) -> {
            SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
