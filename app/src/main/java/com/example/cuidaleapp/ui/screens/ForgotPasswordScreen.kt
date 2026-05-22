package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.ui.components.CuidaleTextField
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ForgotPasswordStatus
import com.example.cuidaleapp.viewmodel.ForgotPasswordStep
import com.example.cuidaleapp.viewmodel.ForgotPasswordViewModel
import java.util.Locale

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBackToLogin: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val status by viewModel.status.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "Recuperación de tu contraseña",
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
                RecoveryProgressBar(currentStep = currentStep)
                Spacer(modifier = Modifier.height(32.dp))
            }

            Crossfade(targetState = currentStep, label = "forgotPasswordStep") { step ->
                when (step) {
                    ForgotPasswordStep.EMAIL -> {
                        EmailStep(
                            isLoading = status is ForgotPasswordStatus.Loading,
                            isDark = isDark,
                            error = (status as? ForgotPasswordStatus.Error)?.message,
                            onSend = { viewModel.sendVerificationCode(it) },
                            onClearError = { viewModel.clearError() }
                        )
                    }
                    ForgotPasswordStep.CODE -> {
                        CodeStep(
                            email = viewModel.lastRequestedEmail.collectAsState().value,
                            timerSeconds = viewModel.timerSeconds.collectAsState().value,
                            isLoading = status is ForgotPasswordStatus.Loading,
                            error = (status as? ForgotPasswordStatus.Error)?.message,
                            isDark = isDark,
                            onVerify = { viewModel.verifyCode(it) },
                            onResend = { viewModel.sendVerificationCode(viewModel.lastRequestedEmail.value) },
                            onCancel = { viewModel.resetToStart() },
                            onClearError = { viewModel.clearError() }
                        )
                    }
                    ForgotPasswordStep.PASSWORD -> {
                        NewPasswordStep(
                            isLoading = status is ForgotPasswordStatus.Loading,
                            error = (status as? ForgotPasswordStatus.Error)?.message,
                            isDark = isDark,
                            onUpdate = { viewModel.updatePassword(it) },
                            onBack = { viewModel.goToStep(ForgotPasswordStep.CODE) },
                            onClearError = { viewModel.clearError() }
                        )
                    }
                    ForgotPasswordStep.SUCCESS -> {
                        SuccessStep(isDark = isDark, onLogin = onBackToLogin)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (currentStep != ForgotPasswordStep.SUCCESS) {
                Row(
                    modifier = Modifier.padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "¿Ya tienes cuenta? ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onGradientText
                    )
                    Text(
                        text = "Ingresa aquí",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = onGradientText,
                        modifier = Modifier.clickable { onBackToLogin() }
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
    var email by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Para iniciar la recuperación de tu contraseña, ingresa el correo que usaste al registrarte. Te enviaremos un código de recuperación.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            Text(
                text = "Ingresa su correo electrónico",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            CuidaleTextField(
                value = email,
                onValueChange = { 
                    email = it
                    if (error != null) onClearError()
                },
                placeholder = "Ej: correo@correo.cl",
                containerColor = customColors.inputBackground,
                isError = error != null
            )

            if (error != null) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
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
    var code by remember { mutableStateOf("") }
    val minutes = timerSeconds / 60
    val seconds = timerSeconds % 60
    val timerText = String.format(Locale.getDefault(), "[%d:%02d]", minutes, seconds)
    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val colors = MaterialTheme.colorScheme

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ingresa el código de expiración",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        HardShadowPanel(
            backgroundColor = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFDFF6E5), // Light green
            shadowColor = Color.Transparent
        ) {
            Text(
                text = "¡Todo listo! Hemos enviado un código de recuperación a $email. Tiene una validez de 5 minutos.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) customColors.success else Color(0xFF1B5E20),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "El código expira en $timerText",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) customColors.success else Color(0xFF1B5E20),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            Text(
                text = "Ingrese el código de recuperación",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            CuidaleTextField(
                value = code,
                onValueChange = { 
                    code = it
                    if (error != null) onClearError()
                },
                placeholder = "Código de recuperación",
                containerColor = customColors.inputBackground,
                isError = error != null
            )

            if (error != null) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "¿Se venció el código?",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Text(
            text = "Presiona aquí para reenviarlo.",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.clickable { onResend() }
        )

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowButton(
            text = if (isLoading) "Verificando..." else "Verificar código",
            containerColor = Color(0xFF4A56E2),
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            onClick = { if (!isLoading) onVerify(code) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        HardShadowButton(
            text = "Cancelar",
            containerColor = Color(0xFF4A56E2),
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onCancel
        )
    }
}

@Composable
fun NewPasswordStep(
    isLoading: Boolean,
    error: String?,
    isDark: Boolean,
    onUpdate: (String) -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val colors = MaterialTheme.colorScheme
    
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasSpecialChar = password.any { !it.isLetterOrDigit() }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    
    val allRequirementsMet = hasMinLength && hasUppercase && hasSpecialChar

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Establece su nueva contraseña para su cuenta",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        val requirementsPanelBg = if (allRequirementsMet) {
            if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFDFF6E5)
        } else {
            if (isDark) Color.White.copy(alpha = 0.05f) else Color.White
        }

        HardShadowPanel(
            backgroundColor = requirementsPanelBg,
            shadowColor = Color.Transparent
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ValidationItem(text = "Mínimo 8 caracteres", isValid = hasMinLength, isDark = isDark)
                ValidationItem(text = "Al menos una mayúscula", isValid = hasUppercase, isDark = isDark)
                ValidationItem(text = "Un carácter especial", isValid = hasSpecialChar, isDark = isDark)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            Text(
                text = "Introduzca su contraseña",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (passwordsMatch) Color(0xFF4A56E2) else Color.LightGray, MaterialTheme.shapes.small)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (passwordsMatch) "✓ La contraseña coincide" else "Las contraseñas deben coincidir",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Repita nuevamente su contraseña",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) customColors.onGradient.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            CuidaleTextField(
                value = confirmPassword,
                onValueChange = { 
                    confirmPassword = it
                    if (error != null) onClearError()
                },
                placeholder = "Reingrese su contraseña",
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
                    modifier = Modifier.padding(top = 4.dp)
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
            onClick = { if (!isLoading && passwordsMatch && hasMinLength && hasUppercase && hasSpecialChar) onUpdate(password) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        HardShadowButton(
            text = "Retroceder",
            containerColor = Color(0xFF4A56E2),
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack
        )
    }
}

@Composable
fun RecoveryProgressBar(currentStep: ForgotPasswordStep) {
    val steps = listOf(
        ForgotPasswordStep.EMAIL to "Paso 1",
        ForgotPasswordStep.CODE to "Paso 2",
        ForgotPasswordStep.PASSWORD to "Paso 3"
    )
    val customColors = CuidaleThemeExtras.customColors
    val successColor = customColors.successStrong

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (step, label) ->
            val isActive = currentStep == step || 
                           (currentStep == ForgotPasswordStep.CODE && index < 1) ||
                           (currentStep == ForgotPasswordStep.PASSWORD && index < 2)
            
            val isCurrent = currentStep == step
            val isCompleted = isActive && !isCurrent

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = successColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) successColor else Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal),
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f)
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    val isLineFilled = (currentStep == ForgotPasswordStep.CODE && index < 1) ||
                                      (currentStep == ForgotPasswordStep.PASSWORD && index < 2)
                    
                    if (isLineFilled) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ValidationItem(text: String, isValid: Boolean, isDark: Boolean) {
    val customColors = CuidaleThemeExtras.customColors
    val successColor = customColors.successStrong
    val inactiveColor = if (isDark) Color.Gray else Color.Black.copy(alpha = 0.55f)
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (isValid) successColor else inactiveColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) successColor else inactiveColor
        )
    }
}

@Composable
fun SuccessStep(isDark: Boolean, onLogin: () -> Unit) {
    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors

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
            shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            Text(
                text = "¡Felicitaciones! Tu contraseña fue restablecida correctamente. Ya puedes iniciar sesión y usar la plataforma con normalidad.",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) customColors.success else Color(0xFF1B5E20),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        HardShadowButton(
            text = "Iniciar sesión",
            containerColor = Color(0xFF4A56E2),
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = isDark,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            onClick = onLogin
        )
    }
}
