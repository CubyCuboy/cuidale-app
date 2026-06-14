package com.example.cuidaleapp.ui.components

import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.models.FrecuenciaTarea
import com.example.cuidaleapp.models.Tarea
import com.example.cuidaleapp.models.TipoTarea
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import java.util.UUID
import java.util.Locale

@Composable
fun TaskItemCard(
    tarea: Tarea,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val customColors = CuidaleThemeExtras.customColors
    val isDark = customColors.isDark
    val shadowPalette = CuidaleThemeExtras.shadows
    
    HardShadowPanel(
        backgroundColor = if (isDark) customColors.panelBackground.copy(alpha = 0.7f) else Color.White,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
        cornerRadius = 16.dp,
        modifier = Modifier.padding(vertical = 4.dp).clickable { onEdit() },
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getIconForTaskType(tarea.tipo),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tarea.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${tarea.horarioInicio} • ${getFrecuenciaText(tarea)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = accentColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

fun getIconForTaskType(tipo: TipoTarea): ImageVector {
    return when (tipo) {
        TipoTarea.COMIDA -> Icons.Default.Restaurant
        TipoTarea.MEDICACION -> Icons.Default.Medication
        TipoTarea.HIGIENE -> Icons.Default.HotTub
        TipoTarea.ACTIVIDAD -> Icons.AutoMirrored.Filled.DirectionsRun
        TipoTarea.OTRO -> Icons.AutoMirrored.Filled.Assignment
    }
}

fun getFrecuenciaText(tarea: Tarea): String {
    return when (tarea.frecuencia) {
        FrecuenciaTarea.DIARIA -> "Diaria"
        FrecuenciaTarea.SEMANAL -> "Semanal (${tarea.diasSeleccionados.joinToString { dayToName(it) }})"
        FrecuenciaTarea.MENSUAL -> {
            val meses = if (tarea.mesesSeleccionados.isEmpty()) "" else " en ${tarea.mesesSeleccionados.joinToString { monthToName(it) }}"
            "Mensual (Día ${tarea.diasSeleccionados.joinToString()}$meses)"
        }
        FrecuenciaTarea.UNA_VEZ -> "Una vez (${tarea.fechaEspecifica})"
    }
}

fun dayToName(day: Int): String {
    return when (day) {
        1 -> "Lun"
        2 -> "Mar"
        3 -> "Mié"
        4 -> "Jue"
        5 -> "Vie"
        6 -> "Sáb"
        7 -> "Dom"
        else -> ""
    }
}

fun monthToName(month: Int): String {
    return when (month) {
        1 -> "Ene"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Abr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Ago"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dic"
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Tarea) -> Unit,
    accentColor: Color,
    defaultType: TipoTarea = TipoTarea.OTRO,
    initialTarea: Tarea? = null,
    existingTasks: List<Tarea> = emptyList()
) {
    var titulo by remember { mutableStateOf(initialTarea?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(initialTarea?.descripcion ?: "") }
    var tipo by remember { mutableStateOf(initialTarea?.tipo ?: defaultType) }
    var frecuencia by remember { mutableStateOf(initialTarea?.frecuencia ?: FrecuenciaTarea.DIARIA) }
    var diasSeleccionados by remember { mutableStateOf<List<Int>>(initialTarea?.diasSeleccionados ?: emptyList()) }
    var mesesSeleccionados by remember { mutableStateOf<List<Int>>(initialTarea?.mesesSeleccionados ?: emptyList()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDuplicateAlert by remember { mutableStateOf(false) }
    
    val initialTime = initialTarea?.horarioInicio?.split(":") ?: listOf("08", "00")
    val timeState = rememberTimePickerState(
        initialHour = try { initialTime[0].toInt() } catch (e: Exception) { 8 },
        initialMinute = try { initialTime[1].toInt() } catch (e: Exception) { 0 },
        is24Hour = true
    )
    var horarioStr by remember { mutableStateOf(initialTarea?.horarioInicio ?: "08:00") }

    val customColors = CuidaleThemeExtras.customColors
    val isDark = customColors.isDark

    val titlePlaceholder = when (tipo) {
        TipoTarea.MEDICACION -> "Nombre del medicamento"
        TipoTarea.COMIDA -> "Ej: Almuerzo / Merienda"
        TipoTarea.HIGIENE -> "Ej: Baño / Aseo Bucal"
        TipoTarea.ACTIVIDAD -> "Ej: Ejercicios / Paseo"
        TipoTarea.OTRO -> "Ej: Control de Signos"
    }

    val dynamicPlaceholder = when (tipo) {
        TipoTarea.MEDICACION -> "Ej: 500mg, vía oral después de comida"
        TipoTarea.COMIDA -> "Ej: Sin sal, bajo en grasas"
        TipoTarea.HIGIENE -> "Ej: Usar jabón neutro, secar bien pliegues"
        TipoTarea.ACTIVIDAD -> "Ej: 15 minutos en el jardín, usar andador"
        TipoTarea.OTRO -> "Ej: Ventilar habitación, cambiar sábanas"
    }

    if (showDuplicateAlert) {
        AlertDialog(
            onDismissRequest = { showDuplicateAlert = false },
            title = { Text("Tarea Duplicada") },
            text = { Text("Ya existe una tarea llamada '$titulo'. ¿Deseas usar otro nombre o editar la existente?") },
            confirmButton = {
                TextButton(onClick = { showDuplicateAlert = false }) { Text("ENTENDIDO") }
            }
        )
    }

    if (showTimePicker) {
        CuidaleTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                horarioStr = String.format(Locale.getDefault(), "%02d:%02d", timeState.hour, timeState.minute)
                showTimePicker = false
            }
        ) {
            TimeInput(state = timeState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        content = {
            HardShadowPanel(
                backgroundColor = if (isDark) Color(0xFF1E1F22) else Color.White,
                shadowColor = Color.Black.copy(alpha = 0.15f),
                cornerRadius = 28.dp,
                shadowOffset = 4.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = accentColor.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (initialTarea == null) Icons.Default.AddCircle else Icons.Default.Edit,
                                    null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (initialTarea == null) "NUEVA TAREA" else "EDITAR TAREA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color.White else Color.Black
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    InputFieldLabel(text = "CATEGORÍA")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(TipoTarea.entries) { item ->
                            val selected = tipo == item
                            Surface(
                                onClick = { tipo = item },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) accentColor else (if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF5F7F9)),
                                border = if (selected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        getIconForTaskType(item),
                                        null,
                                        tint = if (selected) Color.White else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        item.name.replace("_", " "),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    InputFieldLabel(text = "TÍTULO")
                    CuidaleTextField(
                        value = titulo, 
                        onValueChange = { titulo = it }, 
                        placeholder = titlePlaceholder,
                        containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF5F7F9)
                    )
                    
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InputFieldLabel(text = "HORARIO")
                            Surface(
                                onClick = { showTimePicker = true },
                                color = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF5F7F9),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Schedule, null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(horarioStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1.2f)) {
                            InputFieldLabel(text = "FRECUENCIA")
                            CuidaleExposedDropdown(
                                label = "",
                                options = FrecuenciaTarea.entries.map { it.name.replace("_", " ") },
                                selectedOption = frecuencia.name.replace("_", " "),
                                onOptionSelected = {
                                    val cleanName = it.replace(" ", "_")
                                    frecuencia = FrecuenciaTarea.valueOf(cleanName)
                                }
                            )
                        }
                    }

                    if (frecuencia == FrecuenciaTarea.SEMANAL) {
                        Spacer(Modifier.height(16.dp))
                        InputFieldLabel(text = "REPETIR LOS DÍAS")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            (1..7).forEach { day ->
                                val selected = diasSeleccionados.contains(day)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(if (selected) accentColor else Color.Gray.copy(alpha = 0.1f))
                                        .clickable {
                                            diasSeleccionados = if (selected) diasSeleccionados - day else diasSeleccionados + day
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayToName(day).take(1),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    if (frecuencia == FrecuenciaTarea.MENSUAL) {
                        Spacer(Modifier.height(16.dp))
                        InputFieldLabel(text = "MESES DE EJECUCIÓN")
                        val allMonths = (1..12).toList()
                        Column {
                            allMonths.chunked(4).forEach { rowMonths ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowMonths.forEach { month ->
                                        val selected = mesesSeleccionados.contains(month)
                                        Surface(
                                            onClick = {
                                                mesesSeleccionados = if (selected) mesesSeleccionados - month else mesesSeleccionados + month
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            color = if (selected) accentColor else Color.Gray.copy(alpha = 0.1f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    monthToName(month),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) Color.White else Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            InputFieldLabel(text = "DÍA DEL MES")
                            CuidaleTextField(
                                value = diasSeleccionados.firstOrNull()?.toString() ?: "",
                                onValueChange = { 
                                    val day = it.toIntOrNull()
                                    if (day != null && day in 1..31) {
                                        diasSeleccionados = listOf(day)
                                    } else if (it.isEmpty()) {
                                        diasSeleccionados = emptyList()
                                    }
                                },
                                placeholder = "Ej: 15",
                                containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF5F7F9)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    
                    InputFieldLabel(text = "INDICACIONES ADICIONALES")
                    CuidaleTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        placeholder = dynamicPlaceholder,
                        singleLine = false,
                        modifier = Modifier.height(80.dp),
                        containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF5F7F9)
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) { Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold) }
                        
                        HardShadowButton(
                            text = "GUARDAR TAREA",
                            onClick = {
                                val isDuplicate = existingTasks.any { 
                                    it.titulo.equals(titulo, ignoreCase = true) && it.id != initialTarea?.id 
                                }
                                
                                if (isDuplicate) {
                                    showDuplicateAlert = true
                                } else {
                                    val tarea = Tarea(
                                        id = initialTarea?.id ?: UUID.randomUUID().toString(),
                                        titulo = titulo,
                                        descripcion = descripcion,
                                        tipo = tipo,
                                        frecuencia = frecuencia,
                                        horarioInicio = horarioStr,
                                        diasSeleccionados = diasSeleccionados,
                                        mesesSeleccionados = mesesSeleccionados,
                                        activa = true
                                    )
                                    onConfirm(tarea)
                                    onDismiss()
                                }
                            },
                            enabled = titulo.isNotBlank(),
                            modifier = Modifier.weight(1.5f).height(50.dp),
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = accentColor.copy(alpha = 0.2f),
                            isDark = isDark
                        )
                    }
                }
            }
        }
    )
}
