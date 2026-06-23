package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.EmailVerificationViewModel
import com.example.cuidaleapp.viewmodel.VerificationUiState
import java.util.concurrent.TimeUnit

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.cuidaleapp.R

@Composable
fun EmailVerificationScreen(
    isDark: Boolean,
    initialEmail: String = "",
    onNavigateBack: () -> Unit,
    onVerificationSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    viewModel: EmailVerificationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val email by viewModel.email.collectAsState()
    val code by viewModel.code.collectAsState()
    val timeLeft by viewModel.timeLeft.collectAsState()
    val attemptsLeft by viewModel.attemptsLeft.collectAsState()

    LaunchedEffect(initialEmail) {
        if (initialEmail.isNotEmpty() && email.isEmpty()) {
            viewModel.onEmailChange(initialEmail)
        }
    }

    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val stepsLabels = listOf("Correo", "Verificación", "Listo")
    
    val currentStepIndex = when(uiState) {
        is VerificationUiState.Step1Email -> 0
        is VerificationUiState.Step2Code -> 1
        is VerificationUiState.Success -> 2
        is VerificationUiState.Loading -> if (timeLeft > 0) 1 else 0
        is VerificationUiState.Error -> if (timeLeft > 0) 1 else 0
        is VerificationUiState.FailureExhausted -> 1
    }

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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = onGradientText
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Verificación de Cuenta",
                style = MaterialTheme.typography.displaySmall,
                color = onGradientText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            CuidaleStepProgressBar(
                steps = stepsLabels,
                currentStepIndex = currentStepIndex
            )

            Spacer(modifier = Modifier.height(32.dp))

            Crossfade(targetState = uiState, animationSpec = tween(500), label = "verificationTransition") { state ->
                val error = if (state is VerificationUiState.Error) state.message else null
                
                when (state) {
                    is VerificationUiState.Step1Email -> {
                        Step1Email(
                            email = email,
                            onEmailChange = viewModel::onEmailChange,
                            onSend = viewModel::sendVerificationCode,
                            isDark = isDark,
                            isLoading = false,
                            error = error
                        )
                    }
                    is VerificationUiState.Step2Code -> {
                        Step2Code(
                            code = code,
                            email = email,
                            timeLeft = timeLeft,
                            attemptsLeft = attemptsLeft,
                            onCodeChange = viewModel::onCodeChange,
                            onVerify = viewModel::verifyCode,
                            onResend = viewModel::sendVerificationCode,
                            onBack = viewModel::resetToStep1,
                            isDark = isDark,
                            isLoading = false,
                            error = error
                        )
                    }
                    is VerificationUiState.Loading -> {
                        if (timeLeft > 0) {
                            Step2Code(
                                code = code, email = email, timeLeft = timeLeft, attemptsLeft = attemptsLeft,
                                onCodeChange = {}, onVerify = {}, onResend = {}, onBack = {},
                                isDark = isDark, isLoading = true
                            )
                        } else {
                            Step1Email(email = email, onEmailChange = {}, onSend = {}, isDark = isDark, isLoading = true)
                        }
                    }
                    is VerificationUiState.Success -> {
                        SuccessContent(onVerificationSuccess)
                    }
                    is VerificationUiState.FailureExhausted -> {
                        FailureExhaustedContent(onRetry = viewModel::resetToStep1, isDark = isDark)
                    }
                    is VerificationUiState.Error -> {
                        if (timeLeft > 0) {
                            Step2Code(
                                code = code, email = email, timeLeft = timeLeft, attemptsLeft = attemptsLeft,
                                onCodeChange = viewModel::onCodeChange, onVerify = viewModel::verifyCode, 
                                onResend = viewModel::sendVerificationCode, onBack = viewModel::resetToStep1,
                                isDark = isDark, isLoading = false, error = error
                            )
                        } else {
                            Step1Email(
                                email = email, onEmailChange = viewModel::onEmailChange, 
                                onSend = viewModel::sendVerificationCode, isDark = isDark, 
                                isLoading = false, error = error
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun Step1Email(
    email: String,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    isDark: Boolean,
    isLoading: Boolean,
    error: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = panelShadowColor,
            isError = error != null
        ) {
            Text(
                text = "Ingresa tu correo para recibir un código de verificación.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            InputFieldLabel("Correo electrónico")
            
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            CuidaleTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = "ejemplo@correo.com",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                keyboardType = KeyboardType.Email,
                enabled = !isLoading,
                isError = error != null
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = colors.primary, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            HardShadowButton(
                text = "Enviar Código",
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shadowColor = panelShadowColor,
                icon = Icons.Default.Email,
                isDark = isDark,
                enabled = email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches(),
                onClick = onSend
            )
        }
    }
}

@Composable
fun Step2Code(
    code: String,
    email: String,
    timeLeft: Long,
    attemptsLeft: Int,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean,
    isLoading: Boolean,
    error: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary

    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = panelShadowColor,
            isError = error != null
        ) {
            Text(
                text = "Código de Verificación",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            val annotatedText = buildAnnotatedString {
                append("Hemos enviado un código a ")
                withStyle(style = SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) {
                    append(email)
                }
            }

            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Expira en: $timerText",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (timeLeft < 60000) colors.error else colors.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val attemptsColor by animateColorAsState(
                targetValue = if (attemptsLeft <= 1) colors.error else colors.onSurface,
                label = "attemptsColor"
            )

            Text(
                text = "Intentos restantes: $attemptsLeft",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = attemptsColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            InputFieldLabel("Código de 6 dígitos")

            if (error != null) {
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            OtpInputField(
                otpText = code,
                onOtpTextChange = onCodeChange,
                enabled = !isLoading,
                isError = error != null
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "¿No recibiste el código?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Reenviar código",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = CuidaleThemeExtras.customColors.linkColor,
                    modifier = Modifier.clickable(enabled = !isLoading) { onResend() }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = colors.primary, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            HardShadowButton(
                text = "Verificar",
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shadowColor = panelShadowColor,
                icon = Icons.Default.CheckCircle,
                isDark = isDark,
                enabled = code.length == 6,
                onClick = onVerify
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        HardShadowButton(
            text = "Cancelar / Cambiar Correo",
            containerColor = colors.secondary.copy(alpha = 0.8f),
            contentColor = colors.onSecondary,
            shadowColor = Color.Transparent,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            isDark = isDark,
            onClick = onBack,
            enabled = !isLoading
        )
    }
}

@Composable
fun FailureExhaustedContent(onRetry: () -> Unit, isDark: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = colors.errorContainer,
            shadowColor = colors.error.copy(alpha = 0.3f)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Proceso fallido",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Has agotado los intentos permitidos. Se enviará un nuevo código a tu correo para que lo intentes nuevamente.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onErrorContainer,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        HardShadowButton(
            text = "Entendido, reintentar",
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            shadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            isDark = isDark,
            onClick = onRetry
        )
    }
}

@Composable
fun SuccessContent(onFinish: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = CuidaleThemeExtras.customColors.onGradient
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "¡Cuenta Verificada!",
            style = MaterialTheme.typography.displaySmall,
            color = CuidaleThemeExtras.customColors.onGradient,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        HardShadowButton(
            text = "Ir al Login",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            isDark = false,
            onClick = onFinish
        )
    }
}

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit, isDark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = MaterialTheme.colorScheme.errorContainer,
            shadowColor = MaterialTheme.colorScheme.error
        ) {
            Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(32.dp))
        HardShadowButton(
            text = "Reintentar",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            icon = Icons.Default.LockReset,
            isDark = isDark,
            onClick = onRetry
        )
    }
}
