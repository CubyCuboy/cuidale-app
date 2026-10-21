package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.components.ProviderBrandIcon
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.LoginUiState
import com.example.cuidaleapp.viewmodel.LoginViewModel
import com.example.cuidaleapp.models.UserData
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun LoginScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
    onForgotPassword: () -> Unit = {},
    onRegister: () -> Unit = {},
    onVerify: (String) -> Unit = {},
    onGoogleLogin: () -> Unit = {},
    onMicrosoftLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    val screenKey = when (uiState) {
        is LoginUiState.Success -> "success"
        else -> "form"
    }

    Crossfade(targetState = screenKey, label = "screenTransition", animationSpec = tween(600, easing = LinearOutSlowInEasing)) { key ->
        when (key) {
            "form" -> {
                LoginForm(
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
                    state = uiState,
                    onLogin = { id, pass, keep -> viewModel.login(id, pass, keep) },
                    onClearError = { viewModel.clearError() },
                    onForgotPassword = onForgotPassword,
                    onRegister = onRegister,
                    onVerify = onVerify,
                    onGoogleLogin = onGoogleLogin,
                    onMicrosoftLogin = onMicrosoftLogin
                )
            }
            "success" -> {
                LoginLoadingScreen()
            }
        }
    }
}

@Composable
fun LoginForm(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    state: LoginUiState,
    onLogin: (String, String, Boolean) -> Unit,
    onClearError: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onVerify: (String) -> Unit,
    onGoogleLogin: () -> Unit,
    onMicrosoftLogin: () -> Unit
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
    val inputBg = customColors.inputBackground

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

    val loginButtonColor = if (isError) colors.error else if (isDark) colors.primary else colors.primary
    val loginButtonShadow = if (isError) colors.errorContainer else if (isDark) shadows.hardShadowPrimary else shadows.hardShadowPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Text(
                text = stringResource(R.string.login_title),
                style = typography.displaySmall,
                color = onGradientText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            CuidaleAppLogo(
                isDark = isDark,
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowPanel(
                backgroundColor = if (isError) colors.errorContainer.copy(alpha = 0.95f) else panelBg,
                shadowColor = if (isError) colors.error else panelShadowColor,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_welcome),
                    style = typography.bodyLarge,
                    color = colors.onSurface.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                )

                InputFieldLabel(stringResource(R.string.login_identifier_label))

                CuidaleTextField(
                    value = correoOrRut,
                    onValueChange = {
                        correoOrRut = it.lowercase()
                        onClearError()
                    },
                    placeholder = stringResource(R.string.login_identifier_placeholder),
                    containerColor = inputBg,
                    isError = isError,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )

                Spacer(modifier = Modifier.height(16.dp))

                val needsVerification = (state as? LoginUiState.Error)?.message?.contains("verificar") == true

                if (!needsVerification) {
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
                        isError = isError,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (correoOrRut.isNotBlank() && password.isNotBlank()) {
                                    onLogin(correoOrRut, password, keepSession)
                                }
                            }
                        )
                    )
                }

                AnimatedVisibility(visible = isError) {
                    Text(
                        text = (state as? LoginUiState.Error)?.message ?: "",
                        color = if (needsVerification) colors.primary else colors.onErrorContainer,
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

            Spacer(modifier = Modifier.height(24.dp))

            val needsVerification = (state as? LoginUiState.Error)?.message?.contains("verificar") == true

            HardShadowButton(
                text = if (isLoading) stringResource(R.string.login_button_loading)
                else if (needsVerification) "Verificar Cuenta"
                else if (isError) stringResource(R.string.login_button_retry)
                else stringResource(R.string.login_button_enter),
                containerColor = loginButtonColor,
                contentColor = if (isError && !needsVerification) Color.White else colors.onPrimary,
                shadowColor = loginButtonShadow,
                icon = if (needsVerification) Icons.Default.Email else if (isError) Icons.Filled.Block else Icons.AutoMirrored.Outlined.Login,
                isErrorMode = isError && !needsVerification,
                isDark = isDark,
                onClick = {
                    if (!isLoading) {
                        if (needsVerification) {
                            onVerify(correoOrRut)
                        } else {
                            onLogin(correoOrRut, password, keepSession)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onGoogleLogin,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text("Google", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onMicrosoftLogin,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F2F2F)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text("Microsoft", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.login_forgot_password),
                style = typography.labelLarge,
                color = onGradientText,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onForgotPassword() }
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = typography.bodyMedium,
                    color = if (isDark) onGradientText else Color(0xFF0D47A1)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.login_register_here),
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) customColors.linkColor else Color(0xFF1976D2),
                    modifier = Modifier.clickable { onRegister() }
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun LoginLoadingScreen() {
    val gradient = CuidaleThemeExtras.gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedLoadingDots(
                text = "Cargando",
                color = CuidaleThemeExtras.customColors.onGradient,
                modifier = Modifier.fillMaxWidth()
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