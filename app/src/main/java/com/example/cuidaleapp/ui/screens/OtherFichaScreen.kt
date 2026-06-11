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
import androidx.compose.material.icons.filled.Assignment
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
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.models.SolicitudCuidado
import androidx.compose.material.icons.automirrored.filled.Chat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherFichaScreen(
    profileViewModel: ProfileViewModel,
    vinculosViewModel: VinculosViewModel,
    favoritosViewModel: FavoritosViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit,
    onChatNavigate: (String, String, UserData) -> Unit,
    onReviewRequest: (SolicitudCuidado) -> Unit
) {
    val otherUserData by profileViewModel.otherUserData.collectAsState()
    val otherUserStatus by profileViewModel.otherUserStatus.collectAsState()
    val otherFichaState by profileViewModel.otherFichaState.collectAsState()
    val otherProfileState by profileViewModel.otherProfileState.collectAsState()
    val vinculosUiState by vinculosViewModel.uiState.collectAsState()
    val favoritosUiState by favoritosViewModel.uiState.collectAsState()
    val favoritosList by favoritosViewModel.favoritos.collectAsState()

    val isFavorito = otherUserData?.let { favoritosList.contains(it.uid) } ?: false
    val noDeseadosList by favoritosViewModel.noDeseados.collectAsState()
    val isNoDeseado = otherUserData?.let { noDeseadosList.contains(it.uid) } ?: false
    var showUnwantedDialog by remember { mutableStateOf(false) }

    val misVinculos by vinculosViewModel.misVinculos.collectAsState()
    val vinculoExistente = remember(misVinculos, otherUserData) {
        otherUserData?.let { target ->
            misVinculos.find { it.uids.contains(target.uid) }
        }
    }
    var showCancelRequestDialog by remember { mutableStateOf(false) }

    var availableSolicitud by remember { mutableStateOf<SolicitudCuidado?>(null) }

    val scrollState = rememberScrollState()
    val customColors = CuidaleThemeExtras.customColors
    val snackbarHostState = remember { SnackbarHostState() }
    
    val displayRole = otherUserData?.rol ?: ""
    val accentColor = when (displayRole) {
        "cuidador" -> customColors.aqua
        "representante" -> customColors.mint
        else -> customColors.lime
    }

    LaunchedEffect(otherUserData?.uid) {
        val uid = otherUserData?.uid ?: return@LaunchedEffect
        if (displayRole == "representante") {
            try {
                val snapshot = FirebaseFirestore.getInstance().collection("solicitudes")
                    .whereEqualTo("representanteUid", uid)
                    .whereEqualTo("estado", "abierta")
                    .limit(1)
                    .get().await()
                if (!snapshot.isEmpty) {
                    availableSolicitud = snapshot.documents.first().toObject(SolicitudCuidado::class.java)
                }
            } catch (e: Exception) { }
        }
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (otherUserStatus == "en línea") Color(0xFF4CAF50) else Color.Gray)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = otherUserStatus.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
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

                        // Interaction Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HardShadowButton(
                                text = if (isFavorito) "GUARDADO" else "GUARDAR",
                                modifier = Modifier.weight(1f).height(56.dp),
                                containerColor = if (isFavorito) accentColor else Color.White,
                                contentColor = if (isFavorito) Color.White else MaterialTheme.colorScheme.onSurface,
                                shadowColor = accentColor.copy(alpha = 0.3f),
                                icon = if (isFavorito) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                enabled = favoritosUiState !is FavoritosUiState.Loading,
                                onClick = {
                                    otherUserData?.let { favoritosViewModel.toggleFavorito(it.uid) }
                                }
                            )

                            HardShadowButton(
                                text = if (isNoDeseado) "MOSTRAR" else "NO DESEADO",
                                modifier = Modifier.weight(1f).height(56.dp),
                                containerColor = if (isNoDeseado) customColors.warning else Color.White,
                                contentColor = if (isNoDeseado) Color.White else customColors.warning,
                                shadowColor = if (isNoDeseado) customColors.warning.copy(alpha = 0.3f) else customColors.warning.copy(alpha = 0.1f),
                                icon = if (isNoDeseado) Icons.Default.Visibility else Icons.Default.Block,
                                onClick = { showUnwantedDialog = true }
                            )
                        }

                        val scope = rememberCoroutineScope()
                        val buttonText = when {
                            vinculoExistente?.estado == "aceptado" -> "IR AL CHAT"
                            vinculoExistente?.solicitante == vinculosViewModel.myUid -> "CHAT YA SOLICITADO"
                            vinculoExistente?.solicitante != null -> "RESPONDER SOLICITUD"
                            else -> "SOLICITAR CHAT"
                        }

                        HardShadowButton(
                            text = if (vinculosUiState is VinculosUiState.Loading) "PROCESANDO..." else buttonText,
                            modifier = Modifier.fillMaxWidth().height(60.dp).padding(vertical = 8.dp),
                            containerColor = if (vinculoExistente?.solicitante == vinculosViewModel.myUid && vinculoExistente.estado == "pendiente") Color.LightGray else accentColor,
                            contentColor = if (vinculoExistente?.solicitante == vinculosViewModel.myUid && vinculoExistente.estado == "pendiente") Color.DarkGray else Color.White,
                            shadowColor = accentColor.copy(alpha = 0.4f),
                            icon = Icons.AutoMirrored.Filled.Chat,
                            enabled = vinculosUiState !is VinculosUiState.Loading,
                            onClick = {
                                otherUserData?.let { user ->
                                    scope.launch {
                                        when {
                                            vinculoExistente?.estado == "aceptado" -> {
                                                onChatNavigate(vinculoExistente.id, user.uid, user)
                                            }
                                            vinculoExistente?.solicitante == vinculosViewModel.myUid -> {
                                                showCancelRequestDialog = true
                                            }
                                            vinculoExistente?.solicitante != null -> {
                                                snackbarHostState.showSnackbar("Tienes una solicitud pendiente de este usuario.")
                                            }
                                            else -> {
                                                vinculosViewModel.enviarSolicitud(user.uid)
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        // Service Request availability (FOR CAREGIVERS)
                        if (availableSolicitud != null && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != otherUserData?.uid) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Solicitud de cuidado Disponible",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HardShadowButton(
                                    text = "REVISAR SOLICITUD",
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = Color.White,
                                    shadowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                    icon = Icons.Default.Assignment,
                                    onClick = { onReviewRequest(availableSolicitud!!) }
                                )
                            }
                        }

                        if (showCancelRequestDialog) {
                            AlertDialog(
                                onDismissRequest = { showCancelRequestDialog = false },
                                title = { Text("Cancelar solicitud") },
                                text = { Text("¿Deseas cancelar la solicitud de chat enviada? Podrás solicitarlo de nuevo más adelante si lo deseas.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showCancelRequestDialog = false
                                            otherUserData?.let { vinculosViewModel.cancelarSolicitud(it.uid) }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = customColors.warning)
                                    ) { Text("ELIMINAR SOLICITUD") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCancelRequestDialog = false }) { Text("MANTENER") }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

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
                                    isExternal = true, // RESTRICT MEDICATIONS AND DIET
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
