package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.cuidaleapp.R
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.ui.components.CuidaleTextField
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.InputFieldLabel
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.LoginUiState
import com.example.cuidaleapp.viewmodel.LoginViewModel
import com.example.cuidaleapp.viewmodel.UserData
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun LoginScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
    onNavigateToRegister: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    onNavigateToVerification: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Unverified) {
            onNavigateToVerification((uiState as LoginUiState.Unverified).email)
            viewModel.clearError()
        }
    }

    // Clave estable para el formulario para evitar que se borren los campos al cambiar de estado interno
    val screenKey = when (uiState) {
        is LoginUiState.Transition -> "transition"
        is LoginUiState.Success -> "success"
        else -> "form"
    }

    Crossfade(targetState = screenKey, label = "screenTransition", animationSpec = tween(600)) { key ->
        when (key) {
            "form" -> {
                LoginForm(
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
                    state = uiState,
                    onLogin = { id, pass -> viewModel.login(id, pass) },
                    onClearError = { viewModel.clearError() },
                    onForgotPassword = onForgotPassword,
                    onNavigateToRegister = onNavigateToRegister
                )
            }
            "transition" -> {
                val userData = (uiState as? LoginUiState.Transition)?.userData ?: UserData()
                TransitionScreen(userData = userData) {
                    viewModel.onTransitionFinished()
                }
            }
            "success" -> {
                // Pantalla de carga mínima mientras se abre la nueva actividad
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cargando...", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
fun LoginForm(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    state: LoginUiState,
    onLogin: (String, String) -> Unit,
    onClearError: () -> Unit,
    onForgotPassword: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var correoOrRut by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var keepSession by rememberSaveable { mutableStateOf(true) }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val gradient = CuidaleThemeExtras.gradient
    val shadows = CuidaleThemeExtras.shadows
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val customColors = CuidaleThemeExtras.customColors

    val onGradientText = customColors.onGradient
    val panelBg = customColors.panelBackground
    val inputBg = colors.surface

    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state is LoginUiState.Error) {
            repeat(6) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 12f else -12f,
                    animationSpec = tween(durationMillis = 60, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }

    val isError = state is LoginUiState.Error
    val isLoading = state is LoginUiState.Loading

    val loginButtonColor = if (isError) colors.error else if (isDark) customColors.warning else colors.primary
    val loginButtonShadow = if (isError) colors.errorContainer else if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Theme Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onToggleTheme) {
                    Crossfade(targetState = isDark, animationSpec = tween(400), label = "themeAnim") { dark ->
                        Icon(
                            imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Cambiar modo",
                            tint = onGradientText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Iniciar Sesión",
                style = typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = onGradientText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Logo Animado Placeholder
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "LOGO", style = typography.headlineSmall, color = onGradientText)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.login_welcome),
                style = typography.bodyLarge,
                color = onGradientText.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowPanel(
                backgroundColor = if (isError) colors.errorContainer.copy(alpha = 0.95f) else panelBg,
                shadowColor = if (isError) colors.error else panelShadowColor,
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            ) {
                InputFieldLabel(stringResource(R.string.login_identifier_label))

                CuidaleTextField(
                    value = correoOrRut,
                    onValueChange = { 
                        correoOrRut = it.lowercase()
                        onClearError()
                    },
                    placeholder = stringResource(R.string.login_identifier_placeholder),
                    containerColor = inputBg,
                    isError = isError
                )

                Spacer(modifier = Modifier.height(16.dp))

                InputFieldLabel(stringResource(R.string.login_password_label))

                CuidaleTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        onClearError()
                    },
                    placeholder = stringResource(R.string.login_password_placeholder),
                    containerColor = inputBg,
                    isPassword = true,
                    isPasswordVisible = isPasswordVisible,
                    onPasswordVisibilityChange = { isPasswordVisible = !isPasswordVisible },
                    isError = isError
                )

                AnimatedVisibility(visible = isError) {
                    Text(
                        text = (state as? LoginUiState.Error)?.message ?: "",
                        color = colors.onErrorContainer,
                        style = typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = keepSession,
                        onCheckedChange = { keepSession = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.primary,
                            uncheckedColor = colors.outline,
                            checkmarkColor = colors.onPrimary
                        )
                    )
                    Text(text = stringResource(R.string.login_keep_session), style = typography.labelLarge, color = colors.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowButton(
                text = if (isLoading) stringResource(R.string.login_button_loading) else if (isError) stringResource(R.string.login_button_retry) else stringResource(R.string.login_button_enter),
                containerColor = loginButtonColor,
                contentColor = if (isError) Color.White else colors.onPrimary,
                shadowColor = loginButtonShadow,
                icon = if (isError) Icons.Filled.Block else Icons.AutoMirrored.Outlined.Login,
                isErrorMode = isError,
                isDark = isDark,
                onClick = { if (!isLoading) onLogin(correoOrRut, password) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.login_forgot_password),
                style = typography.labelLarge,
                color = onGradientText,
                modifier = Modifier.clickable { onForgotPassword() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.login_no_account), style = typography.bodyMedium, color = onGradientText)
                Text(
                    text = stringResource(R.string.login_register_here),
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = onGradientText,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
            Spacer(modifier = Modifier.height(56.dp))
        }

        Text(
            text = "v0.01",
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            style = typography.labelSmall,
            color = onGradientText.copy(alpha = 0.55f)
        )
    }
}

@Composable
fun TransitionScreen(userData: UserData, onFinished: () -> Unit) {
    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient

    LaunchedEffect(Unit) {
        delay(2500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.login_welcome_back),
                style = MaterialTheme.typography.displayMedium,
                color = onGradientText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${userData.nombre} ${userData.apellido_pa}",
                style = MaterialTheme.typography.headlineMedium,
                color = onGradientText.copy(alpha = 0.9f)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoginPreviewLight() {
    CuidaleAppTheme(darkTheme = false) {
        LoginScreen(isDark = false, onToggleTheme = {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoginPreviewDark() {
    CuidaleAppTheme(darkTheme = true) {
        LoginScreen(isDark = true, onToggleTheme = {})
    }
}
