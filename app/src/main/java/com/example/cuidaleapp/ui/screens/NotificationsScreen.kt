package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.Notification
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.CuidaleBottomBar
import com.example.cuidaleapp.ui.components.CuidaleCircularMenuButton
import com.example.cuidaleapp.ui.components.CuidaleDrawerContent
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.NotificationsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    userData: UserData,
    isDark: Boolean,
    unreadCount: Int = 0,
    pendingNotifs: Int = 0,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onViewProfile: () -> Unit,
    onViewFicha: () -> Unit,
    onViewSettings: () -> Unit,
    onTabSelected: (String) -> Unit,
    onNotificationClick: (Notification) -> Unit,
    notificationsViewModel: NotificationsViewModel = viewModel()
) {
    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val notifications by notificationsViewModel.notifications.collectAsState()
    val users by notificationsViewModel.users.collectAsState()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxHeight().width(320.dp),
                        drawerContainerColor = Color.Transparent,
                        drawerTonalElevation = 0.dp
                    ) {
                        CuidaleDrawerContent(
                            nombre = userData.nombre,
                            apellido = userData.apellido_pa,
                            fotoUrl = userData.fotoUrl,
                            rol = userData.rol,
                            isDark = isDark,
                            onToggleTheme = onToggleTheme,
                            onProfileClick = {
                                scope.launch { drawerState.close() }
                                onViewProfile()
                            },
                            onFichaClick = {
                                scope.launch { drawerState.close() }
                                onViewFicha()
                            },
                            onSettingsClick = {
                                scope.launch { drawerState.close() }
                                onViewSettings()
                            },
                            onLogoutClick = {
                                scope.launch { drawerState.close() }
                                onLogout()
                            },
                            onCloseClick = {
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            CuidaleCircularMenuButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    },
                    bottomBar = {
                        CuidaleBottomBar(
                            selectedTab = "notifications", 
                            unreadCount = unreadCount, 
                            pendingNotifs = pendingNotifs,
                            onTabSelected = onTabSelected
                        )
                    },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(
                                if (isDark) listOf(customColors.panelBackground, Color(0xFF0D1B2A))
                                else listOf(customColors.aqua.copy(alpha = 0.8f), customColors.mint.copy(alpha = 0.6f))
                            ))
                            .padding(innerPadding)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Notificaciones",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = if (isDark) Color.White else customColors.successStrong,
                                modifier = Modifier.padding(24.dp)
                            )

                            if (notifications.isEmpty()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.5f))
                                        Spacer(Modifier.height(16.dp))
                                        Text("No tienes notificaciones", color = Color.Gray)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(notifications) { notif ->
                                        val sender = users[notif.emisor]
                                        NotificationItem(
                                            notification = notif,
                                            sender = sender,
                                            isDark = isDark,
                                            onClick = { 
                                                notificationsViewModel.markAsRead(notif.id)
                                                onNotificationClick(notif)
                                            },
                                            onDelete = { notificationsViewModel.deleteNotification(notif.id) }
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

@Composable
fun NotificationItem(
    notification: Notification,
    sender: UserData?,
    isDark: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val itemAccent = when (notification.tipo) {
        "solicitud_vinculo" -> customColors.aqua
        "nuevo_mensaje" -> customColors.lime
        else -> customColors.successStrong
    }

    HardShadowPanel(
        backgroundColor = if (notification.leida) (if (isDark) Color(0xFF1B263B) else Color.White.copy(alpha = 0.9f)) else (if (isDark) Color(0xFF2B3A55) else Color.White),
        shadowColor = if (notification.leida) Color.Transparent else itemAccent.copy(alpha = 0.2f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(itemAccent.copy(alpha = 0.1f))) {
                if (sender?.fotoUrl?.isNotEmpty() == true) {
                    AsyncImage(model = sender.fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(
                        imageVector = when(notification.tipo) {
                            "solicitud_vinculo" -> Icons.Default.PersonAdd
                            "nuevo_mensaje" -> Icons.AutoMirrored.Filled.Chat
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
                        "nuevo_mensaje" -> "Nuevo mensaje de ${sender?.nombre ?: "Usuario"}"
                        else -> "Notificación"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = when (notification.tipo) {
                        "solicitud_vinculo" -> "${sender?.nombre ?: "Alguien"} quiere conectar contigo."
                        "vinculo_aceptado" -> "${sender?.nombre ?: "Un usuario"} aceptó tu solicitud. ¡Ya pueden hablar!"
                        "nuevo_mensaje" -> notification.data["text"] ?: "Toca para ver el mensaje."
                        else -> "Tienes un nuevo aviso."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.LightGray else Color.DarkGray
                )
                Text(
                    text = formatTime(notification.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
