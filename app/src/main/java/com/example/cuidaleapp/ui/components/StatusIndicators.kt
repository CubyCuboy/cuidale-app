package com.example.cuidaleapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.ConnectivityObserver
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class ConnectivityState {
    NONE,
    OFF,
    RESTORED,
    UNSTABLE
}

private enum class BannerForm {
    HIDDEN,
    INLINE,
    CHIP,
    BUBBLE
}

@Composable
fun ConnectivityBanner(
    scrollState: androidx.compose.foundation.ScrollState? = null,
    lazyListState: LazyListState? = null,
    modifier: Modifier = Modifier
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    var wasOffline by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(ConnectivityState.NONE) }
    var bigFormVisible by remember { mutableStateOf(true) }
    val changes = remember { mutableStateListOf<Long>() }

    LaunchedEffect(isOnline) {
        val now = System.currentTimeMillis()
        changes.add(now)
        changes.removeAll { now - it > 5000L }
        val unstable = changes.size >= 3

        when {
            !isOnline -> {
                wasOffline = true
                state = ConnectivityState.OFF
                bigFormVisible = true
                // Forma grande (banner inline o chip) durante 5 s; luego burbuja persistente.
                delay(5000)
                if (state == ConnectivityState.OFF) bigFormVisible = false
            }
            unstable -> {
                state = ConnectivityState.UNSTABLE
                delay(3000)
                if (state == ConnectivityState.UNSTABLE) state = ConnectivityState.NONE
            }
            wasOffline -> {
                wasOffline = false
                state = ConnectivityState.RESTORED
                bigFormVisible = true
                delay(3000)
                if (state == ConnectivityState.RESTORED) state = ConnectivityState.NONE
            }
            else -> {
                state = ConnectivityState.NONE
            }
        }
    }

    val isScrolled = (scrollState?.value ?: 0) > 0 || (lazyListState?.firstVisibleItemIndex ?: 0) > 0

    LaunchedEffect(isScrolled) {
        com.example.cuidaleapp.utils.AppNavigationState.setScreenScrolled(isScrolled)
    }

    val form: BannerForm = when {
        state == ConnectivityState.NONE -> BannerForm.HIDDEN
        state == ConnectivityState.RESTORED || state == ConnectivityState.UNSTABLE -> BannerForm.INLINE
        bigFormVisible && isScrolled -> BannerForm.CHIP
        bigFormVisible -> BannerForm.INLINE
        else -> BannerForm.BUBBLE
    }

    AnimatedContent(
        targetState = form,
        transitionSpec = {
            (fadeIn(tween(220)) + expandVertically(tween(220))) togetherWith
                (fadeOut(tween(220)) + shrinkVertically(tween(220)))
        },
        modifier = modifier,
        label = "connectivityForm"
    ) { target ->
        if (target == BannerForm.HIDDEN) return@AnimatedContent

        val (text, color, icon) = when (state) {
            ConnectivityState.OFF -> Triple(stringResource(R.string.connectivity_offline), Color(0xFFD32F2F), Icons.Default.WifiOff)
            ConnectivityState.RESTORED -> Triple(stringResource(R.string.connectivity_restored), Color(0xFF4CAF50), Icons.Default.CloudDone)
            ConnectivityState.UNSTABLE -> Triple(stringResource(R.string.connectivity_unstable), Color(0xFFFB8C00), Icons.Default.Warning)
            else -> Triple("", Color.Transparent, Icons.Default.WifiOff)
        }

        when (target) {
            BannerForm.INLINE -> {
                // Banner en línea: ocupa espacio y desplaza el contenido.
                Surface(
                    color = color,
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            BannerForm.CHIP -> {
                // Chip flotante: se mantiene arriba sin desplazar (cuando hay scroll).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .zIndex(10f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        color = color,
                        shape = RoundedCornerShape(50),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            BannerForm.BUBBLE -> {
                // Burbuja compacta persistente: centrada por defecto, debajo de la barra de estado.
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = color.copy(alpha = 0.92f),
                        shape = CircleShape,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.connectivity_offline_short), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            else -> {}
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
