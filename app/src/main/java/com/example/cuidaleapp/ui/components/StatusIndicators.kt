package com.example.cuidaleapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.ConnectivityObserver
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ConnectivityBanner() {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    var showConnected by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (isOnline && wasOffline) {
            showConnected = true
            delay(3000)
            showConnected = false
            wasOffline = false
        } else if (!isOnline) {
            wasOffline = true
            showConnected = false
        }
    }

    AnimatedVisibility(
        visible = !isOnline || showConnected,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Surface(
            color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFD32F2F),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isOnline) "Conexión restablecida" else "Sin conexión a internet",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
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
