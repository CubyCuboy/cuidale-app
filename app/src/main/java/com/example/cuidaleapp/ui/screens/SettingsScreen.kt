package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.example.cuidaleapp.activities.PhoneVerificationActivity
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.AppCacheManager
import com.example.cuidaleapp.viewmodel.ChatViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onBack: () -> Unit,
    onLogoutClick: () -> Unit,
    onResetPass: () -> Unit,
    chatViewModel: ChatViewModel? = null
) {
    val context = LocalContext.current
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val userData by viewModel.userData.collectAsState()
    var showCacheMenu by remember { mutableStateOf(false) }


    val gradient = CuidaleThemeExtras.gradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(gradient.top, gradient.bottom)
                )
            )
    ) {
        if (showCacheMenu) {
            CacheSettingsScreen(
                userData = userData,
                chatViewModel = chatViewModel,
                onBack = { showCacheMenu = false }
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (userData.telefono.isNotEmpty() && !userData.telefonoVerificado) {
                HardShadowPanel(
                    backgroundColor = customColors.warningContainer,
                    shadowColor = customColors.warning.copy(alpha = 0.2f),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = customColors.warning,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Verifica tu número",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = customColors.warning
                            )
                            Text(
                                "Tu número ${userData.telefono} aún no está verificado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.warning.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(
                            onClick = { context.startActivity(Intent(context, PhoneVerificationActivity::class.java)) },
                            colors = ButtonDefaults.buttonColors(containerColor = customColors.warning),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("VERIFICAR AHORA", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            val emailToCheck = userData.correo
            if (emailToCheck.isNotEmpty() && !userData.verificado) {
                HardShadowPanel(
                    backgroundColor = customColors.warningContainer,
                    shadowColor = customColors.warning.copy(alpha = 0.2f),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = customColors.warning,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Verifica tu correo",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = customColors.warning
                            )
                            Text(
                                "Tu correo $emailToCheck aún no está verificado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.warning.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(context, com.example.cuidaleapp.activities.EmailVerificationActivity::class.java).apply {
                                    putExtra("email", emailToCheck)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = customColors.warning),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("VERIFICAR CORREO AHORA", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Cuenta y Seguridad",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val isPasswordLinked = (currentUser?.providerData ?: emptyList()).any { it.providerId == "password" }

                    if (isPasswordLinked) {
                        HardShadowButton(
                            text = stringResource(R.string.profile_reset_pass_button),
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White,
                            shadowColor = MaterialTheme.colorScheme.secondaryContainer,
                            icon = Icons.Default.LockReset,
                            isDark = isDark,
                            onClick = onResetPass
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    HardShadowButton(
                        text = "Gestión de Cuenta",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.primaryContainer,
                        icon = Icons.Default.AccountBox,
                        isDark = isDark,
                        onClick = {
                            context.startActivity(Intent(context, com.example.cuidaleapp.activities.AccountManagementActivity::class.java))
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sesión",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    HardShadowButton(
                        text = stringResource(R.string.profile_logout_button),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.errorContainer,
                        icon = Icons.AutoMirrored.Filled.Logout,
                        isDark = isDark,
                        onClick = {
                            viewModel.logout {
                                onLogoutClick()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "v${stringResource(R.string.app_version)} - Cuidale App",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Almacenamiento y Caché",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val currentUid = userData.uid.ifBlank {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Caché local",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${AppCacheManager.formatBytes(AppCacheManager.cacheSizeBytes(context))} multimedia/imágenes · ${AppCacheManager.formatBytes(com.example.cuidaleapp.utils.ChatCacheStore.sizeForUser(currentUid))} chats",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HardShadowButton(
                        text = "Administrar caché",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        icon = Icons.Default.Storage,
                        isDark = isDark,
                        onClick = { showCacheMenu = true }
                    )
                    Text(
                        text = "Abre un menú dedicado para borrar multimedia, imágenes, chats y configurar cuántos días se guardan para el modo offline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (chatViewModel != null) {
                val manualStatus by chatViewModel.myManualStatus.collectAsState()
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Estado de conexión",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = manualStatus == null,
                                onClick = { chatViewModel.setManualStatus(null) },
                                label = { Text("Conectado") }
                            )
                            FilterChip(
                                selected = manualStatus == "desconectado",
                                onClick = { chatViewModel.setManualStatus("desconectado") },
                                label = { Text("Desconectado") }
                            )
                            FilterChip(
                                selected = manualStatus == "no_molestar",
                                onClick = { chatViewModel.setManualStatus("no_molestar") },
                                label = { Text("No molestar") }
                            )
                        }

                        Text(
                            text = "Inactivo se activa automáticamente tras 5 minutos sin actividad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CacheSettingsScreen(
    userData: com.example.cuidaleapp.models.UserData,
    chatViewModel: ChatViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows

    // Asegura que el singleton de caché tenga el contexto de la app para
    // poder leer/guardar la retención de forma confiable.
    com.example.cuidaleapp.utils.ChatCacheStore.init(context)

    val currentUid = userData.uid.ifBlank {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }
    var mediaBytes by remember { mutableLongStateOf(AppCacheManager.mediaSizeBytes(context)) }
    var imagesBytes by remember { mutableLongStateOf(AppCacheManager.imagesSizeBytes(context)) }
    var chatBytes by remember { mutableLongStateOf(com.example.cuidaleapp.utils.ChatCacheStore.sizeForUser(currentUid)) }
    var busyAction by remember { mutableStateOf<String?>(null) }
    var retentionDays by remember { mutableIntStateOf(com.example.cuidaleapp.utils.ChatCacheStore.getRetentionDays()) }
    var chatsCached by remember { mutableStateOf(com.example.cuidaleapp.utils.ChatCacheStore.loadSummaries(currentUid)) }

    fun refreshSizes() {
        mediaBytes = AppCacheManager.mediaSizeBytes(context)
        imagesBytes = AppCacheManager.imagesSizeBytes(context)
        chatBytes = com.example.cuidaleapp.utils.ChatCacheStore.sizeForUser(currentUid)
        chatsCached = com.example.cuidaleapp.utils.ChatCacheStore.loadSummaries(currentUid)
    }

    // Refresca los tamaños al entrar a la pantalla (y al volver a ella).
    LaunchedEffect(Unit) {
        refreshSizes()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                }
                Text(
                    text = "Caché y almacenamiento",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Usado localmente",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Multimedia: ${AppCacheManager.formatBytes(mediaBytes)} · Imágenes: ${AppCacheManager.formatBytes(imagesBytes)} · Chats: ${AppCacheManager.formatBytes(chatBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    CacheActionRow(
                        icon = Icons.Default.VideoLibrary,
                        title = "Borrar multimedia (videos e imágenes de chats)",
                        subtitle = "Se vuelven a descargar al abrirlos.",
                        destructive = false,
                        buttonText = if (busyAction == "media") "Borrando..." else "Borrar",
                        enabled = busyAction == null,
                        onAction = {
                            busyAction = "media"
                            scope.launch {
                                AppCacheManager.clearMultimediaCaches(context)
                                refreshSizes()
                                busyAction = null
                            }
                        }
                    )

                    CacheActionRow(
                        icon = Icons.Default.Person,
                        title = "Borrar imágenes de perfil / descargadas",
                        subtitle = "Fotos de perfil y miniaturas.",
                        destructive = false,
                        buttonText = if (busyAction == "images") "Borrando..." else "Borrar",
                        enabled = busyAction == null,
                        onAction = {
                            busyAction = "images"
                            scope.launch {
                                AppCacheManager.clearImageCaches(context)
                                refreshSizes()
                                busyAction = null
                            }
                        }
                    )

                    CacheActionRow(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = "Borrar caché de TODOS los chats",
                        subtitle = "Mensajes guardados para modo offline.",
                        destructive = true,
                        buttonText = if (busyAction == "chats") "Borrando..." else "Borrar todo",
                        enabled = busyAction == null,
                        onAction = {
                            busyAction = "chats"
                            scope.launch {
                                if (currentUid.isNotBlank()) com.example.cuidaleapp.utils.ChatCacheStore.clearForUser(currentUid)
                                else com.example.cuidaleapp.utils.ChatCacheStore.clearAll()
                                chatViewModel?.clearAllCachedState()
                                refreshSizes()
                                busyAction = null
                            }
                        }
                    )
                }
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Caché por chat",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (chatsCached.isEmpty()) {
                        Text(
                            "No hay chats cacheados localmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        chatsCached.forEach { summary ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        summary.otherUser?.nombre?.ifBlank { "Chat" } ?: "Chat",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        summary.lastMessage?.text?.take(36) ?: "Sin mensajes",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            com.example.cuidaleapp.utils.ChatCacheStore.clearChat(currentUid, summary.chatId)
                                            refreshSizes()
                                        }
                                    },
                                    enabled = busyAction == null
                                ) {
                                    Text("Borrar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Modo offline — retención",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "Los mensajes se guardan localmente hasta este número de días. Pasado ese tiempo se borran solos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 7, 30).forEach { days ->
                            FilterChip(
                                selected = retentionDays == days,
                                onClick = {
                                    retentionDays = days
                                    com.example.cuidaleapp.utils.ChatCacheStore.setRetentionDays(days)
                                },
                                label = { Text(if (days == 1) "1 día" else "$days días", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean,
    buttonText: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    null,
                    tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onAction, enabled = enabled) {
            Text(
                buttonText,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
