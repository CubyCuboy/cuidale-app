package com.example.cuidaleapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import kotlin.math.roundToInt

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
fun CuidaleCircularMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable (() -> Unit) = { Icon(imageVector = Icons.Default.Menu, contentDescription = null, tint = Color.White) }) {
    val shadows = CuidaleThemeExtras.shadows
    Box(modifier = modifier.size(56.dp).offset(x = 4.dp, y = 4.dp).background(shadows.hardShadowPrimary.copy(alpha = 0.3f), CircleShape)) { IconButton(onClick = onClick, modifier = Modifier.matchParentSize().offset(x = (-4).dp, y = (-4).dp).background(MaterialTheme.colorScheme.primary, CircleShape)) { icon() } }
}
