package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.example.cuidaleapp.utils.DateUtils
import com.example.cuidaleapp.models.SolicitudCuidado
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSolicitudScreen(
    requestId: String? = null,
    viewModel: SolicitudViewModel,
    fichaViewModel: FichaViewModel,
    onNavigateBack: () -> Unit
) {
    val fichaState by fichaViewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var hoursStr by remember { mutableStateOf("") }
    var selectedPeriods by remember { mutableStateOf(setOf<String>()) }
    var esIndefinido by remember { mutableStateOf(true) }
    
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    
    val startDateState = rememberDatePickerState()
    val endDateState = rememberDatePickerState()
    
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    
    // Time Pickers
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val startTimeState = rememberTimePickerState(initialHour = 9, initialMinute = 0)
    val endTimeState = rememberTimePickerState(initialHour = 18, initialMinute = 0)
    var startHourStr by remember { mutableStateOf("") }
    var endHourStr by remember { mutableStateOf("") }

    var isLoadingExisting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val periods = listOf("Mañana", "Tarde", "Noche")

    // Update hoursStr and detected periods when time components change
    LaunchedEffect(startHourStr, endHourStr) {
        if (startHourStr.isNotEmpty() && endHourStr.isNotEmpty()) {
            hoursStr = "De $startHourStr a $endHourStr"
            selectedPeriods = detectPeriodsFromHours(startHourStr, endHourStr)
        }
    }

    // Load existing request if editing
    LaunchedEffect(requestId) {
        if (requestId != null) {
            isLoadingExisting = true
            try {
                val doc = FirebaseFirestore.getInstance().collection("solicitudes").document(requestId).get().await()
                val sol = doc.toObject(SolicitudCuidado::class.java)
                if (sol != null) {
                    hoursStr = sol.horarioStr
                    selectedPeriods = sol.periodos.toSet()
                    esIndefinido = sol.esIndefinido
                    startDate = sol.fechaInicio?.toDate()?.time
                    endDate = sol.fechaFin?.toDate()?.time
                    
                    try {
                        val parts = sol.horarioStr.lowercase().split(" a ")
                        if (parts.size == 2) {
                            startHourStr = parts[0].replace("de ", "").trim()
                            endHourStr = parts[1].trim()
                        }
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Error al cargar datos: ${e.message}") }
            } finally {
                isLoadingExisting = false
            }
        }
    }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = startDateState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("ACEPTAR") }
            }
        ) { DatePicker(state = startDateState) }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = endDateState.selectedDateMillis
                    showEndDatePicker = false
                }) { Text("ACEPTAR") }
            }
        ) { DatePicker(state = endDateState) }
    }

    if (showStartTimePicker) {
        CuidaleTimePickerDialog(
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startHourStr = String.format("%02d:%02d", startTimeState.hour, startTimeState.minute)
                showStartTimePicker = false
            }
        ) { TimePicker(state = startTimeState) }
    }

    if (showEndTimePicker) {
        CuidaleTimePickerDialog(
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endHourStr = String.format("%02d:%02d", endTimeState.hour, endTimeState.minute)
                showEndTimePicker = false
            }
        ) { TimePicker(state = endTimeState) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (requestId == null) "CREAR SOLICITUD" else "EDITAR SOLICITUD", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoadingExisting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val fs = fichaState) {
                    is FichaState.Loading -> CircularProgressIndicator()
                    is FichaState.View -> {
                        val patientName = fs.data["nombre_completo"]?.toString() ?: "Mi Paciente"
                        val patientPhoto = (fs.data["fotos"] as? List<*>)?.firstOrNull()?.toString() ?: ""

                        Text(
                            "Solicitud para: $patientName",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        // Duration
                        InputFieldLabel("DURACIÓN DEL SERVICIO")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = esIndefinido, onCheckedChange = { esIndefinido = it })
                            Text("Indefinido", style = MaterialTheme.typography.bodyLarge)
                        }
                        
                        if (!esIndefinido) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedCard(
                                    onClick = { showStartDatePicker = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("INICIO", style = MaterialTheme.typography.labelSmall)
                                        Text(DateUtils.formatDate(startDate), fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedCard(
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("FIN", style = MaterialTheme.typography.labelSmall)
                                        Text(DateUtils.formatDate(endDate), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Hours Selection
                        InputFieldLabel("HORARIO DEL SERVICIO")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedCard(
                                onClick = { showStartTimePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("DESDE", style = MaterialTheme.typography.labelSmall)
                                    Text(startHourStr.ifEmpty { "00:00" }, fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedCard(
                                onClick = { showEndTimePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("HASTA", style = MaterialTheme.typography.labelSmall)
                                    Text(endHourStr.ifEmpty { "23:59" }, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (hoursStr.isNotEmpty()) {
                            Text(
                                text = hoursStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            periods.forEach { p ->
                                FilterChip(
                                    selected = selectedPeriods.contains(p),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val newSet = if (selectedPeriods.contains(p)) selectedPeriods - p else selectedPeriods + p
                                        selectedPeriods = newSet
                                        
                                        if (newSet.isNotEmpty()) {
                                            if (newSet.contains("Mañana") && newSet.contains("Tarde") && newSet.contains("Noche")) {
                                                startHourStr = "00:00"; endHourStr = "23:59"
                                            } else if (newSet.contains("Mañana") && newSet.contains("Tarde")) {
                                                startHourStr = "05:00"; endHourStr = "19:59"
                                            } else if (newSet.contains("Tarde") && newSet.contains("Noche")) {
                                                startHourStr = "12:00"; endHourStr = "04:59"
                                            } else if (newSet.contains("Mañana") && newSet.contains("Noche")) {
                                                startHourStr = "20:00"; endHourStr = "11:59"
                                            } else if (newSet.contains("Mañana")) {
                                                startHourStr = "05:00"; endHourStr = "11:59"
                                            } else if (newSet.contains("Tarde")) {
                                                startHourStr = "12:00"; endHourStr = "19:59"
                                            } else if (newSet.contains("Noche")) {
                                                startHourStr = "20:00"; endHourStr = "04:59"
                                            }
                                        }
                                    },
                                    label = { Text(p, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        val missingFields = mutableListOf<String>()
                        if (startHourStr.isEmpty() || endHourStr.isEmpty()) missingFields.add("Horario")
                        if (selectedPeriods.isEmpty()) missingFields.add("Etiquetas")
                        if (!esIndefinido && (startDate == null || endDate == null)) missingFields.add("Fechas")

                        if (missingFields.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Falta completar: ${missingFields.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        val canCreate = missingFields.isEmpty() && !isSaving

                        HardShadowButton(
                            text = if (isSaving) "PROCESANDO..." else if (requestId == null) "CREAR SOLICITUD" else "GUARDAR CAMBIOS",
                            containerColor = if (canCreate) MaterialTheme.colorScheme.primary else Color.Gray,
                            contentColor = Color.White,
                            shadowColor = if (canCreate) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                            enabled = canCreate,
                            onClick = {
                                isSaving = true
                                if (requestId == null) {
                                    viewModel.crearSolicitud(
                                        pacienteFichaId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
                                        nombrePaciente = patientName,
                                        fotoPaciente = patientPhoto,
                                        fechaInicio = startDate?.let { Timestamp(Date(it)) },
                                        fechaFin = endDate?.let { Timestamp(Date(it)) },
                                        esIndefinido = esIndefinido,
                                        horarioStr = hoursStr,
                                        periodos = selectedPeriods.toList(),
                                        onSuccess = {
                                            isSaving = false
                                            onNavigateBack()
                                        },
                                        onError = { error ->
                                            isSaving = false
                                            scope.launch { snackbarHostState.showSnackbar("Error: $error") }
                                        }
                                    )
                                } else {
                                    FirebaseFirestore.getInstance().collection("solicitudes").document(requestId).update(
                                        mapOf(
                                            "horarioStr" to hoursStr,
                                            "periodos" to selectedPeriods.toList(),
                                            "esIndefinido" to esIndefinido,
                                            "fechaInicio" to startDate?.let { Timestamp(Date(it)) },
                                            "fechaFin" to endDate?.let { Timestamp(Date(it)) }
                                        )
                                    ).addOnSuccessListener { 
                                        isSaving = false
                                        onNavigateBack() 
                                    }.addOnFailureListener { e ->
                                        isSaving = false
                                        scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") }
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(onClick = onNavigateBack, enabled = !isSaving) {
                            Text("CANCELAR", color = Color.Gray)
                        }
                    }
                    is FichaState.NotFound -> {
                        Text("Primero debes completar tu ficha de familiar/paciente.", textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text("REGRESAR") }
                    }
                    else -> { }
                }
            }
        }
    }
}



fun detectPeriodsFromHours(start: String, end: String): Set<String> {
    val set = mutableSetOf<String>()
    try {
        val sH = start.split(":")[0].toInt()
        val eH = end.split(":")[0].toInt()
        
        fun hourInPeriod(h: Int, period: String): Boolean {
            return when(period) {
                "Mañana" -> h in 5..11
                "Tarde" -> h in 12..19
                "Noche" -> h >= 20 || h < 5
                else -> false
            }
        }

        val hoursToCheck = mutableListOf<Int>()
        if (sH <= eH) {
            for (i in sH..eH) hoursToCheck.add(i)
        } else {
            for (i in sH..23) hoursToCheck.add(i)
            for (i in 0..eH) hoursToCheck.add(i)
        }

        for (h in hoursToCheck) {
            if (hourInPeriod(h, "Mañana")) set.add("Mañana")
            if (hourInPeriod(h, "Tarde")) set.add("Tarde")
            if (hourInPeriod(h, "Noche")) set.add("Noche")
        }

    } catch (e: Exception) { }
    return set
}
