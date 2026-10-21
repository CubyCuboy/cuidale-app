package com.example.cuidaleapp.ui.components

import android.content.Context
import java.io.File
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.models.ChatMessage

object MediaSkeletonCache {
    private val seenUrls = mutableSetOf<String>()
    fun markSeen(url: String) = seenUrls.add(url)
    fun wasSeen(url: String): Boolean = url in seenUrls
    fun clearSeen() = seenUrls.clear()
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
}

@Composable
fun ChatSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    highlightColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(baseColor, highlightColor, baseColor),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(500f, 0f)
                )
            )
            .alpha(alpha)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMediaImage(
    url: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
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
    val showSkeleton = !MediaSkeletonCache.wasSeen(url)
    val request = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = mediaModifier,
        contentScale = ContentScale.Fit,
        onSuccess = { MediaSkeletonCache.markSeen(url) },
        loading = {
            if (showSkeleton) {
                ChatSkeletonBox(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            }
        },
        error = {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMediaVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
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
            factory = { PlayerView(it).apply { player = exoPlayer } },
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
    onCopy: () -> Unit,
    onShare: (() -> Unit)? = null,
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
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Responder", tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.White)
                    }
                }
                if (onShare != null) {
                    Spacer(Modifier.width(12.dp))
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = Color.White)
                        }
                    }
                }
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
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }
            Text(
                text = "Toca fuera para cerrar",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

@Composable
fun FullVideoViewer(
    url: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onShare: (() -> Unit)? = null,
    canPin: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val exoPlayer = remember { VideoPlayerCache.get(context, url) }
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
                    factory = { PlayerView(it).apply { player = exoPlayer } },
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            ) {
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onReply) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Responder", tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.White)
                    }
                }
                if (onShare != null) {
                    Spacer(Modifier.width(12.dp))
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = Color.White)
                        }
                    }
                }
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
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }
            Text(
                text = "Toca fuera para cerrar",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

fun replyLabelFor(msg: ChatMessage): String {
    return when {
        msg.imageUrl != null -> "📷 Imagen"
        msg.videoUrl != null -> "🎬 Video"
        msg.fileUrl != null -> "📄 ${msg.fileName ?: "Documento"}"
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
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
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
                        text = fallbackName ?: "Usuario",
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
