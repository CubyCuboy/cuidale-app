package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.utils.DateUtils
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSolicitudScreen(
    solicitud: SolicitudCuidado,
    viewModel: SolicitudViewModel,
    isAccepted: Boolean = false,
    onNavigateBack: () -> Unit,
    onViewFullFicha: (String) -> Unit,
    onRedirectToRequests: () -> Unit,
    onNavigateToChat: ((String, String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var patientFicha by remember { mutableStateOf<Map<String, Any>?>(null) }
    var patientTareas by remember { mutableStateOf<List<com.example.cuidaleapp.models.Tarea>>(emptyList()) }
    var isLoadingFicha by remember { mutableStateOf(true) }
    var showFinishConfirm by remember { mutableStateOf(false) }

    val customColors = CuidaleThemeExtras.customColors
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var hasAlreadyApplied by remember { mutableStateOf(false) }
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(solicitud.id, currentUid) {
        if (currentUid != null) {
            try {
                val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("postulaciones")
                    .whereEqualTo("solicitudId", solicitud.id)
                    .whereEqualTo("cuidadorUid", currentUid)
                    .whereEqualTo("estado", "pendiente")
                    .get().await()
                hasAlreadyApplied = !snapshot.isEmpty
            } catch (e: Exception) { }
        }
    }

    LaunchedEffect(solicitud.pacienteFichaId) {
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("fichas")
                .document(solicitud.pacienteFichaId).get().await()
            patientFicha = doc.data
            
            val tasksSnapshot = db.collection("fichas")
                .document(solicitud.pacienteFichaId)
                .collection("tareas_plantilla")
                .whereEqualTo("activa", true)
                .get().await()
            patientTareas = tasksSnapshot.toObjects(com.example.cuidaleapp.models.Tarea::class.java)
        } catch (e: Exception) { } finally {
            isLoadingFicha = false
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("¿Finalizar servicio?") },
            text = { Text("¿Estás seguro de terminar el servicio? Esta acción cerrará la solicitud y la moverá al historial.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.finalizarServicio(solicitud) { 
                        showFinishConfirm = false
                        onRedirectToRequests()
                    }
                }) { Text("SÍ, FINALIZAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("NO") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAccepted) "DETALLES DEL SERVICIO" else "DETALLES DE SOLICITUD", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoadingFicha) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (patientFicha != null) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("PACIENTE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = Color.Gray.copy(alpha = 0.1f)
                ) {
                    if (solicitud.fotoPaciente.isNotEmpty()) {
                        AsyncImage(model = solicitud.fotoPaciente, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp), tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(solicitud.nombrePaciente, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                
                // 3. Button "Ver ficha completa"
                TextButton(onClick = { onViewFullFicha(solicitud.pacienteFichaId) }) {
                    Text("VER FICHA COMPLETA", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Section: Representante & Chat
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DATOS DEL REPRESENTANTE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        CuidaleDetailRow(Icons.Default.Person, "Nombre", solicitud.representanteNombre)
                        CuidaleDetailRow(Icons.Default.Phone, "Teléfono", solicitud.representanteTelefono)
                        
                        if (isAccepted && onNavigateToChat != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                    val otherUid = solicitud.representanteUid
                                    val chatId = if (myUid < otherUid) "${myUid}_$otherUid" else "${otherUid}_$myUid"
                                    onNavigateToChat(chatId, otherUid) 
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("IR AL CHAT")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Section: Datos Médicos del Paciente
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DATOS DEL PACIENTE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val enfermedades = (patientFicha!!["enfermedades"] as? List<*>)?.joinToString(", ") ?: "Ninguna"
                        val movilidad = patientFicha!!["movilidad"]?.toString() ?: "No especificada"
                        val genero = patientFicha!!["genero"]?.toString() ?: "No especificado"
                        val observaciones = patientFicha!!["condiciones"]?.toString() ?: "Sin observaciones adicionales"

                        CuidaleDetailRow(Icons.Default.HealthAndSafety, "Enfermedades", enfermedades)
                        CuidaleDetailRow(Icons.Default.Accessibility, "Movilidad", movilidad)
                        CuidaleDetailRow(Icons.Default.Wc, "Género", genero)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Observaciones:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(observaciones, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // 6. Tasks (Only if accepted)
                if (isAccepted && patientTareas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RÉGIMEN DE TAREAS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        patientTareas.forEach { tarea ->
                            TaskItemCardInView(tarea, MaterialTheme.colorScheme.primary, textColor, secondaryTextColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                HardShadowPanel(
                    backgroundColor = Color.White,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("DETALLES DEL SERVICIO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val calle = patientFicha!!["calle"]?.toString() ?: ""
                        val numero = patientFicha!!["numero"]?.toString() ?: ""
                        val depto = patientFicha!!["depto"]?.toString() ?: ""
                        val comuna = patientFicha!!["comuna"]?.toString() ?: ""
                        val direccionStr = if (calle.isNotEmpty()) "$calle $numero ${if(depto.isNotEmpty()) "Depto $depto" else ""}, $comuna" else "No especificada"

                        CuidaleDetailRow(Icons.Default.Place, "Dirección", direccionStr)
                        CuidaleDetailRow(Icons.Default.Schedule, "Horario", solicitud.horarioStr)
                        CuidaleDetailRow(Icons.Default.DateRange, "Duración", if (solicitud.esIndefinido) "Indefinido" else "Temporal")
                        
                        if (!solicitud.esIndefinido) {
                            Text("Desde: ${DateUtils.formatDate(solicitud.fechaInicio?.toDate()?.time)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 24.dp))
                            Text("Hasta: ${DateUtils.formatDate(solicitud.fechaFin?.toDate()?.time)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 24.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                if (isAccepted) {
                    HardShadowButton(
                        text = "FINALIZAR SERVICIO",
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                        onClick = { showFinishConfirm = true },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                } else {
                    HardShadowButton(
                        text = if (hasAlreadyApplied) "POSTULACIÓN PENDIENTE" else "POSTULAR A SOLICITUD",
                        containerColor = if (hasAlreadyApplied) Color.Gray else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shadowColor = if (hasAlreadyApplied) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        enabled = !hasAlreadyApplied,
                        onClick = {
                            viewModel.postularASolicitud(solicitud) { 
                                onRedirectToRequests() 
                            }
                        },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onNavigateBack) {
                    Text("REGRESAR")
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            } else {
                Text("No se pudo cargar la información del paciente.")
            }
        }
    }
}
