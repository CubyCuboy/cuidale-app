package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ForgotPasswordStatus
import com.example.cuidaleapp.viewmodel.ForgotPasswordStep
import com.example.cuidaleapp.viewmodel.ForgotPasswordViewModel
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBackToLogin: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val status by viewModel.status.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val lastRequestedEmail by viewModel.lastRequestedEmail.collectAsState()
    val unmetRequirements by viewModel.unmetRequirementsTrigger.collectAsState()

    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val isLoading = status is ForgotPasswordStatus.Loading
    val errorMessage = (status as? ForgotPasswordStatus.Error)?.message

    val recoverySteps = listOf("Paso 1", "Paso 2", "Paso 3")

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
            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "Recupere su contraseña",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp
                ),
                color = onGradientText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (currentStep != ForgotPasswordStep.SUCCESS) {
                CuidaleStepProgressBar(
                    steps = recoverySteps,
                    currentStepIndex = when (currentStep) {
                        ForgotPasswordStep.EMAIL -> 0
                        ForgotPasswordStep.CODE -> 1
                        ForgotPasswordStep.PASSWORD -> 2
                        else -> 0
                    }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Crossfade(targetState = currentStep, label = "forgotPasswordStep", animationSpec = tween(500)) { step ->
                    when (step) {
                        ForgotPasswordStep.EMAIL -> {
                            EmailStep(
                                isLoading = isLoading,
                                isDark = isDark,
                                error = errorMessage,
                                onSend = { viewModel.sendVerificationCode(it) },
                                onClearError = { viewModel.clearError() }
                            )
                        }

                        ForgotPasswordStep.CODE -> {
                            CodeStep(
                                email = lastRequestedEmail,
                                timerSeconds = timerSeconds,
                                isLoading = isLoading,
                                error = errorMessage,
                                isDark = isDark,
                                onVerify = { viewModel.verifyCode(it) },
                                onResend = {
                                    if (!isLoading && lastRequestedEmail.isNotBlank()) {
                                        viewModel.sendVerificationCode(lastRequestedEmail)
                                    }
                                },
                                onCancel = {
                                    if (!isLoading) viewModel.resetToStart()
                                },
                                onClearError = { viewModel.clearError() }
                            )
                        }

                        ForgotPasswordStep.PASSWORD -> {
                            NewPasswordStep(
                                isLoading = isLoading,
                                error = errorMessage,
                                unmetRequirements = unmetRequirements,
                                isDark = isDark,
                                onUpdate = { p, c -> viewModel.updatePassword(p, c) },
                                onRestart = { viewModel.resetToStart() },
                                onClearError = { viewModel.clearError() }
                            )
                        }

                        ForgotPasswordStep.SUCCESS -> {
                            SuccessStep(isDark = isDark, onLogin = onBackToLogin)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (currentStep != ForgotPasswordStep.SUCCESS) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¿Ya tiene cuenta? ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onGradientText
                    )
                    Text(
                        text = "Inicie sesión aquí",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.clickable {
                            if (!isLoading) onBackToLogin()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmailStep(
    isLoading: Boolean,
    isDark: Boolean,
    error: String?,
    onSend: (String) -> Unit,
    onClearError: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Para iniciar el proceso de recuperación, por favor ingrese la dirección de correo electrónico asociada a su cuenta. Le enviaremos un código de seguridad.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = panelShadowColor.copy(alpha = 0.6f)
        ) {
            Text(
                text = "Ingrese tu correo electrónico",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            CuidaleTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (error != null) onClearError()
                },
                placeholder = "Ej: correo@correo.cl",
                containerColor = customColors.inputBackground,
                isError = error != null,
                keyboardType = KeyboardType.Email
            )

            if (error != null) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowButton(
            text = if (isLoading) "Enviando..." else "Enviar código",
            containerColor = Color(0xFF4A56E2),
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            onClick = { if (!isLoading) onSend(email) }
        )
    }
}

@Composable
fun CodeStep(
    email: String,
    timerSeconds: Int,
    isLoading: Boolean,
    error: String?,
    isDark: Boolean,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    val minutes = timerSeconds / 60
    val seconds = timerSeconds % 60
    val locale = currentJavaLocale()
    val timerText = String.format(locale, "%d:%02d", minutes, seconds)

    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val colors = MaterialTheme.colorScheme
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ingresa el código de recuperación",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        HardShadowPanel(
            backgroundColor = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFDFF6E5),
            shadowColor = Color.Transparent
        ) {
            Text(
                text = "Se ha enviado un código de seguridad a la dirección: $email. Este código será válido durante los próximos 5 minutos.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) customColors.success else Color(0xFF1B5E20),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = if (isDark) customColors.success else Color(0xFF1B5E20),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "El código expira en $timerText",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) customColors.success else Color(0xFF1B5E20),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = panelShadowColor.copy(alpha = 0.6f)
        ) {
            Text(
                text = "Ingrese el código de recuperación",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            CuidaleTextField(
                value = code,
                onValueChange = {
                    val filtered = it.filter(Char::isDigit).take(6)
                    code = filtered
                    if (error != null) onClearError()
                },
                placeholder = "Ingrese los 6 dígitos",
                containerColor = customColors.inputBackground,
                isError = error != null,
                textAlign = TextAlign.Center,
                keyboardType = KeyboardType.Number
            )

            if (error != null) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = !isLoading) { onResend() }
        ) {
            Text(
                text = "¿No ha recibido el código? ",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = if (isLoading) "Procesando..." else "Solicitar uno nuevo",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowButton(
            text = if (isLoading) "Verificando..." else "Verificar identidad",
            containerColor = if (isDark) customColors.warning else colors.primary,
            contentColor = if (isDark) Color.Black else Color.White,
            shadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            onClick = { if (!isLoading) onVerify(code) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Volver y cancelar",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.clickable { if (!isLoading) onCancel() }
        )
    }
}

@Composable
fun NewPasswordStep(
    isLoading: Boolean,
    error: String?,
    unmetRequirements: Set<Int>,
    isDark: Boolean,
    onUpdate: (String, String) -> Unit,
    onRestart: () -> Unit,
    onClearError: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val colors = MaterialTheme.colorScheme
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasSpecialChar = password.any { !it.isLetterOrDigit() }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    val validations = listOf(
        "Mínimo 8 caracteres" to hasMinLength,
        "Al menos una mayúscula" to hasUppercase,
        "Un carácter especial" to hasSpecialChar,
        "Las contraseñas coinciden" to passwordsMatch
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Defina su nueva contraseña",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            CuidaleValidationPanel(
                validations = validations,
                unmetIndices = unmetRequirements,
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Introduzca su contraseña",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            CuidaleTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (error != null) onClearError()
                },
                placeholder = "Ingrese su contraseña",
                containerColor = customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = passwordVisible,
                onPasswordVisibilityChange = { passwordVisible = !passwordVisible }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Repita nuevamente su contraseña",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            CuidaleTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    if (error != null) onClearError()
                },
                placeholder = "Reingrese tu contraseña",
                containerColor = customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = confirmPasswordVisible,
                onPasswordVisibilityChange = { confirmPasswordVisible = !confirmPasswordVisible }
            )

            if (error != null) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowButton(
            text = if (isLoading) "Actualizando..." else "Actualizar la contraseña",
            containerColor = Color(0xFF4A56E2),
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            onClick = { if (!isLoading) onUpdate(password, confirmPassword) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        HardShadowButton(
            text = "Cancelar y reiniciar",
            containerColor = Color(0xFF4A56E2),
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = { if (!isLoading) onRestart() }
        )
    }
}

@Composable
fun SuccessStep(isDark: Boolean, onLogin: () -> Unit) {
    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val colors = MaterialTheme.colorScheme
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Se cambió la contraseña con éxito",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = panelShadowColor.copy(alpha = 0.6f)
        ) {
            Text(
                text = "Tu contraseña fue restablecida correctamente. Ya puedes iniciar sesión y usar la plataforma con normalidad.",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) customColors.success else Color(0xFF1B5E20),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        HardShadowButton(
            text = "Iniciar sesión",
            containerColor = if (isDark) customColors.warning else colors.primary,
            contentColor = if (isDark) Color.Black else Color.White,
            shadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            onClick = onLogin
        )
    }
}

@Composable
private fun currentJavaLocale(): Locale {
    val configuration = LocalConfiguration.current
    return ConfigurationCompat.getLocales(configuration).get(0) ?: LocalLocale.current.platformLocale
}