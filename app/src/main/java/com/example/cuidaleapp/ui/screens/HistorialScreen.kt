package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.HistorialServicio
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.example.cuidaleapp.utils.DateUtils
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
    
    var sortOrder by remember { mutableStateOf("fecha_desc") }
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedHistorial = remember(historial, sortOrder) {
        when (sortOrder) {
            "fecha_desc" -> historial.sortedByDescending { it.fechaTermino }
            "fecha_asc" -> historial.sortedBy { it.fechaTermino }
            "calificacion" -> historial.sortedByDescending { it.calificacion ?: 0.0 }
            "paciente_alf" -> historial.sortedBy { it.pacienteNombre }
            "aceptacion" -> historial.sortedByDescending { it.aceptadoAt }
            else -> historial
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.historial_borrar_todo_titulo)) },
            text = { Text(stringResource(R.string.historial_borrar_todo_mensaje)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.borrarTodoElHistorial(userData.rol)
                    showDeleteAllDialog = false
                }) { Text(stringResource(R.string.historial_borrar_todo_accion), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.historial_titulo), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, stringResource(R.string.historial_ordenar))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.historial_orden_recientes)) },
                                onClick = { sortOrder = "fecha_desc"; showSortMenu = false },
                                leadingIcon = { Icon(Icons.Default.History, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.historial_orden_antiguos)) },
                                onClick = { sortOrder = "fecha_asc"; showSortMenu = false },
                                leadingIcon = { Icon(Icons.Default.Update, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.historial_orden_calificacion)) },
                                onClick = { sortOrder = "calificacion"; showSortMenu = false },
                                leadingIcon = { Icon(Icons.Default.Star, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.historial_orden_paciente)) },
                                onClick = { sortOrder = "paciente_alf"; showSortMenu = false },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.historial_orden_aceptacion)) },
                                onClick = { sortOrder = "aceptacion"; showSortMenu = false },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                            )
                        }
                    }
                    if (historial.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, stringResource(R.string.historial_borrar_todo), tint = MaterialTheme.colorScheme.error)
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
            if (sortedHistorial.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.historial_vacio), color = Color.White)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(sortedHistorial, key = { it.id }) { item ->
                        HistorialCard(
                            item = item,
                            role = userData.rol,
                            onDelete = { viewModel.eliminarDelHistorial(item.id, userData.rol) },
                            onViewProfile = { uid -> onViewProfile(uid) },
                            onRate = { rating, obs ->
                                viewModel.calificarServicio(item.id, rating, obs)
                            }
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
    onViewProfile: (String) -> Unit,
    onRate: (Double, String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showRatingDialog by remember { mutableStateOf(false) }
    
    if (showRatingDialog) {
        RatingDialog(
            initialRating = item.calificacion ?: 0.0,
            initialObservation = item.observacionCalificacion,
            onDismiss = { showRatingDialog = false },
            onConfirm = { rating, obs ->
                onRate(rating, obs)
                showRatingDialog = false
            }
        )
    }

    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TaskAlt, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val dateTermino = DateUtils.formatDate(item.fechaTermino?.toDate()?.time)
                    val dateInicio = DateUtils.formatDate(item.fechaInicio?.toDate()?.time)
                    
                    Text(stringResource(R.string.historial_periodo_servicio), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(stringResource(R.string.historial_periodo_formato, dateInicio, dateTermino), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = colors.error.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(16.dp))

            CuidaleDetailRow(Icons.Default.FamilyRestroom, stringResource(R.string.historial_paciente), stringResource(R.string.historial_paciente_con_edad, item.pacienteNombre, item.pacienteEdad))
            CuidaleDetailRow(Icons.Default.Person, stringResource(R.string.historial_representante), item.representanteNombre)
            
            if (role == "representante") {
                CuidaleDetailRow(Icons.Default.MedicalServices, stringResource(R.string.historial_cuidador), item.cuidadorNombre)
                CuidaleDetailRow(Icons.Default.School, stringResource(R.string.historial_especialidad), item.cuidadorEspecialidad)
            } else {
                val censoredPhone = if (item.representanteTelefono.length >= 4) {
                    "*******" + item.representanteTelefono.takeLast(4)
                } else if (item.representanteTelefono.isNotEmpty()) {
                    item.representanteTelefono
                } else stringResource(R.string.historial_no_registrado)
                
                if (item.representanteTelefono.isNotEmpty()) {
                    CuidaleDetailRow(Icons.Default.Phone, stringResource(R.string.historial_telefono), censoredPhone)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = colors.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (item.calificacion != null) {
                                    stringResource(R.string.historial_calificacion_formato, item.calificacion.toString())
                                } else {
                                    stringResource(R.string.historial_sin_calificacion)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (role == "representante") {
                            TextButton(onClick = { showRatingDialog = true }) {
                                Text(if (item.calificacion == null) stringResource(R.string.historial_calificar) else stringResource(R.string.historial_editar))
                            }
                        }
                    }
                    if (item.observacionCalificacion.isNotEmpty()) {
                        Text(
                            text = item.observacionCalificacion,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            val buttonText = if (role == "representante") stringResource(R.string.historial_ver_perfil_cuidador) else stringResource(R.string.historial_ver_perfil_representante)
            val targetUid = if (role == "representante") item.cuidadorUid else item.representanteUid
            val buttonColor = if (role == "representante") colors.primary else colors.secondary

            HardShadowButton(
                text = buttonText,
                onClick = { onViewProfile(targetUid) },
                containerColor = buttonColor,
                contentColor = Color.White,
                shadowColor = buttonColor.copy(alpha = 0.2f),
                modifier = Modifier.height(44.dp)
            )
        }
    }
}

@Composable
fun RatingDialog(
    initialRating: Double,
    initialObservation: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var rating by remember { mutableStateOf(initialRating.toFloat()) }
    var observation by remember { mutableStateOf(initialObservation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.historial_dialogo_titulo)) },
        text = {
            Column {
                Text(stringResource(R.string.historial_dialogo_pregunta), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    repeat(5) { index ->
                        val starIndex = index + 1
                        IconButton(onClick = { rating = starIndex.toFloat() }) {
                            Icon(
                                imageVector = if (starIndex <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (starIndex <= rating) Color(0xFFFFB300) else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = observation,
                    onValueChange = { observation = it },
                    label = { Text(stringResource(R.string.historial_dialogo_observaciones)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rating.toDouble(), observation) }) {
                Text(stringResource(R.string.historial_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
