package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.Notification
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.ConnectivityBanner
import com.example.cuidaleapp.ui.components.CuidaleBottomBar
import com.example.cuidaleapp.ui.components.CuidaleCircularMenuButton
import com.example.cuidaleapp.ui.components.CuidaleDrawerContent
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.NotificationsViewModel
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    userData: UserData,
    isDark: Boolean,
    onNotificationClick: (Notification) -> Unit,
    notificationsViewModel: NotificationsViewModel = viewModel()
) {
    val customColors = CuidaleThemeExtras.customColors

    val notifications by notificationsViewModel.notifications.collectAsState()
    val users by notificationsViewModel.users.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {



        Text(
            text = "Notificaciones",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = Color.White,
            modifier = Modifier.padding(24.dp)
        )

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        null,
                        modifier = Modifier.size(100.dp),
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No tienes notificaciones",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            val groupedMessages = remember(notifications) {
                notifications.filter { it.tipo == "nuevo_mensaje" }
                    .groupBy { it.emisor }
            }
            val otherNotifs = remember(notifications) {
                notifications.filter { it.tipo != "nuevo_mensaje" }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (groupedMessages.isNotEmpty()) {
                    item(key = "header_msgs") {
                        Text(
                            "MENSAJES PENDIENTES",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDark) Color.LightGray else Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp).animateItem()
                        )
                    }

                    groupedMessages.forEach { (emisorId, msgs) ->
                        val sender = users[emisorId]
                        val chatId = msgs.firstOrNull()?.data?.get("chatId") ?: ""

                        item(key = "group_$emisorId") {
                            HardShadowPanel(
                                backgroundColor = if (isDark) customColors.panelBackground else Color.White,
                                shadowColor = customColors.aqua.copy(alpha = 0.2f),
                                modifier = Modifier.animateItem()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(customColors.aqua.copy(alpha = 0.1f))) {
                                            if (sender?.fotoUrl?.isNotEmpty() == true) {
                                                AsyncImage(model = sender.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else {
                                                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.align(Alignment.Center), tint = customColors.aqua)
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                buildAnnotatedString {
                                                    append("Tienes ")
                                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = customColors.aqua)) {
                                                        append("${msgs.size} mensajes")
                                                    }
                                                    append(" de ")
                                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                        append(sender?.nombre ?: "Usuario")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isDark) Color.White else Color.Black
                                            )
                                        }
                                        Badge(containerColor = customColors.aqua) {
                                            Text(msgs.size.toString(), color = Color.White)
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { notificationsViewModel.markChatGroupAsRead(chatId) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                                        ) {
                                            Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Leído", fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { onNotificationClick(msgs.first()) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = customColors.aqua)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Ir al Chat", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (otherNotifs.isNotEmpty()) {
                    item(key = "header_alerts") {
                        Text(
                            "ALERTAS Y SOLICITUDES",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp).animateItem()
                        )
                    }
                    items(otherNotifs, key = { it.id }) { notif ->
                        val sender = users[notif.emisor]
                        NotificationItem(
                            notification = notif,
                            sender = sender,
                            userRole = userData.rol,
                            isDark = isDark,
                            modifier = Modifier.animateItem(),
                            onMainAction = {
                                notificationsViewModel.markAsRead(notif.id)
                                if (notif.tipo == "nuevo_mensaje") {
                                    val cid = notif.data["chatId"] ?: ""
                                    if (cid.isNotEmpty()) {
                                        notificationsViewModel.markChatGroupAsRead(cid)
                                    }
                                }
                                onNotificationClick(notif)
                            },
                            onMarkRead = { notificationsViewModel.markAsRead(notif.id) },
                            onDelete = { notificationsViewModel.deleteNotification(notif.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    sender: UserData?,
    userRole: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onMainAction: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val isSystemNotification = notification.tipo == "configuracion_cuenta"
    val itemAccent = when (notification.tipo) {
        "solicitud_vinculo" -> customColors.aqua
        "nuevo_mensaje" -> customColors.lime
        "nuevo_usuario" -> customColors.mint
        "recordatorio_tarea" -> MaterialTheme.colorScheme.primary
        "tarea_atrasada" -> Color.Red
        "tarea_finalizada" -> customColors.successStrong
        "tarea_postergada" -> Color(0xFFFFA000)
        "turno_finalizado" -> customColors.aqua
        "configuracion_cuenta" -> MaterialTheme.colorScheme.primary
        else -> customColors.successStrong
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val goToHistoryAndClearNotif = {
        onDelete()
        val intent = android.content.Intent(context, com.example.cuidaleapp.activities.AccountHistoryActivity::class.java)
        context.startActivity(intent)
    }

    HardShadowPanel(
        backgroundColor = if (notification.leida) (if (isDark) Color(0xFF1B263B) else Color.White.copy(alpha = 0.9f)) else (if (isDark) Color(0xFF2B3A55) else Color.White),
        shadowColor = if (notification.leida) Color.Transparent else itemAccent.copy(alpha = 0.2f),
        modifier = modifier.then(
            if (isSystemNotification) {
                Modifier.clickable {
                    goToHistoryAndClearNotif()
                }
            } else {
                Modifier
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(itemAccent.copy(alpha = 0.1f))) {
                    if (sender?.fotoUrl?.isNotEmpty() == true && !isSystemNotification && notification.tipo !in listOf("recordatorio_tarea", "tarea_atrasada", "tarea_finalizada", "tarea_postergada", "turno_finalizado")) {
                        AsyncImage(model = sender.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(
                            imageVector = when(notification.tipo) {
                                "solicitud_vinculo" -> Icons.Default.PersonAdd
                                "nuevo_mensaje" -> Icons.AutoMirrored.Filled.Chat
                                "nuevo_usuario" -> Icons.Default.NewReleases
                                "recordatorio_tarea" -> Icons.Default.NotificationImportant
                                "tarea_atrasada" -> Icons.Default.PriorityHigh
                                "tarea_finalizada" -> Icons.Default.TaskAlt
                                "tarea_postergada" -> Icons.Default.Update
                                "turno_finalizado" -> Icons.Default.Flag
                                "configuracion_cuenta" -> Icons.Default.Security
                                else -> Icons.Default.Notifications
                            },
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.Center),
                            tint = itemAccent
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (notification.tipo) {
                            "solicitud_vinculo" -> "Nueva solicitud de chat"
                            "vinculo_aceptado" -> "¡Solicitud aceptada!"
                            "postulacion_recibida" -> notification.data["titulo"] ?: "Nueva postulación recibida"
                            "postulacion_aceptada" -> notification.data["titulo"] ?: "¡Postulación aceptada!"
                            "servicio_finalizado" -> notification.data["titulo"] ?: "Servicio finalizado"
                            "nuevo_mensaje" -> "Mensaje de ${sender?.nombre ?: "Usuario"}"
                            "nuevo_usuario", "nuevo_usuario_zona" -> "¡Nuevo ${if(userRole == "cuidador") "paciente" else "cuidador"} en tu zona!"
                            "recordatorio_tarea" -> notification.data["titulo"] ?: "Recordatorio de Tarea"
                            "tarea_atrasada" -> notification.data["titulo"] ?: "Tarea Atrasada"
                            "tarea_finalizada" -> notification.data["titulo"] ?: "Tarea Realizada"
                            "tarea_postergada" -> notification.data["titulo"] ?: "Tarea Postergada"
                            "turno_finalizado" -> notification.data["titulo"] ?: "Turno Finalizado"
                            "configuracion_cuenta" -> notification.data["title"] ?: notification.data["titulo"] ?: "Gestión de Cuenta"
                            else -> notification.data["titulo"] ?: notification.data["title"] ?: "Notificación"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )
                    Text(
                        text = when (notification.tipo) {
                            "solicitud_vinculo" -> "${sender?.nombre ?: "Alguien"} quiere conectar contigo."
                            "vinculo_aceptado" -> "${sender?.nombre ?: "Un usuario"} aceptó tu solicitud. ¡Ya pueden hablar!"
                            "postulacion_recibida" -> "${sender?.nombre ?: "Un cuidador"} se ha postulado a tu solicitud de cuidado."
                            "postulacion_aceptada" -> "Tu postulación ha sido aceptada. ¡Revisa la solicitud de servicio!"
                            "servicio_finalizado" -> "El servicio de cuidado ha finalizado exitosamente."
                            "nuevo_mensaje" -> notification.data["text"] ?: notification.data["body"] ?: "Toca para ver el mensaje."
                            "nuevo_usuario", "nuevo_usuario_zona" -> "${sender?.nombre ?: "Alguien"} acaba de unirse a Cuidale. ¡Conócelo!"
                            "recordatorio_tarea", "tarea_atrasada", "tarea_finalizada", "tarea_postergada", "turno_finalizado" -> notification.data["body"] ?: notification.data["text"] ?: notification.data["texto"] ?: ""
                            "configuracion_cuenta" -> notification.data["text"] ?: notification.data["body"] ?: "Actualización de cuenta realizada."
                            else -> notification.data["body"] ?: notification.data["text"] ?: notification.data["texto"] ?: "Tienes un nuevo aviso."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.LightGray else Color.DarkGray,
                        maxLines = 3
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(notification.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )

                if (!notification.leida) {
                    TextButton(onClick = onMarkRead) {
                        Text("LEÍDO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(
                    onClick = if (isSystemNotification) goToHistoryAndClearNotif else onMainAction,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = itemAccent)
                ) {
                    val btnText = when(notification.tipo) {
                        "nuevo_mensaje" -> "IR AL CHAT"
                        "solicitud_vinculo", "nuevo_usuario", "nuevo_usuario_zona" -> "VER PERFIL"
                        "postulacion_recibida", "postulacion_aceptada", "servicio_finalizado" -> "VER SOLICITUD"
                        "recordatorio_tarea", "tarea_atrasada", "tarea_finalizada", "tarea_postergada" -> "VER TAREA"
                        "turno_finalizado" -> "VER INFORME"
                        "configuracion_cuenta" -> "VER HISTORIAL"
                        else -> "VER DETALLES"
                    }
                    Text(btnText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}