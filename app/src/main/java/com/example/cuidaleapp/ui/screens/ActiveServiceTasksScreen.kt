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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.models.*
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.TaskCreationDialog
import com.example.cuidaleapp.ui.components.getIconForTaskType
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.TareasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveServiceTasksScreen(
    solicitud: SolicitudCuidado,
    tareasViewModel: TareasViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit
) {
    val tareasHoy by tareasViewModel.tareasHoy.collectAsState()
    val gradient = CuidaleThemeExtras.gradient
    val isRepresentative = currentUserId == solicitud.representanteUid
    var showCreateDialog by remember { mutableStateOf<TipoTarea?>(null) }
    var editingRegistro by remember { mutableStateOf<RegistroTarea?>(null) }

    LaunchedEffect(solicitud.id) {
        tareasViewModel.generarInstanciasParaHoy(solicitud.id, solicitud.pacienteFichaId)
        tareasViewModel.cargarTareasDelDia(solicitud.id)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SEGUIMIENTO DIARIO", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (isRepresentative) {
                FloatingActionButton(
                    onClick = { showCreateDialog = TipoTarea.OTRO },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, "Agregar Tarea")
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Text(
                    text = solicitud.nombrePaciente,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (tareasHoy.isEmpty()) {
                    EmptyPlaceholder("No hay tareas programadas para hoy.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(tareasHoy) { registro ->
                            DailyTaskCard(
                                registro = registro,
                                isReadOnly = isRepresentative,
                                onToggle = { completed ->
                                    tareasViewModel.marcarTareaCompletada(
                                        solicitud.id,
                                        registro.id,
                                        completed,
                                        currentUserId
                                    )
                                },
                                onEdit = if (isRepresentative) { { editingRegistro = registro } } else null
                            )
                        }
                    }
                }
            }
        }

        if (showCreateDialog != null || editingRegistro != null) {
            val initialTarea = editingRegistro?.let { 
                Tarea(
                    id = it.id,
                    titulo = it.titulo,
                    tipo = it.tipo,
                    horarioInicio = it.horarioPrevisto
                )
            }
            TaskCreationDialog(
                onDismiss = { 
                    showCreateDialog = null
                    editingRegistro = null
                },
                onConfirm = { nuevaTarea ->
                    if (editingRegistro != null) {
                        val updates = mapOf(
                            "titulo" to nuevaTarea.titulo,
                            "tipo" to nuevaTarea.tipo.name,
                            "horarioPrevisto" to nuevaTarea.horarioInicio
                        )
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("solicitudes").document(solicitud.id)
                            .collection("registros_tareas").document(editingRegistro!!.id)
                            .update(updates)
                    } else {
                        tareasViewModel.agregarTareaAPlantilla(solicitud.pacienteFichaId, nuevaTarea)
                        tareasViewModel.agregarTareaARegistroHoy(solicitud.id, nuevaTarea)
                    }
                    showCreateDialog = null
                    editingRegistro = null
                },
                accentColor = MaterialTheme.colorScheme.primary,
                defaultType = showCreateDialog ?: editingRegistro?.tipo ?: TipoTarea.OTRO,
                initialTarea = initialTarea
            )
        }
    }
}

@Composable
fun DailyTaskCard(
    registro: RegistroTarea,
    isReadOnly: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val statusColor = if (registro.completada) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    val isDark = CuidaleThemeExtras.customColors.isDark
    
    HardShadowPanel(
        backgroundColor = if (isDark) Color(0xFF1E1F22) else Color.White,
        shadowColor = Color.Black.copy(alpha = 0.1f),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        getIconForTaskType(registro.tipo),
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = registro.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = "Horario: ${registro.horarioPrevisto}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                if (registro.completada) {
                    Text(
                        text = "Realizada a las ${registro.fechaCompletado?.toDate()?.let { 
                            java.text.SimpleDateFormat("HH:mm").format(it) 
                        }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            if (isReadOnly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                    if (registro.completada) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                    } else {
                        Icon(Icons.Default.RadioButtonUnchecked, null, tint = Color.Gray)
                    }
                }
            } else {
                Checkbox(
                    checked = registro.completada,
                    onCheckedChange = onToggle,
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50))
                )
            }
        }
    }
}
