package com.example.cuidaleapp.ui.screens

import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
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
import com.example.cuidaleapp.models.FormacionAcademica
import com.example.cuidaleapp.models.ExperienciaLaboral
import com.example.cuidaleapp.models.ReferenciaLaboral
import com.example.cuidaleapp.models.Certificado
import com.example.cuidaleapp.models.Tarea
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.R
import com.example.cuidaleapp.utils.DateUtils.formatDate
import android.content.Context
import androidx.compose.ui.res.stringResource
import com.example.cuidaleapp.utils.ErrorCodes
import com.example.cuidaleapp.utils.ErrorMessages
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
            snackbarHostState.showSnackbar(ErrorMessages.get(context, (vinculosUiState as VinculosUiState.Error).message))
            vinculosViewModel.clearState()
        }
    }

    LaunchedEffect(favoritosUiState) {
        if (favoritosUiState is FavoritosUiState.Success) {
            snackbarHostState.showSnackbar((favoritosUiState as FavoritosUiState.Success).message)
            favoritosViewModel.clearState()
        } else if (favoritosUiState is FavoritosUiState.Error) {
            snackbarHostState.showSnackbar(ErrorMessages.get(context, (favoritosUiState as FavoritosUiState.Error).message))
            favoritosViewModel.clearState()
        }
    }

    if (showUnwantedDialog) {
        AlertDialog(
            onDismissRequest = { showUnwantedDialog = false },
            title = { Text(if (isNoDeseado) stringResource(R.string.caregiver_profile_volver_mostrar) else stringResource(R.string.caregiver_profile_marcar_no_deseado)) },
            text = {
                Text(
                    if (isNoDeseado)
                        stringResource(R.string.caregiver_profile_no_deseado_restaurar_msg)
                    else
                        stringResource(R.string.caregiver_profile_no_deseado_confirmar_msg)
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
                    Text(if (isNoDeseado) stringResource(R.string.caregiver_profile_mostrar_de_nuevo) else stringResource(R.string.caregiver_profile_marcar_como_no_deseado))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnwantedDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
                            if (!isOnline) stringResource(R.string.caregiver_profile_error_offline)
                            else ErrorMessages.get(context, (otherProfileState as ProfileViewModel.RemoteState.Error).message),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack) { Text(stringResource(R.string.action_back)) }
                    }
                }
            } else if (otherUserData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.caregiver_profile_cargando))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isOnline) accentColor else Color.Gray)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (displayRole == "cuidador") stringResource(R.string.ficha_title_caregiver) else stringResource(R.string.ficha_title_patient),
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
                                        text = (if (isOnline) otherUserStatus else stringResource(R.string.caregiver_profile_offline)).uppercase(),
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
                            .padding(top = 16.dp),
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
                                    coil3.compose.SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(otherUserData?.fotoUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = stringResource(R.string.cd_profile),
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(Modifier.fillMaxSize().background(shimmerBrush()), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = accentColor)
                                            }
                                        }
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

                        if (displayRole == "cuidador") {
                            otherUserData?.let { user ->
                                CaregiverPublicProfile(
                                    user = user,
                                    ficha = (otherFichaState as? FichaState.View)?.data,
                                    accentColor = if (isOnline) accentColor else Color.Gray,
                                    tareasPlantilla = otherTareasPlantilla
                                )
                            }
                        } else {
                            otherUserData?.let { user ->
                                HardShadowPanel(
                                    backgroundColor = customColors.panelBackground,
                                    shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.1f)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = stringResource(R.string.caregiver_profile_datos_perfil),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isOnline) accentColor else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val fullName = "${user.nombre} ${user.apellido_pa}".trim()
                                        Text(
                                            text = if (fullName.isBlank() || fullName == "n/a") stringResource(R.string.caregiver_profile_sin_nombre) else fullName,
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
                                        ContactItem(icon = Icons.Default.Email, label = stringResource(R.string.caregiver_profile_correo), value = censorEmail(context, user.correo), accentColor = if (isOnline) accentColor else Color.Gray)
                                        ContactItem(icon = Icons.Default.Phone, label = stringResource(R.string.caregiver_profile_telefono), value = censorPhone(context, user.telefono), accentColor = if (isOnline) accentColor else Color.Gray)
                                        if (user.comuna.isNotEmpty() || user.region.isNotEmpty()) {
                                            ContactItem(icon = Icons.Default.Place, label = stringResource(R.string.caregiver_profile_ubicacion_residencia), value = "${user.comuna}${if(user.comuna.isNotEmpty() && user.region.isNotEmpty()) ", " else ""}${user.region}", accentColor = if (isOnline) accentColor else Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Surface(color = (if (isOnline) accentColor else Color.Gray).copy(alpha = 0.05f), shape = MaterialTheme.shapes.medium) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = if (isOnline) accentColor else Color.Gray, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(text = if (isOnline) stringResource(R.string.caregiver_profile_info_protegida) else stringResource(R.string.caregiver_profile_info_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
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
                                text = if (isFavorito) stringResource(R.string.caregiver_profile_guardado) else stringResource(R.string.caregiver_profile_guardar),
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
                                text = if (isNoDeseado) stringResource(R.string.caregiver_profile_mostrar) else stringResource(R.string.caregiver_profile_no_deseado),
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
                            iHaveBlocked -> stringResource(R.string.caregiver_profile_desbloquear)
                            vinculoExistente?.estado == "aceptado" -> stringResource(R.string.caregiver_profile_ir_al_chat)
                            isSolicitante && isPendiente -> stringResource(R.string.caregiver_profile_chat_ya_solicitado)
                            !isSolicitante && isPendiente -> null 
                            else -> stringResource(R.string.caregiver_profile_solicitar_chat)
                        }

                        if (buttonText != null) {
                            HardShadowButton(
                                text = if (vinculosUiState is VinculosUiState.Loading) stringResource(R.string.caregiver_profile_procesando) else buttonText,
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
                                    text = stringResource(R.string.caregiver_profile_rechazar),
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    containerColor = Color.White,
                                    contentColor = Color.Red,
                                    shadowColor = Color.Red.copy(alpha = 0.1f),
                                    enabled = isOnline,
                                    onClick = { vinculosViewModel.rechazarSolicitud(vinculoExistente!!.id) }
                                )
                                HardShadowButton(
                                    text = stringResource(R.string.caregiver_profile_aceptar_solicitud),
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
                                    if (availableSolicitudes.size > 1) stringResource(R.string.caregiver_profile_solicitudes_disponibles_plural) else stringResource(R.string.caregiver_profile_solicitudes_disponibles),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isOnline) accentColor else Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HardShadowButton(
                                    text = if (availableSolicitudes.size > 1) stringResource(R.string.caregiver_profile_revisar_solicitudes) else stringResource(R.string.caregiver_profile_revisar_solicitud),
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
                                title = { Text(stringResource(R.string.caregiver_profile_solicitudes_disponibles_titulo), fontWeight = FontWeight.Bold) },
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
                                                    val fechaStr = if (sol.esIndefinido) stringResource(R.string.ficha_solicitud_indefinido) else "${formatDate(sol.fechaInicio?.toDate()?.time)} - ${formatDate(sol.fechaFin?.toDate()?.time)}"
                                                    Text(stringResource(R.string.ficha_solicitud_duracion, fechaStr), style = MaterialTheme.typography.bodySmall)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(
                                                        onClick = {
                                                            showRequestsListDialog = false
                                                            onReviewRequest(sol)
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(stringResource(R.string.notif_btn_request), fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showRequestsListDialog = false }) { Text(stringResource(R.string.caregiver_profile_cerrar)) }
                                }
                            )
                        }

                        if (showCancelRequestDialog) {
                            AlertDialog(
                                onDismissRequest = { showCancelRequestDialog = false },
                                title = { Text(stringResource(R.string.caregiver_profile_cancelar_solicitud_titulo)) },
                                text = { Text(stringResource(R.string.caregiver_profile_cancelar_solicitud_msg)) },
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
                                    ) { Text(stringResource(R.string.caregiver_profile_eliminar_solicitud)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCancelRequestDialog = false }) { Text(stringResource(R.string.caregiver_profile_mantener)) }
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
                                if (displayRole != "cuidador") {
                                    ProfileDetailView(
                                        role = displayRole,
                                        data = targetState.data,
                                        isExternal = true,
                                        tareasPlantilla = otherTareasPlantilla,
                                        onEdit = { },
                                        isBlurry = vinculoExistente?.estado != "aceptado"
                                    )
                                }
                            }
                            is FichaState.NotFound -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CuidalePlaceholder(
                                        title = stringResource(R.string.ficha_no_disponible),
                                        subtitle = stringResource(R.string.ficha_no_disponible_desc),
                                        icon = Icons.Default.Info,
                                        iconColor = Color.Gray.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            is FichaState.Error -> {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(ErrorMessages.get(context, targetState.message), color = MaterialTheme.colorScheme.error)
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

private fun censorEmail(context: Context, email: String): String {
    if (email.isBlank()) return context.getString(R.string.caregiver_profile_no_disponible)
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

private fun censorPhone(context: Context, phone: String): String {
    if (phone.isBlank()) return context.getString(R.string.caregiver_profile_no_disponible)
    return if (phone.length > 4) {
        "*******" + phone.takeLast(4)
    } else {
        "********"
    }
}

@Composable
private fun CaregiverPublicProfile(
    user: UserData,
    ficha: Map<String, Any>?,
    accentColor: Color,
    tareasPlantilla: List<Tarea> = emptyList()
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current

    val fechaNacimiento = (ficha?.get("fechaNacimiento") as? String)?.takeIf { it.isNotBlank() } ?: user.fechaNacimiento
    val valorHora = (ficha?.get("valorHoraReferencial") as? Number)?.toInt() ?: user.valorHoraReferencial
    val discapBool = (ficha?.get("discapacidad") as? Boolean) ?: user.discapacidad
    val tipoDiscap = (ficha?.get("tipoDiscapacidad") as? String)?.takeIf { it.isNotBlank() } ?: user.tipoDiscapacidad
    val tiempoDiscap = (ficha?.get("tiempoDiscapacidad") as? String)?.takeIf { it.isNotBlank() } ?: user.tiempoDiscapacidad
    val especialidad = (ficha?.get("especializacion") as? String)?.takeIf { it.isNotBlank() } ?: ""
    val experienciaResumen = (ficha?.get("experiencia") as? String)?.takeIf { it.isNotBlank() } ?: ""
    val comunas = (ficha?.get("comunas") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val horarios = (ficha?.get("horarios") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val descripcion = (ficha?.get("descripcion") as? String)?.takeIf { it.isNotBlank() } ?: user.descripcion
    val verificacion = (ficha?.get("verificacionOficial") as? String)?.takeIf { it.isNotBlank() } ?: user.verificacionOficial

    val formaciones = fichaFormaciones(ficha).ifEmpty { user.formacionAcademica }
    val experiencias = fichaExperiencias(ficha).ifEmpty { user.experienciasLaborales }
    val referencias = fichaReferencias(ficha).ifEmpty { user.referenciasLaborales }
    val certificados = fichaCertificados(ficha).ifEmpty { user.certificados }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.12f),
            cornerRadius = 24.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = customColors.avatarBackground
                    ) {
                        if (user.fotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.fotoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp), tint = secondaryTextColor.copy(alpha = 0.6f))
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${user.nombre} ${user.apellido_pa}".trim().ifBlank { stringResource(R.string.caregiver_profile_sin_nombre) },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = textColor
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                                Text(
                                    stringResource(R.string.caregiver_profile_cuidador),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            com.example.cuidaleapp.ui.components.VerificationBadge(verificacion)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.06f))
                Spacer(Modifier.height(14.dp))

                val quickStats = listOf(
                    Triple(Icons.Default.Cake, stringResource(R.string.caregiver_profile_edad), com.example.cuidaleapp.ui.components.calcularEdad(fechaNacimiento)),
                    Triple(Icons.Default.Place, stringResource(R.string.caregiver_profile_ubicacion), listOf(user.comuna, user.region).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { stringResource(R.string.caregiver_profile_no_especificada) }),
                    Triple(Icons.Default.AttachMoney, stringResource(R.string.caregiver_profile_valor_hora), if (valorHora > 0) stringResource(R.string.caregiver_view_valor_hora_formato, formatoMiles(valorHora)) else stringResource(R.string.caregiver_profile_a_convenir)),
                    Triple(Icons.Default.Accessibility, stringResource(R.string.caregiver_profile_discapacidad), if (discapBool) (if (tiempoDiscap.isNotBlank()) stringResource(R.string.caregiver_view_discapacidad_detalle, tipoDiscap, tiempoDiscap) else tipoDiscap) else stringResource(R.string.caregiver_profile_no_declara))
                )
                quickStats.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { (icon, label, value) ->
                            QuickStatCard(
                                icon = icon,
                                label = label,
                                value = value,
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                val highlights = buildList {
                    if (especialidad.isNotBlank()) add(Triple(Icons.Default.School, stringResource(R.string.caregiver_view_especialidad), especialidad))
                    when {
                        experienciaResumen.isNotBlank() -> add(Triple(Icons.Default.History, stringResource(R.string.caregiver_view_experiencia), experienciaResumen))
                        experiencias.isNotEmpty() -> add(Triple(Icons.Default.History, stringResource(R.string.caregiver_view_experiencia), "${experiencias.size} ${if (experiencias.size == 1) stringResource(R.string.caregiver_view_experiencia_registrada) else stringResource(R.string.caregiver_view_experiencias_registradas)}"))
                    }
                    if (comunas.isNotEmpty()) add(Triple(Icons.Default.Place, stringResource(R.string.caregiver_view_trabaja_en), comunas.joinToString(", ")))
                    if (horarios.isNotEmpty()) add(Triple(Icons.Default.Schedule, stringResource(R.string.caregiver_view_disponibilidad), horarios.joinToString(", ")))
                }
                if (highlights.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    highlights.forEach { (icon, label, value) ->
                        HighlightRow(icon = icon, label = label, value = value, accentColor = accentColor)
                    }
                }
                if (valorHora > 0) {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = accentColor.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = accentColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.caregiver_profile_valor_referencial),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (user.fotoUrl.isNullOrBlank()) {
            com.example.cuidaleapp.ui.components.ConsejoFotoPerfil()
        }

        InfoSection(stringResource(R.string.caregiver_profile_acerca_de_titulo)) {
            Text(
                if (descripcion.isNotBlank()) descripcion else stringResource(R.string.caregiver_profile_sin_descripcion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.1f)) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle(stringResource(R.string.caregiver_profile_contacto_titulo), accentColor)
                Spacer(Modifier.height(10.dp))
                ContactItem(icon = Icons.Default.Email, label = stringResource(R.string.caregiver_profile_correo), value = censorEmail(context, user.correo), accentColor = accentColor)
                ContactItem(icon = Icons.Default.Phone, label = stringResource(R.string.caregiver_profile_telefono), value = censorPhone(context, user.telefono), accentColor = accentColor)
            }
        }

        com.example.cuidaleapp.ui.components.FormacionAcademicaSection(formaciones)
        com.example.cuidaleapp.ui.components.ExperienciasLaboralesSection(experiencias)
        com.example.cuidaleapp.ui.components.CertificadosSection(certificados)
        com.example.cuidaleapp.ui.components.ReferenciasLaboralesSection(referencias)

        if (tareasPlantilla.isNotEmpty()) {
            HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.1f)) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(stringResource(R.string.caregiver_view_regimen_tareas), accentColor)
                    Spacer(Modifier.height(10.dp))
                    tareasPlantilla.forEach { tarea ->
                        TaskItemCardInView(
                            tarea = tarea,
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }
            }
        }

        val resenasState = remember { mutableStateOf<List<com.example.cuidaleapp.models.CalificacionPublica>>(emptyList()) }
        LaunchedEffect(user.uid) {
            runCatching {
                val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("fichas").document(user.uid)
                    .collection("resenas")
                    .get().await()
                resenasState.value = snap.documents.mapNotNull { doc ->
                    com.example.cuidaleapp.models.CalificacionPublica(
                        autorNombre = doc.getString("autorNombre") ?: "",
                        calificacion = doc.getDouble("calificacion") ?: 0.0,
                        observacion = doc.getString("comentario") ?: "",
                        fecha = doc.getTimestamp("fecha")
                    )
                }
            }
        }
        com.example.cuidaleapp.ui.components.CalificacionesSection(resenasState.value)
    }
}

@Composable
private fun QuickStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = accentColor.copy(alpha = 0.07f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(15.dp), tint = accentColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HighlightRow(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = accentColor.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(17.dp), tint = accentColor)
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = secondaryTextColor,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}

private fun fichaFormaciones(ficha: Map<String, Any>?): List<FormacionAcademica> =
    (ficha?.get("formacionAcademica") as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            FormacionAcademica(
                titulo = m["titulo"] as? String ?: "",
                institucion = m["institucion"] as? String ?: "",
                anioInicio = m["anioInicio"] as? String ?: "",
                anioFin = m["anioFin"] as? String ?: ""
            )
        }
    } ?: emptyList()

private fun fichaExperiencias(ficha: Map<String, Any>?): List<ExperienciaLaboral> =
    (ficha?.get("experienciasLaborales") as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            ExperienciaLaboral(
                cargo = m["cargo"] as? String ?: "",
                empresa = m["empresa"] as? String ?: "",
                desde = m["desde"] as? String ?: "",
                hasta = m["hasta"] as? String ?: "",
                tiempoExperiencia = m["tiempoExperiencia"] as? String ?: "",
                descripcion = m["descripcion"] as? String ?: ""
            )
        }
    } ?: emptyList()

private fun fichaReferencias(ficha: Map<String, Any>?): List<ReferenciaLaboral> =
    (ficha?.get("referenciasLaborales") as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            ReferenciaLaboral(
                nombre = m["nombre"] as? String ?: "",
                empresa = m["empresa"] as? String ?: "",
                telefono = m["telefono"] as? String ?: "",
                cargo = m["cargo"] as? String ?: "",
                detalle = m["detalle"] as? String ?: ""
            )
        }
    } ?: emptyList()

private fun fichaCertificados(ficha: Map<String, Any>?): List<Certificado> =
    (ficha?.get("certificados") as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            Certificado(
                nombre = m["nombre"] as? String ?: "",
                tipoArchivo = m["tipoArchivo"] as? String ?: "",
                urlArchivo = m["urlArchivo"] as? String ?: "",
                storagePath = m["storagePath"] as? String ?: "",
                fechaSubida = m["fechaSubida"] as? com.google.firebase.Timestamp,
                estadoValidacion = m["estadoValidacion"] as? String ?: "pendiente"
            )
        }
    } ?: emptyList()


@Composable
private fun SectionTitle(text: String, accent: Color) {
    Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accent)
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.1f)) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(title, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
