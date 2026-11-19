@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.cuidaleapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.FileProvider
import kotlin.math.roundToInt
import java.io.File
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.fadingEdge
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.models.ChatMessage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ChatViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver
import com.example.cuidaleapp.utils.MediaCompressor
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
    val outbox by viewModel.outbox.collectAsState()
    val pendingOutbox = outbox.filter { it.chatId == chatId }
    val liveUsers by viewModel.usersMap.collectAsState()
    // Usa los datos más frescos del usuario (Firestore) si están disponibles.
    val liveOtherUser = liveUsers[otherUser.uid]?.let { it.copy(uid = otherUser.uid) } ?: otherUser
    val otherStatus by viewModel.otherUserStatus.collectAsState()
    val isOtherTyping by viewModel.isOtherTyping.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var searchMediaFilter by remember { mutableStateOf<String?>(null) }
    var searchSenderFilter by remember { mutableStateOf<String?>(null) }

    val filteredMessages = remember(messages, searchQuery, searchMediaFilter, searchSenderFilter) {
        val base = if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
        val bySender = when (searchSenderFilter) {
            null -> base
            "me" -> base.filter { it.senderId == myUid }
            "other" -> base.filter { it.senderId != myUid && it.senderId != "system" }
            else -> base
        }
        when (searchMediaFilter) {
            null -> bySender
            "media" -> bySender.filter { it.imageUrl != null || it.videoUrl != null || it.fileUrl != null || messageHasMediaType(it, "media") }
            "image" -> bySender.filter { messageHasMediaType(it, "image") }
            "video" -> bySender.filter { messageHasMediaType(it, "video") }
            "file" -> bySender.filter { messageHasMediaType(it, "file") }
            else -> bySender
        }
    }
    
    val pinnedMessages = remember(messages) {
        messages.filter { it.pinned && !it.deleted }
    }

    val favs by favoritosViewModel?.favoritos?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val bloqueados by favoritosViewModel?.bloqueados?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    val noDeseados by favoritosViewModel?.noDeseados?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
    
    var showShareRequests by remember { mutableStateOf(false) }
    var showShareTasks by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    val pendingMediaItems = remember { mutableStateListOf<PendingMediaItem>() }
    var showMediaWarning by remember { mutableStateOf(false) }
    var pendingMediaAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val mediaLimitReached = pendingMediaItems.size >= 10 || pendingMediaItems.sumOf { it.sizeBytes } >= MediaCompressor.MAX_MEDIA_BYTES
    var myRequests by remember { mutableStateOf<List<SolicitudCuidado>>(emptyList()) }
    var activeTasks by remember { mutableStateOf<List<com.example.cuidaleapp.models.RegistroTarea>>(emptyList()) }    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val primaryColor = MaterialTheme.colorScheme.primary

    var inputText by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showPinnedMessages by remember { mutableStateOf(false) }
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val selectedMessages = remember { mutableStateListOf<ChatMessage>() }

    // Para volver a la búsqueda con el botón de regresar después de haber
    // navegado a un mensaje desde los resultados.
    var pendingReturnToSearch by remember { mutableStateOf(false) }
    var savedSearchQuery by remember { mutableStateOf("") }
    var savedSearchMediaFilter by remember { mutableStateOf<String?>(null) }
    var savedSearchSenderFilter by remember { mutableStateOf<String?>(null) }

    // Indicador de recuperación de mensajes al entrar al chat (caché vacía,
    // conexión lenta o primera carga). Se oculta al llegar mensajes o a los 2.5s.
    var isRecovering by remember { mutableStateOf(true) }
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) isRecovering = false
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        isRecovering = false
    }

    // Si venimos de una búsqueda y navegamos a un mensaje, el botón de
    // regresar del sistema vuelve a la búsqueda (con sus filtros) en lugar de
    // salir del chat.
    androidx.activity.compose.BackHandler(enabled = (isSearchActive || pendingReturnToSearch) && selectedMessages.isEmpty()) {
        when {
            isSearchActive -> {
                // En el menú de búsqueda, volver regresa al chat normal.
                viewModel.setSearchQuery("")
                searchMediaFilter = null
                searchSenderFilter = null
                isSearchActive = false
                pendingReturnToSearch = false
            }
            pendingReturnToSearch -> {
                // Después de navegar a un mensaje, volver restaura la búsqueda.
                isSearchActive = true
                viewModel.setSearchQuery(savedSearchQuery)
                searchMediaFilter = savedSearchMediaFilter
                searchSenderFilter = savedSearchSenderFilter
                pendingReturnToSearch = false
            }
        }
    }

    // Precarga el multimedia de los mensajes recientes a la caché local
    // (imágenes vía Coil, primer frame de videos vía VideoThumbnailCache).
    // Así, al entrar al chat, lo ya visto no se vuelve a descargar visiblemente.
    LaunchedEffect(messages) {
        val urls = buildList {
            messages.takeLast(30).forEach { m ->
                if (m.imageUrl != null) add(Triple("image", m.imageUrl, ""))
                if (m.videoUrl != null) add(Triple("video", m.videoUrl, ""))
                if (m.type == "media_group") {
                    runCatching { com.example.cuidaleapp.ui.components.parseMediaBatch(m.metadata) }
                        .getOrDefault(emptyList())
                        .forEach { (kind, url, _) -> add(Triple(kind, url, "")) }
                }
            }
        }
        urls.forEach { (kind, url, _) ->
            when (kind) {
                "image" -> runCatching {
                    coil3.SingletonImageLoader.get(context).enqueue(
                        coil3.request.ImageRequest.Builder(context)
                            .data(url)
                            .size(512)
                            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .build()
                    )
                }
                "video" -> com.example.cuidaleapp.ui.components.VideoThumbnailCache.fetch(context, url)
            }
        }
    }

    // ── Flujo de multimedia: primero se agregan a una cola local para
    //    previsualizar y poder borrar antes de enviar. ──
    fun requestMediaLaunch(action: () -> Unit) {
        if (mediaLimitReached) {
            mediaError = context.getString(R.string.chat_media_limit_reached)
            return
        }
        val prefs = context.getSharedPreferences("cuidale_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("media_warning_accepted", false)) {
            action()
        } else {
            pendingMediaAction = action
            showMediaWarning = true
        }
    }

    fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) { null }
    }

    fun addPendingMedia(uri: Uri, kind: String): AddMediaResult {
        val resolvedKind = when (kind) {
            "image", "video", "file" -> kind
            else -> "file"
        }
        // Evita duplicados exactos del mismo archivo/URI.
        if (pendingMediaItems.any { it.uri == uri }) return AddMediaResult.DUPLICATE

        val size = runCatching { MediaCompressor.getSize(context, uri) }.getOrDefault(0L)
        val currentCount = pendingMediaItems.size
        val currentBytes = runCatching { pendingMediaItems.sumOf { it.sizeBytes } }.getOrDefault(0L)

        if (currentCount >= 10) return AddMediaResult.COUNT_LIMIT
        if (size > MediaCompressor.MAX_MEDIA_BYTES) return AddMediaResult.SIZE_LIMIT
        if (currentBytes + size > MediaCompressor.MAX_MEDIA_BYTES) return AddMediaResult.TOTAL_LIMIT

        val name = when (resolvedKind) {
            "file" -> queryDisplayName(uri) ?: context.getString(R.string.chat_filename_document)
            "video" -> queryDisplayName(uri) ?: context.getString(R.string.chat_filename_video)
            else -> queryDisplayName(uri) ?: context.getString(R.string.chat_filename_image)
        }
        pendingMediaItems.add(PendingMediaItem(uri = uri, kind = resolvedKind, name = name, sizeBytes = size))
        mediaError = null
        return AddMediaResult.ADDED
    }

    suspend fun uploadSingleMedia(item: PendingMediaItem, replyMsg: ChatMessage?) {
        val extension = when (item.kind) {
            "image" -> ".jpg"
            "video" -> ".mp4"
            else -> ".dat"
        }
        val storageRef = FirebaseStorage.getInstance().reference
            .child("chat_media")
            .child(chatId)
            .child("${System.currentTimeMillis()}-${(0 until 100000).random()}$extension")

        val downloadUrl: String
        when (item.kind) {
            "image" -> {
                // Streaming directo (sin cargar la imagen completa en memoria) para
                // evitar OutOfMemory con imágenes pesadas.
                val input = context.contentResolver.openInputStream(item.uri) ?: throw Exception(context.getString(R.string.chat_error_read_file))
                input.use { stream -> storageRef.putStream(stream).await() }
                downloadUrl = storageRef.downloadUrl.await().toString()
                viewModel.sendMessage(
                    chatId = chatId,
                    text = "",
                    imageUrl = downloadUrl,
                    type = "image",
                    replyToId = replyMsg?.id,
                    replyToText = replyMsg?.let { com.example.cuidaleapp.ui.components.replyLabelFor(it, context) },
                    replyToName = if (replyMsg?.senderId == myUid) context.getString(R.string.chat_me_short) else otherUser.nombre,
                    metadata = replyMsg?.let { com.example.cuidaleapp.ui.components.replyPreviewMetadataFor(it) } ?: emptyMap()
                )
            }
            "video" -> {
                val input = context.contentResolver.openInputStream(item.uri) ?: throw Exception(context.getString(R.string.chat_error_read_file))
                input.use { stream -> storageRef.putStream(stream).await() }
                downloadUrl = storageRef.downloadUrl.await().toString()
                viewModel.sendMessage(
                    chatId = chatId,
                    text = "",
                    videoUrl = downloadUrl,
                    type = "video",
                    replyToId = replyMsg?.id,
                    replyToText = replyMsg?.let { com.example.cuidaleapp.ui.components.replyLabelFor(it, context) },
                    replyToName = if (replyMsg?.senderId == myUid) context.getString(R.string.chat_me_short) else otherUser.nombre,
                    metadata = replyMsg?.let { com.example.cuidaleapp.ui.components.replyPreviewMetadataFor(it) } ?: emptyMap()
                )
            }
            else -> {
                val input = context.contentResolver.openInputStream(item.uri) ?: throw Exception(context.getString(R.string.chat_error_read_file))
                input.use { stream -> storageRef.putStream(stream).await() }
                downloadUrl = storageRef.downloadUrl.await().toString()
                viewModel.sendMessage(
                    chatId = chatId,
                    text = "",
                    fileUrl = downloadUrl,
                    fileName = item.name,
                    type = "file",
                    replyToId = replyMsg?.id,
                    replyToText = replyMsg?.let { com.example.cuidaleapp.ui.components.replyLabelFor(it, context) },
                    replyToName = if (replyMsg?.senderId == myUid) context.getString(R.string.chat_me_short) else otherUser.nombre,
                    metadata = replyMsg?.let { com.example.cuidaleapp.ui.components.replyPreviewMetadataFor(it) } ?: emptyMap()
                )
            }
        }
    }

    /**
     * Sube un solo elemento y devuelve (kind, url, name) sin enviar mensaje,
     * para agrupar el lote en un único mensaje estilo WhatsApp.
     */
    suspend fun uploadMediaForBatch(item: PendingMediaItem): Triple<String, String, String> {
        val extension = when (item.kind) {
            "image" -> ".jpg"
            "video" -> ".mp4"
            else -> ".dat"
        }
        val storageRef = FirebaseStorage.getInstance().reference
            .child("chat_media")
            .child(chatId)
            .child("${System.currentTimeMillis()}-${(0 until 100000).random()}$extension")
        val downloadUrl: String
        when (item.kind) {
            "image" -> {
                val compressed = MediaCompressor.compressImage(context, item.uri) ?: throw Exception(context.getString(R.string.chat_error_compress_image))
                storageRef.putBytes(compressed).await()
                downloadUrl = storageRef.downloadUrl.await().toString()
            }
            "video" -> {
                val input = context.contentResolver.openInputStream(item.uri) ?: throw Exception(context.getString(R.string.chat_error_read_file))
                input.use { stream -> storageRef.putStream(stream).await() }
                downloadUrl = storageRef.downloadUrl.await().toString()
            }
            else -> {
                val input = context.contentResolver.openInputStream(item.uri) ?: throw Exception(context.getString(R.string.chat_error_read_file))
                input.use { stream -> storageRef.putStream(stream).await() }
                downloadUrl = storageRef.downloadUrl.await().toString()
            }
        }
        return Triple(item.kind, downloadUrl, item.name)
    }

    fun sendPendingMedia() {
        if (isUploadingMedia || pendingMediaItems.isEmpty()) return
        scope.launch {
            try {
            isUploadingMedia = true
            val replyMsg = replyingToMessage
            val items = pendingMediaItems.toList()
            var firstError: String? = null

            // Un solo elemento: se envía como mensaje multimedia normal.
            if (items.size == 1) {
                val item = items[0]
                if (item in pendingMediaItems) {
                    runCatching { uploadSingleMedia(item, replyMsg) }
                        .onSuccess { pendingMediaItems.remove(item); replyingToMessage = null }
                        .onFailure { firstError = it.message ?: context.getString(R.string.chat_error_send_media) }
                }
            } else if (items.size > 1) {
                // Varios elementos: se suben todos y se agrupan en UN mensaje.
                val uploaded = mutableListOf<Triple<String, String, String>>()
                val succeededIdx = mutableSetOf<Int>()
                items.forEachIndexed { index, item ->
                    if (item !in pendingMediaItems) return@forEachIndexed
                    runCatching { uploadMediaForBatch(item) }
                        .onSuccess {
                            uploaded.add(it)
                            succeededIdx.add(index)
                        }
                        .onFailure { e ->
                            firstError = firstError ?: (e.message ?: context.getString(R.string.chat_error_send_media))
                        }
                }
                if (uploaded.isNotEmpty()) {
                    runCatching { viewModel.sendMediaBatch(chatId, uploaded) }
                    replyingToMessage = null
                    // Pre-cachea el lote recién enviado para que quede disponible
                    // offline sin volver a descargarlo.
                    uploaded.forEach { (kind, url, _) ->
                        when (kind) {
                            "image" -> runCatching {
                                coil3.SingletonImageLoader.get(context).enqueue(
                                    coil3.request.ImageRequest.Builder(context)
                                        .data(url)
                                        .size(512)
                                        .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                                        .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                                        .build()
                                )
                            }
                            "video" -> com.example.cuidaleapp.ui.components.VideoThumbnailCache.fetch(context, url)
                        }
                    }
                }
                // Eliminar de golpe los que sí se subieron (no uno a uno).
                val removed = items.filterIndexed { index, _ -> index in succeededIdx }.toSet()
                pendingMediaItems.removeAll { it in removed }
            }

            mediaError = firstError
            } catch (t: Throwable) {
                Log.e("ChatScreen", "sendPendingMedia falló", t)
                mediaError = context.getString(R.string.chat_error_send_media)
            } finally {
                isUploadingMedia = false
            }
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            when (addPendingMedia(uri, "image")) {
                AddMediaResult.SIZE_LIMIT -> {
                    val size = runCatching { MediaCompressor.getSize(context, uri) }.getOrDefault(0L)
                    mediaError = context.getString(R.string.chat_error_photo_size, com.example.cuidaleapp.utils.AppCacheManager.formatBytes(size))
                }
                AddMediaResult.COUNT_LIMIT, AddMediaResult.TOTAL_LIMIT -> {
                    mediaError = context.getString(R.string.chat_error_photo_limit, pendingMediaItems.size)
                }
                else -> {}
            }
        } else if (uri != null) {
            mediaError = context.getString(R.string.chat_error_photo_capture)
        }
        cameraUri = null
    }

    val openCamera: () -> Unit = {
        try {
            val pm = context.packageManager
            if (!pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
                mediaError = context.getString(R.string.chat_error_no_camera)
            } else {
                val file = File(context.cacheDir, "cam_chat_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri = uri
                cameraLauncher.launch(uri)
            }
        } catch (e: Exception) {
            mediaError = e.message ?: context.getString(R.string.chat_error_open_camera)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera()
    }

    val launchCamera: () -> Unit = {
        val permission = android.Manifest.permission.CAMERA
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else cameraPermissionLauncher.launch(permission)
    }

    // Selector nativo múltiple (imágenes + videos). Se pide el máximo posible;
    // el filtrado estricto ocurre en addPendingMedia (nunca se supera 10/25MB).
    // Selector nativo múltiple (imágenes + videos). Limita la selección a los
    // cupos disponibles para que el usuario no seleccione de más ni se descarte
    // nada en silencio. IMPORTANTE: PickMultipleVisualMedia exige maxItems > 1,
    // así que cuando la cola llega a 10 no se puede pedir 1 (crash).
    val mediaPickerLauncher = key(pendingMediaItems.size) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = (10 - pendingMediaItems.size).coerceIn(2, 10))
        ) { uris ->
            var added = 0
            var countLimit = false
            var sizeLimit = false
            var totalLimit = false
            val overSized = mutableListOf<Pair<String, String>>() // (nombre, peso legible)
            try {
            uris.forEach { uri ->
                if (pendingMediaItems.size >= 10) { countLimit = true; return@forEach }
                val kind = if (MediaCompressor.isVideo(context, uri)) "video" else "image"
                when (addPendingMedia(uri, kind)) {
                    AddMediaResult.ADDED -> added++
                    AddMediaResult.COUNT_LIMIT -> countLimit = true
                    AddMediaResult.SIZE_LIMIT -> {
                        sizeLimit = true
                        val size = runCatching { MediaCompressor.getSize(context, uri) }.getOrDefault(0L)
                        val nombreArchivo = queryDisplayName(uri) ?: context.getString(R.string.chat_filename_file)
                        overSized.add(nombreArchivo to com.example.cuidaleapp.utils.AppCacheManager.formatBytes(size))
                    }
                    AddMediaResult.TOTAL_LIMIT -> totalLimit = true
                    else -> {}
                }
            }
            val totalAdjuntos = pendingMediaItems.size
            mediaError = when {
                sizeLimit -> {
                    val detalle = overSized.joinToString("; ") { (n, s) -> "$n ($s)" }
                    context.getString(R.string.chat_error_media_size_overall, detalle, totalAdjuntos) +
                        " Envíalo en un mensaje aparte."
                }
                totalLimit -> context.getString(R.string.chat_error_media_total_limit, totalAdjuntos) +
      " El último archivo supera el máximo; envíalo en un mensaje aparte."
                countLimit -> context.getString(R.string.chat_error_media_count_limit, totalAdjuntos)
                added == 0 && uris.isNotEmpty() -> context.getString(R.string.chat_error_media_add_failed)
                else -> null
            }
            } catch (t: Throwable) {
                Log.e("ChatScreen", "media picker falló", t)
                mediaError = context.getString(R.string.chat_error_media_add_failed)
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            when (addPendingMedia(uri, "file")) {
                AddMediaResult.COUNT_LIMIT -> {
                    mediaError = context.getString(R.string.chat_error_media_count_limit, pendingMediaItems.size)
                    showMediaSheet = true
                }
                AddMediaResult.SIZE_LIMIT -> {
                    val size = runCatching { MediaCompressor.getSize(context, uri) }.getOrDefault(0L)
                    mediaError = context.getString(R.string.chat_error_doc_size, queryDisplayName(uri) ?: context.getString(R.string.chat_filename_document), com.example.cuidaleapp.utils.AppCacheManager.formatBytes(size), pendingMediaItems.size)
                    showMediaSheet = true
                }
                AddMediaResult.TOTAL_LIMIT -> {
                    mediaError = context.getString(R.string.chat_error_media_total_limit, pendingMediaItems.size)
                    showMediaSheet = true
                }
                else -> {}
            }
        }
    }
    
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
  var showFirstChatDisclaimer by remember {
      mutableStateOf(
          !context.getSharedPreferences("cuidale_prefs", android.content.Context.MODE_PRIVATE)
              .getBoolean("first_chat_disclaimer_done", false)
      )
  }
    var fullImageUrl by remember { mutableStateOf<String?>(null) }
    var fullVideoUrl by remember { mutableStateOf<String?>(null) }
    var galleryMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var galleryStartUrl by remember { mutableStateOf<String?>(null) }

    /**
     * Navega al mensaje de origen (haciendo scroll y resaltándolo) recordando
     * la búsqueda actual para que el botón de regresar vuelva a ella.
     */
    fun goToMessage(msg: ChatMessage) {
        savedSearchQuery = searchQuery
        savedSearchMediaFilter = searchMediaFilter
        savedSearchSenderFilter = searchSenderFilter
        pendingReturnToSearch = true

        isSearchActive = false
        viewModel.setSearchQuery("")
        searchMediaFilter = null
        searchSenderFilter = null

        // +1 por el item de cabecera ("escribiendo...") en el LazyColumn.
        val index = messages.reversed().indexOfFirst { it.id == msg.id }
        if (index != -1) {
            scope.launch {
                val targetIndex = index + 1
                // Si el mensaje está lejos, salta directo (más fluido); si está
                // cerca, anima el desplazamiento.
                val current = listState.firstVisibleItemIndex
                if (kotlin.math.abs(targetIndex - current) > 20) {
                    listState.scrollToItem(targetIndex)
                } else {
                    listState.animateScrollToItem(targetIndex)
                }
                highlightedMessageId = msg.id
                kotlinx.coroutines.delay(2000)
                highlightedMessageId = null
            }
        }
    }
    
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
            // No forzar "desconectado" al salir del chat: la presencia depende de la
            // visibilidad global de la app (MenuActivity), no de si estás en el chat.
            viewModel.clearActiveChatListeners()
            viewModel.setTyping(chatId, false)
            com.example.cuidaleapp.ui.components.VideoPlayerCache.releaseAll()
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPinConfirmDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {



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
                                Text(stringResource(R.string.chat_selected_count, selectedMessages.size), fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (allFromMe) stringResource(R.string.chat_selected_all_from_me) else stringResource(R.string.chat_selected_only_pin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        } else if (isSearchActive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Search, null, tint = Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it); pendingReturnToSearch = false },
                                    placeholder = { Text(if (isOnline) stringResource(R.string.chat_search_placeholder) else stringResource(R.string.connectivity_offline_short), color = Color.Gray, fontSize = 14.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = isOnline,
                                    colors = TextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery(""); searchMediaFilter = null }) {
                                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onViewOtherProfile(otherUser.uid) }
                            ) {
                                Box(modifier = Modifier.size(42.dp)) {
                                    Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color.Gray.copy(alpha = 0.1f)) {
                                        if (liveOtherUser.fotoUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(liveOtherUser.fotoUrl)
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
                                    val statusDotColor = when {
                                        !isOnline -> Color.Gray
                                        isBlocked -> Color.Red
                                        otherStatus == "en línea" -> Color(0xFF4CAF50)
                                        otherStatus == "inactivo" -> Color(0xFFF1C40F)
                                        else -> Color.Gray
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(statusDotColor)
                                            .align(Alignment.BottomEnd)
                                            .border(2.dp, if (isDark) Color(0xFF1B263B) else Color.White, CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = liveOtherUser.nombre, 
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
                                    val statusTextColor = when {
                                        !isOnline -> Color.Gray
                                        isBlocked -> Color.Red
                                        otherStatus == "en línea" -> Color(0xFF4CAF50)
                                        otherStatus == "inactivo" -> Color(0xFFF1C40F)
                                        else -> Color.Gray
                                    }
                                    Text(
                                        text = if (!isOnline) stringResource(R.string.chat_status_offline) else if (isBlocked) stringResource(R.string.chat_status_blocked) else otherStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusTextColor,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            when {
                                selectedMessages.isNotEmpty() -> selectedMessages.clear()
                                isSearchActive -> {
                                    // El botón de regresar cierra la búsqueda.
                                    viewModel.setSearchQuery("")
                                    searchMediaFilter = null
                                    searchSenderFilter = null
                                    isSearchActive = false
                                }
                                else -> onNavigateBack()
                            }
                        }) {
                            Icon(
                                when {
                                    selectedMessages.isNotEmpty() -> Icons.Default.Close
                                    isSearchActive -> Icons.Default.Close
                                    else -> Icons.AutoMirrored.Filled.ArrowBack
                                },
                                null
                            )
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
                            IconButton(onClick = {
                                if (isSearchActive) {
                                    viewModel.setSearchQuery("")
                                    searchMediaFilter = null
                                    searchSenderFilter = null
                                }
                                isSearchActive = !isSearchActive
                            }) { Icon(Icons.Default.Search, null) }
                            IconButton(onClick = { showPinnedMessages = true }) { 
                                BadgedBox(badge = { if (pinnedMessages.isNotEmpty()) Badge { Text(pinnedMessages.size.toString()) } }) {
                                    Icon(Icons.Default.PushPin, null)
                                }
                            }
                            var showTopMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.chat_menu_view_profile)) }, onClick = { showTopMenu = false; onViewOtherProfile(otherUser.uid) }, leadingIcon = { Icon(Icons.Default.Person, null) })
                                DropdownMenuItem(text = { Text(if (isFavorite) stringResource(R.string.chat_menu_remove_fav) else stringResource(R.string.chat_menu_add_fav)) }, onClick = { showTopMenu = false; favoritosViewModel?.toggleFavorito(otherUser.uid) }, enabled = isOnline, leadingIcon = { Icon(Icons.Default.Star, null, tint = if (isFavorite) Color.Yellow else Color.Gray) })
                                DropdownMenuItem(text = { Text(if (iHaveBlocked) stringResource(R.string.chat_unblock) else stringResource(R.string.chat_block), color = if (iHaveBlocked) Color.Unspecified else Color.Red) }, onClick = { showTopMenu = false; favoritosViewModel?.toggleBloqueo(otherUser.uid) }, enabled = isOnline, leadingIcon = { Icon(if (iHaveBlocked) Icons.Default.CheckCircle else Icons.Default.Block, null, tint = if (iHaveBlocked) Color(0xFF4CAF50) else Color.Red) })
                                DropdownMenuItem(text = { Text(if (iHaveUnwanted) stringResource(R.string.chat_menu_show_in_search) else stringResource(R.string.chat_menu_hide_from_search)) }, onClick = { showTopMenu = false; favoritosViewModel?.toggleNoDeseado(otherUser.uid) }, enabled = isOnline, leadingIcon = { Icon(if (iHaveUnwanted) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) })
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
                if (!isSearchActive && pinnedMessages.isNotEmpty()) {
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

                if (isSearchActive) {
                    Surface(
                        color = if (isDark) Color(0xFF141F2E) else Color(0xFFE8EDF4),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val senderFilters = listOf(
                                null to stringResource(R.string.chat_filter_all),
                                "me" to stringResource(R.string.chat_filter_me),
                                "other" to otherUser.nombre
                            )
                            val filters = listOf(
                                null to Pair(Icons.Default.Search, stringResource(R.string.chat_filter_all)),
                                "media" to Pair(Icons.AutoMirrored.Filled.InsertDriveFile, stringResource(R.string.chat_filter_media)),
                                "image" to Pair(Icons.Default.Image, stringResource(R.string.chat_filter_photos)),
                                "video" to Pair(Icons.Default.VideoLibrary, stringResource(R.string.chat_filter_videos)),
                                "file" to Pair(Icons.Default.Description, stringResource(R.string.chat_filter_docs))
                            )
                            Text(
                                text = stringResource(R.string.chat_filter_from),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            // Fila horizontal con scroll: nunca se comprime en pantallas angostas.
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(end = 8.dp)
                            ) {
                                items(senderFilters) { (value, label) ->
                                    FilterChip(
                                        selected = searchSenderFilter == value,
                                        onClick = { searchSenderFilter = value },
                                        label = { Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        enabled = isOnline
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.chat_filter_type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(end = 8.dp)
                            ) {
                                items(filters) { (value, info) ->
                                    FilterChip(
                                        selected = searchMediaFilter == value,
                                        onClick = { searchMediaFilter = value },
                                        label = { Text(info.second, fontSize = 12.sp) },
                                        leadingIcon = { Icon(info.first, null, modifier = Modifier.size(16.dp)) },
                                        enabled = isOnline
                                    )
                                }
                            }
                        }
                    }
                    if (searchQuery.isNotBlank() || searchMediaFilter != null || searchSenderFilter != null) {
                        Text(
                            text = stringResource(R.string.chat_results_count, filteredMessages.size, if (filteredMessages.size == 1) stringResource(R.string.chat_result_one) else stringResource(R.string.chat_results_many)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // En modo búsqueda no hay barra de escritura, así que
                        // evitamos que los resultados queden bajo la navegación.
                        .then(if (isSearchActive) Modifier.navigationBarsPadding() else Modifier)
                ) {
                    when {
                        !isOnline && filteredMessages.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                                    Spacer(Modifier.height(16.dp))
                                    Text(stringResource(R.string.connectivity_offline), fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text(stringResource(R.string.chat_error_load_messages), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                        isSearchActive && (searchQuery.isNotBlank() || searchMediaFilter != null || searchSenderFilter != null) && filteredMessages.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(56.dp), tint = Color.Gray.copy(alpha = 0.5f))
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.chat_search_no_results), fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text(stringResource(R.string.chat_search_no_results_desc), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                        isSearchActive && searchMediaFilter != null -> {
                            SearchMediaGallery(
                                items = filteredMessages,
                                kindFilter = searchMediaFilter,
                                onImageClick = { fullImageUrl = it },
                                onVideoClick = { fullVideoUrl = it },
                                onOpenBatch = { msg, url ->
                                    galleryMessage = msg
                                    galleryStartUrl = url
                                },
                                onGoToMessage = { msg -> goToMessage(msg) }
                            )
                        }
                        isSearchActive -> {
                            SearchResultsList(
                                messages = filteredMessages,
                                myUid = myUid,
                                isDark = isDark,
                                onImageClick = { fullImageUrl = it },
                                onVideoClick = { fullVideoUrl = it },
                                onOpenBatch = { msg, url ->
                                    galleryMessage = msg
                                    galleryStartUrl = url
                                },
                                onGoToMessage = { msg -> goToMessage(msg) }
                            )
                        }
                        else -> {
                            if (isOnline && messages.isEmpty() && isRecovering) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                        Spacer(Modifier.height(12.dp))
                                        Text(stringResource(R.string.chat_recovering), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                    }
                                }
                            } else {
                                val chatItems = remember(filteredMessages) {
                                    val asc = buildList {
                                        var lastDay: Pair<Int, Int>? = null
                                        filteredMessages.sortedBy { it.timestamp }.forEach { m ->
                                            val c = Calendar.getInstance().apply { timeInMillis = m.timestamp }
                                            val day = c.get(Calendar.YEAR) to c.get(Calendar.DAY_OF_YEAR)
                                            if (day != lastDay) {
                                                add(ChatListItem.Header(m.timestamp))
                                                lastDay = day
                                            }
                                            add(ChatListItem.Message(m))
                                        }
                                    }
                                    asc.asReversed()
                                }
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
                                                    Text(stringResource(R.string.chat_typing, otherUser.nombre), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                }
                                            }
                                        }
                                    }
                                    items(items = chatItems, key = {
                                        when (it) {
                                            is ChatListItem.Header -> "h-${it.timestamp}"
                                            is ChatListItem.Message -> "m-${it.msg.id}"
                                        }
                                    }) { entry ->
                                        when (entry) {
                                            is ChatListItem.Header -> DayDivider(entry.timestamp)
                                            is ChatListItem.Message -> {
                                                val msg = entry.msg
                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = true,
                                                    enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f, animationSpec = tween(250)),
                                                    modifier = Modifier.animateItem()
                                                ) {
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
                                                            onImageClick = { fullImageUrl = msg.imageUrl },
                                                            onExpandVideo = { fullVideoUrl = it },
                                                            onOpenBatchImage = { url ->
                                                                galleryMessage = msg
                                                                galleryStartUrl = url
                                                            },
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
                                }
                            }
                        }
                    }
                }

                if (!isSearchActive) {
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
                                        stringResource(R.string.chat_readonly_mode), 
                                        color = Color.Gray, 
                                        style = MaterialTheme.typography.labelLarge, 
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.chat_readonly_desc), 
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
                                    text = if (iHaveBlocked) stringResource(R.string.chat_blocked_by_me, otherUser.nombre) 
                                          else stringResource(R.string.chat_blocked_cannot_send),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (iHaveBlocked) 
                                        stringResource(R.string.chat_blocked_by_me_desc)
                                        else stringResource(R.string.chat_blocked_cannot_send_desc),
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
                                        Text(stringResource(R.string.chat_unblock))
                                    }
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
                            if (replyingToMessage != null) {
                                val replyMsg = replyingToMessage!!
                                val replyName = if (replyMsg.senderId == myUid) stringResource(R.string.chat_me_short) else otherUser.nombre
                                val replyMeta = com.example.cuidaleapp.ui.components.replyPreviewMetadataFor(replyMsg)
                                val replyLabel = com.example.cuidaleapp.ui.components.replyLabelFor(replyMsg, context)
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                                        // Navegar al mensaje original que se está respondiendo.
                                        val targetIndex = filteredMessages.reversed().indexOfFirst { it.id == replyMsg.id }
                                        if (targetIndex != -1) {
                                            scope.launch {
                                                listState.animateScrollToItem(targetIndex)
                                                highlightedMessageId = replyMsg.id
                                                kotlinx.coroutines.delay(2000)
                                                highlightedMessageId = null
                                            }
                                        }
                                    },
                                    color = Color.Gray.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.width(4.dp).height(32.dp).background(primaryColor, CircleShape))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(replyName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = primaryColor)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val previewType = replyMeta["replyPreviewType"]
                                                val previewUrl = replyMeta["replyPreviewUrl"].orEmpty()
                                                if (previewType == "image" && previewUrl.isNotBlank()) {
                                                    com.example.cuidaleapp.ui.components.ChatMediaImage(
                                                        url = previewUrl,
                                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                } else if (previewType == "video" || previewType == "file") {
                                                    Icon(
                                                        if (previewType == "video") Icons.Default.PlayCircleOutline else Icons.AutoMirrored.Filled.InsertDriveFile,
                                                        null,
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                                Text(replyLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            }
                                        }
                                        IconButton(onClick = { replyingToMessage = null }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = pendingOutbox.isNotEmpty(),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Surface(
                                    color = if (isDark) Color(0xFF1B263B) else Color.White,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.chat_pending_outbox),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        pendingOutbox.forEach { m ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = m.text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.sendOutboxMessage(m) },
                                                    enabled = isOnline
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (isOnline) primaryColor else Color.Gray, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteOutboxMessage(m.id, chatId) }
                                                ) {
                                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = pendingMediaItems.isNotEmpty(),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Surface(
                                    color = if (isDark) Color(0xFF1B263B) else Color.White,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(R.string.chat_attachments, pendingMediaItems.size),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { pendingMediaItems.clear(); mediaError = null }) {
                                                Text(stringResource(R.string.chat_clear_all), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                            }
                                        }
                                        val imageCount = pendingMediaItems.count { it.kind == "image" }
                                        val videoCount = pendingMediaItems.count { it.kind == "video" }
                                        val fileCount = pendingMediaItems.count { it.kind == "file" }
                                        val totalMb = pendingMediaItems.sumOf { it.sizeBytes } / (1024.0 * 1024.0)
                                        Text(
                                            stringResource(R.string.chat_attachments_summary, imageCount, videoCount, fileCount, String.format(java.util.Locale.getDefault(), "%.1f", totalMb)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(pendingMediaItems, key = { it.uid }) { item ->
                                                Box(modifier = Modifier.size(64.dp)) {
                                                    when (item.kind) {
                                                        "image" -> AsyncImage(
                                                            model = coil3.request.ImageRequest.Builder(context).data(item.uri).size(256).build(),
                                                            contentDescription = item.name,
                                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        "video" -> LocalVideoPreview(
                                                            uri = item.uri,
                                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                                        )
                                                        else -> Box(
                                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { pendingMediaItems.remove(item); mediaError = null },
                                                        modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                        if (mediaError != null && pendingMediaItems.isNotEmpty()) {
                                            Text(
                                                mediaError.orEmpty(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                IconButton(onClick = { if (myRole == "representante") showShareRequests = true else showShareTasks = true }) {
                                    Icon(if (myRole == "representante") Icons.Default.AddCircle else Icons.AutoMirrored.Filled.Assignment, null, tint = primaryColor)
                                }

                                IconButton(
                                    onClick = { showMediaSheet = true },
                                    enabled = !isUploadingMedia && isOnline && !mediaLimitReached
                                ) {
                                    if (isUploadingMedia) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = primaryColor)
                                    } else {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            null,
                                            tint = if (mediaLimitReached) MaterialTheme.colorScheme.error else primaryColor
                                        )
                                    }
                                }
                                
                                TextField(
                                    value = inputText,
                                    onValueChange = { inputText = it; viewModel.setTyping(chatId, it.isNotEmpty()) },
                                    placeholder = { Text(if (messageToEdit != null) stringResource(R.string.chat_editing) else stringResource(R.string.chat_input_placeholder)) },
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
                                        if (pendingMediaItems.isNotEmpty()) {
                                            viewModel.setTyping(chatId, false)
                                            sendPendingMedia()
                                        } else if (inputText.isNotBlank()) {
                                            viewModel.setTyping(chatId, false)
                                            if (messageToEdit != null) {
                                                viewModel.editMessage(chatId, messageToEdit!!.id, inputText)
                                                messageToEdit = null
                                            } else {
                                                viewModel.sendMessage(
                                                    chatId = chatId,
                                                    text = inputText,
                                                    replyToId = replyingToMessage?.id,
                                                    replyToText = replyingToMessage?.let { com.example.cuidaleapp.ui.components.replyLabelFor(it, context) },
                                                    replyToName = if (replyingToMessage?.senderId == myUid) context.getString(R.string.chat_me_short) else otherUser.nombre,
                                                    metadata = replyingToMessage?.let { com.example.cuidaleapp.ui.components.replyPreviewMetadataFor(it) } ?: emptyMap()
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
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isOnline,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.chat_reconnecting),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isScrolledUp && !isSearchActive,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(200f)
                    .imePadding()
                    .padding(bottom = 196.dp)
            ) {
                Surface(
                    onClick = {
                        pendingReturnToSearch = false
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    color = if (hasNewMessageBelow) MaterialTheme.colorScheme.secondary else primaryColor,
                    shape = CircleShape,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasNewMessageBelow) Icons.Default.NewReleases else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasNewMessageBelow) stringResource(R.string.chat_new_messages) else stringResource(R.string.chat_back_to_bottom),
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
                        Text(stringResource(R.string.chat_delete_title), fontWeight = FontWeight.Black)
                    }
                    Text(
                        stringResource(R.string.chat_delete_desc),
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
                ) { Text(stringResource(R.string.chat_delete_for_all), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
                        Text(if (allPinned) stringResource(R.string.chat_unpin_title) else stringResource(R.string.chat_pin_title), fontWeight = FontWeight.Black)
                    }
                    Text(
                        if (allPinned) stringResource(R.string.chat_unpin_desc)
                        else stringResource(R.string.chat_pin_desc),
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
                ) { Text(if (allPinned) stringResource(R.string.action_accept) else stringResource(R.string.chat_pin_action), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPinConfirmDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    
    if (showShareRequests) {
        ModalBottomSheet(onDismissRequest = { showShareRequests = false }) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(stringResource(R.string.chat_share_request_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                if (myRequests.isEmpty()) {
                    Text(stringResource(R.string.chat_no_open_requests), color = Color.Gray)
                } else {
                    myRequests.forEach { sol ->
                        Card(
                            onClick = {
                                viewModel.sendMessage(chatId, context.getString(R.string.chat_share_request_message, sol.nombrePaciente), type = "solicitud_compartida", metadata = mapOf("solicitudId" to sol.id))
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
                Text(stringResource(R.string.chat_share_task_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.chat_share_task_desc), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                
                if (activeTasks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AssignmentLate, null, modifier = Modifier.size(48.dp), tint = Color.Gray.copy(alpha = 0.3f))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.chat_no_tasks_today), color = Color.Gray)
                        }
                    }
                } else {
                    val completadas = activeTasks.filter { it.completada }
                    val pospuestas = activeTasks.filter { it.postergada && !it.completada }
                    val pendientes = activeTasks.filter { !it.completada && !it.postergada }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            TaskShareSection(
                                title = stringResource(R.string.chat_task_pending),
                                tasks = pendientes,
                                color = MaterialTheme.colorScheme.primary,
                                onTaskClick = { reg: com.example.cuidaleapp.models.RegistroTarea ->
                                    viewModel.sendMessage(chatId, context.getString(R.string.chat_task_share_pending, reg.titulo, reg.horarioPrevisto), type = "tarea_compartida", metadata = mapOf("tareaId" to reg.id, "solicitudId" to reg.solicitudId))
                                    showShareTasks = false
                                }
                            )
                        }
                        item {
                            TaskShareSection(
                                title = stringResource(R.string.chat_task_completed),
                                tasks = completadas,
                                color = Color(0xFF4CAF50),
                                onTaskClick = { reg: com.example.cuidaleapp.models.RegistroTarea ->
                                    viewModel.sendMessage(chatId, context.getString(R.string.chat_task_share_completed, reg.titulo, reg.horarioPrevisto), type = "tarea_compartida", metadata = mapOf("tareaId" to reg.id, "solicitudId" to reg.solicitudId))
                                    showShareTasks = false
                                }
                            )
                        }
                        item {
                            TaskShareSection(
                                title = stringResource(R.string.chat_task_postponed),
                                tasks = pospuestas,
                                color = Color.Red,
                                onTaskClick = { reg: com.example.cuidaleapp.models.RegistroTarea ->
                                    viewModel.sendMessage(chatId, context.getString(R.string.chat_task_share_postponed, reg.titulo, reg.horarioPrevisto), type = "tarea_compartida", metadata = mapOf("tareaId" to reg.id, "solicitudId" to reg.solicitudId))
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
    
    if (showMediaSheet) {
        ModalBottomSheet(onDismissRequest = { showMediaSheet = false }) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, null, tint = primaryColor)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.chat_send_media_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                val limitReached = mediaLimitReached
                Text(
                    stringResource(R.string.chat_media_limit_info, pendingMediaItems.size, pendingMediaItems.sumOf { it.sizeBytes } / (1024 * 1024)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (limitReached) MaterialTheme.colorScheme.error else Color.Gray,
                    fontWeight = if (limitReached) FontWeight.Bold else FontWeight.Normal
                )
                if (limitReached) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.chat_media_limit_reached),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                if (mediaError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(mediaError.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (pendingMediaItems.isNotEmpty()) {
                    Text(
                        stringResource(R.string.chat_attachments_selected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(pendingMediaItems) { index, item ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                when (item.kind) {
                                    "image" -> AsyncImage(model = coil3.request.ImageRequest.Builder(context).data(item.uri).size(256).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    "video" -> com.example.cuidaleapp.ui.components.VideoFirstFrame(item.uri.toString(), Modifier.fillMaxSize())
                                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.Gray)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .clickable { pendingMediaItems.removeAt(index); mediaError = null },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MediaAttachOption(
                        icon = Icons.Default.PhotoCamera,
                        label = stringResource(R.string.source_camera),
                        enabled = !isUploadingMedia && !limitReached,
                        modifier = Modifier.weight(1f),
                        onClick = { requestMediaLaunch { showMediaSheet = false; launchCamera() } }
                    )
                    MediaAttachOption(
                        icon = Icons.Default.PhotoLibrary,
                        label = stringResource(R.string.source_gallery),
                        enabled = !isUploadingMedia && !limitReached,
                        modifier = Modifier.weight(1f),
                        onClick = { requestMediaLaunch { showMediaSheet = false; mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) } }
                    )
                    MediaAttachOption(
                        icon = Icons.Default.Description,
                        label = stringResource(R.string.chat_attach_docs),
                        enabled = !isUploadingMedia && !limitReached,
                        modifier = Modifier.weight(1f),
                        onClick = { requestMediaLaunch { showMediaSheet = false; documentLauncher.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) } }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showMediaWarning) {
        AlertDialog(
            onDismissRequest = { showMediaWarning = false; pendingMediaAction = null },
            containerColor = if (isDark) Color(0xFF1B263B) else Color.White,
            title = { Text(stringResource(R.string.chat_media_warning_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.chat_media_warning_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("cuidale_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("media_warning_accepted", true).apply()
                    showMediaWarning = false
                    pendingMediaAction?.invoke()
                    pendingMediaAction = null
                }) { Text(stringResource(R.string.chat_got_it), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showMediaWarning = false; pendingMediaAction = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    fullImageUrl?.let { url ->
        pendingReturnToSearch = false
        val target = messages.find { it.imageUrl == url } ?: filteredMessages.find { it.imageUrl == url }
        com.example.cuidaleapp.ui.components.FullImageViewer(
            url = url,
            onDismiss = { fullImageUrl = null },
            onReply = {
                if (target != null) replyingToMessage = target
                fullImageUrl = null
            },
            canPin = target != null && isOnline,
            isPinned = target?.pinned == true,
            onTogglePin = {
                if (target != null) viewModel.togglePin(chatId, target.id, target.pinned, myName)
            },
            onGoToMessage = {
                fullImageUrl = null
                target?.let { goToMessage(it) }
            }
        )
    }

    fullVideoUrl?.let { url ->
        pendingReturnToSearch = false
        val target = messages.find { it.videoUrl == url } ?: filteredMessages.find { it.videoUrl == url }
        com.example.cuidaleapp.ui.components.FullVideoViewer(
            url = url,
            onDismiss = { fullVideoUrl = null },
            onReply = {
                if (target != null) replyingToMessage = target
                fullVideoUrl = null
            },
            canPin = target != null && isOnline,
            isPinned = target?.pinned == true,
            onTogglePin = {
                if (target != null) viewModel.togglePin(chatId, target.id, target.pinned, myName)
            },
            onGoToMessage = {
                fullVideoUrl = null
                target?.let { goToMessage(it) }
            }
        )
    }

    galleryMessage?.let { msg ->
        pendingReturnToSearch = false
        val items = com.example.cuidaleapp.ui.components.parseMediaBatch(msg.metadata)
        if (items.isNotEmpty()) {
            com.example.cuidaleapp.ui.components.MediaBatchGalleryViewer(
                items = items,
                initialUrl = galleryStartUrl,
                onDismiss = { galleryMessage = null; galleryStartUrl = null },
                onReply = {
                    galleryMessage = null
                    galleryStartUrl = null
                    replyingToMessage = msg
                }
            )
        }
    }

      if (showFirstChatDisclaimer) {
        val holdProgress = remember { mutableFloatStateOf(0f) }
        var holding by remember { mutableStateOf(false) }
        LaunchedEffect(holding) {
            if (holding) {
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 3000L) {
                    holdProgress.floatValue = (System.currentTimeMillis() - start) / 3000f
                    kotlinx.coroutines.delay(40)
                }
                holdProgress.floatValue = 1f
                context.getSharedPreferences("cuidale_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("first_chat_disclaimer_done", true).commit()
                showFirstChatDisclaimer = false
            } else {
                holdProgress.floatValue = 0f
            }
        }
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.chat_disclaimer_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.chat_disclaimer_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        holding = true
                                        waitForUpOrCancellation()
                                        holding = false
                                    }
                                }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.chat_disclaimer_hold_cta), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { holdProgress.floatValue }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

  if (showPinnedMessages) {
        var pinnedFilter by remember { mutableStateOf<String?>(null) }
        val filteredPinned = remember(pinnedMessages, pinnedFilter) {
            pinnedMessages.filter { msg ->
                when (pinnedFilter) {
                    null -> true
                    "text" -> msg.imageUrl == null && msg.videoUrl == null && msg.fileUrl == null
                    "media" -> msg.imageUrl != null || msg.videoUrl != null || msg.fileUrl != null
                    else -> true
                }
            }
        }
        ModalBottomSheet(onDismissRequest = { showPinnedMessages = false }) {
            Column(Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(0.65f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, null, tint = primaryColor)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.chat_pinned_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val filters = listOf(null to stringResource(R.string.chat_filter_all), "text" to stringResource(R.string.chat_filter_messages), "media" to stringResource(R.string.chat_filter_media))
                    filters.forEach { (value, label) ->
                        FilterChip(
                            selected = pinnedFilter == value,
                            onClick = { pinnedFilter = value },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    items(filteredPinned) { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = Color.Gray.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                when {
                                    msg.imageUrl != null -> com.example.cuidaleapp.ui.components.ChatMediaImage(
                                        url = msg.imageUrl,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                                        onClick = { fullImageUrl = msg.imageUrl }
                                    )
                                    msg.videoUrl != null -> com.example.cuidaleapp.ui.components.ChatMediaVideoPlayer(
                                        url = msg.videoUrl,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                                        onExpand = { fullVideoUrl = msg.videoUrl }
                                    )
                                    msg.fileUrl != null -> FileBubble(msg.fileName ?: stringResource(R.string.chat_file_fallback), msg.fileUrl)
                                    else -> Text(msg.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                }
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
                                        Text(stringResource(R.string.chat_go_to_message), fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.togglePin(chatId, msg.id, true, myName) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.PushPin, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.chat_unpin_action), fontSize = 10.sp)
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
                    Text(stringResource(R.string.chat_no_tasks), style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(12.dp))
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
    onImageClick: (ChatMessage) -> Unit,
    onExpandVideo: (String) -> Unit,
    onOpenBatchImage: ((String) -> Unit) = {},
    onViewPostulacion: (String) -> Unit,
    onDoubleClick: () -> Unit
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    // Animación suave del desplazamiento al deslizar para responder.
    val animatedSwipe by androidx.compose.animation.core.animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "swipeOffset"
    )

    // Escala animada al seleccionar/resaltar.
    val bubbleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected || isHighlighted) 0.96f else 1f,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "bubbleScale"
    )
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
                translationX = animatedSwipe
                scaleX = bubbleScale
                scaleY = bubbleScale
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
        androidx.compose.animation.AnimatedVisibility(
            visible = animatedSwipe > 0f,
            enter = fadeIn() + scaleIn(initialScale = 0.5f),
            exit = fadeOut()
        ) {
            Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).alpha(animatedSwipe / 100f))
        }

        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            val esLote = msg.type == "media_group" || msg.metadata.containsKey("mediaBatch")
            val hasMedia = msg.imageUrl != null || msg.videoUrl != null || msg.fileUrl != null || esLote
            Surface(
                color = finalBubbleColor,
                shape = RoundedCornerShape(
                    topStart = if (hasMedia) 12.dp else 16.dp,
                    topEnd = if (hasMedia) 12.dp else 16.dp,
                    bottomStart = if (hasMedia) 12.dp else (if (isMe) 16.dp else 4.dp),
                    bottomEnd = if (hasMedia) 12.dp else (if (isMe) 4.dp else 16.dp)
                ),
                tonalElevation = if (hasMedia) 0.dp else (if (isMe) 2.dp else 1.dp),
                shadowElevation = if (isSelected) 4.dp else 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .then(if (hasMedia) Modifier.padding(2.dp) else Modifier.padding(12.dp, 8.dp))
                        .widthIn(max = 280.dp)
                ) {
                    if (msg.replyToId != null) {
                        com.example.cuidaleapp.ui.components.ReplyPreviewBubble(
                            metadata = msg.metadata,
                            fallbackText = msg.replyToText ?: "",
                            fallbackName = msg.replyToName ?: stringResource(R.string.chat_user_fallback),
                            textColor = textColor
                        )
                    }
                    
                    if (msg.deleted) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.chat_message_deleted), style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Color.White.copy(alpha = 0.9f))
                        }
                    } else {
                        if (msg.imageUrl != null) {
                            com.example.cuidaleapp.ui.components.ChatMediaImage(
                                url = msg.imageUrl,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                                onClick = { onImageClick(msg) },
                                onLongClick = onLongClick
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.videoUrl != null) {
                            // Preview estático (primer frame desde caché) + play que
                            // abre el reproductor a pantalla completa. Evita crear
                            // reproductores para cada video visible (menos lag al
                            // hacer scroll rápido por el chat).
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onExpandVideo(msg.videoUrl) },
                                        onLongClick = onLongClick
                                    )
                            ) {
                                com.example.cuidaleapp.ui.components.VideoPreviewWithDuration(url = msg.videoUrl, modifier = Modifier.fillMaxSize())
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(52.dp)
                                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.fileUrl != null) {
                            FileBubble(msg.fileName ?: stringResource(R.string.chat_file_fallback), msg.fileUrl)
                            Spacer(Modifier.height(4.dp))
                        }
                        
                        when {
                            esLote -> {
                                MediaBatchContent(
                                    msg = msg,
                                    onImageClick = { url -> onOpenBatchImage(url) },
                                    onExpandVideo = onExpandVideo,
                                    onLongClick = onLongClick
                                )
                            }
                            msg.type == "aviso_postulacion" || msg.type == "solicitud_compartida" -> {
                                RequestAlertBubble(msg, onViewClick = { onViewPostulacion(msg.metadata["solicitudId"] ?: "") })
                            }
                            msg.type == "tarea_compartida" -> {
                                TaskSharedBubble(msg.text, isMe)
                            }
                            else -> {
                                Text(
                                    text = buildAnnotatedString {
                                        append(msg.text)
                                        if (msg.edited) {
                                            withStyle(SpanStyle(fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))) {
                                                append(stringResource(R.string.chat_edited_suffix))
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
                Text(formatMessageDate(msg.timestamp, LocalContext.current), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                if (msg.metadata["pending_send"] == "true") {
                    Spacer(Modifier.width(4.dp))
                    // Mensaje pendiente de envío (outbox): muestra el indicador de carga.
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.Gray
                    )
                } else if (isMe) {
                    Spacer(Modifier.width(4.dp))
                    val isRead = msg.readBy.size > 1
                    Icon(imageVector = if (isRead) Icons.Default.Visibility else Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (isRead) Color(0xFF00B4D8) else Color.Gray)
                }
            }
        }
    }
}

/**
 * Renderiza un lote de multimedia (estilo WhatsApp) enviado como un solo
 * mensaje de tipo "media_group". Muestra un grid de hasta 6 miniaturas
 * y un indicador "+N más" si hay más elementos.
 */
@Composable
private fun MediaBatchContent(
    msg: ChatMessage,
    onImageClick: (String) -> Unit,
    onExpandVideo: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val items = remember(msg.id, msg.metadata) { com.example.cuidaleapp.ui.components.parseMediaBatch(msg.metadata) }
    if (items.isEmpty()) return

    val total = items.size
    val visible = items.take(6)

    // Precarga el lote completo a la caché local: versión acotada de cada imagen
    // (Coil) y primer frame de cada video, para que todo el conjunto quede cacheados
    // y no haya que descargarlo de nuevo al revisarlo o verlo offline. La precarga
    // debe estar acotada: sin .size() Coil decodifica a resolución completa y 10
    // imágenes simultáneas provocan OutOfMemory.
    val context = LocalContext.current
    LaunchedEffect(items) {
        items.forEach { item ->
            when (item.first) {
                "image" -> runCatching {
                    coil3.SingletonImageLoader.get(context).enqueue(
                        coil3.request.ImageRequest.Builder(context)
                            .data(item.second)
                            .size(1024)
                            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .build()
                    )
                }
                "video" -> com.example.cuidaleapp.ui.components.VideoThumbnailCache.fetch(context, item.second)
            }
        }
    }

    // Grid estilo WhatsApp: celdas cuadradas con Crop (sin barras negras) y
    // disposición adaptativa según la cantidad. El "+N" se integra en la última
    // miniatura visible en lugar de un contador flotante.
    val extraCount = (total - visible.size).coerceAtLeast(0)
    val space = 4.dp

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space)
    ) {
        when (visible.size) {
            1 -> {
                MediaBatchTile(
                    item = visible[0],
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    featured = true,
                    extraCount = extraCount,
                    onImageClick = onImageClick,
                    onLongClick = onLongClick
                )
            }
            2 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space)
                ) {
                    repeat(2) { i ->
                        MediaBatchTile(
                            item = visible[i],
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            extraCount = if (i == 1) extraCount else 0,
                            onImageClick = onImageClick,
                            onLongClick = onLongClick
                        )
                    }
                }
            }
            3 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space)
                ) {
                    MediaBatchTile(
                        item = visible[0],
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        featured = true,
                        onImageClick = onImageClick,
                        onLongClick = onLongClick
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(space)
                    ) {
                        MediaBatchTile(
                            item = visible[1],
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onImageClick = onImageClick,
                            onLongClick = onLongClick
                        )
                        MediaBatchTile(
                            item = visible[2],
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            extraCount = extraCount,
                            onImageClick = onImageClick,
                            onLongClick = onLongClick
                        )
                    }
                }
            }
            4 -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(space)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space)) {
                        MediaBatchTile(visible[0], Modifier.weight(1f).aspectRatio(1f), onImageClick = onImageClick, onLongClick = onLongClick)
                        MediaBatchTile(visible[1], Modifier.weight(1f).aspectRatio(1f), onImageClick = onImageClick, onLongClick = onLongClick)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space)) {
                        MediaBatchTile(visible[2], Modifier.weight(1f).aspectRatio(1f), onImageClick = onImageClick, onLongClick = onLongClick)
                        MediaBatchTile(visible[3], Modifier.weight(1f).aspectRatio(1f), extraCount = extraCount, onImageClick = onImageClick, onLongClick = onLongClick)
                    }
                }
            }
            5 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space)
                ) {
                    MediaBatchTile(
                        item = visible[0],
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        featured = true,
                        onImageClick = onImageClick,
                        onLongClick = onLongClick
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(space)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space)) {
                            MediaBatchTile(visible[1], Modifier.weight(1f).aspectRatio(1f), onImageClick = onImageClick, onLongClick = onLongClick)
                            MediaBatchTile(visible[2], Modifier.weight(1f).aspectRatio(1f), onImageClick = onImageClick, onLongClick = onLongClick)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space)) {
                            MediaBatchTile(visible[3], Modifier.weight(1f).aspectRatio(1f), onImageClick = onImageClick, onLongClick = onLongClick)
                            MediaBatchTile(visible[4], Modifier.weight(1f).aspectRatio(1f), extraCount = extraCount, onImageClick = onImageClick, onLongClick = onLongClick)
                        }
                    }
                }
            }
            else -> {
                // 6 o más: 2 filas × 3 columnas, "+N" en la última celda.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(space)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space)) {
                        repeat(3) { i ->
                            MediaBatchTile(visible[i], Modifier.weight(1f).aspectRatio(1f), onImageClick = onImageClick, onLongClick = onLongClick)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space)) {
                        repeat(3) { i ->
                            val index = i + 3
                            MediaBatchTile(visible[index], Modifier.weight(1f).aspectRatio(1f), extraCount = if (index == 5) extraCount else 0, onImageClick = onImageClick, onLongClick = onLongClick)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Celda individual del lote: miniatura con Crop (sin barras negras), overlay de
 * play para videos y badge "+N" cuando hay más elementos que los visibles.
 */
@Composable
private fun MediaBatchTile(
    item: Triple<String, String, String>,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    extraCount: Int = 0,
    onImageClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val playIconOuter = if (featured) 56.dp else 40.dp
    val playIconInner = if (featured) 32.dp else 24.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onImageClick(item.second) },
                onLongClick = onLongClick
            )
    ) {
        when (item.first) {
            "video" -> {
                com.example.cuidaleapp.ui.components.VideoPreviewWithDuration(
                    url = item.second,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(playIconOuter)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(playIconInner)
                    )
                }
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                com.example.cuidaleapp.ui.components.ChatMediaImage(
                    url = item.second,
                    modifier = Modifier.fillMaxSize(),
                    fullSize = 768,
                    // Crop recorta la imagen para llenar la celda sin barras negras.
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (extraCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MediaAttachOption(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}

/**
 * Determina si un mensaje contiene multimedia del tipo indicado, considerando
 * tanto los campos directos (imageUrl/videoUrl/fileUrl) como los lotes
 * (media_group) incrustados en metadata["mediaBatch"].
 */
private fun messageHasMediaType(msg: ChatMessage, type: String): Boolean {
    when (type) {
        "image" -> if (msg.imageUrl != null) return true
        "video" -> if (msg.videoUrl != null) return true
        "file" -> if (msg.fileUrl != null) return true
    }
    val batchItems = runCatching { com.example.cuidaleapp.ui.components.parseMediaBatch(msg.metadata) }.getOrDefault(emptyList())
    return when (type) {
        "image" -> batchItems.any { it.first == "image" }
        "video" -> batchItems.any { it.first == "video" }
        "file" -> batchItems.any { it.first == "file" }
        "media" -> batchItems.isNotEmpty()
        else -> false
    }
}

private data class MediaSearchItem(
    val message: ChatMessage,
    val kind: String,
    val url: String
)

/**
 * Convierte los mensajes multimedia en una lista plana estilo galería:
 * cada imagen/video/documento (directo o de un lote) se muestra por separado.
 */
private fun flattenMediaSearchItems(messages: List<ChatMessage>): List<MediaSearchItem> {
    val result = mutableListOf<MediaSearchItem>()
    messages.forEach { msg ->
        if (msg.type == "media_group") {
            val batchItems = runCatching { com.example.cuidaleapp.ui.components.parseMediaBatch(msg.metadata) }.getOrDefault(emptyList())
            batchItems.forEach { (kind, url, _) ->
                result.add(MediaSearchItem(msg, kind, url))
            }
        } else {
            if (msg.imageUrl != null) result.add(MediaSearchItem(msg, "image", msg.imageUrl))
            if (msg.videoUrl != null) result.add(MediaSearchItem(msg, "video", msg.videoUrl))
            if (msg.fileUrl != null) result.add(MediaSearchItem(msg, "file", msg.fileUrl))
        }
    }
    return result
}

@Composable
private fun SearchMediaGallery(
    items: List<ChatMessage>,
    kindFilter: String?,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenBatch: (ChatMessage, String) -> Unit,
    onGoToMessage: (ChatMessage) -> Unit
) {
    val hasMedia = { m: ChatMessage ->
        m.imageUrl != null || m.videoUrl != null || m.fileUrl != null || m.type == "media_group"
    }
    // En Fotos/Videos/Docs se desarman los lotes (galería plana). En Multimedia
    // y Todos se mantienen agrupados (cada lote es un tile con su contador).
    val flatMode = kindFilter == "image" || kindFilter == "video" || kindFilter == "file"

    if (flatMode) {
        val flatItems = remember(items, kindFilter) {
            flattenMediaSearchItems(items.filter(hasMedia)).filter { it.kind == kindFilter }
        }
        if (flatItems.isEmpty()) return
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(flatItems) { index, item ->
                key(item.message.id + "_" + item.url + "_" + index) {
                    MediaFlatTile(
                        item = item,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onGoToMessage = onGoToMessage
                    )
                }
            }
        }
    } else {
        val grouped = remember(items) { items.filter(hasMedia) }
        if (grouped.isEmpty()) return
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(grouped, key = { it.id }) { msg ->
                MediaGroupTile(
                    msg = msg,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    onOpenBatch = onOpenBatch,
                    onGoToMessage = onGoToMessage
                )
            }
        }
    }
}

@Composable
private fun MediaFlatTile(
    item: MediaSearchItem,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onGoToMessage: (ChatMessage) -> Unit
) {
    val isVideo = item.kind == "video"
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                when (item.kind) {
                    "video" -> onVideoClick(item.url)
                    "image" -> onImageClick(item.url)
                }
            }
    ) {
        when (item.kind) {
            "video" -> com.example.cuidaleapp.ui.components.VideoFirstFrame(item.url, Modifier.fillMaxSize())
            "image" -> AsyncImage(
                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                    .data(item.url)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            else -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.Gray)
            }
        }

        if (isVideo) {
            IconButton(
                onClick = { onVideoClick(item.url) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // Siempre disponible: ir al mensaje de origen.
        IconButton(
            onClick = { onGoToMessage(item.message) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                stringResource(R.string.chat_go_to_message_cd),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MediaGroupTile(
    msg: ChatMessage,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenBatch: (ChatMessage, String) -> Unit,
    onGoToMessage: (ChatMessage) -> Unit
) {
    val batchItems = runCatching { com.example.cuidaleapp.ui.components.parseMediaBatch(msg.metadata) }.getOrDefault(emptyList())
    val isBatch = msg.type == "media_group" && batchItems.isNotEmpty()
    val batchVideo = if (isBatch) batchItems.firstOrNull { it.first == "video" }?.second else null
    val batchImage = if (isBatch) batchItems.firstOrNull { it.first == "image" }?.second else null
    val videoUrl = msg.videoUrl ?: batchVideo
    val imageUrl = msg.imageUrl ?: batchImage
    val isVideo = videoUrl != null

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                when {
                    isBatch -> onOpenBatch(msg, batchItems.first().second)
                    isVideo -> onVideoClick(videoUrl)
                    msg.imageUrl != null -> onImageClick(msg.imageUrl)
                }
            }
    ) {
        when {
            isVideo -> com.example.cuidaleapp.ui.components.VideoFirstFrame(videoUrl, Modifier.fillMaxSize())
            imageUrl != null -> AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            isBatch -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            else -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.Gray)
            }
        }

        if (isVideo) {
            IconButton(
                onClick = { onVideoClick(videoUrl) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        if (isBatch) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(batchItems.size.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Siempre disponible: ir al mensaje de origen.
        IconButton(
            onClick = { onGoToMessage(msg) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                stringResource(R.string.chat_go_to_message_cd),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    messages: List<ChatMessage>,
    myUid: String,
    isDark: Boolean,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenBatch: (ChatMessage, String) -> Unit,
    onGoToMessage: (ChatMessage) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(messages.reversed(), key = { it.id }) { msg ->
            val batchItems = runCatching { com.example.cuidaleapp.ui.components.parseMediaBatch(msg.metadata) }.getOrDefault(emptyList())
            val isBatch = msg.type == "media_group" && batchItems.isNotEmpty()
            val batchVideo = if (isBatch) batchItems.firstOrNull { it.first == "video" }?.second else null
            val batchImage = if (isBatch) batchItems.firstOrNull { it.first == "image" }?.second else null
            val videoUrl = msg.videoUrl ?: batchVideo
            val imageUrl = msg.imageUrl ?: batchImage
            val isVideo = videoUrl != null
            val containerColor = if (isDark) Color(0xFF1B263B) else Color.White
            Surface(
                onClick = {
                    when {
                        isVideo -> onVideoClick(videoUrl)
                        msg.imageUrl != null -> onImageClick(msg.imageUrl)
                        isBatch -> onOpenBatch(msg, batchItems.first().second)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = containerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icono / miniatura
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isVideo -> com.example.cuidaleapp.ui.components.VideoFirstFrame(videoUrl, Modifier.fillMaxSize())
                            imageUrl != null -> AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            isBatch -> Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            msg.fileUrl != null -> Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.Gray, modifier = Modifier.size(26.dp))
                            else -> Icon(Icons.Default.Forum, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        }
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val title = when {
                                msg.senderId == "system" -> stringResource(R.string.chat_search_system)
                                msg.senderId == myUid -> stringResource(R.string.chat_me_short)
                                else -> stringResource(R.string.chat_search_contact)
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatMessageDate(msg.timestamp, LocalContext.current),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        val snippet = when {
                            isBatch -> stringResource(R.string.chat_search_batch, batchItems.size)
                            msg.imageUrl != null -> stringResource(R.string.chat_media_photo)
                            msg.videoUrl != null -> stringResource(R.string.chat_media_video)
                            msg.fileUrl != null -> stringResource(R.string.chat_media_file, msg.fileName ?: stringResource(R.string.chat_document))
                            else -> msg.text
                        }
                        Text(
                            text = snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.White else Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onGoToMessage(msg) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(R.string.chat_go_to_message_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
        "abierta" -> stringResource(R.string.chat_status_available)
        "cerrada" -> stringResource(R.string.chat_status_taken)
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
                Text(stringResource(R.string.chat_view_details), fontSize = 10.sp)
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
                    stringResource(R.string.chat_task_report),
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
fun SystemMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.Gray.copy(alpha = 0.1f), shape = CircleShape) {
            Text(text, modifier = Modifier.padding(12.dp, 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

private sealed interface ChatListItem {
    data class Header(val timestamp: Long) : ChatListItem
    data class Message(val msg: ChatMessage) : ChatListItem
}

@Composable
fun DayDivider(timestamp: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.2f))
        Text(
            text = formatDividerDate(timestamp, LocalContext.current),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.2f))
    }
}

private fun formatDividerDate(timestamp: Long, context: android.content.Context): String {
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR) -> context.getString(R.string.chat_today)

        now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - date.get(Calendar.DAY_OF_YEAR) == 1 -> context.getString(R.string.chat_yesterday)

        else -> SimpleDateFormat("d 'de' MMMM", Locale("es", "ES")).format(Date(timestamp)).uppercase()
    }
}

private fun formatMessageDate(timestamp: Long, context: android.content.Context): String {
    val now = Calendar.getInstance()
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - msgDate.get(Calendar.DAY_OF_YEAR) == 1 -> {
            context.getString(R.string.chat_yesterday_prefix) + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) -> {
            SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

/**
 * Resultado de intentar agregar un adjunto a la cola de envío.
 */
private enum class AddMediaResult {
    ADDED,
    DUPLICATE,
    COUNT_LIMIT,
    SIZE_LIMIT,
    TOTAL_LIMIT
}

/**
 * Elemento multimedia pendiente de envío (solo local, antes de subir).
 */
private data class PendingMediaItem(
    val uri: android.net.Uri,
    val kind: String, // "image" | "video" | "file"
    val name: String,
    val sizeBytes: Long,
    val uid: String = java.util.UUID.randomUUID().toString()
)

/**
 * Miniatura del primer frame de un video local (content://) para la cola de envío.
 */
@Composable
private fun LocalVideoPreview(uri: android.net.Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frame by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        // Decodifica directamente redimensionado: nunca materializa el frame
                        // a resolución completa (evita OOM con lotes de videos).
                        retriever.getScaledFrameAtTime(1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
                    } else {
                        val raw = retriever.getFrameAtTime(1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
                        // Redimensiona el frame para no dejar bitmaps enormes en memoria.
                        val longSide = if (raw.width >= raw.height) raw.width else raw.height
                        val scale = if (longSide > 640) 640f / longSide else 1f
                        if (scale < 1f) {
                            val scaled = android.graphics.Bitmap.createScaledBitmap(
                                raw,
                                (raw.width * scale).toInt().coerceAtLeast(1),
                                (raw.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                            raw.recycle()
                            scaled
                        } else {
                            raw
                        }
                    }
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }
    if (frame != null) {
        AsyncImage(
            model = frame,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
