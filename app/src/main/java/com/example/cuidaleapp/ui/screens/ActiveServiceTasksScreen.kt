package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.platform.LocalLocale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveServiceTasksScreen(
    solicitud: SolicitudCuidado,
    tareasViewModel: TareasViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String, String, String?) -> Unit // chatId, otherUid, initialMsg
) {
    val tareasHoy by tareasViewModel.tareasHoy.collectAsState()
    val error by tareasViewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val gradient = CuidaleThemeExtras.gradient
    val isRepresentative = currentUserId == solicitud.representanteUid
    val otherUid = if (isRepresentative) solicitud.cuidadorUid ?: "" else solicitud.representanteUid

    var showCreateDialog by remember { mutableStateOf<TipoTarea?>(null) }
    var editingRegistro by remember { mutableStateOf<RegistroTarea?>(null) }
    var taskToConfirm by remember { mutableStateOf<RegistroTarea?>(null) }
    var taskToPostpone by remember { mutableStateOf<RegistroTarea?>(null) }
    var postponeReason by remember { mutableStateOf("") }
    var showSummary by remember { mutableStateOf(false) }
    
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showMassPostponeDialog by remember { mutableStateOf(false) }
    var showMassConfirmDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(solicitud.id) {
        tareasViewModel.cargarTareasServicio(solicitud.id)
        tareasViewModel.generarInstanciasParaHoy(solicitud.id, solicitud.pacienteFichaId)
        tareasViewModel.cargarTareasDelDia(solicitud.id)
        tareasViewModel.iniciarMonitoreoTareas(context)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            tareasViewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isSelectionMode) "${selectedIds.size} seleccionadas" else "SEGUIMIENTO DIARIO", fontWeight = FontWeight.Bold, color = Color.White)
                        if (!isSelectionMode) Text(solicitud.nombrePaciente, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedIds.clear()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    if (isRepresentative && !isSelectionMode && tareasHoy.isNotEmpty()) {
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(Icons.Default.Checklist, "Seleccionar", tint = Color.White)
                        }
                    }
                    if (isSelectionMode && selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showMassConfirmDialog = true }) {
                            Icon(Icons.Default.CheckCircle, "Finalizar", tint = Color.White)
                        }
                        IconButton(onClick = { showMassPostponeDialog = true }) {
                            Icon(Icons.Default.Schedule, "Postergar", tint = Color.White)
                        }
                        IconButton(onClick = {
                            val selectedRegistros = tareasHoy.filter { selectedIds.contains(it.id) }
                            val tareaIds = selectedRegistros.map { it.tareaId }.distinct()
                            
                            tareasViewModel.eliminarTareasSeleccionadas(solicitud.id, tareaIds)
                            // La función eliminarTareasSeleccionadas ya debería limpiar los registros no completados

                            isSelectionMode = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Delete, "Eliminar", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            val allProcessed = tareasHoy.all { it.completada || it.postergada }
            Column(horizontalAlignment = Alignment.End) {
                if (!isRepresentative && !isSelectionMode) {
                    ExtendedFloatingActionButton(
                        onClick = { 
                            if (allProcessed) showSummary = true 
                            else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Debes completar o postergar todas las tareas antes de finalizar el turno.")
                                }
                            }
                        },
                        containerColor = if (allProcessed) Color(0xFF4CAF50) else Color.Gray,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.AssignmentTurnedIn, null) },
                        text = { Text("FINALIZAR TURNO") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (isRepresentative && !isSelectionMode) {
                    FloatingActionButton(
                        onClick = { showCreateDialog = TipoTarea.OTRO },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, "Agregar Tarea")
                    }
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
            if (tareasHoy.isEmpty()) {
                EmptyPlaceholder("No hay tareas para hoy.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp)
                ) {
                    items(tareasHoy, key = { it.id }) { registro ->
                        DailyTaskCard(
                            registro = registro,
                            isSelected = selectedIds.contains(registro.id),
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = {
                                if (selectedIds.contains(registro.id)) selectedIds.remove(registro.id)
                                else selectedIds.add(registro.id)
                            },
                            onToggleStatus = { completed ->
                                if (completed) {
                                    taskToConfirm = registro
                                } else {
                                    tareasViewModel.marcarTareaCompletada(
                                        solicitud.id,
                                        registro.id,
                                        false,
                                        currentUserId
                                    )
                                }
                            },
                            onPostpone = if (!isRepresentative) { { taskToPostpone = registro } } else null,
                            onEdit = if (isRepresentative) { 
                                if (registro.completada) null else { { editingRegistro = registro } }
                            } else null,
                            onAsk = if (!isRepresentative) { {
                                val frecuenciaFriendly = when(registro.frecuencia) {
                                    FrecuenciaTarea.DIARIA -> "Diaria"
                                    FrecuenciaTarea.SEMANAL -> "Semanal"
                                    FrecuenciaTarea.MENSUAL -> "Mensual"
                                    FrecuenciaTarea.UNA_VEZ -> "Una vez"
                                }
                                val msg = "Hola, tengo una duda sobre esta tarea:\n\n" +
                                         "*${registro.titulo}*\n" +
                                         "⏰ Horario: ${registro.horarioPrevisto}\n" +
                                         "📋 Frecuencia: $frecuenciaFriendly\n" +
                                         "📝 Indicaciones: ${if (registro.descripcion.isNotBlank()) registro.descripcion else "Sin indicaciones específicas"}"
                                
                                val chatId = if (currentUserId < otherUid) "${currentUserId}_$otherUid" else "${otherUid}_$currentUserId"
                                onNavigateToChat(chatId, otherUid, msg)
                            } } else null
                        )
                    }
                }
            }
        }

        if (showMassConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showMassConfirmDialog = false },
                title = { Text("Finalizar Tareas") },
                text = { Text("¿Deseas marcar las ${selectedIds.size} tareas seleccionadas como finalizadas?") },
                confirmButton = {
                    Button(onClick = {
                        tareasViewModel.completarMultiplesTareas(solicitud.id, selectedIds.toList(), currentUserId, otherUid)
                        isSelectionMode = false
                        selectedIds.clear()
                        showMassConfirmDialog = false
                    }) { Text("SÍ, FINALIZAR") }
                },
                dismissButton = {
                    TextButton(onClick = { showMassConfirmDialog = false }) { Text("CANCELAR") }
                }
            )
        }

        if (showMassPostponeDialog) {
            var massPostponeReason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showMassPostponeDialog = false },
                title = { Text("Postergar Selección") },
                text = {
                    Column {
                        Text("Indica el motivo para postergar estas ${selectedIds.size} tareas:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = massPostponeReason,
                            onValueChange = { massPostponeReason = it },
                            placeholder = { Text("Ej: Reprogramación masiva") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            tareasViewModel.postergarMultiplesTareas(solicitud.id, selectedIds.toList(), massPostponeReason, currentUserId, otherUid)
                            isSelectionMode = false
                            selectedIds.clear()
                            showMassPostponeDialog = false
                        },
                        enabled = massPostponeReason.isNotBlank()
                    ) { Text("POSTERGAR TODAS") }
                },
                dismissButton = {
                    TextButton(onClick = { showMassPostponeDialog = false }) { Text("CANCELAR") }
                }
            )
        }

        if (taskToConfirm != null) {
            AlertDialog(
                onDismissRequest = { taskToConfirm = null },
                title = { Text("Finalizar Tarea") },
                text = { Text("¿Deseas marcar '${taskToConfirm?.titulo}' como finalizada?") },
                confirmButton = {
                    Button(onClick = {
                        taskToConfirm?.let {
                            tareasViewModel.marcarTareaCompletada(
                                solicitud.id,
                                it.id,
                                true,
                                currentUserId,
                                titulo = it.titulo,
                                otherUid = otherUid
                            )
                        }
                        taskToConfirm = null
                    }) { Text("SÍ, FINALIZAR") }
                },
                dismissButton = {
                    TextButton(onClick = { taskToConfirm = null }) { Text("CANCELAR") }
                }
            )
        }

        if (taskToPostpone != null) {
            AlertDialog(
                onDismissRequest = { taskToPostpone = null },
                title = { Text("Postergar Tarea") },
                text = {
                    Column {
                        Text("Indica el motivo de la postergación:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = postponeReason,
                            onValueChange = { postponeReason = it },
                            placeholder = { Text("Ej: El paciente está durmiendo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            taskToPostpone?.let {
                                tareasViewModel.postergarTarea(
                                    solicitud.id,
                                    it.id,
                                    postponeReason,
                                    currentUserId,
                                    it.titulo,
                                    otherUid
                                )
                            }
                            taskToPostpone = null
                            postponeReason = ""
                        },
                        enabled = postponeReason.isNotBlank()
                    ) { Text("POSTERGAR") }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        taskToPostpone = null
                        postponeReason = ""
                    }) { Text("CANCELAR") }
                }
            )
        }

        if (showSummary) {
            var observaciones by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSummary = false },
                title = { Text("RESUMEN DE TURNO") },
                text = {
                    Column {
                        val completadas = tareasHoy.count { it.completada }
                        val total = tareasHoy.size
                        Text("Tareas realizadas: $completadas de $total", fontWeight = FontWeight.Bold)
                        
                        if (tareasHoy.any { it.postergada }) {
                            Text("Tareas postergadas: ${tareasHoy.count { it.postergada }}", color = Color.Red)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Observaciones adicionales para el representante:")
                        OutlinedTextField(
                            value = observaciones,
                            onValueChange = { observaciones = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        tareasViewModel.generarInformeFinal(solicitud.id, currentUserId, observaciones) {
                            showSummary = false
                            onNavigateBack()
                        }
                    }) { Text("ENVIAR Y TERMINAR") }
                },
                dismissButton = {
                    TextButton(onClick = { showSummary = false }) { Text("VOLVER") }
                }
            )
        }

        if (showCreateDialog != null || editingRegistro != null) {
            val initialTarea = editingRegistro?.let { 
                Tarea(
                    id = it.tareaId,
                    titulo = it.titulo,
                    tipo = it.tipo,
                    horarioInicio = it.horarioPrevisto,
                    descripcion = it.descripcion,
                    frecuencia = it.frecuencia
                )
            }
            val plantillas by tareasViewModel.tareasPlantilla.collectAsState()
            TaskCreationDialog(
                onDismiss = { 
                    showCreateDialog = null
                    editingRegistro = null
                },
                onConfirm = { nuevaTarea ->
                    if (editingRegistro != null) {
                        val tareaParaActualizar = nuevaTarea.copy(id = editingRegistro!!.tareaId)
                        
                        val updates = mapOf(
                            "titulo" to tareaParaActualizar.titulo,
                            "tipo" to tareaParaActualizar.tipo.name,
                            "horarioPrevisto" to tareaParaActualizar.horarioInicio,
                            "descripcion" to tareaParaActualizar.descripcion,
                            "frecuencia" to tareaParaActualizar.frecuencia.name
                        )
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("solicitudes").document(solicitud.id)
                            .collection("registros_tareas").document(editingRegistro!!.id)
                            .update(updates)
                        
                        if (tareaParaActualizar.id.isNotEmpty()) {
                            tareasViewModel.agregarTareaAServicio(solicitud.id, tareaParaActualizar)
                        }
                    } else {
                        tareasViewModel.agregarTareaAServicio(solicitud.id, nuevaTarea)
                    }
                    showCreateDialog = null
                    editingRegistro = null
                },
                accentColor = MaterialTheme.colorScheme.primary,
                defaultType = showCreateDialog ?: editingRegistro?.tipo ?: TipoTarea.OTRO,
                initialTarea = initialTarea,
                existingTasks = plantillas
            )
        }
    }
}

@Composable
fun DailyTaskCard(
    registro: RegistroTarea,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelection: () -> Unit,
    onToggleStatus: (Boolean) -> Unit,
    onPostpone: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAsk: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when {
        registro.completada -> Color(0xFF4CAF50)
        registro.postergada -> Color.Red
        else -> MaterialTheme.colorScheme.primary
    }
    val isDark = CuidaleThemeExtras.customColors.isDark
    
    HardShadowPanel(
        backgroundColor = if (isDark) Color(0xFF1E1F22) else Color.White,
        shadowColor = if (isSelected) statusColor.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.05f),
        cornerRadius = 20.dp,
        shadowOffset = 3.dp,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.clickable { 
            if (isSelectionMode) onToggleSelection() else expanded = !expanded 
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        colors = CheckboxDefaults.colors(checkedColor = statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Horario: ${registro.horarioPrevisto}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        if (registro.completada && registro.postergada) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CON RETRASO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFA000),
                                fontWeight = FontWeight.Bold
                            )
                        } else if (registro.postergada) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "POSTERGADA",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (!registro.completada) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PENDIENTE",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!isSelectionMode) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Checkbox(
                            checked = registro.completada,
                            onCheckedChange = onToggleStatus,
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50)),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "INDICACIONES:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                    Text(
                        text = if (registro.descripcion.isNotBlank()) registro.descripcion else "Sin indicaciones registradas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.LightGray else Color.DarkGray
                    )

                    if (registro.postergada && registro.motivoPostergacion.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "POSTERGADA A LAS ${registro.fechaPostergacion?.toDate()?.let { 
                                    java.text.SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(it) 
                                }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red
                            )
                        }
                        Text(
                            text = "Motivo: ${registro.motivoPostergacion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.LightGray else Color.DarkGray
                        )
                    }

                    if (registro.notas.isNotBlank() && !registro.postergada) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NOTAS DE EJECUCIÓN:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = statusColor
                        )
                        Text(
                            text = registro.notas,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.LightGray else Color.DarkGray
                        )
                    }

                    if (registro.completada) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Realizada a las ${registro.fechaCompletado?.toDate()?.let { 
                                    java.text.SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(it) 
                                }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onEdit != null) {
                            TextButton(onClick = onEdit) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("EDITAR")
                            }
                        }
                        if (onPostpone != null && !registro.completada) {
                            TextButton(onClick = onPostpone, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (registro.postergada) "RE-POSTERGAR" else "POSTERGAR")
                            }
                        }
                        if (onAsk != null) {
                            Button(
                                onClick = onAsk,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = statusColor.copy(alpha = 0.1f), contentColor = statusColor)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PREGUNTAR", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}


