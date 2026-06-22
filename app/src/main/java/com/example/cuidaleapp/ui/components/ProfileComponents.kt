package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.Tarea
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@Composable
fun ProfileInfoRow(icon: ImageVector, label: String, value: String, isPublic: Boolean = true, showVisibilityIcon: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
        }
        if (showVisibilityIcon) {
            Icon(imageVector = if (isPublic) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = if (isPublic) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ProfileDetailView(
    role: String,
    data: Map<String, Any>,
    isEditMode: Boolean = false,
    isSummary: Boolean = false,
    isExternal: Boolean = false,
    showEditButton: Boolean = !isExternal,
    tareasPlantilla: List<Tarea> = emptyList(),
    onEdit: () -> Unit,
    onEditTasks: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    isBlurry: Boolean = false
) {
    if (isSummary && onExpand != null) {
        FichaSummaryCard(
            role = role,
            data = data,
            onExpand = onExpand,
            onEdit = onEdit
        )
    } else {
        FullProfileDetailView(role, data, isEditMode, isExternal, showEditButton, tareasPlantilla, onEdit, onEditTasks, onExpand, isBlurry)
    }
}

@Composable
fun FichaSummaryCard(
    role: String,
    data: Map<String, Any>,
    onExpand: () -> Unit,
    onEdit: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val accentColor = if (role == "cuidador") MaterialTheme.colorScheme.primary else customColors.successStrong
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val panelBg = customColors.panelBackground

    HardShadowPanel(
        backgroundColor = panelBg,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
        cornerRadius = 24.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (role == "cuidador") "MI EXPERIENCIA" else "DATOS DEL PACIENTE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (role == "representante" || data["tipo"] == "familiar") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = accentColor)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = data["nombre_completo"]?.toString() ?: "No especificado",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = "${data["edad"] ?: "--"} años • ${data["genero"] ?: "No especificado"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(12.dp))
            }

            val preferences = if (role == "cuidador") {
                Pair(
                    (data["comunas"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    (data["horarios"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            } else {
                Pair(
                    (data["comunas_preferencia"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    (data["horarios_preferencia"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            }

            SummaryItem(
                icon = Icons.Default.Place,
                label = if (role == "cuidador") "Trabaja en" else "Busca en",
                value = preferences.first.joinToString(", ").ifEmpty { "No especificado" },
                accentColor = accentColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor
            )

            SummaryItem(
                icon = Icons.Default.Schedule,
                label = "Disponibilidad",
                value = preferences.second.joinToString(", ").ifEmpty { "No especificado" },
                accentColor = accentColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor
            )

            if (role == "cuidador") {
                SummaryItem(
                    icon = Icons.Default.School,
                    label = "Especialidad",
                    value = data["especializacion"]?.toString() ?: "No especificado",
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            } else {
                SummaryItem(
                    icon = Icons.Default.Accessibility,
                    label = "Dependencia",
                    value = data["nivel_dependencia"]?.toString() ?: "No especificado",
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "VER FICHA COMPLETA", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = accentColor
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = accentColor, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun SummaryItem(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = secondaryTextColor.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = secondaryTextColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FullProfileDetailView(
    role: String,
    data: Map<String, Any>,
    isEditMode: Boolean = false,
    isExternal: Boolean = false,
    showEditButton: Boolean = !isExternal,
    tareasPlantilla: List<Tarea> = emptyList(),
    onEdit: () -> Unit,
    onEditTasks: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    isBlurry: Boolean = false
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    
    val accentColor = if (role == "cuidador") MaterialTheme.colorScheme.primary else customColors.successStrong
    val panelBg = customColors.panelBackground
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        HardShadowPanel(
            backgroundColor = panelBg,
            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETALLES DE FICHA",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accentColor
                    )
                    Row {
                        if (onExpand != null) {
                            TextButton(onClick = onExpand) {
                                Text("Ver menos", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!isEditMode && showEditButton) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.background(accentColor.copy(alpha = 0.15f), CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = textColor.copy(alpha = if (customColors.isDark) 0.2f else 0.08f))
                
                val excludedKeys = mutableSetOf("fotos", "tipo", "ultima_actualizacion", "horarios", "comunas", "horarios_preferencia", "comunas_preferencia", "dieta", "medicamentos")
                if (isExternal) {
                    excludedKeys.add("calle")
                    excludedKeys.add("numero")
                    excludedKeys.add("depto")
                }

                val preferredOrder = if (role == "cuidador") {
                    listOf("especializacion", "experiencia", "comunas", "descripcion")
                } else {
                    listOf(
                        "nombre_completo", "edad", "genero",
                        "nivel_dependencia", "movilidad", "comunicacion", 
                        "enfermedades", "discapacidades",
                        "region", "comuna", "calle", "numero", "depto",
                        "condiciones"
                    )
                }

                val dataToShow = data.filterKeys { it !in excludedKeys }
                
                if (role == "cuidador") {
                    val sortedKeys = dataToShow.keys.sortedBy { key ->
                        val index = preferredOrder.indexOf(key)
                        if (index == -1) Int.MAX_VALUE else index
                    }
                    sortedKeys.forEach { key ->
                        ProfileDataItem(key, dataToShow[key]!!, accentColor, textColor, secondaryTextColor, customColors.isDark)
                    }
                } else {
                    ProfileSectionHeader("IDENTIFICACIÓN", accentColor)
                    
                    val patientFoto = data["fotoUrl"]?.toString() ?: ""
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, accentColor.copy(alpha = 0.5f)),
                            color = customColors.avatarBackground
                        ) {
                            if (patientFoto.isNotEmpty()) {
                                AsyncImage(
                                    model = patientFoto,
                                    contentDescription = "Paciente",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .blur(if (isBlurry) 25.dp else 0.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person, 
                                    null, 
                                    modifier = Modifier.padding(24.dp), 
                                    tint = secondaryTextColor.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    listOf("nombre_completo", "edad", "genero").forEach { key ->
                        dataToShow[key]?.let { ProfileDataItem(key, it, accentColor, textColor, secondaryTextColor, customColors.isDark) }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileSectionHeader("SALUD Y DEPENDENCIA", accentColor)
                    listOf("nivel_dependencia", "movilidad", "comunicacion", "enfermedades", "discapacidades").forEach { key ->
                        dataToShow[key]?.let { ProfileDataItem(key, it, accentColor, textColor, secondaryTextColor, customColors.isDark) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileSectionHeader("UBICACIÓN", accentColor)
                    listOf("region", "comuna", "calle", "numero", "depto").forEach { key ->
                        dataToShow[key]?.let { ProfileDataItem(key, it, accentColor, textColor, secondaryTextColor, customColors.isDark) }
                    }

                    if (dataToShow.containsKey("condiciones")) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileSectionHeader("OBSERVACIONES ADICIONALES", accentColor)
                        ProfileDataItem("condiciones", dataToShow["condiciones"]!!, accentColor, textColor, secondaryTextColor, customColors.isDark)
                    }
                }

                if (tareasPlantilla.isNotEmpty() || !isExternal) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(textColor.copy(alpha = if (customColors.isDark) 0.08f else 0.03f), RoundedCornerShape(12.dp))
                            .border(1.dp, textColor.copy(alpha = if (customColors.isDark) 0.15f else 0.06f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RÉGIMEN DE TAREAS",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = secondaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isEditMode && showEditButton) {
                                IconButton(onClick = { if (onEditTasks != null) onEditTasks() else onEdit() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, "Editar Tareas", tint = accentColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (tareasPlantilla.isEmpty()) {
                            Text(
                                "No hay tareas programadas aún.",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        } else {
                            tareasPlantilla.forEach { tarea ->
                                TaskItemCardInView(tarea, accentColor, textColor, secondaryTextColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItemCardInView(tarea: Tarea, accentColor: Color, textColor: Color, secondaryTextColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accentColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getIconForTaskType(tarea.tipo),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(tarea.titulo, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
            Text("${tarea.horarioInicio} — ${getFrecuenciaText(tarea)}", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
        }
    }
}

@Composable
fun ProfileSectionHeader(title: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(4.dp).height(16.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
            color = color
        )
    }
}

@Composable
fun ProfileDataItem(
    key: String,
    value: Any,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isDark: Boolean
) {
    val label = when(key) {
        "especializacion" -> "Especialidad o formación"
        "experiencia" -> "Experiencia previa"
        "comunas" -> "Comunas de preferencia"
        "descripcion" -> "Descripción sobre mí"
        "nombre_completo" -> "Nombre del paciente"
        "edad" -> "Edad"
        "genero" -> "Género"
        "vives_con_familiar" -> "Vive con el solicitante"
        "region" -> "Región"
        "comuna" -> "Comuna"
        "calle" -> "Calle / Pasaje"
        "numero" -> "Número"
        "depto" -> "Depto / Block"
        "comunas_preferencia" -> "Comunas de interés"
        "horarios_preferencia" -> "Horarios de preferencia"
        "nivel_dependencia" -> "Nivel de Dependencia"
        "movilidad" -> "Movilidad"
        "comunicacion" -> "Comunicación"
        "discapacidades" -> "Discapacidades"
        "discapacidad_otro" -> "Otra discapacidad"
        "enfermedades" -> "Enfermedades crónicas"
        "enfermedad_otro" -> "Otra enfermedad"
        "dieta" -> "Plan de alimentación"
        "medicamentos" -> "Medicamentos"
        "condiciones" -> "Otras observaciones"
        else -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
    
    val displayValue = when {
        value is List<*> -> if (value.all { it is Map<*, *> }) "" else value.joinToString(", ")
        value is Map<*, *> -> ""
        value is Boolean -> if (value) "Sí" else "No"
        else -> value.toString()
    }
    
    if ((displayValue.isNotBlank() || value is List<*> || value is Map<*, *>) && displayValue != "No") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(textColor.copy(alpha = if (isDark) 0.08f else 0.03f), RoundedCornerShape(12.dp))
                .border(1.dp, textColor.copy(alpha = if (isDark) 0.15f else 0.06f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val isComplex = value is List<*> || value is Map<*, *> || displayValue.length > 30
            
            if (!isComplex) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        val sectionIcon = when(key) {
                            "especializacion" -> Icons.Default.School
                            "experiencia" -> Icons.Default.History
                            "comunas", "region", "comuna" -> Icons.Default.Place
                            "nombre_completo" -> Icons.Default.Person
                            "edad" -> Icons.Default.Cake
                            "nivel_dependencia" -> Icons.Default.Accessibility
                            "enfermedades" -> Icons.Default.HealthAndSafety
                            else -> null
                        }
                        if (sectionIcon != null) {
                            Icon(sectionIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = secondaryTextColor
                        )
                    }
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sectionIcon = when(key) {
                        "especializacion" -> Icons.Default.School
                        "experiencia" -> Icons.Default.History
                        "comunas", "region", "comuna" -> Icons.Default.Place
                        "nombre_completo" -> Icons.Default.Person
                        "edad" -> Icons.Default.Cake
                        "nivel_dependencia" -> Icons.Default.Accessibility
                        "enfermedades" -> Icons.Default.HealthAndSafety
                        else -> null
                    }
                    if (sectionIcon != null) {
                        Icon(sectionIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = secondaryTextColor
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))

                if (displayValue.isNotBlank()) {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun SearchPreferencesCard(
    role: String,
    data: Map<String, Any>,
    availableCommunes: List<String>,
    onUpdate: (List<String>, List<String>) -> Unit,
    accentColor: Color,
    panelBg: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isLoading: Boolean = false,
    isOnline: Boolean = true
) {
    var isEditing by remember { mutableStateOf(false) }
    
    val currentCommunes = if (role == "cuidador") 
        (data["comunas"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    else 
        (data["comunas_preferencia"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        
    val currentHorarios = if (role == "cuidador") 
        (data["horarios"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    else 
        (data["horarios_preferencia"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    var tempCommunes by remember(isEditing, currentCommunes) { mutableStateOf(currentCommunes) }
    var tempHorarios by remember(isEditing, currentHorarios) { mutableStateOf(currentHorarios) }

    val shadowPalette = CuidaleThemeExtras.shadows

    HardShadowPanel(
        backgroundColor = panelBg,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = if (isOnline) accentColor else Color.Gray, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "PREFERENCIAS DE BÚSQUEDA", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = if (isOnline) textColor else textColor.copy(alpha = 0.5f)
                    )
                }
                if (!isEditing) {
                    IconButton(
                        onClick = { if (isOnline) isEditing = true },
                        enabled = isOnline,
                        modifier = Modifier.background(if (isOnline) accentColor.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f), CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Preferencias", tint = if (isOnline) accentColor else Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (!isOnline && !isEditing) {
                Text(
                    "Modo offline: No se pueden modificar las preferencias.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (isEditing) {
                SearchPreferencesFields(
                    role = role,
                    communes = tempCommunes,
                    horarios = tempHorarios,
                    availableCommunes = availableCommunes,
                    onCommunesChanged = { tempCommunes = it },
                    onHorariosChanged = { tempHorarios = it }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HardShadowButton(
                        text = "CANCELAR",
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        containerColor = if (CuidaleThemeExtras.customColors.isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFE2E8F0),
                        contentColor = secondaryTextColor,
                        shadowColor = Color.Transparent,
                        isDark = CuidaleThemeExtras.customColors.isDark
                    )
                    HardShadowButton(
                        text = if (isLoading) "GUARDANDO..." else "GUARDAR",
                        onClick = {
                            if (isOnline) {
                                onUpdate(tempCommunes, tempHorarios)
                                isEditing = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        containerColor = if (isOnline) accentColor else Color.Gray,
                        contentColor = Color.White,
                        shadowColor = shadowPalette.hardShadowPrimary,
                        isDark = CuidaleThemeExtras.customColors.isDark,
                        enabled = !isLoading && isOnline
                    )
                }
            } else {
                SummaryItem(
                    icon = Icons.Default.Place,
                    label = if (role == "cuidador") "Comunas de trabajo" else "Comunas de interés",
                    value = currentCommunes.joinToString(", ").ifEmpty { "No especificadas" },
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
                SummaryItem(
                    icon = Icons.Default.Schedule,
                    label = if (role == "cuidador") "Horarios disponibles" else "Horarios de interés",
                    value = currentHorarios.joinToString(", ").ifEmpty { "No especificados" },
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            }
        }
    }
}

@Composable
fun SearchPreferencesFields(
    role: String,
    communes: List<String>,
    horarios: List<String>,
    availableCommunes: List<String>,
    onCommunesChanged: (List<String>) -> Unit,
    onHorariosChanged: (List<String>) -> Unit
) {
    Column {
        CuidaleMultiSelectDropdown(
            label = if (role == "cuidador") "COMUNAS DE TRABAJO" else "COMUNAS DE INTERÉS",
            options = availableCommunes,
            selectedOptions = communes,
            maxSelection = 3,
            onOptionToggled = { option: String ->
                val newList = if (communes.contains(option)) communes - option
                else if (communes.size < 3) communes + option
                else communes
                onCommunesChanged(newList)
            }
        )
        Spacer(modifier = Modifier.height(20.dp))
        CuidaleMultiSelectDropdown(
            label = if (role == "cuidador") "HORARIOS DE DISPONIBILIDAD" else "HORARIOS DE INTERÉS",
            options = listOf("Mañana", "Tarde", "Noche", "Fines de Semana"),
            selectedOptions = horarios,
            onOptionToggled = { option: String ->
                val newList = if (horarios.contains(option)) horarios - option
                else horarios + option
                onHorariosChanged(newList)
            }
        )
    }
}
