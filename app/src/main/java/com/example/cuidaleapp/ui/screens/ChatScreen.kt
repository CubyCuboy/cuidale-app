package com.example.cuidaleapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.ChatMessage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUser: UserData,
    myUid: String,
    myName: String,
    isDark: Boolean = false,
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onViewOtherProfile: (String) -> Unit,
    favoritosViewModel: com.example.cuidaleapp.viewmodel.FavoritosViewModel? = null
) {
    val messages by viewModel.messages.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val otherStatus by viewModel.otherUserStatus.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pinnedMessages by viewModel.pinnedMessages.collectAsState()

    val favs by favoritosViewModel?.favoritos?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val noDeseados by favoritosViewModel?.noDeseados?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val isFavorite = favs.contains(otherUser.uid)
    val iHaveBlocked = noDeseados.contains(otherUser.uid)
    
    var inputText by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showPinnedMessages by remember { mutableStateOf(false) }

    val customColors = CuidaleThemeExtras.customColors

    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToMenu by remember { mutableStateOf<ChatMessage?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadFile(chatId, it, context, onSuccess = {}, onError = {})
        }
    }

    LaunchedEffect(chatId) {
        viewModel.listenMessages(chatId)
        viewModel.listenUserStatus(otherUser.uid)
        viewModel.archiveOldMessages(chatId) // Archivar mensajes viejos al entrar
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
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
                                Text(otherUser.nombre + " " + otherUser.apellido_pa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!isSearchActive) {
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
                                text = { Text(if (iHaveBlocked) "Desbloquear" else "Bloquear", color = Color.Red) },
                                onClick = { 
                                    showTopMenu = false
                                    favoritosViewModel?.toggleNoDeseado(otherUser.uid)
                                },
                                leadingIcon = { Icon(Icons.Default.Block, null, tint = Color.Red) }
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
            val filteredMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true
            ) {
                items(filteredMessages.reversed()) { msg ->
                    if (msg.senderId == "system") {
                        SystemMessage(msg.text, isDark)
                    } else {
                        ChatBubble(
                            msg = msg,
                            isMe = msg.senderId == myUid,
                            isDark = isDark,
                            onLongClick = {
                                if (!isBlocked) {
                                    messageToMenu = msg
                                    showMenu = true
                                }
                            }
                        )
                    }
                }
            }

            if (showPinnedMessages) {
                ModalBottomSheet(onDismissRequest = { showPinnedMessages = false }) {
                    Column(modifier = Modifier.fillMaxHeight(0.6f).padding(16.dp)) {
                        Text("Mensajes Fijados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pinnedMessages) { msg ->
                                val authorName = if (msg.senderId == myUid) "Tú" else otherUser.nombre
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        text = authorName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.LightGray else Color.Gray,
                                        modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                                    )
                                    ChatBubble(msg = msg, isMe = msg.senderId == myUid, isDark = isDark)
                                }
                            }
                        }
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
                            if (messageToMenu?.senderId == myUid && !messageToMenu!!.isDeleted) {
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
                                viewModel.togglePin(chatId, messageToMenu!!.id, messageToMenu!!.isPinned, myName)
                                showMenu = false
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PushPin, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (messageToMenu?.isPinned == true) "Desfijar" else "Fijar")
                                }
                            }
                        }
                    }
                )
            }

            // Input area
            Surface(
                tonalElevation = 2.dp,
                color = if (isDark) Color(0xFF1F3B46) else Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBlocked || iHaveBlocked) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (iHaveBlocked) "Has bloqueado a este usuario." 
                                  else "Parece ser que ya no podrás contactar con este usuario a menos que vuelva a abrir la conversación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(8.dp).windowInsetsPadding(WindowInsets.navigationBars),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { mediaPicker.launch("*/*") },
                            enabled = !isUploading
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = customColors.linkColor)
                            } else {
                                Icon(Icons.Default.Add, null, tint = customColors.linkColor)
                            }
                        }
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text(if (messageToEdit != null) "EDITANDO MENSAJE..." else "ESCRIBE TU MENSAJE AQUÍ", color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray) },
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
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
                                    if (messageToEdit != null) {
                                        viewModel.editMessage(chatId, messageToEdit!!.id, inputText)
                                        messageToEdit = null
                                    } else {
                                        viewModel.sendMessage(chatId, inputText)
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
    onLongClick: () -> Unit = {}
) {
    val bubbleColor = if (isMe) {
        if (isDark) Color(0xFF4B0082).copy(alpha = 0.8f) else Color(0xFFD8BFD8)
    } else {
        if (isDark) Color(0xFF1E3A5F) else Color(0xFFB0C4DE)
    }
    val textColor = if (isDark) Color.White else Color.Black
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMe) RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (msg.isPinned) 16.dp else 0.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = alignment
    ) {
        if (msg.isPinned) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-12).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PushPin, null, modifier = Modifier.size(10.dp), tint = Color.Gray)
                Spacer(Modifier.width(4.dp))
                Text("Mensaje fijado", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                color = bubbleColor,
                shape = shape,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (msg.isDeleted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(14.dp), tint = if (isDark) Color.LightGray else Color.Gray)
                            Spacer(Modifier.width(4.dp))
                            Text("Este mensaje fue eliminado", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = if (isDark) Color.LightGray else Color.Gray)
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
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                                if (msg.isEdited) {
                                    Text(
                                        text = " (editado)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (isDark) Color.LightGray.copy(alpha = 0.7f) else Color.Gray,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                    )
                                }
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
                        imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
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
                // Ajustar el tamaño del reproductor para que no sea demasiado alto
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
