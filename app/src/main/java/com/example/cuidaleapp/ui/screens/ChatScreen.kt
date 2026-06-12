package com.example.cuidaleapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.models.ChatMessage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ChatViewModel
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
    favoritosViewModel: com.example.cuidaleapp.viewmodel.FavoritosViewModel? = null
) {
    val messages by viewModel.messages.collectAsState()
    val otherStatus by viewModel.otherUserStatus.collectAsState()
    val isOtherTyping by viewModel.isOtherTyping.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val filteredMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    
    val pinnedMessages = messages.filter { it.pinned && !it.deleted }

    val favs by favoritosViewModel?.favoritos?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val bloqueados by favoritosViewModel?.bloqueados?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val noDeseados by favoritosViewModel?.noDeseados?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    
    var showShareRequests by remember { mutableStateOf(false) }
    var myRequests by remember { mutableStateOf<List<SolicitudCuidado>>(emptyList()) }
    
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

    val isFavorite = favs.contains(otherUser.uid)
    val iHaveBlocked = bloqueados.contains(otherUser.uid)
    val iHaveUnwanted = noDeseados.contains(otherUser.uid)
    
    var inputText by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showPinnedMessages by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val customColors = CuidaleThemeExtras.customColors

    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToMenu by remember { mutableStateOf<ChatMessage?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    
    val expandedBlockedMessages = remember { mutableStateListOf<String>() }
    
    val isScrolledUp = remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var hasNewMessageBelow by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (isScrolledUp.value) {
            hasNewMessageBelow = true
        } else {
            // Scroll automatico al recibir mensaje si estamos abajo
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }
    }
    
    LaunchedEffect(isScrolledUp.value) {
        if (!isScrolledUp.value) {
            hasNewMessageBelow = false
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedMessages = remember { mutableStateListOf<ChatMessage>() }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPinConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        AppNavigationState.setActiveChat(chatId)
        viewModel.listenMessages(chatId)
        viewModel.listenUserStatus(otherUser.uid)
        viewModel.listenTyping(chatId, otherUser.uid)
        viewModel.setUserStatus(true)
        viewModel.archiveOldMessages(chatId)
        viewModel.clearChatNotifications(chatId)
    }

    DisposableEffect(Unit) {
        onDispose {
            AppNavigationState.setActiveChat(null)
            viewModel.setUserStatus(false)
            viewModel.setTyping(chatId, false)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(), // Aplicamos el padding del teclado a todo el Scaffold
        topBar = {
            TopAppBar(
                title = {
                    if (selectedMessages.isNotEmpty()) {
                        Text("${selectedMessages.size} seleccionados")
                    } else if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Buscar mensajes...", color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                focusedTextColor = if (isDark) Color.White else Color.Black,
                                unfocusedTextColor = if (isDark) Color.White else Color.Black
                            ),
                            trailingIcon = {
                                IconButton(onClick = { 
                                    isSearchActive = false
                                    viewModel.setSearchQuery("")
                                }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onViewOtherProfile(otherUser.uid) }
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(customColors.avatarBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                if (otherUser.fotoUrl.isNotEmpty()) {
                                    AsyncImage(model = otherUser.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Person, null, tint = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(otherUser.nombre + " " + otherUser.apellido_pa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    if (isFavorite) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = if (isBlocked) "Cerrado" else otherStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isBlocked) Color.Red else (if (otherStatus == "en línea") Color(0xFF4CAF50) else Color.Gray)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (selectedMessages.isNotEmpty()) {
                        IconButton(onClick = { selectedMessages.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    if (selectedMessages.isNotEmpty()) {
                        val allFromMe = selectedMessages.all { it.senderId == myUid }
                        if (allFromMe) {
                            IconButton(onClick = { showDeleteConfirmDialog = true }) { 
                                Icon(Icons.Default.Delete, null, tint = Color.Red) 
                            }
                        }
                        IconButton(onClick = { showPinConfirmDialog = true }) { 
                            Icon(Icons.Default.PushPin, null) 
                        }
                        
                        IconButton(onClick = {
                            val textToCopy = selectedMessages.joinToString("\n") { it.text }
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Mensajes de Cuidale", textToCopy)
                            clipboard.setPrimaryClip(clip)
                            selectedMessages.clear()
                        }) { Icon(Icons.Default.ContentCopy, null) }
                    } else if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, null) }
                        IconButton(onClick = { showPinnedMessages = true }) { 
                            BadgedBox(badge = { if (pinnedMessages.isNotEmpty()) Badge { Text(pinnedMessages.size.toString()) } }) {
                                Icon(Icons.Default.PushPin, null)
                            }
                        }
                        var showTopMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Ver Perfil") },
                                onClick = { 
                                    showTopMenu = false
                                    onViewOtherProfile(otherUser.uid)
                                },
                                leadingIcon = { Icon(Icons.Default.Person, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isFavorite) "Quitar de Favoritos" else "Añadir a Favoritos") },
                                onClick = { 
                                    showTopMenu = false
                                    favoritosViewModel?.toggleFavorito(otherUser.uid)
                                },
                                leadingIcon = { Icon(Icons.Default.Star, null, tint = if (isFavorite) Color.Yellow else Color.Gray) }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        if (iHaveBlocked) "Desbloquear" else "Bloquear",
                                        color = if (iHaveBlocked) Color.Unspecified else Color.Red 
                                    ) 
                                },
                                onClick = { 
                                    showTopMenu = false
                                    favoritosViewModel?.toggleBloqueo(otherUser.uid)
                                },
                                leadingIcon = { 
                                    Icon(
                                        if (iHaveBlocked) Icons.Default.CheckCircle else Icons.Default.Block, 
                                        null, 
                                        tint = if (iHaveBlocked) Color(0xFF4CAF50) else Color.Red
                                    ) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (iHaveUnwanted) "Mostrar en búsquedas" else "Ocultar de búsquedas") },
                                onClick = {
                                    showTopMenu = false
                                    favoritosViewModel?.toggleNoDeseado(otherUser.uid)
                                },
                                leadingIcon = { Icon(if (iHaveUnwanted) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF182634) else Color(0xFFADE8F4),
                    titleContentColor = if (isDark) Color.White else Color(0xFF03045E),
                    navigationIconContentColor = if (isDark) Color.White else Color(0xFF03045E),
                    actionIconContentColor = if (isDark) Color.White else Color(0xFF03045E)
                ),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isDark) Color(0xFF0D1B2A) else Color(0xFF90E0EF).copy(alpha = 0.5f))
        ) {
            AnimatedVisibility(
                visible = pinnedMessages.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = if (isDark) Color(0xFF1B263B) else Color(0xFFCAF0F8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPinnedMessages = true },
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PushPin,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isDark) Color.LightGray else Color.Gray
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Mensajes fijados",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF03045E)
                            )
                            val lastPinned = pinnedMessages.lastOrNull()
                            if (lastPinned != null) {
                                Text(
                                    lastPinned.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = if (isDark) Color.LightGray else Color.Gray
                                )
                            }
                        }
                        Text(
                            "VER TODOS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = customColors.linkColor
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true
                ) {
                    item {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isOtherTyping,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                    if (otherUser.fotoUrl.isNotEmpty()) {
                                        AsyncImage(model = otherUser.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${otherUser.nombre} está escribiendo...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Color.LightGray else Color.Gray,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                    items(filteredMessages.reversed(), key = { it.id }) { msg ->
                        if (msg.senderId == "system") {
                            SystemMessage(msg.text, isDark)
                        } else {
                            val isFromBlocked = !msg.isMe(myUid) && iHaveBlocked
                            val isExpanded = expandedBlockedMessages.contains(msg.id)
                            
                            if (isFromBlocked && !isExpanded) {
                                BlockedMessageBar(
                                    isDark = isDark,
                                    onShow = { expandedBlockedMessages.add(msg.id) }
                                )
                            } else {
                                Box(modifier = Modifier.animateContentSize()) {
                                    ChatBubble(
                                        msg = msg,
                                        isMe = msg.senderId == myUid,
                                        isDark = isDark,
                                        isSelected = selectedMessages.any { it.id == msg.id },
                                        onLongClick = {
                                            if (!isBlocked) {
                                                if (selectedMessages.isEmpty()) {
                                                    selectedMessages.add(msg)
                                                }
                                            }
                                        },
                                        onClick = {
                                            if (selectedMessages.isNotEmpty()) {
                                                if (selectedMessages.any { it.id == msg.id }) {
                                                    selectedMessages.removeAll { it.id == msg.id }
                                                } else {
                                                    selectedMessages.add(msg)
                                                }
                                            }
                                        },
                                        onSwipeRight = {
                                            replyingToMessage = msg
                                        },
                                        onViewPostulacion = { _ ->
                                            onRedirectToRequests() // Redirect specifically to requests
                                        },
                                        onDoubleClick = {
                                            if (msg.senderId == myUid && !msg.deleted) {
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = hasNewMessageBelow,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = customColors.linkColor.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                            .clickable { 
                                scope.launch { listState.animateScrollToItem(0) }
                                hasNewMessageBelow = false
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Nuevos mensajes",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Input area
            Surface(
                tonalElevation = 2.dp,
                color = if (isDark) Color(0xFF1F3B46) else Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                if (isBlocked || iHaveBlocked) {
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
                    Column {
                        if (replyingToMessage != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .background(customColors.linkColor)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (replyingToMessage!!.senderId == myUid) "Tú" else otherUser.nombre,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.linkColor
                                    )
                                    Text(
                                        replyingToMessage!!.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { replyingToMessage = null }) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = inputText,
                                onValueChange = { 
                                    inputText = it
                                    viewModel.setTyping(chatId, it.isNotEmpty())
                                },
                                placeholder = { Text(if (messageToEdit != null) "EDITANDO MENSAJE..." else "Mensajear con @${otherUser.nombre}", color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray) },
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                                leadingIcon = if (myRole == "representante" && messageToEdit == null) {
                                    {
                                        IconButton(onClick = { showShareRequests = true }) {
                                            Icon(Icons.Default.AddCircle, null, tint = customColors.linkColor)
                                        }
                                    }
                                } else null,
                                trailingIcon = {
                                    if (messageToEdit != null) {
                                        IconButton(onClick = { 
                                            messageToEdit = null
                                            inputText = ""
                                        }) {
                                            Icon(Icons.Default.Close, null)
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = if (isDark) Color(0xFF133B5C) else Color(0xFFE9ECEF),
                                    focusedContainerColor = if (isDark) Color(0xFF133B5C) else Color(0xFFE9ECEF),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = if (isDark) Color.White else Color.Black,
                                    unfocusedTextColor = if (isDark) Color.White else Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
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
                                enabled = inputText.isNotBlank(),
                                modifier = Modifier.background(customColors.linkColor, CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("¿Eliminar ${selectedMessages.size} mensajes?") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    Text("Se eliminarán los siguientes mensajes:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(selectedMessages) { msg ->
                            Surface(
                                color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    msg.text,
                                    modifier = Modifier.padding(8.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Eliminar para todos")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showPinConfirmDialog) {
        val allPinned = selectedMessages.all { it.pinned }
        AlertDialog(
            onDismissRequest = { showPinConfirmDialog = false },
            title = { Text(if (allPinned) "¿Desfijar mensajes?" else "¿Fijar mensajes?") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    Text("Mensajes seleccionados:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(selectedMessages) { msg ->
                            Surface(
                                color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PushPin, null, modifier = Modifier.size(14.dp), tint = if (msg.pinned) Color.Red else Color.Gray)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        msg.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedMessages.forEach { 
                        if (it.pinned == allPinned) {
                            viewModel.togglePin(chatId, it.id, it.pinned, myName)
                        }
                    }
                    selectedMessages.clear()
                    showPinConfirmDialog = false
                }) {
                    Text(if (allPinned) "Desfijar todos" else "Fijar todos")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showPinnedMessages) {
        ModalBottomSheet(
            onDismissRequest = { showPinnedMessages = false },
            containerColor = if (isDark) Color(0xFF1E1F22) else Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = if (isDark) Color.Gray else Color.LightGray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Mensajes fijados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color.White else Color.Black
                    )
                    IconButton(onClick = { showPinnedMessages = false }) {
                        Icon(Icons.Default.Close, null, tint = if (isDark) Color.LightGray else Color.Gray)
                    }
                }
                
                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))

                if (pinnedMessages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(48.dp), tint = Color.Gray.copy(alpha = 0.5f))
                            Text("No hay mensajes fijados", color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(pinnedMessages.reversed()) { msg ->
                            PinnedMessageItem(
                                msg = msg,
                                isMe = msg.senderId == myUid,
                                otherUser = otherUser,
                                isDark = isDark,
                                onJump = {
                                    showPinnedMessages = false
                                    val indexInReversed = filteredMessages.reversed().indexOf(msg)
                                    if (indexInReversed != -1) {
                                        scope.launch {
                                            listState.animateScrollToItem(indexInReversed)
                                        }
                                    }
                                },
                                onUnpin = {
                                    viewModel.togglePin(chatId, msg.id, true, myName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareRequests) {
        ModalBottomSheet(
            onDismissRequest = { showShareRequests = false },
            containerColor = if (isDark) Color(0xFF1E1F22) else Color.White
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Compartir solicitud de cuidado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (myRequests.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No tienes solicitudes abiertas actualmente.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxHeight(0.5f)) {
                        items(myRequests) { sol ->
                            Card(
                                onClick = {
                                    viewModel.sendMessage(
                                        chatId = chatId,
                                        text = "Hola, me gustaría invitarte a revisar mi solicitud de cuidado para ${sol.nombrePaciente}",
                                        type = "solicitud_compartida",
                                        metadata = mapOf("solicitudId" to sol.id)
                                    )
                                    showShareRequests = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = sol.fotoPaciente,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sol.nombrePaciente, fontWeight = FontWeight.Bold)
                                        Text(sol.horarioStr, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = customColors.linkColor)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showMenu && messageToMenu != null) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = {},
            title = { Text("Opciones de mensaje") },
            text = {
                Column {
                    if (messageToMenu?.senderId == myUid && !messageToMenu!!.deleted) {
                        TextButton(onClick = {
                            messageToEdit = messageToMenu
                            inputText = messageToMenu?.text ?: ""
                            showMenu = false
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Editar")
                            }
                        }
                        TextButton(onClick = {
                            viewModel.deleteMessage(chatId, messageToMenu!!.id)
                            showMenu = false
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red)
                                Spacer(Modifier.width(8.dp))
                                Text("Eliminar", color = Color.Red)
                            }
                        }
                    }
                    TextButton(onClick = {
                        viewModel.togglePin(chatId, messageToMenu!!.id, messageToMenu!!.pinned, myName)
                        showMenu = false
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PushPin, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (messageToMenu?.pinned == true) "Desfijar" else "Fijar")
                        }
                    }
                }
            }
        )
    }
}


fun ChatMessage.isMe(myUid: String): Boolean = senderId == myUid

@Composable
fun BlockedMessageBar(isDark: Boolean, onShow: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onShow() },
        color = if (isDark) Color(0xFF2B2D31) else Color(0xFFF2F3F5),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mensaje bloqueado",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.LightGray else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Mostrar mensaje",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF00A8FC),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PinnedMessageItem(
    msg: ChatMessage,
    isMe: Boolean,
    otherUser: UserData,
    isDark: Boolean,
    onJump: () -> Unit,
    onUnpin: () -> Unit
) {
    Surface(
        color = if (isDark) Color(0xFF2B2D31) else Color(0xFFF2F3F5),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatarUrl = if (isMe) "" else otherUser.fotoUrl 
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isMe) "Tú" else otherUser.nombre,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )
                    Text(
                        formatMessageDate(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = onUnpin, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.PushPin, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color.LightGray else Color.DarkGray,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onJump,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF4E5058) else Color(0xFFE3E5E8),
                    contentColor = if (isDark) Color.White else Color.Black
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Ir al mensaje", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SystemMessage(text: String, isDark: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.LightGray else Color.DarkGray
            )
        }
    }
}

@Composable
fun ChatBubble(
    msg: ChatMessage,
    isMe: Boolean,
    isDark: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onViewPostulacion: (String) -> Unit = {}
) {
    val bubbleColor = when {
        isSelected -> if (isDark) Color(0xFF32353B) else Color(0xFFD3E3FD)
        msg.deleted -> Color(0xFFD32F2F)
        isMe -> if (isDark) Color(0xFF4B0082).copy(alpha = 0.8f) else Color(0xFFD8BFD8)
        else -> if (isDark) Color(0xFF1E3A5F) else Color(0xFFB0C4DE)
    }
    val textColor = if (msg.deleted || isDark) Color.White else Color.Black
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMe) RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    val customColors = CuidaleThemeExtras.customColors

    var swipeOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (msg.pinned) 16.dp else 0.dp)
            .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .pointerInput(msg.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .pointerInput(msg.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        val newOffset = swipeOffset + dragAmount
                        if (newOffset in 0f..150f) {
                            swipeOffset = newOffset
                        }
                    },
                    onDragEnd = {
                        if (swipeOffset > 80f) {
                            onSwipeRight()
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = {
                        swipeOffset = 0f
                    }
                )
            },
        contentAlignment = alignment
    ) {
        if (swipeOffset > 0) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    null,
                    tint = customColors.linkColor.copy(alpha = (swipeOffset / 150f).coerceIn(0f, 1f)),
                    modifier = Modifier.size((24 * (swipeOffset / 100f)).dp.coerceIn(0.dp, 24.dp))
                )
            }
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .padding(horizontal = 8.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = bubbleColor,
                shape = shape,
                shadowElevation = if (isSelected) 4.dp else 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Muestra el mensaje al que se responde
                    if (msg.replyToId != null) {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.3f))
                                .padding(8.dp)
                        ) {
                            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(customColors.linkColor))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    msg.replyToName ?: "Usuario",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.linkColor
                                )
                                Text(
                                    msg.replyToText ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    if (msg.deleted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.width(4.dp))
                            Text("Este mensaje fue eliminado", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Color.White.copy(alpha = 0.9f))
                        }
                    } else {
                        if (msg.imageUrl != null) {
                            AsyncImage(
                                model = msg.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (msg.text.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (msg.videoUrl != null) {
                            VideoPlayer(
                                videoUrl = msg.videoUrl,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            if (msg.text.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (msg.fileUrl != null) {
                            FileBubble(msg.fileName ?: "Archivo", msg.fileUrl, isDark)
                            if (msg.text.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (msg.text.isNotBlank()) {
                            if (msg.type == "aviso_postulacion" || msg.type == "solicitud_compartida") {
                                RequestAlertBubble(
                                    text = msg.text,
                                    isMe = isMe,
                                    solicitudId = msg.metadata["solicitudId"] ?: "",
                                    onViewClick = { onViewPostulacion(msg.metadata["solicitudId"] ?: "") }
                                )
                            } else {
                                Text(
                                    text = buildAnnotatedString {
                                        append(msg.text)
                                        if (msg.edited) {
                                            withStyle(SpanStyle(fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))) {
                                                append(" (editado)")
                                            }
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = formatMessageDate(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color.LightGray else Color.DarkGray,
                    fontSize = 10.sp
                )
                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val isRead = msg.readBy.size > 1
                    Icon(
                        imageVector = if (isRead) Icons.Default.Visibility else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isRead) Color(0xFF00B4D8) else (if (isDark) Color.LightGray else Color.Gray)
                    )
                }
            }
        }
    }
}

@Composable
fun RequestAlertBubble(text: String, isMe: Boolean, solicitudId: String, onViewClick: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(solicitudId) {
        if (solicitudId.isNotEmpty()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("solicitudes").document(solicitudId).get().await()
                status = if (doc.exists()) doc.getString("estado") ?: "abierta" else "inexistente"
            } catch (e: Exception) {
                status = "error"
            }
        }
    }

    val statusText = when(status) {
        "abierta" -> "DISPONIBLE"
        "cerrada" -> "YA TOMADA"
        "finalizada" -> "FINALIZADA"
        "inexistente" -> "CANCELADA/ELIMINADA"
        "error" -> "ERROR"
        else -> "..."
    }
    
    val statusColor = when(status) {
        "abierta" -> Color(0xFF4CAF50)
        "cerrada" -> Color(0xFFF44336)
        "finalizada" -> Color.Gray
        "inexistente" -> Color.Gray
        else -> Color.Gray.copy(alpha = 0.5f)
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Bold, 
                modifier = Modifier.weight(1f),
                color = if (isMe) Color.White else Color.Black
            )
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Black
                )
            }
        }
        
        if (status == "abierta") {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onViewClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMe) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Assignment, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("VER SOLICITUD", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FileBubble(fileName: String, fileUrl: String, isDark: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        color = if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .clickable {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(fileUrl))
                context.startActivity(intent)
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (isDark) Color.LightGray else Color.DarkGray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = "Toca para abrir",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color.LightGray else Color.DarkGray
                )
            }
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = if (isDark) Color.LightGray else Color.DarkGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
        }
    }

    DisposableEffect(videoUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = true
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

private fun formatMessageDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) -> {
            SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
