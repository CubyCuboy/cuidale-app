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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.R
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
                val frecuenciaText = getFrecuenciaText(tarea)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${tarea.horarioInicio} • $frecuenciaText",
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
        TipoTarea.INYECCION -> Icons.Default.Science
        TipoTarea.CAMBIO_SUERO -> Icons.Default.LocalHospital
        TipoTarea.CAMBIO_POSTURA -> Icons.Default.Accessibility
        TipoTarea.OTRO -> Icons.AutoMirrored.Filled.Assignment
    }
}

@Composable
fun getFrecuenciaText(tarea: Tarea): String {
    return when (tarea.frecuencia) {
        FrecuenciaTarea.DIARIA -> stringResource(R.string.task_frecuencia_diaria)
        FrecuenciaTarea.SEMANAL -> {
            val diasNombres = StringBuilder()
            var primeraDia = true
            for (dia in tarea.diasSeleccionados) {
                if (!primeraDia) diasNombres.append(", ")
                primeraDia = false
                diasNombres.append(dayToName(dia))
            }
            stringResource(R.string.task_frecuencia_semanal, diasNombres.toString())
        }
        FrecuenciaTarea.MENSUAL -> {
            val diasStr = tarea.diasSeleccionados.joinToString()
            val mesesNombres = StringBuilder()
            var primerMes = true
            for (mes in tarea.mesesSeleccionados) {
                if (!primerMes) mesesNombres.append(", ")
                primerMes = false
                mesesNombres.append(monthToName(mes))
            }
            val mesesStr = mesesNombres.toString()
            if (mesesStr.isEmpty()) {
                stringResource(R.string.task_frecuencia_mensual, diasStr)
            } else {
                stringResource(R.string.task_frecuencia_mensual_con_meses, diasStr, mesesStr)
            }
        }
        FrecuenciaTarea.UNA_VEZ -> stringResource(R.string.task_frecuencia_una_vez, tarea.fechaEspecifica.orEmpty())
    }
}

@Composable
fun dayToName(day: Int): String {
    return when (day) {
        1 -> stringResource(R.string.task_dia_lun)
        2 -> stringResource(R.string.task_dia_mar)
        3 -> stringResource(R.string.task_dia_mie)
        4 -> stringResource(R.string.task_dia_jue)
        5 -> stringResource(R.string.task_dia_vie)
        6 -> stringResource(R.string.task_dia_sab)
        7 -> stringResource(R.string.task_dia_dom)
        else -> ""
    }
}

@Composable
fun monthToName(month: Int): String {
    return when (month) {
        1 -> stringResource(R.string.task_mes_ene)
        2 -> stringResource(R.string.task_mes_feb)
        3 -> stringResource(R.string.task_mes_mar)
        4 -> stringResource(R.string.task_mes_abr)
        5 -> stringResource(R.string.task_mes_may)
        6 -> stringResource(R.string.task_mes_jun)
        7 -> stringResource(R.string.task_mes_jul)
        8 -> stringResource(R.string.task_mes_ago)
        9 -> stringResource(R.string.task_mes_sep)
        10 -> stringResource(R.string.task_mes_oct)
        11 -> stringResource(R.string.task_mes_nov)
        12 -> stringResource(R.string.task_mes_dic)
        else -> ""
    }
}

@Composable
fun getTipoTareaLabel(tipo: TipoTarea): String {
    return when (tipo) {
        TipoTarea.COMIDA -> stringResource(R.string.task_tipo_comida)
        TipoTarea.MEDICACION -> stringResource(R.string.task_tipo_medicacion)
        TipoTarea.HIGIENE -> stringResource(R.string.task_tipo_higiene)
        TipoTarea.ACTIVIDAD -> stringResource(R.string.task_tipo_actividad)
        TipoTarea.INYECCION -> stringResource(R.string.task_tipo_inyeccion)
        TipoTarea.CAMBIO_SUERO -> stringResource(R.string.task_tipo_suero)
        TipoTarea.CAMBIO_POSTURA -> stringResource(R.string.task_tipo_postura)
        TipoTarea.OTRO -> stringResource(R.string.task_tipo_otro)
    }
}

@Composable
fun getFrecuenciaOpcionLabel(frecuencia: FrecuenciaTarea): String {
    return when (frecuencia) {
        FrecuenciaTarea.DIARIA -> stringResource(R.string.task_frecuencia_opcion_diaria)
        FrecuenciaTarea.SEMANAL -> stringResource(R.string.task_frecuencia_opcion_semanal)
        FrecuenciaTarea.MENSUAL -> stringResource(R.string.task_frecuencia_opcion_mensual)
        FrecuenciaTarea.UNA_VEZ -> stringResource(R.string.task_frecuencia_opcion_una_vez)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreationScreen(
    onCancel: () -> Unit,
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
    var dosis by remember { mutableStateOf(initialTarea?.dosis ?: "") }
    var unidades by remember { mutableStateOf(initialTarea?.unidades ?: "") }
    var viaAdministracion by remember { mutableStateOf(initialTarea?.viaAdministracion ?: "") }

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
        TipoTarea.MEDICACION -> stringResource(R.string.task_ph_medication_title)
        TipoTarea.COMIDA -> stringResource(R.string.task_ph_food_title)
        TipoTarea.HIGIENE -> stringResource(R.string.task_ph_hygiene_title)
        TipoTarea.ACTIVIDAD -> stringResource(R.string.task_ph_activity_title)
        TipoTarea.INYECCION -> stringResource(R.string.task_ph_inyeccion_title)
        TipoTarea.CAMBIO_SUERO -> stringResource(R.string.task_ph_suero_title)
        TipoTarea.CAMBIO_POSTURA -> stringResource(R.string.task_ph_postura_title)
        TipoTarea.OTRO -> stringResource(R.string.task_ph_other_title)
    }

    val dynamicPlaceholder = when (tipo) {
        TipoTarea.MEDICACION -> stringResource(R.string.task_ph_medication_desc)
        TipoTarea.COMIDA -> stringResource(R.string.task_ph_food_desc)
        TipoTarea.HIGIENE -> stringResource(R.string.task_ph_hygiene_desc)
        TipoTarea.ACTIVIDAD -> stringResource(R.string.task_ph_activity_desc)
        TipoTarea.INYECCION -> stringResource(R.string.task_ph_inyeccion_desc)
        TipoTarea.CAMBIO_SUERO -> stringResource(R.string.task_ph_suero_desc)
        TipoTarea.CAMBIO_POSTURA -> stringResource(R.string.task_ph_postura_desc)
        TipoTarea.OTRO -> stringResource(R.string.task_ph_other_desc)
    }

    val frecuenciaOptions = mutableListOf<String>()
    for (frecuenciaOpcion in FrecuenciaTarea.entries) {
        frecuenciaOptions.add(getFrecuenciaOpcionLabel(frecuenciaOpcion))
    }

    if (showDuplicateAlert) {
        AlertDialog(
            onDismissRequest = { showDuplicateAlert = false },
            title = { Text(stringResource(R.string.task_duplicate_title)) },
            text = { Text(stringResource(R.string.task_duplicate_message, titulo)) },
            confirmButton = {
                TextButton(onClick = { showDuplicateAlert = false }) { Text(stringResource(R.string.action_got_it)) }
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

    val neutralBg = if (isDark) Color(0xFF141519) else Color(0xFFEFF1F4)
    val cardBg = if (isDark) Color(0xFF1E1F22) else Color.White
    val fieldBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFF5F7F9)
    val onCard = if (isDark) Color.White else Color.Black
    val mutedText = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray

    Surface(modifier = Modifier.fillMaxSize(), color = neutralBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                shape = RoundedCornerShape(28.dp),
                color = cardBg,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = getIconForTaskType(tipo),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (initialTarea == null) stringResource(R.string.task_new_title) else stringResource(R.string.task_edit_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = onCard
                            )
                            val hintText = when (tipo) {
                                TipoTarea.MEDICACION -> "Incluye el medicamento y la dosis"
                                TipoTarea.COMIDA -> "Describe la comida o el alimento"
                                TipoTarea.HIGIENE -> "Detalla la rutina de higiene"
                                TipoTarea.ACTIVIDAD -> "Describe la actividad que hay que realizar"
                                TipoTarea.INYECCION -> "Detalla la inyección y cómo aplicarla"
                                TipoTarea.CAMBIO_SUERO -> "Indica el proceso para el cambio de suero"
                                TipoTarea.CAMBIO_POSTURA -> "Describe la postura y la frecuencia"
                                TipoTarea.OTRO -> "Agrega los detalles que consideres útiles"
                            }
                            Text(hintText, style = MaterialTheme.typography.bodySmall, color = mutedText)
                        }
                    }

                    TaskSectionDivider()

                    // Categoría
                    Spacer(Modifier.height(12.dp))
                    InputFieldLabel(text = stringResource(R.string.task_category))
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(TipoTarea.entries) { item ->
                            val selected = tipo == item
                            Surface(
                                onClick = { tipo = item },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) accentColor else fieldBg,
                                border = if (selected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f)),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        getIconForTaskType(item),
                                        null,
                                        tint = if (selected) Color.White else mutedText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        getTipoTareaLabel(item),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else mutedText
                                    )
                                }
                            }
                        }
                    }

                    // Título
                    Spacer(Modifier.height(16.dp))
                    InputFieldLabel(text = stringResource(R.string.task_title))
                    Spacer(Modifier.height(8.dp))
                    CuidaleTextField(
                        value = titulo,
                        onValueChange = { titulo = it },
                        placeholder = titlePlaceholder,
                        containerColor = fieldBg
                    )

                    if (tipo == TipoTarea.MEDICACION || tipo == TipoTarea.INYECCION || tipo == TipoTarea.CAMBIO_SUERO) {
                        TaskSectionDivider()
                        Spacer(Modifier.height(16.dp))
                        InputFieldLabel(text = stringResource(R.string.task_detail_section_title))
                        Spacer(Modifier.height(8.dp))
                        InputFieldLabel(text = stringResource(R.string.task_detail_dosis))
                        Spacer(Modifier.height(8.dp))
                        CuidaleTextField(
                            value = dosis,
                            onValueChange = { dosis = it },
                            placeholder = "Ej: 500 mg",
                            containerColor = fieldBg
                        )
                        Spacer(Modifier.height(16.dp))
                        InputFieldLabel(text = stringResource(R.string.task_detail_unidades))
                        Spacer(Modifier.height(8.dp))
                        CuidaleTextField(
                            value = unidades,
                            onValueChange = { unidades = it },
                            placeholder = "Ej: 2 cápsulas",
                            containerColor = fieldBg
                        )
                        Spacer(Modifier.height(16.dp))
                        InputFieldLabel(text = stringResource(R.string.task_detail_via))
                        Spacer(Modifier.height(8.dp))
                        val viaOptions = listOf("Oral", "Con alimentos", "Antes de comer", "Después de comer", "Subcutánea", "Intramuscular", "Intravenosa", "Tópica", "Inhalada")
                        CuidaleExposedDropdown(
                            label = "",
                            options = viaOptions,
                            selectedOption = if (viaAdministracion in viaOptions) viaAdministracion else viaOptions.first(),
                            onOptionSelected = { viaAdministracion = it }
                        )
                    }

                    // Horario + Frecuencia
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InputFieldLabel(text = stringResource(R.string.task_schedule))
                            Surface(
                                onClick = { showTimePicker = true },
                                color = fieldBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Schedule, null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(horarioStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = onCard)
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1.2f)) {
                            InputFieldLabel(text = stringResource(R.string.task_frequency))
                            CuidaleExposedDropdown(
                                label = "",
                                options = frecuenciaOptions,
                                selectedOption = getFrecuenciaOpcionLabel(frecuencia),
                                onOptionSelected = {
                                    val cleanName = it.replace(" ", "_")
                                    frecuencia = FrecuenciaTarea.valueOf(cleanName)
                                }
                            )
                        }
                    }

                    if (frecuencia == FrecuenciaTarea.SEMANAL) {
                        TaskSectionDivider()
                        Spacer(Modifier.height(16.dp))
                        InputFieldLabel(text = stringResource(R.string.task_repeat_days))
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..7).forEach { day ->
                                val selected = diasSeleccionados.contains(day)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(if (selected) accentColor else fieldBg)
                                        .border(1.dp, if (selected) Color.Transparent else Color.Gray.copy(alpha = 0.15f), CircleShape)
                                        .clickable {
                                            diasSeleccionados = if (selected) diasSeleccionados - day else diasSeleccionados + day
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayToName(day).take(1),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else mutedText
                                    )
                                }
                            }
                        }
                    }

                    if (frecuencia == FrecuenciaTarea.MENSUAL) {
                        TaskSectionDivider()
                        Spacer(Modifier.height(16.dp))
                        val allMonths = (1..12).toList()
                        InputFieldLabel(text = stringResource(R.string.task_months))
                        Spacer(Modifier.height(8.dp))
                        Column {
                            allMonths.chunked(4).forEach { rowMonths ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowMonths.forEach { month ->
                                        val selected = mesesSeleccionados.contains(month)
                                        Surface(
                                            onClick = {
                                                mesesSeleccionados = if (selected) mesesSeleccionados - month else mesesSeleccionados + month
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            color = if (selected) accentColor else fieldBg,
                                            border = BorderStroke(1.dp, if (selected) Color.Transparent else Color.Gray.copy(alpha = 0.15f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    monthToName(month),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) Color.White else mutedText
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        InputFieldLabel(text = stringResource(R.string.task_dias_mensuales))
                        Spacer(Modifier.height(8.dp))
                        Column {
                            (1..31).toList().chunked(7).forEach { rowDays ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowDays.forEach { day ->
                                        val selected = diasSeleccionados.contains(day)
                                        Surface(
                                            onClick = {
                                                diasSeleccionados = if (selected) diasSeleccionados - day else diasSeleccionados + day
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            color = if (selected) accentColor else fieldBg,
                                            border = BorderStroke(1.dp, if (selected) Color.Transparent else Color.Gray.copy(alpha = 0.15f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    day.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selected) Color.White else mutedText
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }

                    // Notas
                    TaskSectionDivider()
                    Spacer(Modifier.height(16.dp))
                    InputFieldLabel(text = stringResource(R.string.task_notes))
                    Spacer(Modifier.height(8.dp))
                    CuidaleTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        placeholder = dynamicPlaceholder,
                        singleLine = false,
                        modifier = Modifier.height(84.dp),
                        containerColor = fieldBg
                    )

                    // Action row
                    TaskSectionDivider()
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) { Text(stringResource(R.string.action_cancel), color = mutedText, fontWeight = FontWeight.Bold) }

                        HardShadowButton(
                            text = stringResource(R.string.task_save),
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
                                        dosis = dosis,
                                        unidades = unidades,
                                        viaAdministracion = viaAdministracion,
                                        activa = true
                                    )
                                    onConfirm(tarea)
                                    onCancel()
                                }
                            },
                            enabled = titulo.isNotBlank(),
                            modifier = Modifier.weight(1.5f).height(52.dp),
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = accentColor.copy(alpha = 0.25f),
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSectionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha = 0.15f)))
}
