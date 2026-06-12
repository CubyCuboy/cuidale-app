package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.PostulacionCuidado
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.SolicitudViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolicitudesScreen(
    viewModel: SolicitudViewModel,
    userData: UserData,
    unreadCount: Int,
    pendingNotifs: Int,
    onTabSelected: (String) -> Unit,
    onCreateRequest: () -> Unit,
    onEditRequest: (String) -> Unit,
    onViewPatientDetails: (String) -> Unit,
    onViewCaregiverProfile: (String) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val misSolicitudes by viewModel.misSolicitudes.collectAsState()
    val solicitudesAceptadas by viewModel.solicitudesAceptadas.collectAsState()
    val misPostulaciones by viewModel.misPostulaciones.collectAsState()
    val postulacionesRecibidas by viewModel.postulacionesRecibidas.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val pullToRefreshState = rememberPullToRefreshState()
    val gradient = CuidaleThemeExtras.gradient

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

    Scaffold(
        bottomBar = {
            CuidaleBottomBar(
                selectedTab = "requests",
                unreadCount = unreadCount,
                pendingNotifs = pendingNotifs,
                onTabSelected = onTabSelected
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isLoading,
            onRefresh = { viewModel.refreshData() },
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Text(
                    text = "SOLICITUDES",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onNavigateToHistory() }) {
                        Icon(Icons.Default.History, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HISTORIAL", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (userData.rol == "representante") {
                    RepresentanteRequestsContent(
                        solicitudes = misSolicitudes,
                        postulacionesMap = postulacionesRecibidas,
                        onCreateRequest = onCreateRequest,
                        onEdit = onEditRequest,
                        onDelete = { viewModel.eliminarSolicitud(it) },
                        onAccept = { sol, post -> viewModel.aceptarCuidador(sol, post) {} },
                        onReject = { viewModel.rechazarPostulacion(it) },
                        onViewCaregiverProfile = onViewCaregiverProfile,
                        onFinish = { requestToFinish = it }
                    )
                } else {
                    CuidadorRequestsContent(
                        solicitudesAceptadas = solicitudesAceptadas,
                        misPostulaciones = misPostulaciones,
                        onViewDetails = onViewPatientDetails,
                        onFinish = { requestToFinish = it },
                        onCancelPostulacion = { viewModel.eliminarPostulacionPorId(it) }
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
    onCreateRequest: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAccept: (SolicitudCuidado, PostulacionCuidado) -> Unit,
    onReject: (PostulacionCuidado) -> Unit,
    onViewCaregiverProfile: (String) -> Unit,
    onFinish: (SolicitudCuidado) -> Unit
) {
    Column {
        HardShadowButton(
            text = "CREAR SOLICITUD",
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.primary,
            shadowColor = Color.Black.copy(alpha = 0.2f),
            icon = Icons.Default.Add,
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (sol.estado != "finalizada") {
                                ActionButton(text = "EDITAR", icon = Icons.Default.Edit, color = MaterialTheme.colorScheme.secondary) { onEdit(sol.id) }
                            }
                            
                            if (sol.estado == "cerrada") {
                                ActionButton(text = "FINALIZAR", icon = Icons.Default.Check, color = Color(0xFF4CAF50)) { onFinish(sol) }
                            } else {
                                ActionOutlinedButton(text = "ELIMINAR", icon = Icons.Default.Delete, color = MaterialTheme.colorScheme.error) { onDelete(sol.id) }
                            }
                        }
                    }
                    
                    if (sol.estado == "abierta" && applicants.isNotEmpty()) {
                        Text("POSTULANTES (${applicants.size})", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        applicants.forEach { applicant ->
                            ApplicantMiniCard(applicant = applicant, onAccept = { onAccept(sol, applicant) }, onReject = { onReject(applicant) }, onViewProfile = { onViewCaregiverProfile(applicant.cuidadorUid) })
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
    onViewDetails: (String) -> Unit,
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(text = "VER PACIENTE", icon = Icons.Default.Person, color = MaterialTheme.colorScheme.primary) { onViewDetails(sol.pacienteFichaId) }
                        ActionButton(text = "FINALIZAR", icon = Icons.Default.Check, color = MaterialTheme.colorScheme.secondary) { onFinish(sol) }
                    }
                }
            }
        }

        if (misPostulaciones.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("MIS POSTULACIONES", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            // Use the Postulation ID as key for stability
            items(misPostulaciones, key = { it.second.id }) { (sol, post) ->
                PostulacionStatusCard(sol, post.estado, onCancel = { onCancelPostulacion(post.id) })
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
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f))) {
                    if (imageUrl.isNotEmpty()) {
                        AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
fun RowScope.ActionButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp)
    }
}

@Composable
fun RowScope.ActionOutlinedButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 11.sp)
    }
}

@Composable
fun ApplicantMiniCard(applicant: PostulacionCuidado, onAccept: () -> Unit, onReject: () -> Unit, onViewProfile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAccept, modifier = Modifier.size(40.dp)) { 
                    Icon(Icons.Default.CheckCircle, "Aceptar", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp)) 
                }
                IconButton(onClick = onReject, modifier = Modifier.size(40.dp)) { 
                    Icon(Icons.Default.Cancel, "Rechazar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) 
                }
            }
        }
    }
}

@Composable
fun PostulacionStatusCard(solicitud: SolicitudCuidado, estado: String, onCancel: () -> Unit) {
    HardShadowPanel(backgroundColor = Color.White.copy(alpha = 0.9f), shadowColor = Color.Black.copy(alpha = 0.05f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(solicitud.nombrePaciente, fontWeight = FontWeight.Bold)
                    Text(solicitud.horarioStr, style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = when(estado) { "aceptada" -> Color(0xFF4CAF50).copy(alpha = 0.1f); "rechazada" -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f); else -> Color.Blue.copy(alpha = 0.1f) }, shape = CircleShape) {
                    Text(text = if (estado == "pendiente") "EN ESPERA DE APROBACIÓN" else estado.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = when(estado) { "aceptada" -> Color(0xFF4CAF50); "rechazada" -> MaterialTheme.colorScheme.error; else -> Color.Blue })
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (estado == "pendiente") {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CANCELAR POSTULACIÓN", fontSize = 11.sp)
                }
            } else if (estado == "rechazada") {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
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
            Icon(Icons.Default.AssignmentLate, null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White.copy(alpha = 0.6f))
        }
    }
}
