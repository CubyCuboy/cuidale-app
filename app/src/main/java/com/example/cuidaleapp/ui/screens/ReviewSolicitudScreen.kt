package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSolicitudScreen(
    solicitud: SolicitudCuidado,
    viewModel: SolicitudViewModel,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var patientFicha by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoadingFicha by remember { mutableStateOf(true) }

    LaunchedEffect(solicitud.pacienteFichaId) {
        try {
            val doc = FirebaseFirestore.getInstance().collection("fichas")
                .document(solicitud.pacienteFichaId).get().await()
            patientFicha = doc.data
        } catch (e: Exception) { } finally {
            isLoadingFicha = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DETALLES DE SOLICITUD", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoadingFicha) {
                CircularProgressIndicator()
            } else if (patientFicha != null) {
                // Patient Summary - LIMITED VIEW for caregiver
                ProfileDetailView(
                    role = "familiar",
                    data = patientFicha!!,
                    isExternal = true, // RESTRICT MEDICATIONS AND DIET
                    onEdit = { }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Request Details
                HardShadowPanel(
                    backgroundColor = Color.White,
                    shadowColor = Color.Black.copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("INFORMACIÓN DEL SERVICIO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        DetailItem(Icons.Default.Schedule, "Horario", solicitud.horarioStr)
                        DetailItem(Icons.Default.DateRange, "Duración", if (solicitud.esIndefinido) "Indefinido" else "Temporal")
                        
                        if (!solicitud.esIndefinido) {
                            Text("Desde: ${formatDate(solicitud.fechaInicio?.toDate()?.time)}")
                            Text("Hasta: ${formatDate(solicitud.fechaFin?.toDate()?.time)}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                HardShadowButton(
                    text = "POSTULAR A SOLICITUD",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    onClick = {
                        viewModel.postularASolicitud(solicitud, onNavigateBack)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onNavigateBack) {
                    Text("REGRESAR")
                }
            } else {
                Text("No se pudo cargar la información del paciente.")
            }
        }
    }
}

@Composable
fun DetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Text("$label: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
