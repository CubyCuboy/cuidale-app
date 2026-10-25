package com.example.cuidaleapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.utils.InAppNotification
import com.example.cuidaleapp.utils.InAppNotificationCenter
import com.example.cuidaleapp.utils.InAppNotificationNavigator

private const val LIFETIME_MS = 4500L

@Composable
fun InAppNotificationHost(
    onNotificationClick: ((InAppNotification) -> Unit)? = null,
    onMarkRead: ((InAppNotification) -> Unit)? = null,
    onReply: ((InAppNotification) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val handleClick: (InAppNotification) -> Unit = onNotificationClick ?: { notification ->
        InAppNotificationNavigator.navigate(context, notification)
    }
    // Las acciones SIEMPRE cierran la notificación interna (no se reinicia su vida útil)
    // y luego ejecutan el handler personalizado si existe.
    val handleMarkRead: (InAppNotification) -> Unit = { notification ->
        InAppNotificationCenter.dismiss(notification.id)
        onMarkRead?.invoke(notification)
    }
    val handleReply: (InAppNotification) -> Unit = { notification ->
        InAppNotificationCenter.dismiss(notification.id)
        onReply?.invoke(notification)
    }

    val events by InAppNotificationCenter.events.collectAsState()
    val current = events.firstOrNull()
    val isScrolled by AppNavigationState.isScreenScrolled.collectAsState()

    var dismissedId by remember { mutableStateOf<String?>(null) }

    val lifetimeProgress = remember(current?.id) { Animatable(1f) }

    LaunchedEffect(current?.id) {
        if (current != null) {
            dismissedId = null
            lifetimeProgress.snapTo(1f)
            lifetimeProgress.animateTo(0f, tween(LIFETIME_MS.toInt(), easing = LinearEasing))
            InAppNotificationCenter.dismiss(current.id)
        }
    }

    AnimatedVisibility(
        visible = current != null && dismissedId != current.id,
        enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300)),
        modifier = modifier
    ) {
        current?.let { notification ->
            val customColors = CuidaleThemeExtras.customColors
            val accent = colorForType(notification.tipo)

            AnimatedContent(
                targetState = isScrolled,
                transitionSpec = {
                    (fadeIn(tween(220)) + expandVertically(tween(220))) togetherWith
                        (fadeOut(tween(220)) + shrinkVertically(tween(220)))
                },
                label = "notificationModeTransition"
            ) { floating ->
                if (floating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .zIndex(20f)
                            .clickable { dismissedId = notification.id; handleClick(notification) }
                    ) {
                        Surface(
                            color = customColors.panelBackground,
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 10.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NotificationCardContent(
                                notification = notification,
                                accent = accent,
                                onDismiss = {
                                    dismissedId = notification.id
                                    InAppNotificationCenter.dismiss(notification.id)
                                },
                                onMarkRead = { handleMarkRead(notification) },
                                onReply = { handleReply(notification) },
                                lifetimeProgress = lifetimeProgress
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dismissedId = notification.id; handleClick(notification) }
                    ) {
                        Surface(
                            color = customColors.panelBackground,
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NotificationCardContent(
                                notification = notification,
                                accent = accent,
                                onDismiss = {
                                    dismissedId = notification.id
                                    InAppNotificationCenter.dismiss(notification.id)
                                },
                                onMarkRead = { handleMarkRead(notification) },
                                onReply = { handleReply(notification) },
                                lifetimeProgress = lifetimeProgress
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCardContent(
    notification: InAppNotification,
    accent: Color,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onReply: () -> Unit,
    lifetimeProgress: Animatable<Float, *>
) {
    Column {
        val isChat = notification.tipo == "nuevo_mensaje"
        val senderName = notification.payload["emisorNombre"] ?: stringResource(R.string.notification_chat_default_sender)
        val senderPhoto = notification.payload["emisorFoto"] ?: ""
        val chatName = notification.payload["chatNombre"] ?: ""
        val messageText = notification.payload["text"]?.take(160) ?: notification.body.take(160)
        val mediaTipo = notification.payload["mediaTipo"]
        val mediaUrl = notification.payload["mediaUrl"] ?: ""
        val time = formatNotifTime(notification.timestamp)

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (senderPhoto.isNotBlank() && senderPhoto != "n/a") {
                    coil3.compose.AsyncImage(
                        model = senderPhoto,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isChat) Icons.AutoMirrored.Filled.Chat else iconForType(notification.tipo),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isChat) senderName else notification.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (isChat && chatName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.notification_chat_in, chatName),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.notification_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Preview multimedia (imagen o primer frame de video)
        if (mediaTipo != null && mediaUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (mediaTipo) {
                    "image" -> coil3.compose.AsyncImage(
                        model = mediaUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    "video" -> VideoFirstFrame(
                        url = mediaUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Acciones
        if (isChat) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onReply,
                    color = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.notification_chat_reply), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accent)
                    }
                }
                Surface(
                    onClick = onMarkRead,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.notification_chat_mark_read), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            LinearProgressIndicator(
                progress = { lifetimeProgress.value },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = accent,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun CuidaleAppRoot(
    darkTheme: Boolean,
    onNotificationClick: (InAppNotification) -> Unit = {},
    content: @Composable () -> Unit
) {
    com.example.cuidaleapp.ui.theme.CuidaleAppTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
            Column(modifier = Modifier.fillMaxWidth()) {
                ConnectivityBanner()
                InAppNotificationHost(onNotificationClick = onNotificationClick)
            }
        }
    }
}

private fun formatNotifTime(timestamp: Long): String {
    return try {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

private fun iconForType(tipo: String): ImageVector {
    return when (tipo) {
        "nuevo_mensaje" -> Icons.AutoMirrored.Filled.Chat
        "solicitud_vinculo", "vinculo_aceptado", "nuevo_usuario_zona" -> Icons.Default.PersonAdd
        "postulacion_recibida", "postulacion_aceptada", "servicio_finalizado" -> Icons.Default.TaskAlt
        "recordatorio_tarea", "tarea_atrasada", "tarea_finalizada", "tarea_postergada", "turno_finalizado" -> Icons.AutoMirrored.Filled.EventNote
        "configuracion_cuenta" -> Icons.Default.VpnKey
        "verificacion_cuenta" -> Icons.Default.CheckCircle
        else -> Icons.Default.Notifications
    }
}

private fun colorForType(tipo: String): Color {
    return when (tipo) {
        "nuevo_mensaje" -> Color(0xFF1E88E5)
        "solicitud_vinculo", "vinculo_aceptado", "nuevo_usuario_zona" -> Color(0xFF43A047)
        "postulacion_recibida", "postulacion_aceptada", "servicio_finalizado", "tarea_finalizada" -> Color(0xFF8E24AA)
        "recordatorio_tarea", "tarea_atrasada", "tarea_postergada", "turno_finalizado" -> Color(0xFFF4511E)
        "configuracion_cuenta", "verificacion_cuenta" -> Color(0xFFFB8C00)
        else -> Color(0xFF1E88E5)
    }
}
