package com.example.cuidaleapp.ui.components

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
        FrecuenciaTarea.MENSUAL -> "Mensual (Día ${tarea.diasSeleccionados.joinToString()})"
        FrecuenciaTarea.UNA_VEZ -> "Una vez (${tarea.fechaEspecifica})"
        FrecuenciaTarea.INDEFINIDA -> "Indefinida"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Tarea) -> Unit,
    accentColor: Color,
    defaultType: TipoTarea = TipoTarea.OTRO,
    initialTarea: Tarea? = null
) {
    var titulo by remember { mutableStateOf(initialTarea?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(initialTarea?.descripcion ?: "") }
    var tipo by remember { mutableStateOf(initialTarea?.tipo ?: defaultType) }
    var frecuencia by remember { mutableStateOf(initialTarea?.frecuencia ?: FrecuenciaTarea.INDEFINIDA) }
    var diasSeleccionados by remember { mutableStateOf<List<Int>>(initialTarea?.diasSeleccionados ?: emptyList()) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val initialTime = initialTarea?.horarioInicio?.split(":") ?: listOf("08", "00")
    val timeState = rememberTimePickerState(
        initialHour = initialTime[0].toInt(),
        initialMinute = initialTime[1].toInt(),
        is24Hour = true
    )
    var horarioStr by remember { mutableStateOf(initialTarea?.horarioInicio ?: "08:00") }

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
        title = { Text(if (initialTarea == null) "Nueva Tarea" else "Editar Tarea", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column {
                    InputFieldLabel(text = "TÍTULO")
                    CuidaleTextField(
                        value = titulo, 
                        onValueChange = { titulo = it }, 
                        placeholder = if (tipo == TipoTarea.MEDICACION) "Nombre del medicamento" else "Título de la tarea"
                    )
                }
                
                Column {
                    InputFieldLabel(text = "DESCRIPCIÓN / INDICACIONES")
                    CuidaleTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        placeholder = if (tipo == TipoTarea.MEDICACION) "Dosis o indicaciones" else "Indicaciones adicionales...",
                        singleLine = false,
                        modifier = Modifier.height(80.dp)
                    )
                }

                CuidaleExposedDropdown(
                    label = "TIPO DE TAREA",
                    options = TipoTarea.entries.map { it.name },
                    selectedOption = tipo.name,
                    onOptionSelected = { tipo = TipoTarea.valueOf(it) }
                )

                Column {
                    InputFieldLabel(text = "HORARIO")
                    Surface(
                        onClick = { showTimePicker = true },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, null, tint = accentColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(horarioStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Cambiar", style = MaterialTheme.typography.labelSmall, color = accentColor)
                        }
                    }
                }

                CuidaleExposedDropdown(
                    label = "FRECUENCIA",
                    options = FrecuenciaTarea.entries.map { it.name },
                    selectedOption = frecuencia.name,
                    onOptionSelected = { frecuencia = FrecuenciaTarea.valueOf(it) }
                )

                if (frecuencia == FrecuenciaTarea.SEMANAL) {
                    Column {
                        InputFieldLabel(text = "DÍAS DE LA SEMANA")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items((1..7).toList()) { day ->
                                val selected = diasSeleccionados.contains(day)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        diasSeleccionados = if (selected) diasSeleccionados - day else diasSeleccionados + day
                                    },
                                    label = { Text(dayToName(day)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tarea = Tarea(
                        id = initialTarea?.id ?: UUID.randomUUID().toString(),
                        titulo = titulo,
                        descripcion = descripcion,
                        tipo = tipo,
                        frecuencia = frecuencia,
                        horarioInicio = horarioStr,
                        diasSeleccionados = diasSeleccionados
                    )
                    onConfirm(tarea)
                    onDismiss()
                },
                enabled = titulo.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) { Text("GUARDAR", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("CANCELAR", color = Color.Gray) 
            } 
        }
    )
}
