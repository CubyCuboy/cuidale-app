package com.example.cuidaleapp.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsCompat
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
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.ChatMessage
import kotlinx.coroutines.launch
import java.io.File

object VideoThumbnailCache {
    private const val MAX_MEMORY_ENTRIES = 64
    private val memory = object : LinkedHashMap<String, android.graphics.Bitmap>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }

    private fun diskDir(context: Context): File {
        return File(context.cacheDir, "video_thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun diskFile(context: Context, url: String): File {
        val name = java.security.MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(diskDir(context), "$name.png")
    }

    private val memoryDuration = java.util.concurrent.ConcurrentHashMap<String, Long>()
    
    private val fetchGate = java.util.concurrent.Semaphore(2)

    private fun durationFile(context: Context, url: String): File {
        val name = java.security.MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(diskDir(context), "$name.dur")
    }

    fun getDuration(context: Context, url: String): Long {
        memoryDuration[url]?.let { return it }
        return runCatching {
            durationFile(context, url).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: -1L
        }.getOrDefault(-1L)
    }

    private fun saveDuration(context: Context, url: String, durationMs: Long) {
        if (durationMs <= 0) return
        memoryDuration[url] = durationMs
        runCatching { durationFile(context, url).writeText(durationMs.toString()) }
    }

    private fun downloadVideoToTemp(context: Context, url: String): File? {
        return try {
            val tmp = File(context.cacheDir, "vid_tmp_" + System.currentTimeMillis() + ".mp4")
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) CuidaleApp/1.0")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            tmp
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleToMax(bmp: android.graphics.Bitmap, maxSide: Int): android.graphics.Bitmap {
        val longSide = if (bmp.width >= bmp.height) bmp.width else bmp.height
        val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
        if (scale >= 1f) return bmp
        val scaled = android.graphics.Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1),
            true
        )
        bmp.recycle()
        return scaled
    }

    private fun scaledDims(widthPx: Int, heightPx: Int, maxSide: Int): Pair<Int, Int> {
        if (widthPx <= 0 || heightPx <= 0) return maxSide to maxSide
        val longSide = maxOf(widthPx, heightPx)
        if (longSide <= maxSide) return widthPx to heightPx
        val scale = maxSide.toFloat() / longSide
        return (widthPx * scale).toInt().coerceAtLeast(1) to (heightPx * scale).toInt().coerceAtLeast(1)
    }

    private fun extractFrameScaled(retriever: android.media.MediaMetadataRetriever, dw: Int, dh: Int): android.graphics.Bitmap? {
        return if (android.os.Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(500_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dw, dh)
                ?: retriever.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dw, dh)
        } else {
            val raw = retriever.getFrameAtTime(500_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            raw?.let { scaleToMax(it, 480) }
        }
    }

    fun get(context: Context, url: String): android.graphics.Bitmap? {
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

    fun put(context: Context, url: String, bmp: android.graphics.Bitmap) {
        memory[url] = bmp
        runCatching {
            diskFile(context, url).outputStream().use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    suspend fun fetch(context: Context, url: String): android.graphics.Bitmap? {
        return fetchWithDuration(context, url).first
    }

    suspend fun fetchWithDuration(context: Context, url: String): Pair<android.graphics.Bitmap?, Long> {
        val cached = get(context, url)
        if (cached != null) return cached to getDuration(context, url)

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                var durationMs = -1L
                var frame: android.graphics.Bitmap? = null
                var dw = 480
                var dh = 480
                fetchGate.acquire()
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, android.net.Uri.parse(url))
                        durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
                        val vw = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: -1
                        val vh = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: -1
                        val (sw, sh) = scaledDims(vw, vh, 480)
                        dw = sw
                        dh = sh
                        frame = extractFrameScaled(retriever, dw, dh)
                    } catch (e: Exception) {
                        frame = null
                    } finally {
                        runCatching { retriever.release() }
                    }

                    if (frame == null) {
                        val tmp = downloadVideoToTemp(context, url)
                        if (tmp != null) {
                            try {
                                val r2 = android.media.MediaMetadataRetriever()
                                try {
                                    r2.setDataSource(tmp.absolutePath)
                                    durationMs = r2.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
                                    frame = extractFrameScaled(r2, dw, dh)
                                } finally {
                                    runCatching { r2.release() }
                                }
                            } catch (e: Exception) {
                                frame = null
                            }
                        }
                        runCatching { tmp?.delete() }
                    }
                } finally {
                    fetchGate.release()
                }

                val result = frame?.let { scaleToMax(it, 480) }
                if (result != null) put(context, url, result)
                if (durationMs > 0) saveDuration(context, url, durationMs)
                result to durationMs
            }.getOrDefault(null to -1L)
        }
    }

    fun clear(context: Context) {
        memory.clear()
        runCatching { diskDir(context).deleteRecursively() }
    }
}

@Composable
fun VideoFirstFrame(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val frame by produceState(initialValue = VideoThumbnailCache.get(context, url), url) {
        value = VideoThumbnailCache.fetch(context, url)
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

@Composable
fun VideoPreviewWithDuration(
    url: String,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true
) {
    val context = LocalContext.current
    val state by produceState<Pair<android.graphics.Bitmap?, Long>>(
        initialValue = VideoThumbnailCache.get(context, url) to VideoThumbnailCache.getDuration(context, url),
        url
    ) {
        value = VideoThumbnailCache.fetchWithDuration(context, url)
    }
    val frame = state.first
    val durationMs = state.second

    Box(modifier = modifier) {
        if (frame != null) {
            AsyncImage(
                model = frame,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }

        if (showDuration && durationMs > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    formatVideoDuration(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMin = minutes % 60
        "%d:%02d:%02d".format(hours, remMin, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

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

    private const val MAX_PLAYERS = 8
    private val players = java.util.LinkedHashMap<String, ExoPlayer>()
    private val readyUrls = mutableSetOf<String>()
    private val endedUrls = mutableSetOf<String>()

    fun get(context: Context, url: String): ExoPlayer {
        players[url]?.let { return it }
        if (players.size >= MAX_PLAYERS) {
            val oldestKey = players.keys.firstOrNull()
            if (oldestKey != null) {
                runCatching { players[oldestKey]?.release() }
                players.remove(oldestKey)
            }
        }
        val player = ExoPlayer.Builder(context).build().apply {
            val mediaSource = ProgressiveMediaSource.Factory(MultimediaDiskCache.dataSourceFactory(context))
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(mediaSource)
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        readyUrls.add(url)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        endedUrls.add(url)
                    }
                }
            })
        }
        players[url] = player
        return player
    }

    fun isReady(url: String): Boolean = url in readyUrls
    fun isEnded(url: String): Boolean = url in endedUrls
    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
        readyUrls.clear()
        endedUrls.clear()
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
    fullSize: Int? = null,
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

    val thumbRequest = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(120)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val fullRequest = remember(url, retryKey) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { fullSize?.let { size(it) } }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    Box(modifier = mediaModifier) {
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
            }
        )

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
    onTogglePin: (() -> Unit)? = null,
    onGoToMessage: (() -> Unit)? = null
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
                if (onGoToMessage != null) {
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                        IconButton(onClick = { onGoToMessage.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Ir al mensaje", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
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
    onTogglePin: (() -> Unit)? = null,
    onGoToMessage: (() -> Unit)? = null
) {
    val context = LocalContext.current
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
        runCatching {
            val startPos = mainPlayer.currentPosition
            mainPlayer.pause()
            fullscreenPlayer.seekTo(startPos)
            fullscreenPlayer.playWhenReady = true
        }
        onDispose {
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
                if (onGoToMessage != null) {
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = CircleShape) {
                        IconButton(onClick = { onGoToMessage.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Ir al mensaje", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
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
                                contentDescription = if (isPinned) "Des fijar" else "Fijar",
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

fun replyLabelFor(msg: ChatMessage, context: Context): String {
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
                when (previewType) {
                    "image" if previewUrl.isNotBlank() -> {
                        ChatMediaImage(
                            url = previewUrl,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    "video" if previewUrl.isNotBlank() -> {
                        VideoFirstFrame(
                            url = previewUrl,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    "file" -> {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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

@Composable
private fun ZoomableImage(
    url: String,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var scale by remember(url) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1.01f

    LaunchedEffect(zoomed) {
        onZoomChanged(zoomed)
    }

    Box(
        modifier = modifier
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (scale <= 1.01f) {
                            scale = 2.5f
                            offset = Offset.Zero
                            onZoomChanged(true)
                        } else {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        }
                    }
                )
            }

            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 && scale <= 5f) {
                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoomChange != 1f || scale > 1.01f) {
                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                offset = (offset + pan)
                                val limit = 600f * (scale - 1f)
                                offset = Offset(
                                    offset.x.coerceIn(-limit, limit),
                                    offset.y.coerceIn(-limit, limit)
                                )
                                if (scale <= 1.01f) {
                                    offset = Offset.Zero
                                    onZoomChanged(false)
                                } else {
                                    onZoomChanged(true)
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(url, zoomed) {
                if (zoomed) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = (offset + pan)
                        val limit = 600f * (scale - 1f)
                        offset = Offset(
                            offset.x.coerceIn(-limit, limit),
                            offset.y.coerceIn(-limit, limit)
                        )
                        if (scale <= 1.01f) {
                            offset = Offset.Zero
                            onZoomChanged(false)
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            ChatMediaImage(
                url = url,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun MediaThumbnailCard(
    item: Triple<String, String, String>,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbScale"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = tween(180),
        label = "thumbAlpha"
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 12.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbElevation"
    )
    val lift by animateDpAsState(
        targetValue = if (selected) (-8).dp else 0.dp,
        animationSpec = tween(220),
        label = "thumbLift"
    )

    val dealDelay = (index * 45).coerceAtMost(450)

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(tween(320, delayMillis = dealDelay)) { it } +
            fadeIn(tween(320, delayMillis = dealDelay))
    ) {
        Box(
            modifier = modifier
                .zIndex(if (selected) 1f else 0f)
                .size(58.dp)
                .shadow(elevation, shape, clip = false)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift.toPx()
                    this.alpha = thumbAlpha
                }
                .clip(shape)
                .border(2.dp, if (selected) Color.White else Color.Transparent, shape)
                .clickable(onClick = onClick)
        ) {
            when (item.first) {
                "video" -> VideoFirstFrame(item.second, Modifier.fillMaxSize())
                else -> SubcomposeAsyncImage(
                    model = item.second,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Imagen no disponible",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )
            }
        }
    }
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
    val thumbListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Sincroniza la fila de miniaturas con la página activa del visor.
    LaunchedEffect(pagerState.currentPage) {
        thumbListState.animateScrollToItem(pagerState.currentPage.coerceAtLeast(0))
    }

    // Carrusel de miniaturas: filmstrip sincronizado con el pager.
    val density = LocalDensity.current

    // Insets reales de navegación: la ventana del Dialog no siempre los reporta en
    // dispositivos edge-to-edge (Android 15), así que se leen desde el decorView de la Activity.
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
    }
    val decorNavInsetPx = remember(activity) {
        val insets = activity?.window?.decorView?.rootWindowInsets
        if (insets == null) 0
        else WindowInsetsCompat.toWindowInsetsCompat(insets)
            .getInsets(WindowInsetsCompat.Type.navigationBars())
            .bottom
    }
    var dialogHeightPx by remember { mutableIntStateOf(0) }
    val displayHeightPx = remember { Resources.getSystem().displayMetrics.heightPixels }
    val necesitaInsetAbajo = dialogHeightPx > 0 &&
        (displayHeightPx - dialogHeightPx) < decorNavInsetPx

    var chromeVisible by remember { mutableStateOf(true) }

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        val currentItem = items.getOrNull(pagerState.currentPage)
        val currentUrl = if (currentItem?.first == "video") currentItem.second else null
        VideoPlayerCache.pauseExcept(currentUrl)

        // Ocultar el chrome (miniaturas) tras 3 s mientras se reproduce un video,
        // y mostrarlo de nuevo automáticamente cuando el video termina.
        if (currentItem?.first == "video") {
            kotlinx.coroutines.delay(3000)
            chromeVisible = false
            while (true) {
                kotlinx.coroutines.delay(500)
                if (VideoPlayerCache.isEnded(currentItem.second)) {
                    chromeVisible = true
                    break
                }
            }
        } else {
            chromeVisible = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            VideoPlayerCache.pauseAll()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { dialogHeightPx = it.height }
                .background(Color.Black)
        ) {

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { -it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
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
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val item = items[page]
                when (item.first) {
                    "video" -> {
                        ChatMediaVideoPlayer(
                            url = item.second,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        ZoomableImage(
                            url = item.second,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            onSingleTap = { chromeVisible = !chromeVisible },
                            onZoomChanged = { zoomed -> chromeVisible = !zoomed }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val thumbSizeDp = 58.dp
                    val thumbSpacingDp = 10.dp
                    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val leadingPx = ((viewportWidthPx - thumbSizePx) / 2f).coerceAtLeast(0f)
                    val bottomInsetPx = if (necesitaInsetAbajo) {
                        maxOf(
                            WindowInsets.navigationBars.getBottom(density),
                            decorNavInsetPx
                        )
                    } else 0

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = with(density) { bottomInsetPx.toDp() })
                            .background(
                                Color.Black.copy(alpha = 0.65f),
                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    ) {
                        LazyRow(
                            state = thumbListState,
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentPadding = PaddingValues(horizontal = with(density) { leadingPx.toDp() }),
                            horizontalArrangement = Arrangement.spacedBy(thumbSpacingDp)
                        ) {
                            itemsIndexed(items, key = { _, item -> "${item.first}:${item.second}" }) { index, item ->
                                MediaThumbnailCard(
                                    item = item,
                                    index = index,
                                    selected = index == pagerState.currentPage,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
