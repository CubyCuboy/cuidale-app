package com.example.cuidaleapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

private fun formatearMiles(valor: Int): String {
    if (valor <= 0) return valor.toString()
    val s = valor.toString()
    val sb = StringBuilder()
    val len = s.length
    for (i in s.indices) {
        sb.append(s[i])
        val remaining = len - i - 1
        if (remaining > 0 && remaining % 3 == 0) sb.append('.')
    }
    return sb.toString()
}

@Composable
fun CuidaleUserCard(
    nombre: String,
    apellido: String,
    fotoUrl: String,
    representanteFotoUrl: String = "",
    comuna: String = "",
    comunas: List<String> = emptyList(),
    tipo: String = "",
    edad: String = "",
    nivelDependencia: String = "",
    especializacion: String = "",
    horarios: List<String> = emptyList(),
    isNew: Boolean = false,
    isLoading: Boolean = false,
    verificado: Boolean = false,
    calificacion: Double = 0.0,
    nroCalificaciones: Int = 0,
    valorHora: Int = 0,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    onSendSolicitud: () -> Unit = {},
    hasChat: Boolean = false,
    onGoToChat: () -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (isLoading) {
        SkeletonUserCard(modifier)
    } else if (tipo == "cuidador") {
        CuidaleCuidadorCard(
            nombre = nombre,
            apellido = apellido,
            fotoUrl = fotoUrl,
            comuna = comuna,
            comunas = comunas,
            especializacion = especializacion,
            horarios = horarios,
            isNew = isNew,
            verificado = verificado,
            calificacion = calificacion,
            nroCalificaciones = nroCalificaciones,
            valorHora = valorHora,
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            onSendSolicitud = onSendSolicitud,
            hasChat = hasChat,
            onGoToChat = onGoToChat,
            modifier = modifier,
            onClick = onClick
        )
    } else {
        CuidalePacienteCard(
            nombre = nombre,
            apellido = apellido,
            fotoUrl = fotoUrl,
            representanteFotoUrl = representanteFotoUrl,
            comuna = comuna,
            comunas = comunas,
            edad = edad,
            nivelDependencia = nivelDependencia,
            isNew = isNew,
            verificado = verificado,
            isSaved = isSaved,
            onToggleSave = onToggleSave,
            onSendSolicitud = onSendSolicitud,
            hasChat = hasChat,
            onGoToChat = onGoToChat,
            modifier = modifier,
            onClick = onClick
        )
    }
}

@Composable
private fun CuidaleCuidadorCard(
    nombre: String,
    apellido: String,
    fotoUrl: String,
    comuna: String,
    comunas: List<String>,
    especializacion: String,
    horarios: List<String>,
    isNew: Boolean,
    verificado: Boolean,
    calificacion: Double,
    nroCalificaciones: Int,
    valorHora: Int,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onSendSolicitud: () -> Unit,
    hasChat: Boolean,
    onGoToChat: () -> Unit,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shadows = CuidaleThemeExtras.shadows; val colors = MaterialTheme.colorScheme; val customColors = CuidaleThemeExtras.customColors
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val fullName = listOf(nombre, apellido).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { stringResource(R.string.common_default_user) }
    val locationText = if (comuna.isNotEmpty()) comuna else comunas.joinToString(", ")

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
            },
            label = "caregiverCard"
        ) { isExpanded ->
            if (isExpanded) {
                // ===== EXPANDED: full big card =====
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(colors.primaryContainer, colors.surfaceVariant.copy(alpha = 0.6f))
                                )
                            )
                    ) {
                        if (fotoUrl.isNotEmpty() && !fotoUrl.startsWith("res:")) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(fotoUrl)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                loading = {
                                    Box(Modifier.fillMaxSize().background(shimmerBrush()), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).size(96.dp),
                                tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        if (isNew) {
                            Surface(
                                color = Color.Red,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.badge_new),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_status_available),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = fullName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (especializacion.isNotEmpty()) {
                            Text(
                                text = especializacion,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Rating row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFB300))
                            Spacer(modifier = Modifier.width(4.dp))
                            if (calificacion > 0) {
                                val ratingDisplay = (Math.round(calificacion * 10) / 10.0).toString()
                                Text(
                                    text = ratingDisplay,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface
                                )
                                if (nroCalificaciones > 0) {
                                    Text(
                                        text = " ($nroCalificaciones)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.mapa_sin_calificaciones),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Hourly value row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (valorHora > 0) {
                                Icon(Icons.Default.AttachMoney, null, modifier = Modifier.size(16.dp), tint = colors.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.mapa_valor_hora, formatearMiles(valorHora)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurface
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.mapa_valor_a_convenir),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Location row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            if (locationText.isNotEmpty()) {
                                Text(
                                    text = locationText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            if (!verificado) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        "Sin verificación oficial",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (comunas.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.user_card_comunas_interes, comunas.joinToString(", ")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }

                        if (verificado) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, modifier = Modifier.size(18.dp), tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.mapa_verificado),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }


                        if (horarios.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.user_card_schedule, horarios.joinToString(", ")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action row (consumes its own taps, does not trigger expand)
                        CuidaleActionRow(
                            onViewProfile = onClick,
                            hasChat = hasChat,
                            onSendSolicitud = onSendSolicitud,
                            onGoToChat = onGoToChat,
                            isSaved = isSaved,
                            onToggleSave = onToggleSave
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Centered collapse affordance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                // ===== COMPACT: compact row =====
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar bubble
                    Box(modifier = Modifier.size(64.dp)) {
                        Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = customColors.avatarBackground) {
                            if (fotoUrl.isNotEmpty() && !fotoUrl.startsWith("res:")) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(fotoUrl)
                                        .crossfade(true)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(Modifier.fillMaxSize().background(shimmerBrush()), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    }
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
                                    stringResource(R.string.badge_new),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        if (isSaved) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.BottomEnd).size(24.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(16.dp), tint = colors.error)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
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

                        if (especializacion.isNotEmpty()) {
                            Text(
                                text = especializacion,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (valorHora > 0) {
                            Text(
                                text = stringResource(R.string.mapa_valor_hora, formatearMiles(valorHora)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.mapa_valor_a_convenir),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun CuidalePacienteCard(
    nombre: String,
    apellido: String,
    fotoUrl: String,
    representanteFotoUrl: String,
    comuna: String,
    comunas: List<String>,
    edad: String,
    nivelDependencia: String,
    isNew: Boolean,
    verificado: Boolean,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onSendSolicitud: () -> Unit,
    hasChat: Boolean,
    onGoToChat: () -> Unit,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shadows = CuidaleThemeExtras.shadows; val colors = MaterialTheme.colorScheme; val customColors = CuidaleThemeExtras.customColors
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val fullName = listOf(nombre, apellido).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { stringResource(R.string.common_default_user) }
    val locationText = if (comuna.isNotEmpty()) comuna else comunas.joinToString(", ")
    val infoLine = buildString {
        if (edad.isNotEmpty()) {
            append(edad)
            append(stringResource(R.string.age_years_suffix))
        }
        if (locationText.isNotEmpty()) {
            if (isNotEmpty()) append(" • ")
            append(locationText)
        }
    }

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
            },
            label = "patientCard"
        ) { isExpanded ->
            if (isExpanded) {
                // ===== EXPANDED: full big card =====
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(colors.primaryContainer, colors.surfaceVariant.copy(alpha = 0.6f))
                                    )
                                )
                        ) {
                            // Mitad izquierda: foto del representante
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(colors.primaryContainer, colors.surfaceVariant.copy(alpha = 0.6f))
                                        )
                                    )
                            ) {
                                if (representanteFotoUrl.isNotEmpty() && !representanteFotoUrl.startsWith("res:")) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(representanteFotoUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(Modifier.fillMaxSize().background(shimmerBrush()), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                            }
                                        }
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                // Overlay label: representante
                                Surface(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.historial_representante),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Mitad derecha: foto del paciente
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(colors.primaryContainer, colors.surfaceVariant.copy(alpha = 0.6f))
                                        )
                                    )
                            ) {
                                if (fotoUrl.isNotEmpty() && !fotoUrl.startsWith("res:")) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(fotoUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(Modifier.fillMaxSize().background(shimmerBrush()), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                            }
                                        }
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                // Overlay label: paciente
                                Surface(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.historial_paciente),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (isNew) {
                            Surface(
                                color = Color.Red,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.badge_new),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = fullName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (edad.isNotEmpty()) {
                            Text(
                                text = "${edad}${stringResource(R.string.age_years_suffix)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }

                        if (nivelDependencia.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.user_card_dependency, nivelDependencia),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary
                            )
                        }

                        if (locationText.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = locationText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (verificado) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, modifier = Modifier.size(18.dp), tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.mapa_verificado),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action row (consumes its own taps, does not trigger expand)
                        CuidaleActionRow(
                            onViewProfile = onClick,
                            hasChat = hasChat,
                            onSendSolicitud = onSendSolicitud,
                            onGoToChat = onGoToChat,
                            isSaved = isSaved,
                            onToggleSave = onToggleSave
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Centered collapse affordance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                // ===== COMPACT: compact row =====
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar bubble
                    Box(modifier = Modifier.size(64.dp)) {
                        Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = customColors.avatarBackground) {
                            if (fotoUrl.isNotEmpty() && !fotoUrl.startsWith("res:")) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(fotoUrl)
                                        .crossfade(true)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(Modifier.fillMaxSize().background(shimmerBrush()), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    }
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
                                    stringResource(R.string.badge_new),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        if (isSaved) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.BottomEnd).size(24.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(16.dp), tint = colors.error)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
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

                        if (infoLine.isNotEmpty()) {
                            Text(
                                text = infoLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (nivelDependencia.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.user_card_dependency, nivelDependencia),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun CuidaleActionRow(
    onViewProfile: () -> Unit,
    hasChat: Boolean,
    onSendSolicitud: () -> Unit,
    onGoToChat: () -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val secondaryLabel = stringResource(if (hasChat) R.string.mapa_ir_al_chat else R.string.mapa_enviar_solicitud)
    val saveLabel = stringResource(if (isSaved) R.string.mapa_guardado else R.string.mapa_guardar)

    val viewProfileInteractionSource = remember { MutableInteractionSource() }
    val secondaryInteractionSource = remember { MutableInteractionSource() }
    val saveInteractionSource = remember { MutableInteractionSource() }
    val saveIconScale by animateFloatAsState(
        targetValue = if (isSaved) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "saveIconPop"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onViewProfile,
            interactionSource = viewProfileInteractionSource,
            modifier = Modifier.weight(1f).pressScale(viewProfileInteractionSource),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.solicitudes_ver_perfil),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = if (hasChat) onGoToChat else onSendSolicitud,
            interactionSource = secondaryInteractionSource,
            modifier = Modifier.weight(1f).pressScale(secondaryInteractionSource),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            onClick = onToggleSave,
            interactionSource = saveInteractionSource,
            shape = RoundedCornerShape(50),
            color = if (isSaved) colors.error.copy(alpha = 0.12f) else colors.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.pressScale(saveInteractionSource)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).graphicsLayer {
                        scaleX = saveIconScale
                        scaleY = saveIconScale
                    },
                    tint = if (isSaved) colors.error else colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = saveLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = if (isSaved) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SkeletonUserCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Transparent,
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(brush))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            }
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
