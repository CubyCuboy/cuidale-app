package com.example.cuidaleapp.ui.screens

import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.ui.draw.scale
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.viewmodel.VinculosViewModel
import com.example.cuidaleapp.viewmodel.VinculosUiState
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import com.example.cuidaleapp.viewmodel.FavoritosUiState
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.ProfileDetailView
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import androidx.compose.material.icons.automirrored.filled.Chat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherFichaScreen(
    profileViewModel: ProfileViewModel,
    vinculosViewModel: VinculosViewModel,
    favoritosViewModel: FavoritosViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit
) {
    val otherUserData by profileViewModel.otherUserData.collectAsState()
    val otherFichaState by profileViewModel.otherFichaState.collectAsState()
    val otherProfileState by profileViewModel.otherProfileState.collectAsState()
    val vinculosUiState by vinculosViewModel.uiState.collectAsState()
    val favoritosUiState by favoritosViewModel.uiState.collectAsState()
    val favoritosList by favoritosViewModel.favoritos.collectAsState()

    val isFavorito = otherUserData?.let { favoritosList.contains(it.uid) } ?: false
    val isNoDeseado = otherUserData?.let { favoritosViewModel.noDeseados.value.contains(it.uid) } ?: false
    var showUnwantedDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val customColors = CuidaleThemeExtras.customColors
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Use the role from the loaded user data
    val displayRole = otherUserData?.rol ?: ""
    val accentColor = when (displayRole) {
        "cuidador" -> MaterialTheme.colorScheme.primary
        "representante" -> customColors.successStrong
        else -> MaterialTheme.colorScheme.secondary
    }

    LaunchedEffect(vinculosUiState) {
        if (vinculosUiState is VinculosUiState.Success) {
            snackbarHostState.showSnackbar((vinculosUiState as VinculosUiState.Success).message)
            vinculosViewModel.clearState()
        } else if (vinculosUiState is VinculosUiState.Error) {
            snackbarHostState.showSnackbar((vinculosUiState as VinculosUiState.Error).message)
            vinculosViewModel.clearState()
        }
    }

    LaunchedEffect(favoritosUiState) {
        if (favoritosUiState is FavoritosUiState.Success) {
            snackbarHostState.showSnackbar((favoritosUiState as FavoritosUiState.Success).message)
            favoritosViewModel.clearState()
        } else if (favoritosUiState is FavoritosUiState.Error) {
            snackbarHostState.showSnackbar((favoritosUiState as FavoritosUiState.Error).message)
            favoritosViewModel.clearState()
        }
    }

    if (showUnwantedDialog) {
        AlertDialog(
            onDismissRequest = { showUnwantedDialog = false },
            title = { Text(if (isNoDeseado) "¿Volver a mostrar?" else "¿Marcar como no deseado?") },
            text = { 
                Text(
                    if (isNoDeseado) 
                        "Este usuario volverá a aparecer en tus sugerencias y búsquedas." 
                    else 
                        "Este usuario dejará de aparecer en tus resultados de búsqueda. Puedes revertir esto en la sección de Gestión de Usuarios en tu perfil."
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnwantedDialog = false
                        otherUserData?.let { 
                            favoritosViewModel.toggleNoDeseado(it.uid)
                            if (!isNoDeseado) onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = if (isNoDeseado) accentColor else customColors.warning)
                ) {
                    Text(if (isNoDeseado) "MOSTRAR DE NUEVO" else "MARCAR COMO NO DESEADO")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnwantedDialog = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (otherProfileState is ProfileViewModel.RemoteState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else if (otherProfileState is ProfileViewModel.RemoteState.Error) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            (otherProfileState as ProfileViewModel.RemoteState.Error).message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack) { Text("VOLVER") }
                    }
                }
            } else if (otherUserData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando perfil...")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Header / Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(accentColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (displayRole == "cuidador") "FICHA DE CUIDADOR" else "FICHA DEL PACIENTE",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "${otherUserData?.nombre} ${otherUserData?.apellido_pa}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .offset(y = (-40).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Standard Avatar Header
                        Surface(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                                .padding(4.dp),
                            shape = CircleShape,
                            color = Color.White,
                            tonalElevation = 8.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(customColors.avatarBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                if (otherUserData?.fotoUrl?.isNotEmpty() == true && !otherUserData?.fotoUrl?.startsWith("res:")!!) {
                                    AsyncImage(
                                        model = otherUserData?.fotoUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Perfil Data Section (Outside Ficha)
                        otherUserData?.let { user ->
                            HardShadowPanel(
                                backgroundColor = customColors.panelBackground,
                                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.1f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "DATOS DEL PERFIL",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = accentColor
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "${user.nombre} ${user.apellido_pa}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (user.descripcion.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = user.descripcion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    ContactItem(
                                        icon = Icons.Default.Email,
                                        label = "Correo electrónico",
                                        value = censorEmail(user.correo),
                                        accentColor = accentColor
                                    )
                                    ContactItem(
                                        icon = Icons.Default.Phone,
                                        label = "Teléfono de contacto",
                                        value = censorPhone(user.telefono),
                                        accentColor = accentColor
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Surface(
                                        color = accentColor.copy(alpha = 0.05f),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Por seguridad, algunos datos están protegidos. Contacta al usuario para coordinar y conocer más detalles.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Interaction Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val favInteractionSource = remember { MutableInteractionSource() }
                            val isFavPressed by favInteractionSource.collectIsPressedAsState()
                            val favScale by animateFloatAsState(
                                targetValue = if (isFavPressed) 0.92f else 1f,
                                animationSpec = tween(durationMillis = 100),
                                label = "favScale"
                            )

                            HardShadowButton(
                                text = if (isFavorito) "GUARDADO" else "GUARDAR",
                                modifier = Modifier.weight(1f).scale(favScale),
                                interactionSource = favInteractionSource,
                                containerColor = if (isFavorito) accentColor.copy(alpha = 0.1f) else Color.White,
                                contentColor = if (isFavorito) accentColor else MaterialTheme.colorScheme.onSurface,
                                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.1f),
                                icon = if (isFavorito) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                enabled = favoritosUiState !is FavoritosUiState.Loading,
                                onClick = {
                                    otherUserData?.let { favoritosViewModel.toggleFavorito(it.uid) }
                                }
                            )

                            val unwantedInteractionSource = remember { MutableInteractionSource() }
                            val isUnwantedPressed by unwantedInteractionSource.collectIsPressedAsState()
                            val unwantedScale by animateFloatAsState(
                                targetValue = if (isUnwantedPressed) 0.92f else 1f,
                                animationSpec = tween(durationMillis = 100),
                                label = "unwantedScale"
                            )

                            HardShadowButton(
                                text = if (isNoDeseado) "VOLVER A VER EN SUGERENCIAS" else "NO DESEADO",
                                modifier = Modifier.weight(1f).scale(unwantedScale),
                                interactionSource = unwantedInteractionSource,
                                containerColor = if (isNoDeseado) accentColor.copy(alpha = 0.1f) else customColors.warning.copy(alpha = 0.1f),
                                contentColor = if (isNoDeseado) accentColor else customColors.warning,
                                shadowColor = if (isNoDeseado) accentColor.copy(alpha = 0.2f) else customColors.warning.copy(alpha = 0.2f),
                                icon = if (isNoDeseado) Icons.Default.Visibility else Icons.Default.Block,
                                onClick = { showUnwantedDialog = true }
                            )
                        }

                        val chatInteractionSource = remember { MutableInteractionSource() }
                        val isChatPressed by chatInteractionSource.collectIsPressedAsState()
                        val chatScale by animateFloatAsState(
                            targetValue = if (isChatPressed) 0.96f else 1f,
                            animationSpec = tween(durationMillis = 100),
                            label = "chatScale"
                        )

                        HardShadowButton(
                            text = if (vinculosUiState is VinculosUiState.Loading) "ENVIANDO..." else "SOLICITAR CHAT",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).scale(chatScale),
                            interactionSource = chatInteractionSource,
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = accentColor.copy(alpha = 0.3f),
                            icon = Icons.AutoMirrored.Filled.Chat,
                            enabled = vinculosUiState !is VinculosUiState.Loading,
                            onClick = {
                                otherUserData?.let { vinculosViewModel.enviarSolicitud(it.uid) }
                            }
                        )

                        when (val targetState = otherFichaState) {
                            is FichaState.Loading -> {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accentColor)
                                }
                            }
                            is FichaState.View -> {
                                ProfileDetailView(
                                    role = displayRole,
                                    data = targetState.data,
                                    showEditButton = false,
                                    isExternal = true,
                                    onEdit = { }
                                )
                            }
                            is FichaState.NotFound -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    HardShadowPanel(
                                        backgroundColor = customColors.panelBackground,
                                        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.Info, null, modifier = Modifier.size(48.dp), tint = accentColor.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "Este usuario aún no ha completado su ficha técnica.",
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                            is FichaState.Error -> {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(targetState.message, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            else -> {}
                        }
                    }
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }
}

@Composable
fun ContactItem(icon: ImageVector, label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = accentColor)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun censorEmail(email: String): String {
    if (email.isBlank()) return "No disponible"
    val parts = email.split("@")
    if (parts.size != 2) return "****"
    val name = parts[0]
    val domain = parts[1]
    return if (name.length > 2) {
        name.take(1) + "***" + name.takeLast(1) + "@" + domain
    } else {
        "*@$domain"
    }
}

private fun censorPhone(phone: String): String {
    if (phone.isBlank()) return "No disponible"
    return if (phone.length > 4) {
        "*******" + phone.takeLast(4)
    } else {
        "********"
    }
}
