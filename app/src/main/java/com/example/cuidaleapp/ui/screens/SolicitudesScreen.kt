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
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver

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
    
    val pullToRefreshState = rememberPullToRefreshState()

    var requestToFinish by remember { mutableStateOf<SolicitudCuidado?>(null) }

    if (requestToFinish != null) {
        AlertDialog(
            onDismissRequest = { requestToFinish = null },
            title = { Text("¿Finalizar servicio?") },
            text = { Text("¿Estás seguro de terminar el servicio? Esta acción cerrará la solicitud y la moverá al historial.") },
            confirmButton = {
                TextButton(onClick = {
                    requestToFinish?.let { sol ->
                        viewModel.finalizarServicio(sol) {
                            requestToFinish = null
                        }
                    }
                }) { Text("SÍ, FINALIZAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { requestToFinish = null }) { Text("NO") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ConnectivityBanner()
        
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isLoading,
            onRefresh = { if (isOnline) viewModel.refreshData() },
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Text(
                    text = "SOLICITUDES",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onNavigateToHistory() }) {
                        Icon(Icons.Default.History, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HISTORIAL", color = Color.White, fontWeight = FontWeight.Bold)
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
                            Text("Estás offline. Funciones de edición desactivadas.", color = Color.White, style = MaterialTheme.typography.bodySmall)
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
            text = "CREAR SOLICITUD",
            containerColor = if (isOnline) Color.White else Color.Gray,
            contentColor = if (isOnline) MaterialTheme.colorScheme.primary else Color.White,
            shadowColor = Color.Black.copy(alpha = 0.2f),
            icon = Icons.Default.Add,
            enabled = isOnline,
            onClick = onCreateRequest
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (solicitudes.isEmpty()) {
            EmptyPlaceholder("Aún no has creado solicitudes.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                items(solicitudes, key = { it.id }) { sol ->
                    val applicants = postulacionesMap[sol.id]?.filter { it.estado == "pendiente" } ?: emptyList()
                    
                    BaseSolicitudCard(
                        title = sol.nombrePaciente,
                        subtitle = sol.horarioStr,
                        imageUrl = sol.fotoPaciente,
                        status = when(sol.estado) {
                            "abierta" -> "PENDIENTE"
                            "cerrada" -> "ACTIVA"
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
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(text = "TAREAS", icon = Icons.Default.ListAlt, color = Color(0xFF4CAF50), modifier = Modifier.weight(1f)) { onViewTasks(sol) }
                                ActionButton(text = "EDITAR", icon = Icons.Default.Edit, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f), enabled = isOnline) { onEdit(sol.id) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HardShadowButton(
                                text = "FINALIZAR SERVICIO",
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
                                ActionButton(text = "EDITAR", icon = Icons.Default.Edit, color = MaterialTheme.colorScheme.secondary, enabled = isOnline) { onEdit(sol.id) }
                                ActionOutlinedButton(text = "ELIMINAR", icon = Icons.Default.Delete, color = MaterialTheme.colorScheme.error, enabled = isOnline) { onDelete(sol.id) }
                            }
                        }
                    }
                    
                    if (sol.estado == "abierta" && applicants.isNotEmpty()) {
                        Text("POSTULANTES (${applicants.size})", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
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
    isOnline: Boolean = true,
    onViewDetails: (String) -> Unit,
    onViewTasks: (SolicitudCuidado) -> Unit,
    onFinish: (SolicitudCuidado) -> Unit,
    onCancelPostulacion: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        if (solicitudesAceptadas.isNotEmpty()) {
            item { Text("SERVICIOS ACTIVOS", style = MaterialTheme.typography.titleMedium, color = Color.White) }
            items(solicitudesAceptadas, key = { it.id }) { sol ->
                BaseSolicitudCard(
                    title = sol.nombrePaciente,
                    subtitle = sol.horarioStr,
                    imageUrl = sol.fotoPaciente,
                    status = "EN CURSO",
                    statusColor = MaterialTheme.colorScheme.primary
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(text = "TAREAS", icon = Icons.Default.ListAlt, color = Color(0xFF4CAF50)) { onViewTasks(sol) }
                        ActionButton(text = "DETALLES", icon = Icons.Default.Info, color = MaterialTheme.colorScheme.primary) { onViewDetails(sol.pacienteFichaId) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HardShadowButton(
                        text = "FINALIZAR TURNO",
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

        if (misPostulaciones.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("MIS POSTULACIONES", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            items(misPostulaciones, key = { it.second.id }) { (sol, post) ->
                PostulacionStatusCard(
                    solicitud = sol, 
                    estado = post.estado, 
                    isOnline = isOnline,
                    onCancel = { onCancelPostulacion(post.id) },
                    onViewDetails = { onViewDetails(sol.pacienteFichaId) }
                )
            }
        }

        if (solicitudesAceptadas.isEmpty() && misPostulaciones.isEmpty()) {
            item { EmptyPlaceholder("No tienes actividades pendientes.") }
        }
    }
}

@Composable
fun BaseSolicitudCard(
    title: String,
    subtitle: String,
    imageUrl: String,
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
                    if (imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
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
            Text("Cuidador asignado:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onViewProfile) {
            Text("VER PERFIL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun RowScope.ActionOutlinedButton(text: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f), enabled = enabled, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, maxLines = 1)
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
                        Text("VER PERFIL COMPLETO", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
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
                    Text("ACEPTAR", fontSize = 11.sp)
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
                    Text("RECHAZAR", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun PostulacionStatusCard(solicitud: SolicitudCuidado, estado: String, isOnline: Boolean = true, onCancel: () -> Unit, onViewDetails: () -> Unit) {
    val context = LocalContext.current
    HardShadowPanel(backgroundColor = Color.White.copy(alpha = 0.9f), shadowColor = Color.Black.copy(alpha = 0.05f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                    if (solicitud.fotoPaciente.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(solicitud.fotoPaciente)
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
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fadingEdge(Brush.horizontalGradient(0.95f to Color.Black, 1f to Color.Transparent))
                    )
                    Text(solicitud.horarioStr, style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = when(estado) { "aceptada" -> Color(0xFF4CAF50).copy(alpha = 0.1f); "rechazada" -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f); else -> Color.Blue.copy(alpha = 0.1f) }, shape = CircleShape) {
                    Text(text = if (estado == "pendiente") "EN ESPERA DE APROBACIÓN" else estado.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = when(estado) { "aceptada" -> Color(0xFF4CAF50); "rechazada" -> MaterialTheme.colorScheme.error; else -> Color.Blue })
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (estado == "pendiente") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(text = "VER SOLICITUD", icon = Icons.AutoMirrored.Filled.Assignment, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), onClick = onViewDetails)
                    OutlinedButton(onClick = onCancel, enabled = isOnline, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CANCELAR", fontSize = 11.sp)
                    }
                }
            } else if (estado == "rechazada") {
                OutlinedButton(onClick = onCancel, enabled = isOnline, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("QUITAR DEL LISTADO", fontSize = 11.sp)
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
