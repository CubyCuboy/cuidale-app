package com.example.cuidaleapp.ui.screens

import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.ui.components.fadingEdge
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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.viewmodel.VinculosViewModel
import com.example.cuidaleapp.viewmodel.VinculosUiState
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import com.example.cuidaleapp.viewmodel.FavoritosUiState
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.models.UserData
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.utils.DateUtils.formatDate
import androidx.compose.material.icons.automirrored.filled.Chat
import com.example.cuidaleapp.utils.ConnectivityObserver
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
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val otherUserData by profileViewModel.otherUserData.collectAsState()
    val otherUserStatus by profileViewModel.otherUserStatus.collectAsState()
    val otherFichaState by profileViewModel.otherFichaState.collectAsState()
    val otherTareasPlantilla by profileViewModel.otherTareasPlantilla.collectAsState()
    val otherProfileState by profileViewModel.otherProfileState.collectAsState()
    val vinculosUiState by vinculosViewModel.uiState.collectAsState()
    val favoritosUiState by favoritosViewModel.uiState.collectAsState()
    val favoritosList by favoritosViewModel.favoritos.collectAsState()

    val isFavorito = otherUserData?.let { favoritosList.contains(it.uid) } ?: false
    val noDeseadosList by favoritosViewModel.noDeseados.collectAsState()
    val isNoDeseado = otherUserData?.let { noDeseadosList.contains(it.uid) } ?: false
    val bloqueadosList by favoritosViewModel.bloqueados.collectAsState()
    val iHaveBlocked = otherUserData?.let { bloqueadosList.contains(it.uid) } ?: false
    var showUnwantedDialog by remember { mutableStateOf(false) }

    val misVinculos by vinculosViewModel.misVinculos.collectAsState()
    val vinculoExistente = remember(misVinculos, otherUserData) {
        otherUserData?.let { target ->
            misVinculos.find { it.uids.contains(target.uid) }
        }
    }
    var showCancelRequestDialog by remember { mutableStateOf(false) }

    var availableSolicitudes by remember { mutableStateOf<List<SolicitudCuidado>>(emptyList()) }
    var showRequestsListDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val customColors = CuidaleThemeExtras.customColors
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val displayRole = otherUserData?.rol ?: ""
    val accentColor = when (displayRole) {
        "cuidador" -> customColors.aqua
        "representante" -> customColors.mint
        else -> customColors.lime
    }

    LaunchedEffect(otherUserData?.uid, isOnline) {
        val uid = otherUserData?.uid ?: return@LaunchedEffect
        if (isOnline && otherProfileState is ProfileViewModel.RemoteState.Success && displayRole == "representante") {
            try {
                val snapshot = FirebaseFirestore.getInstance().collection("solicitudes")
                    .whereEqualTo("representanteUid", uid)
                    .whereEqualTo("estado", "abierta")
                    .get().await()
                availableSolicitudes = snapshot.toObjects(SolicitudCuidado::class.java)
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
                        if (isOnline) {
                            otherUserData?.let { 
                                favoritosViewModel.toggleNoDeseado(it.uid)
                                if (!isNoDeseado) onNavigateBack()
                            }
                        }
                    },
                    enabled = isOnline,
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
            } else if (otherProfileState is ProfileViewModel.RemoteState.Error || (!isOnline && otherUserData == null)) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (!isOnline) Icons.Default.WifiOff else Icons.Default.ErrorOutline, 
                            null, 
                            modifier = Modifier.size(48.dp), 
                            tint = if (!isOnline) Color.Gray else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (!isOnline) "No se puede cargar el perfil sin conexión." 
                            else (otherProfileState as ProfileViewModel.RemoteState.Error).message,
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
                    ConnectivityBanner()
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(if (isOnline) accentColor else Color.Gray)
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
                                            .background(if (otherUserStatus == "en línea" && isOnline) Color(0xFF4CAF50) else Color.Gray)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = (if (isOnline) otherUserStatus else "OFFLINE").uppercase(),
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
                                        model = ImageRequest.Builder(context)
                                            .data(otherUserData?.fotoUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(customColors.avatarBackground)
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

                        otherUserData?.let { user ->
                            HardShadowPanel(
                                backgroundColor = customColors.panelBackground,
                                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.1f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "DATOS DEL PERFIL",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isOnline) accentColor else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val fullName = "${user.nombre} ${user.apellido_pa}".trim()
                                    Text(
                                        text = if (fullName.isBlank() || fullName == "n/a") "Usuario sin nombre" else fullName,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 28.sp
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
                                        accentColor = if (isOnline) accentColor else Color.Gray
                                    )
                                    ContactItem(
                                        icon = Icons.Default.Phone,
                                        label = "Teléfono de contacto",
                                        value = censorPhone(user.telefono),
                                        accentColor = if (isOnline) accentColor else Color.Gray
                                    )

                                    if (user.comuna.isNotEmpty() || user.region.isNotEmpty()) {
                                        ContactItem(
                                            icon = Icons.Default.Place,
                                            label = "Ubicación de residencia",
                                            value = "${user.comuna}${if(user.comuna.isNotEmpty() && user.region.isNotEmpty()) ", " else ""}${user.region}",
                                            accentColor = if (isOnline) accentColor else Color.Gray
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Surface(
                                        color = (if (isOnline) accentColor else Color.Gray).copy(alpha = 0.05f),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (isOnline) accentColor else Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = if (isOnline) "Por seguridad, algunos datos están protegidos. Contacta al usuario para coordinar y conocer más detalles."
                                                       else "Estás en modo offline. Algunas funciones de interacción están desactivadas.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HardShadowButton(
                                text = if (isFavorito) "GUARDADO" else "GUARDAR",
                                modifier = Modifier.weight(1f).height(56.dp),
                                containerColor = if (!isOnline) Color.Gray else if (isFavorito) accentColor else Color.White,
                                contentColor = if (isFavorito) Color.White else MaterialTheme.colorScheme.onSurface,
                                shadowColor = (if (isOnline) accentColor else Color.Gray).copy(alpha = 0.3f),
                                icon = if (isFavorito) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                enabled = favoritosUiState !is FavoritosUiState.Loading && isOnline,
                                onClick = {
                                    otherUserData?.let { favoritosViewModel.toggleFavorito(it.uid) }
                                }
                            )

                            HardShadowButton(
                                text = if (isNoDeseado) "MOSTRAR" else "NO DESEADO",
                                modifier = Modifier.weight(1f).height(56.dp),
                                containerColor = if (!isOnline) Color.Gray else if (isNoDeseado) customColors.warning else Color.White,
                                contentColor = if (isNoDeseado) Color.White else customColors.warning,
                                shadowColor = (if (isNoDeseado) customColors.warning else Color.Gray).copy(alpha = 0.3f),
                                icon = if (isNoDeseado) Icons.Default.Visibility else Icons.Default.Block,
                                enabled = isOnline,
                                onClick = { showUnwantedDialog = true }
                            )
                        }

                        val scope = rememberCoroutineScope()
                        val isSolicitante = vinculoExistente?.solicitante == vinculosViewModel.myUid
                        val isPendiente = vinculoExistente?.estado == "pendiente"
                        
                        val buttonText = when {
                            iHaveBlocked -> "DESBLOQUEAR USUARIO"
                            vinculoExistente?.estado == "aceptado" -> "IR AL CHAT"
                            isSolicitante && isPendiente -> "CHAT YA SOLICITADO"
                            !isSolicitante && isPendiente -> null 
                            else -> "SOLICITAR CHAT"
                        }

                        if (buttonText != null) {
                            HardShadowButton(
                                text = if (vinculosUiState is VinculosUiState.Loading) "PROCESANDO..." else buttonText,
                                modifier = Modifier.fillMaxWidth().height(60.dp).padding(vertical = 8.dp),
                                containerColor = when {
                                    !isOnline && vinculoExistente?.estado != "aceptado" -> Color.Gray
                                    iHaveBlocked -> Color.Red
                                    isSolicitante && isPendiente -> Color.LightGray
                                    else -> accentColor
                                },
                                contentColor = if (isSolicitante && isPendiente) Color.DarkGray else Color.White,
                                shadowColor = (if (iHaveBlocked) Color.Red else (if (isOnline) accentColor else Color.Gray)).copy(alpha = 0.4f),
                                icon = if (iHaveBlocked) Icons.Default.LockOpen else Icons.AutoMirrored.Filled.Chat,
                                enabled = vinculosUiState !is VinculosUiState.Loading && (isOnline || vinculoExistente?.estado == "aceptado"),
                                onClick = {
                                    otherUserData?.let { user ->
                                        scope.launch {
                                            when {
                                                iHaveBlocked -> {
                                                    favoritosViewModel.toggleBloqueo(user.uid)
                                                }
                                                vinculoExistente?.estado == "aceptado" -> {
                                                    onChatNavigate(vinculoExistente.id, user.uid, user)
                                                }
                                                isSolicitante && isPendiente -> {
                                                    showCancelRequestDialog = true
                                                }
                                                else -> {
                                                    vinculosViewModel.enviarSolicitud(user.uid)
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        if (!isSolicitante && isPendiente) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HardShadowButton(
                                    text = "RECHAZAR",
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    containerColor = Color.White,
                                    contentColor = Color.Red,
                                    shadowColor = Color.Red.copy(alpha = 0.1f),
                                    enabled = isOnline,
                                    onClick = { vinculosViewModel.rechazarSolicitud(vinculoExistente!!.id) }
                                )
                                HardShadowButton(
                                    text = "ACEPTAR SOLICITUD",
                                    modifier = Modifier.weight(1.5f).height(50.dp),
                                    containerColor = if (isOnline) customColors.successStrong else Color.Gray,
                                    contentColor = Color.White,
                                    shadowColor = (if (isOnline) customColors.successStrong else Color.Gray).copy(alpha = 0.3f),
                                    enabled = isOnline,
                                    onClick = { vinculosViewModel.aceptarSolicitud(vinculoExistente!!.id) }
                                )
                            }
                        }

                        if (availableSolicitudes.isNotEmpty() && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != otherUserData?.uid) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (availableSolicitudes.size > 1) "Solicitudes de cuidado Disponibles" else "Solicitud de cuidado Disponible",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isOnline) accentColor else Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HardShadowButton(
                                    text = if (availableSolicitudes.size > 1) "REVISAR SOLICITUDES" else "REVISAR SOLICITUD",
                                    containerColor = if (isOnline) MaterialTheme.colorScheme.secondary else Color.Gray,
                                    contentColor = Color.White,
                                    shadowColor = (if (isOnline) MaterialTheme.colorScheme.secondary else Color.Gray).copy(alpha = 0.3f),
                                    icon = Icons.Default.Assignment,
                                    enabled = isOnline,
                                    onClick = { 
                                        if (availableSolicitudes.size > 1) {
                                            showRequestsListDialog = true
                                        } else {
                                            onReviewRequest(availableSolicitudes.first())
                                        }
                                    }
                                )
                            }
                        }

                        if (showRequestsListDialog) {
                            AlertDialog(
                                onDismissRequest = { showRequestsListDialog = false },
                                title = { Text("Solicitudes Disponibles", fontWeight = FontWeight.Bold) },
                                text = {
                                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                                        items(availableSolicitudes) { sol: com.example.cuidaleapp.models.SolicitudCuidado ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(sol.horarioStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                                    val fechaStr = if (sol.esIndefinido) "Indefinido" else "${formatDate(sol.fechaInicio?.toDate()?.time)} - ${formatDate(sol.fechaFin?.toDate()?.time)}"
                                                    Text("Duración: $fechaStr", style = MaterialTheme.typography.bodySmall)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(
                                                        onClick = {
                                                            showRequestsListDialog = false
                                                            onReviewRequest(sol)
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("Ver solicitud", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showRequestsListDialog = false }) { Text("CERRAR") }
                                }
                            )
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
                                            if (isOnline) {
                                                otherUserData?.let { vinculosViewModel.cancelarSolicitud(it.uid) }
                                            }
                                        },
                                        enabled = isOnline,
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
                                    isExternal = true,
                                    tareasPlantilla = otherTareasPlantilla,
                                    onEdit = { },
                                    isBlurry = vinculoExistente?.estado != "aceptado"
                                )
                            }
                            is FichaState.NotFound -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CuidalePlaceholder(
                                        title = "Ficha no disponible",
                                        subtitle = "Este usuario aún no ha completado su ficha técnica. Contacta con él para conocer más detalles.",
                                        icon = Icons.Default.Info,
                                        iconColor = Color.Gray.copy(alpha = 0.5f)
                                    )
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
