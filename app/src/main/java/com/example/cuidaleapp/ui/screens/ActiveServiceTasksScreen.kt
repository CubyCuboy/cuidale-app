package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.*
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.TareasViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver
import com.example.cuidaleapp.utils.ErrorCodes
import com.example.cuidaleapp.utils.ErrorMessages
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveServiceTasksScreen(
    solicitud: SolicitudCuidado,
    tareasViewModel: TareasViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String, String, String?) -> Unit
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val tareasHoy by tareasViewModel.tareasHoy.collectAsState()
    val tareasAtrasadas by tareasViewModel.tareasAtrasadas.collectAsState()
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

    val isCreatingTask = showCreateDialog != null || editingRegistro != null

    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    val msgCompletarTodas = stringResource(R.string.tareas_error_completar_todas)
    val estadoCompletada = stringResource(R.string.tareas_estado_completada)
    val estadoPospuesta = stringResource(R.string.tareas_estado_pospuesta)
    val estadoPendiente = stringResource(R.string.tareas_estado_pendiente)
    val shareMensajeTemplate = stringResource(R.string.tareas_share_mensaje)

    var photoEvidenceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var photoEvidenceUrl by remember { mutableStateOf<String?>(null) }
    var photoEvidencePath by remember { mutableStateOf<String?>(null) }
    var uploadingEvidence by remember { mutableStateOf(false) }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoEvidenceUri = uri
            uploadingEvidence = true
            scope.launch {
                val uploaded = tareasViewModel.subirFotoEvidencia(context, uri)
                if (uploaded != null) {
                    photoEvidenceUrl = uploaded.first
                    photoEvidencePath = uploaded.second
                } else {
                    snackbarHostState.showSnackbar(ErrorMessages.resolve(context, ErrorCodes.Storage.UPLOAD_FAILED))
                }
                uploadingEvidence = false
            }
        }
    }
    LaunchedEffect(solicitud.id) {
        tareasViewModel.cargarTareasServicio(solicitud.id)
        tareasViewModel.generarInstanciasParaHoy(solicitud.id, solicitud.pacienteFichaId)
        tareasViewModel.cargarTareasDelDia(solicitud.id)
        tareasViewModel.iniciarMonitoreoTareas(context)
        tareasViewModel.limpiarEvidenciasViejas(solicitud.id)
        tareasViewModel.cargarTareasAtrasadas(solicitud.id)
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
            Column {
                CenterAlignedTopAppBar(
                    title = { 
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when {
                                    isCreatingTask -> (if (editingRegistro != null) stringResource(R.string.task_edit_title) else stringResource(R.string.task_new_title))
                                    isSelectionMode -> stringResource(R.string.tareas_seleccionadas_count, selectedIds.size)
                                    else -> stringResource(R.string.tareas_titulo_seguimiento_diario)
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (isCreatingTask) {
                                Text(
                                    if (editingRegistro != null) "Edita los detalles de la tarea" else "Nueva tarea para ${solicitud.nombrePaciente}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            } else if (!isSelectionMode) {
                                Text(solicitud.nombrePaciente, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            }
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
                        if (isRepresentative && !isSelectionMode && tareasHoy.isNotEmpty() && isOnline && !isCreatingTask) {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Default.Checklist, stringResource(R.string.tareas_cd_seleccionar), tint = Color.White)
                            }
                        }
                        if (isSelectionMode && selectedIds.isNotEmpty() && isOnline) {
                            IconButton(onClick = { showMassConfirmDialog = true }) {
                                Icon(Icons.Default.CheckCircle, stringResource(R.string.tareas_cd_finalizar), tint = Color.White)
                            }
                            IconButton(onClick = { showMassPostponeDialog = true }) {
                                Icon(Icons.Default.Schedule, stringResource(R.string.tareas_cd_postergar), tint = Color.White)
                            }
                            IconButton(onClick = {
                                val selectedRegistros = tareasHoy.filter { selectedIds.contains(it.id) }
                                val tareaIds = selectedRegistros.map { it.tareaId }.distinct()
                                
                                tareasViewModel.eliminarTareasSeleccionadas(solicitud.id, tareaIds)

                                isSelectionMode = false
                                selectedIds.clear()
                            }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.tareas_cd_eliminar), tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        floatingActionButton = {
            val allProcessed = tareasHoy.all { it.completada || it.postergada }
            Column(horizontalAlignment = Alignment.End) {
                if (!isRepresentative && !isSelectionMode && isOnline && !isCreatingTask) {
                    ExtendedFloatingActionButton(
                        onClick = { 
                            if (allProcessed) showSummary = true 
                            else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(msgCompletarTodas)
                                }
                            }
                        },
                        containerColor = if (allProcessed) Color(0xFF4CAF50) else Color.Gray,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.AssignmentTurnedIn, null) },
                        text = { Text(stringResource(R.string.tareas_finalizar_turno)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (isRepresentative && !isSelectionMode && isOnline && !isCreatingTask) {
                    FloatingActionButton(
                        onClick = { showCreateDialog = TipoTarea.OTRO },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.tareas_cd_agregar_tarea))
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



            if (isCreatingTask) {
                val initialTarea = editingRegistro?.let {
                    Tarea(id = it.tareaId, titulo = it.titulo, tipo = it.tipo, horarioInicio = it.horarioPrevisto, descripcion = it.descripcion, frecuencia = it.frecuencia)
                }
                val plantillas by tareasViewModel.tareasPlantilla.collectAsState()
                TaskCreationScreen(
                    onCancel = { showCreateDialog = null; editingRegistro = null },
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
            } else if (!isOnline && tareasHoy.isEmpty()) {
                OfflinePlaceholder(subtitle = stringResource(R.string.tareas_offline_subtitle))
            } else if (tareasHoy.isEmpty()) {
                EmptyPlaceholder(stringResource(R.string.tareas_vacio_hoy))
            } else {
                Column {
                    if (!isOnline) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WifiOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.tareas_offline_banner), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp)
                    ) {
                        val completadas = tareasHoy.filter { it.completada }
                        val pospuestas = tareasHoy.filter { it.postergada && !it.completada }
                        val pendientes = tareasHoy.filter { !it.completada && !it.postergada }

                        if (tareasAtrasadas.isNotEmpty()) {
                            item {
                                TaskExpandableSection(
                                    title = stringResource(R.string.tareas_seccion_atrasadas),
                                    count = tareasAtrasadas.size,
                                    tasks = tareasAtrasadas,
                                    solicitud = solicitud,
                                    currentUserId = currentUserId,
                                    otherUid = otherUid,
                                    isRepresentative = isRepresentative,
                                    isSelectionMode = isSelectionMode,
                                    selectedIds = selectedIds,
                                    isOnline = isOnline,
                                    tareasViewModel = tareasViewModel,
                                    onNavigateToChat = onNavigateToChat,
                                    onConfirmTask = { taskToConfirm = it },
                                    onPostponeTask = { taskToPostpone = it },
                                    onEditTask = { editingRegistro = it },
                                    onToggleSelection = { id ->
                                        if (selectedIds.contains(id)) selectedIds.remove(id)
                                        else selectedIds.add(id)
                                    },
                                    onShareInChat = { reg ->
                                        val estado = when {
                                            reg.completada -> estadoCompletada
                                            reg.postergada -> estadoPospuesta
                                            else -> estadoPendiente
                                        }
                                        val msg = String.format(shareMensajeTemplate, reg.titulo, reg.horarioPrevisto, estado)
                                        val chatId = if (currentUserId < otherUid) "${currentUserId}_$otherUid" else "${otherUid}_$currentUserId"
                                        onNavigateToChat(chatId, otherUid, msg)
                                    }
                                )
                            }
                        }

                        item {
                            TaskExpandableSection(
                                title = stringResource(R.string.chat_task_pending),
                                count = pendientes.size,
                                tasks = pendientes,
                                solicitud = solicitud,
                                currentUserId = currentUserId,
                                otherUid = otherUid,
                                isRepresentative = isRepresentative,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedIds,
                                isOnline = isOnline,
                                tareasViewModel = tareasViewModel,
                                onNavigateToChat = onNavigateToChat,
                                onConfirmTask = { taskToConfirm = it },
                                onPostponeTask = { taskToPostpone = it },
                                onEditTask = { editingRegistro = it },
                                onToggleSelection = { id ->
                                    if (selectedIds.contains(id)) selectedIds.remove(id)
                                    else selectedIds.add(id)
                                },
                                onShareInChat = { reg ->
                                    val estado = when {
                                        reg.completada -> estadoCompletada
                                        reg.postergada -> estadoPospuesta
                                        else -> estadoPendiente
                                    }
                                    val msg = String.format(shareMensajeTemplate, reg.titulo, reg.horarioPrevisto, estado)
                                    val chatId = if (currentUserId < otherUid) "${currentUserId}_$otherUid" else "${otherUid}_$currentUserId"
                                    onNavigateToChat(chatId, otherUid, msg)
                                }
                            )
                        }

                        item {
                            TaskExpandableSection(
                                title = stringResource(R.string.chat_task_completed),
                                count = completadas.size,
                                tasks = completadas,
                                solicitud = solicitud,
                                currentUserId = currentUserId,
                                otherUid = otherUid,
                                isRepresentative = isRepresentative,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedIds,
                                isOnline = isOnline,
                                tareasViewModel = tareasViewModel,
                                onNavigateToChat = onNavigateToChat,
                                onConfirmTask = { taskToConfirm = it },
                                onPostponeTask = { taskToPostpone = it },
                                onEditTask = { editingRegistro = it },
                                onToggleSelection = { id ->
                                    if (selectedIds.contains(id)) selectedIds.remove(id)
                                    else selectedIds.add(id)
                                },
                                onShareInChat = { reg ->
                                    val estado = when {
                                        reg.completada -> estadoCompletada
                                        reg.postergada -> estadoPospuesta
                                        else -> estadoPendiente
                                    }
                                    val msg = String.format(shareMensajeTemplate, reg.titulo, reg.horarioPrevisto, estado)
                                    val chatId = if (currentUserId < otherUid) "${currentUserId}_$otherUid" else "${otherUid}_$currentUserId"
                                    onNavigateToChat(chatId, otherUid, msg)
                                }
                            )
                        }

                        item {
                            TaskExpandableSection(
                                title = stringResource(R.string.chat_task_postponed),
                                count = pospuestas.size,
                                tasks = pospuestas,
                                solicitud = solicitud,
                                currentUserId = currentUserId,
                                otherUid = otherUid,
                                isRepresentative = isRepresentative,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedIds,
                                isOnline = isOnline,
                                tareasViewModel = tareasViewModel,
                                onNavigateToChat = onNavigateToChat,
                                onConfirmTask = { taskToConfirm = it },
                                onPostponeTask = { taskToPostpone = it },
                                onEditTask = { editingRegistro = it },
                                onToggleSelection = { id ->
                                    if (selectedIds.contains(id)) selectedIds.remove(id)
                                    else selectedIds.add(id)
                                },
                                onShareInChat = { reg ->
                                    val estado = when {
                                        reg.completada -> estadoCompletada
                                        reg.postergada -> estadoPospuesta
                                        else -> estadoPendiente
                                    }
                                    val msg = String.format(shareMensajeTemplate, reg.titulo, reg.horarioPrevisto, estado)
                                    val chatId = if (currentUserId < otherUid) "${currentUserId}_$otherUid" else "${otherUid}_$currentUserId"
                                    onNavigateToChat(chatId, otherUid, msg)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showMassConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showMassConfirmDialog = false },
                title = { Text(stringResource(R.string.tareas_confirm_finalizar_titulo)) },
                text = { Text(stringResource(R.string.tareas_confirm_finalizar_mensaje, selectedIds.size)) },
                confirmButton = {
                    Button(onClick = {
                        tareasViewModel.completarMultiplesTareas(solicitud.id, selectedIds.toList(), currentUserId, otherUid)
                        isSelectionMode = false
                        selectedIds.clear()
                        showMassConfirmDialog = false
                    }) { Text(stringResource(R.string.tareas_confirm_si_finalizar)) }
                },
                dismissButton = {
                    TextButton(onClick = { showMassConfirmDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }

        if (showMassPostponeDialog) {
            var massPostponeReason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showMassPostponeDialog = false },
                title = { Text(stringResource(R.string.tareas_postergar_seleccion_titulo)) },
                text = {
                    Column {
                        Text(stringResource(R.string.tareas_postergar_seleccion_mensaje, selectedIds.size))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = massPostponeReason,
                            onValueChange = { massPostponeReason = it },
                            placeholder = { Text(stringResource(R.string.tareas_postergar_placeholder_masiva)) },
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
                    ) { Text(stringResource(R.string.tareas_postergar_todas)) }
                },
                dismissButton = {
                    TextButton(onClick = { showMassPostponeDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }

        if (taskToConfirm != null) {
            AlertDialog(
                onDismissRequest = {
                    taskToConfirm = null
                    photoEvidenceUri = null
                    photoEvidenceUrl = null
                    photoEvidencePath = null
                },
                title = { Text(stringResource(R.string.tareas_finalizar_tarea_titulo)) },
                text = {
                    Column {
                        Text(stringResource(R.string.tareas_finalizar_tarea_mensaje, taskToConfirm?.titulo ?: ""))
                        if (!isRepresentative) {
                            Spacer(Modifier.height(12.dp))
                            if (taskToConfirm?.exigirFoto == true && photoEvidenceUrl == null) {
                                Surface(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.tareas_requiere_foto),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            if (photoEvidenceUrl != null) {
                                coil3.compose.AsyncImage(
                                    model = photoEvidenceUrl,
                                    contentDescription = stringResource(R.string.tareas_cd_evidencia_fotografica),
                                    modifier = Modifier.fillMaxWidth().height(140.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(8.dp))
                            } else if (photoEvidenceUri != null && uploadingEvidence) {
                                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            Button(
                                onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = !uploadingEvidence,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (photoEvidenceUrl == null) stringResource(R.string.tareas_adjuntar_foto) else stringResource(R.string.tareas_cambiar_foto), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            taskToConfirm?.let {
                                tareasViewModel.marcarTareaCompletada(
                                    solicitud.id,
                                    it.id,
                                    true,
                                    currentUserId,
                                    titulo = it.titulo,
                                    otherUid = otherUid,
                                    fotoUrl = photoEvidenceUrl.orEmpty(),
                                    fotoStoragePath = photoEvidencePath.orEmpty(),
                                    exigirFoto = it.exigirFoto
                                )
                            }
                            taskToConfirm = null
                            photoEvidenceUri = null
                            photoEvidenceUrl = null
                            photoEvidencePath = null
                        },
                        enabled = !uploadingEvidence
                    ) { Text(stringResource(R.string.tareas_confirm_si_finalizar)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        taskToConfirm = null
                        photoEvidenceUri = null
                        photoEvidenceUrl = null
                        photoEvidencePath = null
                    }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }

        if (taskToPostpone != null) {
            AlertDialog(
                onDismissRequest = { taskToPostpone = null },
                title = { Text(stringResource(R.string.tareas_postergar_tarea_titulo)) },
                text = {
                    Column {
                        Text(stringResource(R.string.tareas_postergar_tarea_mensaje))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = postponeReason,
                            onValueChange = { postponeReason = it },
                            placeholder = { Text(stringResource(R.string.tareas_postergar_placeholder_ejemplo)) },
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
                    ) { Text(stringResource(R.string.tareas_postergar)) }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        taskToPostpone = null
                        postponeReason = ""
                    }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }

        if (showSummary) {
            var observaciones by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSummary = false },
                title = { Text(stringResource(R.string.tareas_resumen_turno_titulo)) },
                text = {
                    Column {
                        val completadas = tareasHoy.count { it.completada }
                        val total = tareasHoy.size
                        Text(stringResource(R.string.tareas_resumen_realizadas, completadas, total), fontWeight = FontWeight.Bold)
                        
                        if (tareasHoy.any { it.postergada }) {
                            Text(stringResource(R.string.tareas_resumen_postergadas, tareasHoy.count { it.postergada }), color = Color.Red)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.tareas_resumen_observaciones))
                        OutlinedTextField(
                            value = observaciones,
                            onValueChange = { observaciones = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        tareasViewModel.generarInformeFinal(solicitud.id, currentUserId, observaciones, otherUid) {
                            showSummary = false
                            onNavigateBack()
                        }
                    }) { Text(stringResource(R.string.tareas_enviar_y_terminar)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSummary = false }) { Text(stringResource(R.string.action_back)) }
                }
            )
        }

    }
}

@Composable
fun TaskExpandableSection(
    title: String,
    count: Int,
    tasks: List<RegistroTarea>,
    solicitud: SolicitudCuidado,
    currentUserId: String,
    otherUid: String,
    isRepresentative: Boolean,
    isSelectionMode: Boolean,
    selectedIds: List<String>,
    isOnline: Boolean,
    tareasViewModel: TareasViewModel,
    onNavigateToChat: (String, String, String?) -> Unit,
    onConfirmTask: (RegistroTarea) -> Unit,
    onPostponeTask: (RegistroTarea) -> Unit,
    onEditTask: (RegistroTarea) -> Unit,
    onToggleSelection: (String) -> Unit = {},
    onShareInChat: (RegistroTarea) -> Unit = {}
) {
    val fallbackTarea = stringResource(R.string.tareas_fallback_tarea)
    var expanded by remember { mutableStateOf(title == "PENDIENTES") }
    val sectionColor = when(title) {
        "COMPLETADAS" -> Color(0xFF4CAF50)
        "POSPUESTAS" -> Color.Red
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color.White
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = sectionColor,
                    shape = CircleShape
                ) {
                    Text(
                        count.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tasks.isEmpty()) {
                    Text(
                        stringResource(R.string.tareas_seccion_vacia),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else {
                    tasks.forEach { registro ->
                        DailyTaskCard(
                            registro = registro,
                            isSelected = selectedIds.contains(registro.id),
                            isSelectionMode = isSelectionMode,
                            isOnline = isOnline,
                            onToggleSelection = { onToggleSelection(registro.id) },
                            onToggleStatus = { completed ->
                                if (isOnline) {
                                    if (completed) onConfirmTask(registro)
                                    else tareasViewModel.marcarTareaCompletada(solicitud.id, registro.id, false, currentUserId)
                                }
                            },
                            onPostpone = if (!isRepresentative && isOnline) { { onPostponeTask(registro) } } else null,
                            onEdit = if (isRepresentative && isOnline) { 
                                if (registro.completada) null else { { onEditTask(registro) } }
                            } else null,
                            onAsk = {
                                if (otherUid.isNotBlank()) {
                                    tareasViewModel.enviarConsultaTarea(
                                        myUid = currentUserId,
                                        otherUid = otherUid,
                                        titulo = registro.titulo.ifBlank { fallbackTarea },
                                        horario = registro.horarioPrevisto,
                                        descripcion = registro.descripcion,
                                        solicitudId = solicitud.id,
                                        registroId = registro.id
                                    )
                                    val chatId = if (currentUserId < otherUid) "${currentUserId}_$otherUid" else "${otherUid}_$currentUserId"
                                    onNavigateToChat(chatId, otherUid, null)
                                }
                            },
                            onShareInChat = { onShareInChat(registro) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyTaskCard(
    registro: RegistroTarea,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isOnline: Boolean = true,
    onToggleSelection: () -> Unit,
    onToggleStatus: (Boolean) -> Unit,
    onPostpone: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAsk: (() -> Unit)? = null,
    onShareInChat: (() -> Unit)? = null
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
                            text = stringResource(R.string.tareas_horario, registro.horarioPrevisto),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        if (registro.completada && registro.postergada) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.tareas_estado_con_retraso),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFA000),
                                fontWeight = FontWeight.Bold
                            )
                        } else if (registro.postergada) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.tareas_estado_postergada),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (!registro.completada) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.tareas_estado_pendiente),
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
                            onCheckedChange = if (isOnline) onToggleStatus else null,
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50)),
                            enabled = isOnline,
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
                        text = stringResource(R.string.tareas_indicaciones),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                    Text(
                        text = if (registro.descripcion.isNotBlank()) registro.descripcion else stringResource(R.string.tareas_sin_indicaciones),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.LightGray else Color.DarkGray
                    )

                    if (registro.postergada && registro.motivoPostergacion.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.tareas_postergada_a_las, registro.fechaPostergacion?.toDate()?.let {
                                    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                                } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red
                            )
                        }
                        Text(
                            text = stringResource(R.string.tareas_motivo, registro.motivoPostergacion),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.LightGray else Color.DarkGray
                        )
                    }

                    if (registro.notas.isNotBlank() && !registro.postergada) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (registro.completada) stringResource(R.string.tareas_descripcion_realizado) else stringResource(R.string.tareas_notas_ejecucion),
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
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.tareas_realizada_a_las, registro.fechaCompletado?.toDate()?.let {
                                    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                                } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (registro.fotoUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.tareas_evidencia_fotografica),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AsyncImage(
                            model = registro.fotoUrl,
                            contentDescription = stringResource(R.string.tareas_cd_foto_prueba),
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
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
                                Text(stringResource(R.string.tareas_editar))
                            }
                        }
                        if (onPostpone != null && !registro.completada) {
                            TextButton(onClick = onPostpone, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (registro.postergada) stringResource(R.string.tareas_re_postergar) else stringResource(R.string.tareas_postergar))
                            }
                        }
                        if (onAsk != null) {
                            Button(
                                onClick = onAsk,
                                enabled = isOnline,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = statusColor.copy(alpha = 0.1f), contentColor = statusColor)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.tareas_preguntar), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onShareInChat != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onShareInChat,
                                modifier = Modifier.background(statusColor.copy(alpha = 0.1f), CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Share, null, tint = statusColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
