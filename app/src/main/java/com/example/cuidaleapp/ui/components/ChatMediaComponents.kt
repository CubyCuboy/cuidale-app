package com.example.cuidaleapp.ui.components

import android.content.Context
import java.io.File
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.example.cuidaleapp.R
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.models.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Caché (en memoria + disco) del primer frame de videos, para no re-extraerlos.
 */
object VideoThumbnailCache {
    private const val MAX_MEMORY_ENTRIES = 64
    private val memory = object : LinkedHashMap<String, android.graphics.Bitmap>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }

    private fun diskDir(context: android.content.Context): java.io.File {
        return java.io.File(context.cacheDir, "video_thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun diskFile(context: android.content.Context, url: String): java.io.File {
        val name = java.security.MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return java.io.File(diskDir(context), "$name.png")
    }

    fun get(context: android.content.Context, url: String): android.graphics.Bitmap? {
        memory[url]?.let { return it }
        return runCatching {
            val file = diskFile(context, url)
            if (file.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) memory[url] = bmp
                bmp
            } else {
                null
            }
        }.getOrNull()
    }

    fun put(context: android.content.Context, url: String, bmp: android.graphics.Bitmap) {
        memory[url] = bmp
        runCatching {
            diskFile(context, url).outputStream().use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    fun clear(context: android.content.Context) {
        memory.clear()
        runCatching { diskDir(context).deleteRecursively() }
    }
}

/**
 * Obtiene el primer frame de un video remoto (para usar como preview en respuestas).
 * Con caché de thumbnails para evitar re-extraer el mismo video.
 */
@Composable
fun VideoFirstFrame(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val frame by produceState<android.graphics.Bitmap?>(initialValue = VideoThumbnailCache.get(context, url), url) {
        value = VideoThumbnailCache.get(context, url) ?: withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(url)
                    retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()?.also { VideoThumbnailCache.put(context, url, it) }
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
            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Caché en disco compartida para todo el multimedia (videos/imágenes).
 * Usa SimpleCache (Media3) con evicción LRU para limitar el espacio usado.
 */
@androidx.media3.common.util.UnstableApi
object MultimediaDiskCache {
    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB

    @Volatile
    private var cacheInstance: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return cacheInstance ?: synchronized(this) {
            cacheInstance ?: SimpleCache(
                File(context.cacheDir, "multimedia_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                androidx.media3.database.StandaloneDatabaseProvider(context)
            ).also { cacheInstance = it }
        }
    }

    fun dataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstream = DefaultDataSource.Factory(context)
        return CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun clear(context: Context) {
        runCatching { cacheInstance?.release() }
        cacheInstance = null
        runCatching { File(context.cacheDir, "multimedia_cache").deleteRecursively() }
    }
}

@androidx.media3.common.util.UnstableApi
object VideoPlayerCache {
    private val players = mutableMapOf<String, ExoPlayer>()
    private val readyUrls = mutableSetOf<String>()

    fun get(context: Context, url: String): ExoPlayer {
        return players.getOrPut(url) {
            ExoPlayer.Builder(context).build().apply {
                val mediaSource = ProgressiveMediaSource.Factory(MultimediaDiskCache.dataSourceFactory(context))
                    .createMediaSource(MediaItem.fromUri(url))
                setMediaSource(mediaSource)
                prepare()
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                            readyUrls.add(url)
                        }
                    }
                })
            }
        }
    }

    fun isReady(url: String): Boolean = url in readyUrls
    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
        readyUrls.clear()
    }

    fun pauseAll() {
        players.values.forEach { it.pause() }
    }

    fun pauseExcept(url: String?) {
        players.forEach { (u, p) -> if (u != url) p.pause() }
    }
}

@Composable
fun ChatSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    highlightColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )

    // Barrido diagonal elegante del reflejo sobre la base.
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(progress * 800f, 0f),
        end = Offset(progress * 800f + 400f, 400f)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMediaImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val mediaModifier = when {
        onClick != null && onLongClick != null -> modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Image,
            onClick = onClick,
            onLongClick = onLongClick
        )
        onClick != null -> modifier.clickable(onClick = onClick)
        onLongClick != null -> modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Image,
            onClick = {},
            onLongClick = onLongClick
        )
        else -> modifier
    }

    var fullLoaded by remember(url) { mutableStateOf(false) }
    var fullFailed by remember(url) { mutableStateOf(false) }
    var retryKey by remember(url) { mutableIntStateOf(0) }

    // Miniatura en baja resolución: se muestra borrosa mientras la imagen
    // completa se descarga (y como respaldo si la alta calidad falla).
    val thumbRequest = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(120)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    // Imagen en alta calidad (reintentable).
    val fullRequest = remember(url, retryKey) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    Box(modifier = mediaModifier) {
        // Capa de baja calidad con blur mientras no esté la imagen completa.
        // Si la alta calidad falla (p.ej. sin conexión y sin caché), esta
        // miniatura borrosa queda visible como respaldo.
        if (!fullLoaded) {
            SubcomposeAsyncImage(
                model = thumbRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(14.dp),
                contentScale = contentScale,
                loading = {
                    ChatSkeletonBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            )
        }

        // Capa de alta calidad.
        SubcomposeAsyncImage(
            model = fullRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onSuccess = {
                fullLoaded = true
                fullFailed = false
            },
            onError = {
                fullLoaded = false
                fullFailed = true
            },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            error = {
                // Debajo queda visible la miniatura borrosa.
            }
        )

        // Botón para reintentar la descarga en alta calidad.
        if (fullFailed) {
            Surface(
                onClick = {
                    retryKey++
                    fullFailed = false
                    fullLoaded = false
                },
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center).size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reintentar descarga en alta calidad",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun ChatMediaVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    onExpand: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var hasBeenReady by remember { mutableStateOf(VideoPlayerCache.isReady(url)) }
    val exoPlayer = remember {
        VideoPlayerCache.get(context, url).apply {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                hasBeenReady = true
            }
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        hasBeenReady = true
                    }
                }
            })
        }
    }
    val showSkeleton = !VideoPlayerCache.isReady(url)
    Box(modifier = modifier) {
        if (!hasBeenReady && showSkeleton) {
            ChatSkeletonBox(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
            }
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    if (fill) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize().alpha(if (hasBeenReady) 1f else 0f)
        )
        if (onExpand != null || onLongClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(34.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onExpand?.invoke() },
                        onLongClick = onLongClick
                    )
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Fullscreen, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun FullImageViewer(
    url: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    canPin: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            ChatMediaImage(
                url = url,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            ) {
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onReply) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = stringResource(R.string.notification_chat_reply), tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (canPin) {
                    Spacer(Modifier.width(12.dp))
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                        IconButton(onClick = { onTogglePin?.invoke() }) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = if (isPinned) stringResource(R.string.notification_unpin) else stringResource(R.string.notification_pin),
                                tint = if (isPinned) Color(0xFFFF5252) else Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notification_dismiss), tint = Color.White)
                    }
                }
            }
            Text(
                text = stringResource(R.string.notification_tap_outside),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun FullVideoViewer(
    url: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    canPin: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Player independiente para el visor a pantalla completa: no roza el player del chat
    // y permite transferir la posición al volver.
    val fullscreenPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaSource = ProgressiveMediaSource.Factory(MultimediaDiskCache.dataSourceFactory(context))
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }
    val mainPlayer = remember { VideoPlayerCache.get(context, url) }

    DisposableEffect(url) {
        // Al abrir la pantalla completa, pausar el player del chat para evitar audio doble
        // y arrancar el visor desde la posición actual.
        runCatching {
            val startPos = mainPlayer.currentPosition
            mainPlayer.pause()
            fullscreenPlayer.seekTo(startPos)
            fullscreenPlayer.playWhenReady = true
        }
        onDispose {
            // Sincronizar la posición de reproducción con el player del chat y pausarlo
            // para que al volver se continúe desde donde quedó, con video.
            runCatching {
                mainPlayer.seekTo(fullscreenPlayer.currentPosition)
                mainPlayer.pause()
            }
            runCatching { fullscreenPlayer.release() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { PlayerView(it).apply { player = fullscreenPlayer } },
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            ) {
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onReply) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = stringResource(R.string.notification_chat_reply), tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (canPin) {
                    Spacer(Modifier.width(12.dp))
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                        IconButton(onClick = { onTogglePin?.invoke() }) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = if (isPinned) "Desfijar" else "Fijar",
                                tint = if (isPinned) Color(0xFFFF5252) else Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notification_dismiss), tint = Color.White)
                    }
                }
            }
            Text(
                text = stringResource(R.string.notification_tap_outside),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

fun replyLabelFor(msg: ChatMessage, context: android.content.Context): String {
    return when {
        msg.imageUrl != null -> context.getString(R.string.notification_media_image)
        msg.videoUrl != null -> context.getString(R.string.notification_media_video)
        msg.fileUrl != null -> "📄 ${msg.fileName ?: context.getString(R.string.notification_media_file)}"
        else -> msg.text
    }
}

fun replyPreviewMetadataFor(msg: ChatMessage): Map<String, String> {
    return when {
        msg.imageUrl != null -> mapOf("replyPreviewType" to "image", "replyPreviewUrl" to msg.imageUrl)
        msg.videoUrl != null -> mapOf("replyPreviewType" to "video", "replyPreviewUrl" to msg.videoUrl)
        msg.fileUrl != null -> mapOf("replyPreviewType" to "file")
        else -> emptyMap()
    }
}

@Composable
fun ReplyPreviewBubble(
    metadata: Map<String, String>,
    fallbackText: String,
    fallbackName: String?,
    textColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Surface(
            color = Color.Black.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewType = metadata["replyPreviewType"]
                val previewUrl = metadata["replyPreviewUrl"].orEmpty()
                if (previewType == "image" && previewUrl.isNotBlank()) {
                    ChatMediaImage(
                        url = previewUrl,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (previewType == "video" && previewUrl.isNotBlank()) {
                    VideoFirstFrame(
                        url = previewUrl,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                } else if (previewType == "file") {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.InsertDriveFile,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = fallbackName ?: stringResource(R.string.notification_chat_default_sender),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = textColor.copy(alpha = 0.85f)
                    )
                    Text(
                        text = fallbackText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

fun parseMediaBatch(metadata: Map<String, String>): List<Triple<String, String, String>> {
    return runCatching {
        val arr = org.json.JSONArray(metadata["mediaBatch"] ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            Triple(o.optString("kind"), o.optString("url"), o.optString("name"))
        }
    }.getOrDefault(emptyList())
}

@androidx.media3.common.util.UnstableApi
@Composable
fun MediaBatchGalleryViewer(
    items: List<Triple<String, String, String>>,
    initialUrl: String?,
    onDismiss: () -> Unit,
    onReply: () -> Unit
) {
    val initialIndex = items.indexOfFirst { it.second == initialUrl }.coerceAtLeast(0)
    val pagerState = rememberPagerState(pageCount = { items.size }, initialPage = initialIndex)
    val scope = rememberCoroutineScope()

    // Al moverte entre páginas, pausa cualquier video que ya no esté visible.
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        val currentItem = items.getOrNull(pagerState.currentPage)
        val currentUrl = if (currentItem?.first == "video") currentItem.second else null
        VideoPlayerCache.pauseExcept(currentUrl)
    }

    // Al cerrar el visor, detén todos los videos para que no sigan sonando.
    DisposableEffect(Unit) {
        onDispose {
            VideoPlayerCache.pauseAll()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = items[page]
                when (item.first) {
                    "video" -> {
                        // Reproducción en línea dentro del grupo: no saca al usuario
                        // del visor colectivo.
                        ChatMediaVideoPlayer(
                            url = item.second,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        ChatMediaImage(
                            url = item.second,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 60.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onReply) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Responder", tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(12.dp))
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                    ) {
                        when (item.first) {
                            "video" -> VideoFirstFrame(item.second, Modifier.fillMaxSize())
                            else -> AsyncImage(model = item.second, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}
