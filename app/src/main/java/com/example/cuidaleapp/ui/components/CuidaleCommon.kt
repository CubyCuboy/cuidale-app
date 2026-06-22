package com.example.cuidaleapp.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.R

@Composable
fun CuidalePlaceholder(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.9f) + fadeIn(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp).fillMaxWidth()
            ) {
                Surface(
                    color = iconColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = iconColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )
                
                if (actionText != null && onActionClick != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    HardShadowButton(
                        text = actionText,
                        onClick = onActionClick,
                        modifier = Modifier.widthIn(min = 200.dp).height(50.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OfflinePlaceholder(
    title: String = "No hay conexión",
    subtitle: String = "Esta sección requiere internet para cargar datos actualizados.",
    icon: ImageVector = Icons.Default.CloudOff
) {
    CuidalePlaceholder(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = Color.Gray
    )
}

fun Modifier.fadingEdge(brush: Brush) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }

@Composable
fun CuidaleAppLogo(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val logoRes = if (isDark) R.drawable.ic_app_logo_dark else R.drawable.ic_app_logo_light
    
    Image(
        painter = painterResource(id = logoRes),
        contentDescription = "Cuidale Logo",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
