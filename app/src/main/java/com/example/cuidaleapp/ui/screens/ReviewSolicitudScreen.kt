package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSolicitudScreen(
    solicitud: SolicitudCuidado,
    viewModel: SolicitudViewModel,
    isAccepted: Boolean = false,
    onNavigateBack: () -> Unit,
    onViewFullFicha: (String) -> Unit,
    onRedirectToRequests: () -> Unit
) {
    val scrollState = rememberScrollState()
    var patientFicha by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoadingFicha by remember { mutableStateOf(true) }
    var showFinishConfirm by remember { mutableStateOf(false) }

    val customColors = CuidaleThemeExtras.customColors
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(solicitud.pacienteFichaId) {
        try {
            val doc = FirebaseFirestore.getInstance().collection("fichas")
                .document(solicitud.pacienteFichaId).get().await()
            patientFicha = doc.data
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
                
                // 1. Title "Paciente"
                Text("PACIENTE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Photo and Name
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

                // 4. Panel "Representante"
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("REPRESENTANTE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        CuidaleDetailRow(Icons.Default.Person, "Nombre", solicitud.representanteNombre)
                        CuidaleDetailRow(Icons.Default.Badge, "RUT", solicitud.representanteRut)
                        CuidaleDetailRow(Icons.Default.Phone, "Teléfono", solicitud.representanteTelefono)
                    }
                }

                // 5 & 6. Meds and Diet (Only if accepted)
                if (isAccepted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MedicationListPanel(
                        value = patientFicha!!["medicamentos"],
                        accentColor = MaterialTheme.colorScheme.primary,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DietListPanel(
                        value = patientFicha!!["dieta"],
                        accentColor = MaterialTheme.colorScheme.primary,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // 7. Request Service Details
                HardShadowPanel(
                    backgroundColor = Color.White,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("INFORMACIÓN DEL SERVICIO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        CuidaleDetailRow(Icons.Default.Schedule, "Horario", solicitud.horarioStr)
                        CuidaleDetailRow(Icons.Default.DateRange, "Duración", if (solicitud.esIndefinido) "Indefinido" else "Temporal")
                        
                        if (!solicitud.esIndefinido) {
                            Text("Desde: ${formatDate(solicitud.fechaInicio?.toDate()?.time)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Hasta: ${formatDate(solicitud.fechaFin?.toDate()?.time)}", style = MaterialTheme.typography.bodyMedium)
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
                        text = "POSTULAR A SOLICITUD",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
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
