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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    keyboardType: KeyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val customColors = CuidaleThemeExtras.customColors
    val borderColor = if (isError) customColors.warning else MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = LocalTextStyle.current.copy(textAlign = textAlign),
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
            keyboardType = keyboardType
        ),
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
fun PhotoGalleryDialog(
    photos: List<Any>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = photos[page]
                val context = LocalContext.current
                val imageSource: Any = when (photo) {
                    is ProfileImage -> {
                        if (photo.sourceType == ImageSourceType.DEFAULT_AVATAR) {
                            val iconName = photo.cacheKey.substringAfter("res:")
                            context.resources.getIdentifier(iconName, "drawable", context.packageName).let {
                                if (it != 0) it else R.drawable.profile_default
                            }
                        } else photo.remoteUrl ?: R.drawable.profile_default
                    }
                    else -> photo
                }

                AsyncImage(
                    model = imageSource,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(Color.Black.copy(0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun ProfileDetailView(
    role: String,
    data: Map<String, Any>,
    isEditMode: Boolean = false,
    onEdit: () -> Unit,
    defaultPhoto: String? = null,
    onPhotoClick: ((Int, List<Any>) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val fotos = data["fotos"] as? List<*> ?: emptyList<Any>()
    
    val displayPhotos = if (fotos.isEmpty() && defaultPhoto != null) listOf(defaultPhoto) else fotos

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESUMEN DE FICHA",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                    if (!isEditMode) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.background(colors.primary.copy(alpha = 0.1f), CircleShape).size(36.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.primary.copy(alpha = 0.2f))
                
                if (displayPhotos.isNotEmpty()) {
                    Text(
                        text = "FOTOS ADJUNTAS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = colors.primary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(displayPhotos) { foto ->
                            AsyncImage(
                                model = foto.toString(),
                                contentDescription = "Ficha Image",
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Gray.copy(alpha = 0.05f))
                                    .border(1.dp, colors.primary.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                    .clickable { 
                                        onPhotoClick?.invoke(displayPhotos.indexOf(foto), displayPhotos as List<Any>)
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                val excludedKeys = mutableSetOf("fotos", "tipo", "ultima_actualizacion", "horarios")

                val preferredOrder = if (role == "cuidador") {
                    listOf("especializacion", "experiencia", "comunas", "descripcion")
                } else {
                    listOf(
                        "nombre_completo", "edad", "genero", 
                        "vives_con_familiar", "region", "comuna", "calle", "numero", "depto",
                        "comunas_preferencia", "horarios_preferencia",
                        "nivel_dependencia", "movilidad", "comunicacion",
                        "discapacidades", "discapacidad_otro", "enfermedades", "enfermedad_otro", 
                        "dieta", "medicamentos", "condiciones"
                    )
                }

                val dataToShow = data.filterKeys { it !in excludedKeys }
                val sortedKeys = dataToShow.keys.sortedWith { k1, k2 ->
                    val i1 = preferredOrder.indexOf(k1).let { if (it == -1) Int.MAX_VALUE else it }
                    val i2 = preferredOrder.indexOf(k2).let { if (it == -1) Int.MAX_VALUE else it }
                    i1.compareTo(i2)
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
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, colors.primary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
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
                                            Icon(sectionIcon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = label.removeSuffix(":"),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colors.primary
                                        )
                                    }
                                    Text(
                                        text = displayValue,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurface,
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
                                        Icon(sectionIcon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.primary
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
                                                .background(colors.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Medication, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = m["nombre"].toString(),
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = colors.primary
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
                                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "${admin["hora"]} — ${admin["dosis"]}",
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                            if ((m["observaciones"] as? String)?.isNotBlank() == true) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    color = colors.secondaryContainer.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Nota: ${m["observaciones"]}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSecondaryContainer,
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
                                                color = colors.primary
                                            )
                                            Text(
                                                text = entry.value.toString(),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = displayValue,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurface
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
fun PersonalDataCard(
    userData: ProfileDomainModel,
    onEditClick: (() -> Unit)? = null,
    onPhotoClick: (() -> Unit)? = null
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DATOS PERSONALES",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                if (onEditClick != null) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Perfil",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .then(if (onPhotoClick != null) Modifier.clickable { onPhotoClick() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                val profilePhoto = userData.profilePhoto
                val context = androidx.compose.ui.platform.LocalContext.current

                val imageSource: Any = when {
                    profilePhoto == null -> R.drawable.profile_default
                    profilePhoto.sourceType == ImageSourceType.DEFAULT_AVATAR -> {
                        val iconName = profilePhoto.cacheKey.substringAfter("res:")
                        context.resources.getIdentifier(
                            iconName,
                            "drawable",
                            context.packageName
                        ).let {
                            if (it != 0) it else R.drawable.profile_default
                        }
                    }
                    else -> profilePhoto.remoteUrl ?: R.drawable.profile_default
                }

                AsyncImage(
                    model = imageSource,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${userData.nombre} ${userData.apellido_pa}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (userData.descripcion.isNotBlank()) {
                Text(
                    text = userData.descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).padding(horizontal = 16.dp)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = (if (userData.rol == "representante") "Representante" else userData.rol).uppercase(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ProfileInfoRow(
                icon = Icons.Default.Email,
                label = stringResource(R.string.profile_label_email),
                value = userData.correo,
                isPublic = userData.mostrarCorreo
            )
            ProfileInfoRow(
                icon = Icons.Default.Badge,
                label = stringResource(R.string.profile_label_rut),
                value = userData.rut,
                isPublic = userData.mostrarRut
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ProfileInfoRow(
                        icon = Icons.Default.Phone,
                        label = stringResource(R.string.profile_label_phone),
                        value = if (userData.telefono.isNotBlank()) userData.telefono else "No registrado",
                        isPublic = userData.mostrarTelefono
                    )
                }
                if (userData.telefono.isNotBlank()) {
                    val verificado = userData.telefonoVerificado
                    Surface(
                        color = if (verificado) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (verificado) "Verificado" else "No verificado",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (verificado) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HardShadowPanel(
    backgroundColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
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
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(animatedShadow)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(animatedBg)
                .border(
                    width = if (isError) 2.dp else 0.dp,
                    color = if (isError) colors.error.copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
fun HardShadowButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isErrorMode: Boolean = false,
    errorText: String? = null,
    isDark: Boolean,
    enabled: Boolean = true,
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
                colors.primary.copy(alpha = 0.82f)
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
    ) {
        if (enabled || isErrorMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = 6.dp, y = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(animatedShadowColor)
            )
        }

        Button(
            onClick = onClick,
            enabled = enabled && !isErrorMode,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp)
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
                    unfocusedBorderColor = if (isError) borderColor.copy(alpha = 0.8f) else Color.Transparent,
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
                    unfocusedBorderColor = Color.Transparent,
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
                        label = { Text(option) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            labelColor = MaterialTheme.colorScheme.primary
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
