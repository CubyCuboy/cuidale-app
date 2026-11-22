package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.ui.components.fadingEdge
import com.example.cuidaleapp.models.PostulacionCuidado
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.R
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolicitudesScreen(
    viewModel: SolicitudViewModel,
    userData: UserData,
    onCreateRequest: () -> Unit,
    onEditRequest: (String) -> Unit,
    onViewPatientDetails: (String) -> Unit,
    onViewCaregiverProfile: (String) -> Unit,
    onViewTasks: (SolicitudCuidado) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val misSolicitudes by viewModel.misSolicitudes.collectAsState()
    val solicitudesAceptadas by viewModel.solicitudesAceptadas.collectAsState()
    val misPostulaciones by viewModel.misPostulaciones.collectAsState()
    val postulacionesRecibidas by viewModel.postulacionesRecibidas.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val representantesFotos by viewModel.representantesFotos.collectAsState()
    
    val pullToRefreshState = rememberPullToRefreshState()

    var requestToFinish by remember { mutableStateOf<SolicitudCuidado?>(null) }

    if (requestToFinish != null) {
        AlertDialog(
            onDismissRequest = { requestToFinish = null },
            title = { Text(stringResource(R.string.solicitudes_finalizar_servicio_titulo)) },
            text = { Text(stringResource(R.string.solicitudes_finalizar_servicio_mensaje)) },
            confirmButton = {
                TextButton(onClick = {
                    requestToFinish?.let { sol ->
                        viewModel.finalizarServicio(sol) {
                            requestToFinish = null
                        }
                    }
                }) { Text(stringResource(R.string.solicitudes_si_finalizar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { requestToFinish = null }) { Text(stringResource(R.string.solicitudes_no)) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isLoading,
            onRefresh = { if (isOnline) viewModel.refreshData() },
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Text(
                    text = stringResource(R.string.solicitudes_titulo),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onNavigateToHistory() }) {
                        Icon(Icons.Default.History, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.solicitudes_historial), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isOnline) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.solicitudes_offline_mensaje), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (userData.rol == "representante") {
                    RepresentanteRequestsContent(
                        solicitudes = misSolicitudes,
                        postulacionesMap = postulacionesRecibidas,
                        isOnline = isOnline,
                        onCreateRequest = onCreateRequest,
                        onEdit = onEditRequest,
                        onDelete = { if (isOnline) viewModel.eliminarSolicitud(it) },
                        onAccept = { sol, post -> if (isOnline) viewModel.aceptarCuidador(sol, post) {} },
                        onReject = { if (isOnline) viewModel.rechazarPostulacion(it) },
                        onViewCaregiverProfile = onViewCaregiverProfile,
                        onViewPatientDetails = onViewPatientDetails,
                        onViewTasks = onViewTasks,
                        onFinish = { if (isOnline) requestToFinish = it }
                    )
                } else {
                    CuidadorRequestsContent(
                        solicitudesAceptadas = solicitudesAceptadas,
                        misPostulaciones = misPostulaciones,
                        representantesFotos = representantesFotos,
                        isOnline = isOnline,
                        onViewDetails = onViewPatientDetails,
                        onViewTasks = onViewTasks,
                        onFinish = { if (isOnline) requestToFinish = it },
                        onCancelPostulacion = { if (isOnline) viewModel.eliminarPostulacionPorId(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun RepresentanteRequestsContent(
    solicitudes: List<SolicitudCuidado>,
    postulacionesMap: Map<String, List<PostulacionCuidado>>,
    isOnline: Boolean = true,
    onCreateRequest: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAccept: (SolicitudCuidado, PostulacionCuidado) -> Unit,
    onReject: (PostulacionCuidado) -> Unit,
    onViewCaregiverProfile: (String) -> Unit,
    onViewPatientDetails: (String) -> Unit,
    onViewTasks: (SolicitudCuidado) -> Unit,
    onFinish: (SolicitudCuidado) -> Unit
) {
    Column {
        HardShadowButton(
            text = stringResource(R.string.solicitudes_crear_solicitud),
            containerColor = if (isOnline) Color.White else Color.Gray,
            contentColor = if (isOnline) MaterialTheme.colorScheme.primary else Color.White,
            shadowColor = Color.Black.copy(alpha = 0.2f),
            icon = Icons.Default.Add,
            enabled = isOnline,
            onClick = onCreateRequest
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (solicitudes.isEmpty()) {
            EmptyPlaceholder(stringResource(R.string.solicitudes_vacio_representante))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(solicitudes, key = { it.id }) { sol ->
                    val applicants = postulacionesMap[sol.id]?.filter { it.estado == "pendiente" } ?: emptyList()
                    
                    BaseSolicitudCard(
                        title = sol.nombrePaciente,
                        subtitle = sol.horarioStr,
                        imageUrl = sol.fotoPaciente,
                        status = when(sol.estado) {
                            "abierta" -> stringResource(R.string.solicitudes_estado_pendiente)
                            "cerrada" -> stringResource(R.string.solicitudes_estado_activa)
                            else -> sol.estado.uppercase()
                        },
                        statusColor = if (sol.estado == "abierta") Color(0xFF4CAF50) else Color(0xFF2196F3)
                    ) {
                        if (sol.estado == "cerrada" && sol.cuidadorNombre.isNotEmpty()) {
                            AssignedCaregiverRow(
                                name = sol.cuidadorNombre,
                                photo = sol.cuidadorFoto,
                                onViewProfile = { sol.cuidadorUid?.let { onViewCaregiverProfile(it) } }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        if (sol.estado == "cerrada") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(text = stringResource(R.string.solicitudes_tareas), icon = Icons.Default.ListAlt, color = Color(0xFF4CAF50), modifier = Modifier.fillMaxWidth()) { onViewTasks(sol) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ActionButton(text = stringResource(R.string.solicitudes_detalles), icon = Icons.Default.Info, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)) { onViewPatientDetails(sol.pacienteFichaId) }
                                    ActionButton(text = stringResource(R.string.solicitudes_editar), icon = Icons.Default.Edit, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f), enabled = isOnline) { onEdit(sol.id) }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HardShadowButton(
                                text = stringResource(R.string.solicitudes_finalizar_servicio),
                                containerColor = if (isOnline) MaterialTheme.colorScheme.secondary else Color.Gray,
                                contentColor = Color.White,
                                shadowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                icon = Icons.Default.Check,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                enabled = isOnline,
                                onClick = { onFinish(sol) }
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(text = stringResource(R.string.solicitudes_editar), icon = Icons.Default.Edit, color = MaterialTheme.colorScheme.secondary, enabled = isOnline) { onEdit(sol.id) }
                                ActionOutlinedButton(text = stringResource(R.string.solicitudes_eliminar), icon = Icons.Default.Delete, color = MaterialTheme.colorScheme.error, enabled = isOnline) { onDelete(sol.id) }
                            }
                        }
                    }
                    
                    if (sol.estado == "abierta" && applicants.isNotEmpty()) {
                        Text(stringResource(R.string.solicitudes_postulantes_count, applicants.size), style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        applicants.forEach { applicant ->
                            ApplicantMiniCard(applicant = applicant, isOnline = isOnline, onAccept = { onAccept(sol, applicant) }, onReject = { onReject(applicant) }, onViewProfile = { onViewCaregiverProfile(applicant.cuidadorUid) })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CuidadorRequestsContent(
    solicitudesAceptadas: List<SolicitudCuidado>,
    misPostulaciones: List<Pair<SolicitudCuidado, PostulacionCuidado>>,
    representantesFotos: Map<String, String> = emptyMap(),
    isOnline: Boolean = true,
    onViewDetails: (String) -> Unit,
    onViewTasks: (SolicitudCuidado) -> Unit,
    onFinish: (SolicitudCuidado) -> Unit,
    onCancelPostulacion: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        if (solicitudesAceptadas.isNotEmpty()) {
            item { Text(stringResource(R.string.solicitudes_servicios_activos), style = MaterialTheme.typography.titleMedium, color = Color.White) }
            items(solicitudesAceptadas, key = { it.id }) { sol ->
                BaseSolicitudCard(
                    title = sol.nombrePaciente,
                    subtitle = sol.horarioStr,
                    imageUrl = sol.fotoPaciente,
                    representanteFoto = representantesFotos[sol.representanteUid] ?: "",
                    status = stringResource(R.string.solicitudes_estado_en_curso),
                    statusColor = MaterialTheme.colorScheme.primary
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(text = stringResource(R.string.solicitudes_tareas), icon = Icons.Default.ListAlt, color = Color(0xFF4CAF50)) { onViewTasks(sol) }
                        ActionButton(text = stringResource(R.string.solicitudes_detalles), icon = Icons.Default.Info, color = MaterialTheme.colorScheme.primary) { onViewDetails(sol.pacienteFichaId) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HardShadowButton(
                        text = stringResource(R.string.solicitudes_finalizar_turno),
                        containerColor = if (isOnline) MaterialTheme.colorScheme.secondary else Color.Gray,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                        icon = Icons.Default.Check,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = isOnline,
                        onClick = { onFinish(sol) }
                    )
                }
            }
        }

        val aceptadasIds = solicitudesAceptadas.map { it.id }.toSet()
        val postulacionesPendientes = misPostulaciones.filterNot {
            it.second.estado == "aceptada" || it.first.estado == "cerrada" || it.first.id in aceptadasIds
        }

        if (postulacionesPendientes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.solicitudes_mis_postulaciones), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            items(postulacionesPendientes, key = { it.second.id }) { (sol, post) ->
                PostulacionStatusCard(
                    solicitud = sol, 
                    estado = post.estado, 
                    representanteFoto = representantesFotos[sol.representanteUid] ?: "",
                    isOnline = isOnline,
                    onCancel = { onCancelPostulacion(post.id) },
                    onViewDetails = { onViewDetails(sol.pacienteFichaId) }
                )
            }
        }

        if (solicitudesAceptadas.isEmpty() && postulacionesPendientes.isEmpty()) {
            item { EmptyPlaceholder(stringResource(R.string.solicitudes_vacio_cuidador)) }
        }
    }
}

@Composable
fun BaseSolicitudCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    representanteFoto: String = "",
    status: String,
    statusColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                    val avatarUrl = representanteFoto.ifEmpty { imageUrl }
                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fadingEdge(Brush.horizontalGradient(0.95f to Color.Black, 1f to Color.Transparent))
                    )
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Surface(color = statusColor.copy(alpha = 0.1f), shape = CircleShape) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun AssignedCaregiverRow(name: String, photo: String, onViewProfile: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = photo, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.solicitudes_cuidador_asignado), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onViewProfile) {
            Text(stringResource(R.string.solicitudes_ver_perfil), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun RowScope.ActionOutlinedButton(text: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ApplicantMiniCard(applicant: PostulacionCuidado, isOnline: Boolean = true, onAccept: () -> Unit, onReject: () -> Unit, onViewProfile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = applicant.cuidadorFoto,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(applicant.cuidadorNombre, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    TextButton(
                        onClick = onViewProfile, 
                        contentPadding = PaddingValues(0.dp), 
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(stringResource(R.string.solicitudes_ver_perfil_completo), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    enabled = isOnline,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.solicitudes_aceptar), fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = isOnline,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.solicitudes_rechazar), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun PostulacionStatusCard(solicitud: SolicitudCuidado, estado: String, representanteFoto: String = "", isOnline: Boolean = true, onCancel: () -> Unit, onViewDetails: () -> Unit) {
    val context = LocalContext.current
    HardShadowPanel(backgroundColor = Color.White.copy(alpha = 0.9f), shadowColor = Color.Black.copy(alpha = 0.05f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                    val avatarUrl = representanteFoto.ifEmpty { solicitud.fotoPaciente }
                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = solicitud.nombrePaciente,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(solicitud.horarioStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = when (estado) {
                    "aceptada" -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                    "rechazada" -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else -> Color.Blue.copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (estado == "pendiente") stringResource(R.string.solicitudes_estado_en_espera) else estado.uppercase(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = when (estado) {
                        "aceptada" -> Color(0xFF4CAF50)
                        "rechazada" -> MaterialTheme.colorScheme.error
                        else -> Color.Blue
                    },
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (estado == "pendiente") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(text = stringResource(R.string.solicitudes_ver_solicitud), icon = Icons.AutoMirrored.Filled.Assignment, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), onClick = onViewDetails)
                    OutlinedButton(onClick = onCancel, enabled = isOnline, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.solicitudes_cancelar), fontSize = 11.sp)
                    }
                }
            } else if (estado == "rechazada") {
                OutlinedButton(onClick = onCancel, enabled = isOnline, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.solicitudes_quitar_listado), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AssignmentLate, null, modifier = Modifier.size(64.dp), tint = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White)
        }
    }
}
