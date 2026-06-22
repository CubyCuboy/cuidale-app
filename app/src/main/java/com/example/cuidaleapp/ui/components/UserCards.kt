package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

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
