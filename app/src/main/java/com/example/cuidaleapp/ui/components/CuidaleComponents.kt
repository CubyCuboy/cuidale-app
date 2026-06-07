package com.example.cuidaleapp.ui.components

import android.net.Uri
import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import kotlin.math.roundToInt

@Composable
fun InputFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
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
    val unfocusedBorderColor = if (isError) {
        borderColor.copy(alpha = 0.55f)
    } else {
        if (customColors.isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = LocalTextStyle.current.copy(textAlign = textAlign, color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface),
        placeholder = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if(textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = textAlign
                )
            }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword && !isPasswordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (isPassword && onPasswordVisibilityChange != null) {
                IconButton(onClick = onPasswordVisibilityChange) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = "Ver u ocultar contraseña"
                    )
                }
            } else {
                trailingIcon?.invoke()
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedBorderColor = borderColor,
            unfocusedBorderColor = unfocusedBorderColor,
            errorBorderColor = borderColor
        )
    )
}

@Composable
fun OtpInputField(
    otpText: String,
    onOtpTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    otpCount: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    val focusRequesters = remember { List(otpCount) { FocusRequester() } }

    LaunchedEffect(Unit) {
        if (enabled && otpText.length < otpCount) {
            focusRequesters[otpText.length].requestFocus()
        }
    }

    BasicTextField(
        value = otpText,
        onValueChange = {
            if (it.length <= otpCount && it.all { char -> char.isDigit() }) {
                onOtpTextChange(it)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = enabled,
        decorationBox = {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                repeat(otpCount) { index ->
                    val char = when {
                        index >= otpText.length -> ""
                        else -> otpText[index].toString()
                    }
                    val isFocused = index == otpText.length

                    OtpCharBox(
                        char = char,
                        isFocused = isFocused && enabled,
                        isError = isError,
                        isDark = CuidaleThemeExtras.customColors.isDark,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequesters[index])
                    )
                }
            }
        }
    )
}

@Composable
private fun OtpCharBox(
    char: String,
    isFocused: Boolean,
    isError: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val customColors = CuidaleThemeExtras.customColors
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> colors.error
            isFocused -> colors.primary
            else -> if (isDark) Color.White.copy(alpha = 0.35f) else colors.outline.copy(alpha = 0.25f)
        },
        label = "otpBorderColor"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused || isError) 2.dp else 1.dp,
        label = "otpBorderWidth"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(customColors.inputBackground)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isError) colors.error else colors.onSurface
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isPublic: Boolean = true,
    showVisibilityIcon: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showVisibilityIcon) {
            Icon(
                imageVector = if (isPublic) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isPublic) "Público" else "Privado",
                tint = if (isPublic) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ProfileDetailView(
    role: String,
    data: Map<String, Any>,
    isEditMode: Boolean = false,
    isSummary: Boolean = false,
    onEdit: () -> Unit,
    onExpand: (() -> Unit)? = null
) {
    if (isSummary && onExpand != null) {
        FichaSummaryCard(
            role = role,
            data = data,
            onExpand = onExpand,
            onEdit = onEdit
        )
    } else {
        FullProfileDetailView(role, data, isEditMode, onEdit, onExpand)
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

            if (role == "representante") {
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
                onClick = onEdit, // Cambiado de onExpand a onEdit para que navegue directamente
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "VER FICHA COMPLETA", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), // Letra más grande y negra
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
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FullProfileDetailView(
    role: String,
    data: Map<String, Any>,
    isEditMode: Boolean = false,
    onEdit: () -> Unit,
    onExpand: (() -> Unit)? = null
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
                        if (!isEditMode) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.background(accentColor.copy(alpha = 0.15f), CircleShape).size(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = textColor.copy(alpha = if (customColors.isDark) 0.2f else 0.1f))
                
                val excludedKeys = mutableSetOf("fotos", "tipo", "ultima_actualizacion", "horarios", "comunas", "horarios_preferencia", "comunas_preferencia")

                val preferredOrder = if (role == "cuidador") {
                    listOf("especializacion", "experiencia", "comunas", "descripcion")
                } else {
                    listOf(
                        "nombre_completo", "nivel_dependencia", "enfermedades", "edad",
                        "movilidad", "comunicacion", "genero",
                        "dieta", "medicamentos"
                    )
                }

                val dataToShow = data.filterKeys { it !in excludedKeys }
                val sortedKeys = dataToShow.keys.sortedBy { key ->
                    val index = preferredOrder.indexOf(key)
                    if (index == -1) Int.MAX_VALUE else index
                }

                sortedKeys.forEach { key ->
                    val value = dataToShow[key]!!
                    val label = when(key) {
                        "especializacion" -> "Especialidad o formación:"
                        "experiencia" -> "Tu experiencia previa:"
                        "comunas" -> "Comunas de preferencia:"
                        "descripcion" -> "Descripción sobre mí:"
                        "nombre_completo" -> "Nombre de la persona a cuidar:"
                        "edad" -> "Edad:"
                        "genero" -> "Género:"
                        "vives_con_familiar" -> "Vive con el solicitante:"
                        "region" -> "Región:"
                        "comuna" -> "Comuna:"
                        "calle" -> "Calle / Pasaje:"
                        "numero" -> "Número:"
                        "depto" -> "Depto / Block:"
                        "comunas_preferencia" -> "Comunas de interés:"
                        "horarios_preferencia" -> "Horarios de preferencia:"
                        "nivel_dependencia" -> "Nivel de Dependencia:"
                        "movilidad" -> "Movilidad:"
                        "comunicacion" -> "Comunicación:"
                        "discapacidades" -> "Discapacidades:"
                        "discapacidad_otro" -> "Otra discapacidad:"
                        "enfermedades" -> "Enfermedades crónicas:"
                        "enfermedad_otro" -> "Otra enfermedad:"
                        "dieta" -> "Plan de alimentación:"
                        "medicamentos" -> "Medicamentos:"
                        "condiciones" -> "Otras observaciones:"
                        else -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
                    }
                    
                    val displayValue = when {
                        value is List<*> -> {
                            if (value.all { it is Map<*, *> }) {
                                ""
                            } else {
                                value.joinToString(", ")
                            }
                        }
                        value is Map<*, *> -> {
                            ""
                        }
                        value is Boolean -> if (value) "Sí" else "No"
                        else -> value.toString()
                    }
                    
                    if ((displayValue.isNotBlank() || value is List<*> || value is Map<*, *>) && displayValue != "No") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(textColor.copy(alpha = if (customColors.isDark) 0.08f else 0.04f), RoundedCornerShape(12.dp))
                                .border(1.dp, textColor.copy(alpha = if (customColors.isDark) 0.15f else 0.1f), RoundedCornerShape(12.dp))
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
                                            "dieta" -> Icons.Default.Restaurant
                                            "medicamentos" -> Icons.Default.Medication
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
                                            Icon(sectionIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = label.removeSuffix(":"),
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
                                        "dieta" -> Icons.Default.Restaurant
                                        "medicamentos" -> Icons.Default.Medication
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
                                        Icon(sectionIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = secondaryTextColor
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))

                                if (key == "medicamentos" && value is List<*>) {
                                    value.forEach { med ->
                                        val m = med as Map<*, *>
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp)
                                                .background(textColor.copy(alpha = if (customColors.isDark) 0.15f else 0.08f), RoundedCornerShape(12.dp))
                                                .border(1.dp, textColor.copy(alpha = if (customColors.isDark) 0.1f else 0.05f), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Medication, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = m["nombre"].toString(),
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = textColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val admins = m["administraciones"] as? List<Map<String, String>>
                                            if (admins != null) {
                                                admins.forEach { admin ->
                                                    Row(
                                                        modifier = Modifier.padding(bottom = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "${admin["hora"]} — ${admin["dosis"]}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = textColor
                                                        )
                                                    }
                                                }
                                            }
                                            if ((m["observaciones"] as? String)?.isNotBlank() == true) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    color = accentColor.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Nota: ${m["observaciones"]}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = textColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (key == "dieta" && value is Map<*, *>) {
                                    value.entries.filter { it.value.toString().isNotBlank() }.forEach { entry ->
                                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                            Text(
                                                text = entry.key.toString().replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = secondaryTextColor
                                            )
                                            Text(
                                                text = entry.value.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = textColor
                                            )
                                        }
                                    }
                                } else {
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
                        containerColor = if (CuidaleThemeExtras.customColors.isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFEEEEEE),
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
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
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

    val targetBgColor = if (isError) {
        colors.errorContainer.copy(alpha = 0.98f)
    } else {
        backgroundColor
    }

    val animatedBg by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(500),
        label = "panelBg"
    )

    val targetShadowColor = if (isError) {
        colors.error.copy(alpha = 0.3f)
    } else {
        shadowColor
    }

    val animatedShadow by animateColorAsState(
        targetValue = targetShadowColor,
        animationSpec = tween(500),
        label = "panelShadow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .padding(bottom = 8.dp) // Añadido padding para que no se vea cortada la sombra
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
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
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
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

    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 400),
        label = "buttonContainerColor"
    )

    val finalContentColor = if (!enabled) {
        if (isDark) Color.White.copy(alpha = 0.4f) else Color.Gray
    } else if (isErrorMode) {
        Color.White
    } else {
        contentColor
    }

    val animatedContentColor by animateColorAsState(
        targetValue = finalContentColor,
        animationSpec = tween(durationMillis = 400),
        label = "buttonContentColor"
    )

    val targetShadowColor = if (!enabled) {
        Color.Transparent
    } else if (isErrorMode) {
        colors.errorContainer.copy(alpha = 0.7f)
    } else {
        shadowColor
    }

    val animatedShadowColor by animateColorAsState(
        targetValue = targetShadowColor,
        animationSpec = tween(durationMillis = 400),
        label = "buttonShadowColor"
    )

    val errorBorderWidth by animateDpAsState(
        targetValue = if (isErrorMode) 2.dp else 0.dp,
        animationSpec = tween(400),
        label = "errorBorder"
    )

    val buttonBrush = if (!enabled || isErrorMode) {
        Brush.linearGradient(listOf(animatedContainerColor, animatedContainerColor))
    } else if (isDark) {
        Brush.linearGradient(
            listOf(
                animatedContainerColor,
                Color(0xFF64B5F6).copy(alpha = 0.9f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                animatedContainerColor,
                animatedContainerColor.copy(alpha = 0.92f)
            )
        )
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
            .padding(bottom = shadowOffset, end = shadowOffset) // Crear espacio para la sombra dentro del contenedor
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
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .fillMaxHeight(0.08f)
                            .align(Alignment.BottomCenter)
                            .background(Color.White.copy(alpha = 0.4f))
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (icon != null && !isErrorMode) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = animatedContentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (isErrorMode) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = animatedContentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = if (isErrorMode && errorText != null) errorText else text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = animatedContentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun CuidaleStepProgressBar(
    steps: List<String>,
    currentStepIndex: Int
) {
    val customColors = CuidaleThemeExtras.customColors
    val successColor = customColors.successStrong
    val onGradient = customColors.onGradient
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = circleColor,
                            shape = CircleShape
                        )
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = contentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isActive) onGradient else onGradient.copy(alpha = 0.5f)
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(2.dp)
                        .background(onGradient.copy(alpha = 0.3f))
                ) {
                    if (index < currentStepIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(onGradient)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CuidaleValidationPanel(
    validations: List<Pair<String, Boolean>>,
    unmetIndices: Set<Int> = emptySet(),
    isDark: Boolean
) {
    val allRequirementsMet = validations.all { it.second }

    val requirementsPanelBg = if (allRequirementsMet) {
        if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFDFF6E5)
    } else {
        if (isDark) Color.White.copy(alpha = 0.05f) else Color.White
    }

    HardShadowPanel(
        backgroundColor = requirementsPanelBg,
        shadowColor = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            validations.forEachIndexed { index, (text, isValid) ->
                ValidationItem(
                    text = text,
                    isValid = isValid,
                    isError = unmetIndices.contains(index),
                    isDark = isDark
                )
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

    val color by animateColorAsState(
        targetValue = when {
            isValid -> successColor
            isError -> errorColor
            else -> inactiveColor
        },
        animationSpec = tween(300),
        label = "validationColor"
    )

    val icon = when {
        isValid -> Icons.Default.Check
        isError -> Icons.Default.Close
        else -> Icons.Default.Check
    }

    val shakeOffset by animateDpAsState(
        targetValue = if (isError) 6.dp else 0.dp,
        animationSpec = if (isError) {
            repeatable(
                iterations = 4,
                animation = tween(durationMillis = 50, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(0)
        },
        label = "shake"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier
            .padding(vertical = 2.dp)
            .offset { IntOffset(shakeOffset.toPx().roundToInt(), 0) }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal
            ),
            color = color
        )
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
        
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = isError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = customColors.inputBackground,
                    unfocusedContainerColor = customColors.inputBackground,
                    disabledContainerColor = customColors.inputBackground.copy(alpha = 0.1f),
                    errorContainerColor = customColors.inputBackground,
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = if (isError) {
                        borderColor.copy(alpha = 0.8f)
                    } else {
                        if (customColors.isDark) Color.White.copy(alpha = 0.12f) else Color.Transparent
                    },
                    errorBorderColor = borderColor,
                    disabledBorderColor = Color.Transparent.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = enabled,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(customColors.panelBackground)
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = selectionOption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onOptionSelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
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
        
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = if (selectedOptions.isEmpty()) "" else selectedOptions.joinToString(", "),
                onValueChange = {},
                readOnly = true,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
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
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(customColors.panelBackground)
            ) {
                options.forEach { option ->
                    val isSelected = selectedOptions.contains(option)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        onClick = {
                            if (isSelected || selectedOptions.size < maxSelection) {
                                onOptionToggled(option)
                            }
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        
        if (selectedOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedOptions) { option ->
                    SuggestionChip(
                        onClick = { onOptionToggled(option) },
                        label = { 
                            Text(
                                text = option,
                                style = MaterialTheme.typography.labelLarge
                            ) 
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (customColors.isDark) 0.25f else 0.1f),
                            labelColor = MaterialTheme.colorScheme.primary,
                            iconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = if (customColors.isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            borderWidth = 1.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CuidaleBinaryChoice(
    label: String,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    extraContent: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(text = label)
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.clickable { onOptionSelected("Si") }
            ) {
                RadioButton(
                    selected = selectedOption == "Si",
                    onClick = null
                )
                Text(text = "Si", modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface)
            }
            
            Spacer(modifier = Modifier.width(24.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.clickable { onOptionSelected("No") }
            ) {
                RadioButton(
                    selected = selectedOption == "No",
                    onClick = null
                )
                Text(text = "No", modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        
        if (selectedOption == "Si" && extraContent != null) {
            Spacer(modifier = Modifier.height(8.dp))
            extraContent()
        }
    }
}

@Composable
fun CuidaleImagePicker(
    label: String,
    imageUri: Uri?,
    onImageClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CuidaleThemeExtras.customColors.inputBackground)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    RoundedCornerShape(16.dp)
                )
                .clickable { onImageClick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

@Composable
fun CuidaleMultiImagePicker(
    label: String,
    imageUris: List<Uri>,
    onAddImage: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    maxImages: Int = 3
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            imageUris.forEach { uri ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CuidaleThemeExtras.customColors.inputBackground)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { onRemoveImage(uri) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Eliminar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (imageUris.size < maxImages) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CuidaleThemeExtras.customColors.inputBackground)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onAddImage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Agregar foto",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
