package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.HistorialServicio
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    viewModel: SolicitudViewModel,
    userData: UserData,
    onNavigateBack: () -> Unit,
    onViewProfile: (String) -> Unit
) {
    val historial by viewModel.historial.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Borrar todo el historial") },
            text = { Text("¿Estás seguro de eliminar todo el historial de servicios? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.borrarTodoElHistorial(userData.rol)
                    showDeleteAllDialog = false
                }) { Text("BORRAR TODO", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("CANCELAR") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HISTORIAL DE SERVICIOS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (historial.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Borrar todo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (historial.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No hay servicios finalizados en el historial.", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(historial) { item ->
                        HistorialCard(
                            item = item,
                            role = userData.rol,
                            onDelete = { viewModel.eliminarDelHistorial(item.id, userData.rol) },
                            onViewProfile = { uid -> onViewProfile(uid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistorialCard(
    item: HistorialServicio,
    role: String,
    onDelete: () -> Unit,
    onViewProfile: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TaskAlt, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val dateStr = item.fechaTermino?.toDate()?.let { 
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                    } ?: ""
                    Text("Servicio Finalizado", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(dateStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = colors.error.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(16.dp))

            if (role == "representante") {
                // Rep sees Caregiver info
                CuidaleDetailRow(Icons.Default.MedicalServices, "Cuidador", item.cuidadorNombre)
                CuidaleDetailRow(Icons.Default.School, "Especialidad", item.cuidadorEspecialidad)
                
                Spacer(modifier = Modifier.height(16.dp))
                HardShadowButton(
                    text = "VER PERFIL CUIDADOR",
                    onClick = { onViewProfile(item.cuidadorUid) },
                    containerColor = colors.primary,
                    contentColor = Color.White,
                    shadowColor = colors.primary.copy(alpha = 0.2f),
                    modifier = Modifier.height(44.dp)
                )
            } else {
                // Caregiver sees Rep and Patient info
                CuidaleDetailRow(Icons.Default.Person, "Representante", item.representanteNombre)
                val censoredPhone = if (item.representanteTelefono.length >= 4) {
                    "*******" + item.representanteTelefono.takeLast(4)
                } else if (item.representanteTelefono.isNotEmpty()) {
                    item.representanteTelefono
                } else "No registrado"
                
                if (item.representanteTelefono.isNotEmpty()) {
                    CuidaleDetailRow(Icons.Default.Phone, "Teléfono", censoredPhone)
                }

                Spacer(modifier = Modifier.height(8.dp))
                CuidaleDetailRow(Icons.Default.FamilyRestroom, "Paciente", "${item.pacienteNombre} (${item.pacienteEdad} años)")

                Spacer(modifier = Modifier.height(16.dp))
                HardShadowButton(
                    text = "VER PERFIL REPRESENTANTE",
                    onClick = { onViewProfile(item.representanteUid) },
                    containerColor = colors.secondary,
                    contentColor = Color.White,
                    shadowColor = colors.secondary.copy(alpha = 0.2f),
                    modifier = Modifier.height(44.dp)
                )
            }
        }
    }
}
