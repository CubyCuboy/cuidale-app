package com.example.cuidaleapp.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.utils.ConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Elemento multimedia de la galería del dispositivo. */
data class MediaPickerItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val dateAdded: Long
)

/** Estado de carga del selector multimedia. */
private enum class MediaLoadState {
    LOADING,
    LOADED,
    ERROR
}

/**
 * Selector multimedia propio estilo Discord.
 *
 * Muestra un grid de fotos y videos con multi-selección (checkmark overlay),
 * tabs de filtro y límite máximo configurable. Cuando se alcanza el límite,
 * las demás miniaturas se bloquean visualmente hasta deseleccionar alguna.
 * Incluye manejo de errores de carga, permisos denegados (con acceso a
 * ajustes) y reintento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuidaleMediaPicker(
    maxItems: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var mediaItems by remember { mutableStateOf<List<MediaPickerItem>>(emptyList()) }
    var loadState by remember { mutableStateOf(MediaLoadState.LOADING) }
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var permissionAsked by remember { mutableStateOf(false) }

    val selectedUris = remember { mutableStateListOf<Uri>() }
    val limitReached = selectedUris.size >= maxItems
    val limitReachedMsg = stringResource(R.string.media_picker_limit_reached, maxItems)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionAsked = true
        val granted = result.values.all { it }
        permissionGranted = granted
        if (granted) {
            permissionPermanentlyDenied = false
            loadState = MediaLoadState.LOADING
            scope.launch { loadMediaFromStore(context, onLoaded = { items ->
                mediaItems = items
                loadState = if (items.isEmpty()) MediaLoadState.LOADED else MediaLoadState.LOADED
            }, onError = { loadState = MediaLoadState.ERROR }) }
        } else {
            // Si todos los permisos fueron denegados y no se puede volver a preguntar,
            // solo queda ir a ajustes del sistema.
            permissionPermanentlyDenied = result.values.none { it } &&
                !shouldShowPermissionRationale(context)
            loadState = MediaLoadState.LOADED
        }
    }

    fun requestPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(Unit) {
        if (hasMediaPermission(context)) {
            permissionGranted = true
            permissionAsked = true
            loadState = MediaLoadState.LOADING
            loadMediaFromStore(context, onLoaded = { items ->
                mediaItems = items
                loadState = MediaLoadState.LOADED
            }, onError = { loadState = MediaLoadState.ERROR })
        } else {
            loadState = MediaLoadState.LOADED
        }
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.media_picker_title), fontWeight = FontWeight.Bold)
                            Text(
                                if (selectedUris.isEmpty()) stringResource(R.string.media_picker_select_hint)
                                else stringResource(R.string.media_picker_selected_count, selectedUris.size, maxItems),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_picker_back_cd))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { onConfirm(selectedUris.toList()) },
                            enabled = selectedUris.isNotEmpty() && isOnline
                        ) {
                            Text(stringResource(R.string.media_picker_send), fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Tabs de filtro.
                TabRow(selectedTabIndex = filter.ordinal) {
                    MediaFilter.entries.forEach { f ->
                        Tab(
                            selected = filter == f,
                            onClick = { filter = f },
                            text = {
                                Text(stringResource(f.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        )
                    }
                }

                when {
                    loadState == MediaLoadState.LOADING -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.media_picker_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    loadState == MediaLoadState.ERROR -> {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(R.string.media_picker_load_error),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                loadState = MediaLoadState.LOADING
                                scope.launch { loadMediaFromStore(context, onLoaded = { items ->
                                    mediaItems = items
                                    loadState = MediaLoadState.LOADED
                                }, onError = { loadState = MediaLoadState.ERROR }) }
                            }) {
                                Text(stringResource(R.string.media_picker_retry))
                            }
                        }
                    }
                    !permissionGranted -> {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(if (permissionPermanentlyDenied) R.string.media_picker_permission_denied_settings else R.string.media_picker_permission_required),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.media_picker_permission_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            if (permissionPermanentlyDenied) {
                                Button(onClick = { openAppSettings(context) }) {
                                    Text(stringResource(R.string.media_picker_open_settings))
                                }
                            } else {
                                Button(onClick = { requestPermissions() }) {
                                    Text(stringResource(R.string.media_picker_allow_permission))
                                }
                            }
                        }
                    }
                    mediaItems.isEmpty() -> {
                        EmptyMediaPlaceholder(stringResource(R.string.media_picker_empty_all))
                    }
                    else -> {
                        val visible = when (filter) {
                            MediaFilter.ALL -> mediaItems
                            MediaFilter.PHOTOS -> mediaItems.filter { !it.isVideo }
                            MediaFilter.VIDEOS -> mediaItems.filter { it.isVideo }
                        }
                        if (visible.isEmpty()) {
                            EmptyMediaPlaceholder(
                                stringResource(
                                    when (filter) {
                                        MediaFilter.ALL -> R.string.media_picker_empty_all
                                        MediaFilter.PHOTOS -> R.string.media_picker_empty_photos
                                        MediaFilter.VIDEOS -> R.string.media_picker_empty_videos
                                    }
                                )
                            )
                        } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(visible, key = { it.id }) { item ->
                                val selected = selectedUris.contains(item.uri)
                                val blocked = limitReached && !selected
                                MediaPickerTile(
                                    item = item,
                                    selected = selected,
                                    blocked = blocked,
                                    onClick = {
                                        if (blocked) {
                                            showMessage(limitReachedMsg)
                                        } else if (selected) {
                                            selectedUris.remove(item.uri)
                                        } else {
                                            selectedUris.add(item.uri)
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

private enum class MediaFilter(val labelRes: Int) {
    ALL(R.string.media_picker_filter_all),
    PHOTOS(R.string.media_picker_filter_photos),
    VIDEOS(R.string.media_picker_filter_videos)
}

/** Placeholder vacío estilo Discord. */
@Composable
private fun EmptyMediaPlaceholder(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MediaPickerTile(
    item: MediaPickerItem,
    selected: Boolean,
    blocked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = stringResource(R.string.media_picker_video_cd),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Overlay de selección estilo Discord.
        if (selected) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        // Estado bloqueado: se atenúa y muestra candado.
        if (blocked) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
            Box(
                Modifier.align(Alignment.Center)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    .padding(6.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.media_picker_locked_cd),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Checkmark de selección (esquina superior derecha).
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF3BA55D) else Color.Black.copy(alpha = 0.4f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.media_picker_selected_cd),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun hasMediaPermission(context: Context): Boolean {
    val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun shouldShowPermissionRationale(context: Context): Boolean {
    val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return permissions.any { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(context as android.app.Activity, it) }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private suspend fun loadMediaFromStore(
    context: Context,
    onLoaded: (List<MediaPickerItem>) -> Unit,
    onError: () -> Unit
) {
    try {
        val items = withContext(Dispatchers.IO) {
            val images = queryMedia(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false
            )
            val videos = queryMedia(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true
            )
            (images + videos).sortedByDescending { it.dateAdded }
        }
        onLoaded(items)
    } catch (e: Exception) {
        onError()
    }
}

private fun queryMedia(context: Context, uri: Uri, isVideo: Boolean): List<MediaPickerItem> {
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATE_ADDED
    )
    val result = mutableListOf<MediaPickerItem>()
    context.contentResolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "media_$id"
            val dateAdded = cursor.getLong(dateCol)
            val itemUri = android.content.ContentUris.withAppendedId(uri, id)
            result.add(MediaPickerItem(id = id, uri = itemUri, name = name, isVideo = isVideo, dateAdded = dateAdded))
        }
    }
    return result
}
