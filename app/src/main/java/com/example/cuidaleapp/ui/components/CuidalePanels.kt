package com.example.cuidaleapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import kotlin.math.roundToInt

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
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .padding(bottom = shadowOffset, end = shadowOffset)
    ) {
        // Shadow layer - Now correctly sized relative to the content
        Surface(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset),
            shape = RoundedCornerShape(cornerRadius),
            color = animatedShadow
        ) {}

        // Main panel
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
