package com.example.cuidaleapp.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import kotlin.math.roundToInt

@Composable
fun InputFieldLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun CuidaleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = Color.Unspecified,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val customColors = CuidaleThemeExtras.customColors
    val borderColor = if (isError) customColors.warning else MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = LocalTextStyle.current.copy(
            textAlign = textAlign,
            color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface
        ),
        placeholder = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if(textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = textAlign
                )
            }
        },
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        maxLines = maxLines,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            if (isPassword && onPasswordVisibilityChange != null) {
                IconButton(onClick = onPasswordVisibilityChange) {
                    Icon(imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, contentDescription = null)
                }
            } else {
                trailingIcon?.invoke()
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedBorderColor = borderColor,
            unfocusedBorderColor = if (isError) borderColor.copy(alpha = 0.55f) else Color.Transparent,
            errorBorderColor = borderColor
        )
    )
}

@Composable
fun OtpInputField(otpText: String, onOtpTextChange: (String) -> Unit, modifier: Modifier = Modifier, otpCount: Int = 6, isError: Boolean = false, enabled: Boolean = true) {
    val focusRequesters = remember { List(otpCount) { FocusRequester() } }
    LaunchedEffect(Unit) { if (enabled && otpText.length < otpCount) focusRequesters[otpText.length].requestFocus() }
    BasicTextField(
        value = otpText,
        onValueChange = { if (it.length <= otpCount && it.all { char -> char.isDigit() }) onOtpTextChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = enabled,
        decorationBox = {
            Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(otpCount) { index ->
                    val char = when { index >= otpText.length -> "" else -> otpText[index].toString() }
                    val isFocused = index == otpText.length
                    OtpCharBox(
                        char = char,
                        isFocused = isFocused && enabled,
                        isError = isError,
                        isDark = CuidaleThemeExtras.customColors.isDark,
                        modifier = Modifier.weight(1f).focusRequester(focusRequesters[index])
                    )
                }
            }
        }
    )
}

@Composable
private fun OtpCharBox(char: String, isFocused: Boolean, isError: Boolean, isDark: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val customColors = CuidaleThemeExtras.customColors
    val borderColor by animateColorAsState(targetValue = when { isError -> colors.error; isFocused -> colors.primary; else -> if (isDark) Color.White.copy(alpha = 0.35f) else colors.outline.copy(alpha = 0.25f) }, label = "otpBorderColor")
    val borderWidth by animateDpAsState(targetValue = if (isFocused || isError) 2.dp else 1.dp, label = "otpBorderWidth")
    Box(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(customColors.inputBackground).border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = char, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = if (isError) colors.error else colors.onSurface)
    }
}

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
                Pair(data["comunas"] as? List<String> ?: emptyList(), data["horarios"] as? List<String> ?: emptyList())
            } else {
                Pair(data["comunas_preferencia"] as? List<String> ?: emptyList(), data["horarios_preferencia"] as? List<String> ?: emptyList())
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
                Icon(Icons.Default.KeyboardArrowRight, null, tint = accentColor, modifier = Modifier.size(24.dp))
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
                    // SECCIONES PARA REPRESENTANTE (PACIENTE)
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
    isLoading: Boolean = false
) {
    var isEditing by remember { mutableStateOf(false) }
    
    val currentCommunes = if (role == "cuidador") 
        data["comunas"] as? List<String> ?: emptyList()
    else 
        data["comunas_preferencia"] as? List<String> ?: emptyList()
        
    val currentHorarios = if (role == "cuidador") 
        data["horarios"] as? List<String> ?: emptyList()
    else 
        data["horarios_preferencia"] as? List<String> ?: emptyList()

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
                    Icon(Icons.Default.Search, null, tint = accentColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "PREFERENCIAS DE BÚSQUEDA", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = textColor
                    )
                }
                if (!isEditing) {
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.background(accentColor.copy(alpha = 0.15f), CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Preferencias", tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

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
                            onUpdate(tempCommunes, tempHorarios)
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        containerColor = accentColor,
                        contentColor = Color.White,
                        shadowColor = shadowPalette.hardShadowPrimary,
                        isDark = CuidaleThemeExtras.customColors.isDark,
                        enabled = !isLoading
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
            onOptionToggled = {
                val newList = if (communes.contains(it)) communes - it
                else if (communes.size < 3) communes + it
                else communes
                onCommunesChanged(newList)
            }
        )
        Spacer(modifier = Modifier.height(20.dp))
        CuidaleMultiSelectDropdown(
            label = if (role == "cuidador") "HORARIOS DE DISPONIBILIDAD" else "HORARIOS DE INTERÉS",
            options = listOf("Mañana", "Tarde", "Noche", "Fines de Semana"),
            selectedOptions = horarios,
            onOptionToggled = {
                val newList = if (horarios.contains(it)) horarios - it
                else horarios + it
                onHorariosChanged(newList)
            }
        )
    }
}

@Composable
fun HardShadowPanel(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    shadowColor: Color = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.2f),
    isError: Boolean = false,
    cornerRadius: Dp = 16.dp,
    shadowOffset: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(isError) {
        if (isError) {
            repeat(8) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 12f else -12f,
                    animationSpec = tween(durationMillis = 40, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }

    val targetBgColor = if (isError) colors.errorContainer.copy(alpha = 0.98f) else backgroundColor
    val animatedBg by animateColorAsState(targetValue = targetBgColor, animationSpec = tween(500), label = "panelBg")
    val targetShadowColor = if (isError) colors.error.copy(alpha = 0.3f) else shadowColor
    val animatedShadow by animateColorAsState(targetValue = targetShadowColor, animationSpec = tween(500), label = "panelShadow")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .padding(bottom = shadowOffset, end = shadowOffset)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(RoundedCornerShape(cornerRadius))
                .background(animatedShadow)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(animatedBg)
                .border(
                    width = if (isError) 2.dp else 0.dp,
                    color = if (isError) colors.error.copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun HardShadowButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth().height(58.dp),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shadowColor: Color = CuidaleThemeExtras.shadows.hardShadowPrimary,
    icon: ImageVector? = null,
    isErrorMode: Boolean = false,
    errorText: String? = null,
    isDark: Boolean = CuidaleThemeExtras.customColors.isDark,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(isErrorMode) {
        if (isErrorMode) {
            repeat(6) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 10f else -10f,
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }

    val targetContainerColor = if (!enabled) {
        if (isDark) Color.Gray.copy(alpha = 0.3f) else Color.LightGray
    } else if (isErrorMode) {
        colors.error
    } else {
        containerColor
    }

    val animatedContainerColor by animateColorAsState(targetValue = targetContainerColor, animationSpec = tween(400), label = "buttonContainerColor")
    val finalContentColor = if (!enabled) (if (isDark) Color.White.copy(alpha = 0.4f) else Color.Gray) else if (isErrorMode) Color.White else contentColor
    val animatedContentColor by animateColorAsState(targetValue = finalContentColor, animationSpec = tween(400), label = "buttonContentColor")
    val targetShadowColor = if (!enabled) Color.Transparent else if (isErrorMode) colors.errorContainer.copy(alpha = 0.7f) else shadowColor
    val animatedShadowColor by animateColorAsState(targetValue = targetShadowColor, animationSpec = tween(400), label = "buttonShadowColor")
    val errorBorderWidth by animateDpAsState(targetValue = if (isErrorMode) 2.dp else 0.dp, animationSpec = tween(400), label = "errorBorder")

    val buttonBrush = if (!enabled || isErrorMode) {
        Brush.linearGradient(listOf(animatedContainerColor, animatedContainerColor))
    } else if (isDark) {
        Brush.linearGradient(listOf(animatedContainerColor, Color(0xFF64B5F6).copy(alpha = 0.9f)))
    } else {
        Brush.linearGradient(listOf(animatedContainerColor, animatedContainerColor.copy(alpha = 0.92f)))
    }

    val scale by animateFloatAsState(
        targetValue = if (!enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    val shadowOffset = 5.dp

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .padding(bottom = shadowOffset, end = shadowOffset)
    ) {
        if (enabled || isErrorMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffset, y = shadowOffset)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(animatedShadowColor)
            )
        }

        Button(
            onClick = onClick,
            enabled = enabled && !isErrorMode,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(cornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = animatedContentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = animatedContentColor
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(buttonBrush)
                    .border(
                        width = errorBorderWidth,
                        color = colors.onErrorContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(cornerRadius)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isErrorMode) {
                    val progress = remember { Animatable(1f) }
                    LaunchedEffect(Unit) {
                        progress.animateTo(0f, animationSpec = tween(3000, easing = LinearEasing))
                    }
                    Box(modifier = Modifier.fillMaxWidth(progress.value).fillMaxHeight(0.08f).align(Alignment.BottomCenter).background(Color.White.copy(alpha = 0.4f)))
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (icon != null && !isErrorMode) {
                        Icon(imageVector = icon, contentDescription = null, tint = animatedContentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (isErrorMode) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = animatedContentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = if (isErrorMode && errorText != null) errorText else text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = animatedContentColor, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun CuidaleStepProgressBar(steps: List<String>, currentStepIndex: Int) {
    val customColors = CuidaleThemeExtras.customColors
    val successColor = customColors.successStrong
    val onGradient = customColors.onGradient
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val isCompleted = index < currentStepIndex
            val isCurrent = index == currentStepIndex
            val isActive = isCompleted || isCurrent

            val circleColor = when {
                isCompleted -> successColor
                isCurrent -> primaryColor
                else -> onGradient.copy(alpha = 0.25f)
            }

            val contentColor = when {
                isCompleted || isCurrent -> onPrimaryColor
                else -> onGradient.copy(alpha = 0.5f)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).background(color = circleColor, shape = CircleShape)) {
                    if (isCompleted) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
                    } else {
                        Text(text = (index + 1).toString(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = contentColor)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal), color = if (isActive) onGradient else onGradient.copy(alpha = 0.5f))
            }

            if (index < steps.size - 1) {
                Box(modifier = Modifier.weight(0.5f).height(2.dp).background(onGradient.copy(alpha = 0.3f))) {
                    if (index < currentStepIndex) {
                        Box(modifier = Modifier.fillMaxSize().background(onGradient))
                    }
                }
            }
        }
    }
}

@Composable
fun CuidaleValidationPanel(validations: List<Pair<String, Boolean>>, unmetIndices: Set<Int> = emptySet(), isDark: Boolean) {
    val allRequirementsMet = validations.all { it.second }
    val requirementsPanelBg = if (allRequirementsMet) {
        if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFDFF6E5)
    } else {
        if (isDark) Color.White.copy(alpha = 0.05f) else Color.White
    }

    HardShadowPanel(backgroundColor = requirementsPanelBg, shadowColor = Color.Transparent) {
        Column(modifier = Modifier.fillMaxWidth()) {
            validations.forEachIndexed { index, (text, isValid) ->
                ValidationItem(text = text, isValid = isValid, isError = unmetIndices.contains(index), isDark = isDark)
            }
        }
    }
}

@Composable
fun ValidationItem(text: String, isValid: Boolean, isError: Boolean, isDark: Boolean) {
    val customColors = CuidaleThemeExtras.customColors
    val successColor = if (isDark) customColors.successStrong else Color(0xFF00C853)
    val errorColor = MaterialTheme.colorScheme.error
    val inactiveColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF455A64)

    val color by animateColorAsState(targetValue = when { isValid -> successColor; isError -> errorColor; else -> inactiveColor }, animationSpec = tween(300), label = "validationColor")
    val icon = when { isValid -> Icons.Default.Check; isError -> Icons.Default.Close; else -> Icons.Default.Check }
    val shakeOffset by animateDpAsState(targetValue = if (isError) 6.dp else 0.dp, animationSpec = if (isError) { repeatable(iterations = 4, animation = tween(durationMillis = 50, easing = LinearEasing), repeatMode = RepeatMode.Reverse) } else { tween(0) }, label = "shake")

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp).offset { IntOffset(shakeOffset.toPx().roundToInt(), 0) }) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal), color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuidaleExposedDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    placeholder: String = "Seleccionar..."
) {
    var expanded by remember { mutableStateOf(false) }
    val customColors = CuidaleThemeExtras.customColors
    val borderColor = if (isError) customColors.warning else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(text = label)
        ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(text = placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = isError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = customColors.inputBackground,
                    unfocusedContainerColor = customColors.inputBackground,
                    disabledContainerColor = customColors.inputBackground.copy(alpha = 0.1f),
                    errorContainerColor = customColors.inputBackground,
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = if (isError) borderColor.copy(alpha = 0.8f) else { if (customColors.isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent },
                    errorBorderColor = borderColor,
                    disabledBorderColor = Color.Transparent.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.background(customColors.panelBackground)) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(text = { Text(text = selectionOption, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }, onClick = { onOptionSelected(selectionOption); expanded = false }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuidaleMultiSelectDropdown(
    label: String,
    options: List<String>,
    selectedOptions: List<String>,
    onOptionToggled: (String) -> Unit,
    maxSelection: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    placeholder: String = "Seleccionar..."
) {
    var expanded by remember { mutableStateOf(false) }
    val customColors = CuidaleThemeExtras.customColors

    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(text = label)
        ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (selectedOptions.isEmpty()) "" else selectedOptions.joinToString(", "),
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(text = placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = customColors.inputBackground,
                    unfocusedContainerColor = customColors.inputBackground,
                    disabledContainerColor = customColors.inputBackground.copy(alpha = 0.1f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (customColors.isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                    disabledBorderColor = Color.Transparent.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.background(customColors.panelBackground)) {
                options.forEach { option ->
                    val isSelected = selectedOptions.contains(option)
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isSelected, onCheckedChange = null); Spacer(modifier = Modifier.width(8.dp)); Text(text = option, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) } },
                        onClick = { if (isSelected || selectedOptions.size < maxSelection) onOptionToggled(option) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                }
            }
        }
        if (selectedOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedOptions) { option ->
                    SuggestionChip(onClick = { onOptionToggled(option) }, label = { Text(text = option, style = MaterialTheme.typography.labelLarge) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (customColors.isDark) 0.25f else 0.1f), labelColor = MaterialTheme.colorScheme.primary, iconContentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp), border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = if (customColors.isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), borderWidth = 1.dp))
                }
            }
        }
    }
}

@Composable
fun CuidaleBinaryChoice(label: String, selectedOption: String?, onOptionSelected: (String) -> Unit, extraContent: @Composable (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(text = label)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOptionSelected("Si") }) { RadioButton(selected = selectedOption == "Si", onClick = null); Text(text = "Si", modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface) }
            Spacer(modifier = Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOptionSelected("No") }) { RadioButton(selected = selectedOption == "No", onClick = null); Text(text = "No", modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface) }
        }
        if (selectedOption == "Si" && extraContent != null) { Spacer(modifier = Modifier.height(8.dp)); extraContent() }
    }
}

@Composable
fun CuidaleImagePicker(label: String, imageUri: Uri?, onImageClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
        Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(CuidaleThemeExtras.customColors.inputBackground).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)).clickable { onImageClick() }, contentAlignment = Alignment.Center) {
            if (imageUri != null) AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            else Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
fun CuidaleMultiImagePicker(label: String, imageUris: List<Uri>, onAddImage: () -> Unit, onRemoveImage: (Uri) -> Unit, maxImages: Int = 3) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            imageUris.forEach { uri ->
                Box(modifier = Modifier.size(80.dp).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(CuidaleThemeExtras.customColors.inputBackground)) {
                    AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    IconButton(onClick = { onRemoveImage(uri) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) { Icon(imageVector = Icons.Default.Close, contentDescription = "Eliminar", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            if (imageUris.size < maxImages) {
                Box(modifier = Modifier.size(80.dp).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(CuidaleThemeExtras.customColors.inputBackground).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)).clickable { onAddImage() }, contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = "Agregar foto", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp)) }
            }
        }
    }
}

@Composable
fun CuidaleDrawerContent(nombre: String, apellido: String, fotoUrl: String, rol: String = "", isDark: Boolean, onToggleTheme: () -> Unit, onProfileClick: () -> Unit, onFichaClick: () -> Unit, onSettingsClick: () -> Unit, onLogoutClick: () -> Unit, onCloseClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme; val customColors = CuidaleThemeExtras.customColors
    val accentColor = if (rol == "cuidador") colors.primary else customColors.successStrong
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize().background(customColors.panelBackground).padding(24.dp).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCloseClick) { Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = colors.onSurface) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = null, tint = colors.primary)
                Spacer(modifier = Modifier.width(8.dp)); Text(text = "Modo Oscuro", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                Spacer(modifier = Modifier.width(8.dp)); Switch(checked = isDark, onCheckedChange = { onToggleTheme() })
            }
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp), color = colors.outlineVariant.copy(alpha = 0.5f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(customColors.avatarBackground).border(2.dp, accentColor.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                if (fotoUrl.isNotEmpty() && !fotoUrl.startsWith("res:")) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(fotoUrl)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(customColors.avatarBackground)
                    )
                } else {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(50.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            val fullName = listOf(nombre, apellido).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Usuario Cuidale" }
            
            Text(
                text = fullName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fadingEdge(
                    Brush.horizontalGradient(
                        0.98f to Color.Black,
                        1f to Color.Transparent
                    )
                )
            )
            
            if (rol.isNotEmpty()) {
                Surface(color = accentColor.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = rol.uppercase(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accentColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        NavigationDrawerItem(label = { Text(stringResource(R.string.menu_item_profile)) }, selected = false, onClick = onProfileClick, icon = { Icon(Icons.Default.Person, contentDescription = null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
        NavigationDrawerItem(label = { Text(stringResource(R.string.menu_item_ficha)) }, selected = false, onClick = onFichaClick, icon = { Icon(Icons.Default.Badge, contentDescription = null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
        NavigationDrawerItem(label = { Text("Configuración") }, selected = false, onClick = onSettingsClick, icon = { Icon(Icons.Default.Settings, contentDescription = null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "v${stringResource(R.string.app_version)} - Cuidale",
            modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.5f)
        )


        Spacer(modifier = Modifier.weight(1f)); HardShadowButton(text = stringResource(R.string.menu_logout), containerColor = colors.error, contentColor = Color.White, shadowColor = colors.errorContainer, isDark = customColors.isDark, onClick = onLogoutClick, icon = Icons.Default.Logout)
    }
}

@Composable
fun CuidaleSearchContent(
    allRegions: List<String>,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit,
    allCommunes: List<String>,
    selectedCommunes: List<String>,
    onCommuneToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onClose: () -> Unit,
    useProfilePreferences: Boolean = false,
    onTogglePreferences: (Boolean) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val customColors = CuidaleThemeExtras.customColors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.search_button), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                if (selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty()) { TextButton(onClick = onClearFilters, modifier = Modifier.padding(start = 8.dp)) { Text(text = "Limpiar Filtros", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = colors.error) } }
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(customColors.panelBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Usar preferencias de mi ficha", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Aplica automáticamente tu región y comunas", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Switch(checked = useProfilePreferences, onCheckedChange = onTogglePreferences)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        CuidaleExposedDropdown(label = "Filtrar por región", options = allRegions, selectedOption = selectedRegion, onOptionSelected = onRegionSelected, placeholder = "Seleccionar región...")
        Spacer(modifier = Modifier.height(16.dp)); CuidaleMultiSelectDropdown(label = stringResource(R.string.search_filter_commune), options = allCommunes, selectedOptions = selectedCommunes, onOptionToggled = onCommuneToggle, maxSelection = 3, placeholder = if (selectedRegion.isEmpty()) "Selecciona una región primero" else stringResource(R.string.search_max_communes), enabled = selectedRegion.isNotEmpty())
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CuidaleCircularMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable (() -> Unit) = { Icon(imageVector = Icons.Default.Menu, contentDescription = null, tint = Color.White) }) {
    val shadows = CuidaleThemeExtras.shadows
    Box(modifier = modifier.size(56.dp).offset(x = 4.dp, y = 4.dp).background(shadows.hardShadowPrimary.copy(alpha = 0.3f), CircleShape)) { IconButton(onClick = onClick, modifier = Modifier.matchParentSize().offset(x = (-4).dp, y = (-4).dp).background(MaterialTheme.colorScheme.primary, CircleShape)) { icon() } }
}

@Composable
fun CuidaleUserCard(
    nombre: String, 
    apellido: String, 
    fotoUrl: String, 
    comuna: String = "", 
    comunas: List<String> = emptyList(), 
    tipo: String = "", 
    edad: String = "", 
    nivelDependencia: String = "", 
    especializacion: String = "", 
    horarios: List<String> = emptyList(), 
    isNew: Boolean = false,
    onClick: () -> Unit
) {
    val shadows = CuidaleThemeExtras.shadows; val colors = MaterialTheme.colorScheme; val customColors = CuidaleThemeExtras.customColors
    val context = LocalContext.current
    
    HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f), modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = customColors.avatarBackground) {
                    if (fotoUrl.isNotEmpty() && !fotoUrl.startsWith("res:")) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fotoUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(customColors.avatarBackground)
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp).align(Alignment.Center), tint = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                
                if (isNew) {
                    Surface(
                        color = Color.Red,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp)
                    ) {
                        Text(
                            "NUEVO", 
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) {
                val fullName = listOf(nombre, apellido).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Usuario Cuidale" }
                
                Text(
                    text = fullName, 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fadingEdge(
                        Brush.horizontalGradient(
                            0.98f to Color.Black,
                            1f to Color.Transparent
                        )
                    )
                )
                if (tipo == "familiar") {
                    Text(text = buildString { if (edad.isNotEmpty()) append("$edad años • "); append(comuna) }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (nivelDependencia.isNotEmpty()) Text(text = "Dependencia: $nivelDependencia", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.primary)
                } else if (tipo == "cuidador") {
                    val locationText = if (comuna.isNotEmpty()) comuna else comunas.joinToString(", "); if (locationText.isNotEmpty()) Text(text = locationText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (especializacion.isNotEmpty()) Text(text = especializacion, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (horarios.isNotEmpty()) Text(text = "Horario: ${horarios.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}



@Composable
fun CuidaleDetailRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, modifier = Modifier.size(16.dp).padding(top = 2.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ", 
            style = MaterialTheme.typography.bodySmall, 
            color = Color.Gray, 
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.bodySmall, 
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

fun Modifier.fadingEdge(brush: Brush) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }

@Composable
fun CuidaleBottomBar(selectedTab: String, unreadCount: Int = 0, pendingNotifs: Int = 0, pendingRequests: Int = 0, onTabSelected: (String) -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    Surface(color = customColors.panelBackground, tonalElevation = 8.dp, shadowElevation = 16.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(72.dp).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BottomNavItem(icon = Icons.Default.Notifications, isSelected = selectedTab == "notifications", badgeCount = pendingNotifs, onClick = { onTabSelected("notifications") })
            BottomNavItem(icon = Icons.Default.Assignment, isSelected = selectedTab == "requests", badgeCount = pendingRequests, onClick = { onTabSelected("requests") })
            BottomNavItem(icon = Icons.Default.Home, isSelected = selectedTab == "home", onClick = { onTabSelected("home") }, isCenter = true)
            BottomNavItem(icon = Icons.AutoMirrored.Filled.Chat, isSelected = selectedTab == "chats", badgeCount = unreadCount, onClick = { onTabSelected("chats") })
        }
    }
}

@Composable
private fun BottomNavItem(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, isCenter: Boolean = false, badgeCount: Int = 0) {
    val colors = MaterialTheme.colorScheme
    val backgroundColor by animateColorAsState(targetValue = if (isSelected) colors.primary else Color.Transparent, animationSpec = tween(300), label = "tabBg")
    val iconColor by animateColorAsState(targetValue = if (isSelected) Color.White else colors.onSurface.copy(alpha = 0.6f), animationSpec = tween(300), label = "tabIcon")
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.15f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "tabScale")
    Box(modifier = Modifier.size(if (isCenter) 52.dp else 44.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(if (isCenter) 16.dp else 12.dp)).background(backgroundColor).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        BadgedBox(badge = { if (badgeCount > 0) Badge(containerColor = Color.Red, contentColor = Color.White, modifier = Modifier.offset(x = 4.dp, y = (-4).dp)) { Text(if (badgeCount > 99) "+99" else badgeCount.toString(), style = MaterialTheme.typography.labelSmall) } }) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(if (isCenter) 28.dp else 24.dp))
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
fun CuidaleTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Seleccionar hora",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("CANCELAR") }
                    TextButton(onClick = onConfirm) { Text("ACEPTAR") }
                }
            }
        }
    }
}
