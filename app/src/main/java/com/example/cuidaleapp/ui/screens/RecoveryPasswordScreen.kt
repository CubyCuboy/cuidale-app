package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.RecoveryViewModel
import com.example.cuidaleapp.viewmodel.RecoveryUiState

enum class RecoveryStep {
    EMAIL, CODE, PASSWORD, SUCCESS
}

@Composable
fun RecoveryPasswordScreen(
    viewModel: RecoveryViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit,
    fromProfile: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val uiState by viewModel.uiState.collectAsState()
    val email by viewModel.email.collectAsState()
    
    var currentStep by rememberSaveable { mutableStateOf(RecoveryStep.EMAIL) }
    
    LaunchedEffect(uiState) {
        when(uiState) {
            is RecoveryUiState.CodeSent -> currentStep = RecoveryStep.CODE
            is RecoveryUiState.CodeVerified -> currentStep = RecoveryStep.PASSWORD
            is RecoveryUiState.Success -> currentStep = RecoveryStep.SUCCESS
            else -> {}
        }
    }
    
    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val typography = MaterialTheme.typography

    val stepsLabels = listOf(
        stringResource(R.string.forgot_password_step_email),
        stringResource(R.string.forgot_password_step_code),
        stringResource(R.string.forgot_password_step_pass),
        stringResource(R.string.forgot_password_step_success)
    )
    val currentStepIndex = when(currentStep) {
        RecoveryStep.EMAIL -> 0
        RecoveryStep.CODE -> 1
        RecoveryStep.PASSWORD -> 2
        RecoveryStep.SUCCESS -> 3
    }

    val backButtonLabel = if (fromProfile) stringResource(R.string.recovery_back_to_profile) else stringResource(R.string.forgot_password_cancel_button)

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
                        contentDescription = backButtonLabel,
                        tint = onGradientText
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.forgot_password_screen_title),
                style = typography.displaySmall,
                color = onGradientText,
                textAlign = TextAlign.Center,
                lineHeight = typography.displaySmall.lineHeight
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            CuidaleStepProgressBar(
                steps = stepsLabels,
                currentStepIndex = currentStepIndex
            )

            Spacer(modifier = Modifier.height(32.dp))

            Crossfade(targetState = currentStep, animationSpec = tween(500), label = "stepTransition") { step ->
                val error = if (uiState is RecoveryUiState.Error) (uiState as RecoveryUiState.Error).message else null
                
                when (step) {
                    RecoveryStep.EMAIL -> RecoveryEmailStep(
                        isDark = isDark,
                        isLoading = uiState is RecoveryUiState.Loading,
                        email = email,
                        error = error,
                        onEmailChange = viewModel::onEmailChange,
                        onNext = viewModel::sendRecoveryCode
                    )
                    RecoveryStep.CODE -> RecoveryCodeStep(
                        isDark = isDark,
                        isLoading = uiState is RecoveryUiState.Loading,
                        email = email,
                        code = viewModel.code.collectAsState().value,
                        error = error,
                        onCodeChange = viewModel::onCodeChange,
                        timerText = viewModel.timerText.value,
                        attemptsRemaining = viewModel.attemptsRemaining.value,
                        onVerify = viewModel::verifyCode,
                        onResend = viewModel::sendRecoveryCode,
                        onBack = { currentStep = RecoveryStep.EMAIL }
                    )
                    RecoveryStep.PASSWORD -> RecoveryNewPasswordStep(
                        isDark = isDark,
                        isLoading = uiState is RecoveryUiState.Loading,
                        error = error,
                        onUpdate = viewModel::updatePassword,
                        onBack = { currentStep = RecoveryStep.CODE }
                    )
                    RecoveryStep.SUCCESS -> RecoverySuccessStep(
                        onLogin = onFinish
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            if (currentStep != RecoveryStep.SUCCESS) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.forgot_password_footer_prefix),
                        style = typography.bodyMedium,
                        color = onGradientText
                    )
                    Text(
                        text = stringResource(R.string.forgot_password_footer_link),
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = CuidaleThemeExtras.customColors.linkColor,
                        modifier = Modifier.clickable { onNavigateBack() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

@Composable
fun RecoveryEmailStep(
    isDark: Boolean,
    isLoading: Boolean,
    email: String,
    error: String? = null,
    onEmailChange: (String) -> Unit,
    onNext: () -> Unit
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
                text = stringResource(R.string.forgot_password_email_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            InputFieldLabel(stringResource(R.string.forgot_password_email_label))
            
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            CuidaleTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.forgot_password_email_placeholder),
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                enabled = !isLoading,
                isError = error != null,
                keyboardType = KeyboardType.Email
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (isLoading) {
            CircularProgressIndicator(color = colors.primary, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            HardShadowButton(
                text = stringResource(R.string.forgot_password_send_code),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                isDark = isDark,
                isErrorMode = error != null,
                errorText = error,
                onClick = onNext
            )
        }
    }
}

@Composable
fun RecoveryCodeStep(
    isDark: Boolean,
    isLoading: Boolean,
    email: String,
    code: String,
    error: String? = null,
    onCodeChange: (String) -> Unit,
    timerText: String,
    attemptsRemaining: Int,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
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
                text = stringResource(R.string.forgot_password_code_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            val fullText = stringResource(R.string.forgot_password_code_desc, email)
            val emailIndex = fullText.indexOf(email)
            
            val annotatedText = buildAnnotatedString {
                if (emailIndex != -1) {
                    append(fullText.substring(0, emailIndex))
                    withStyle(style = SpanStyle(
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(email)
                    }
                    append(fullText.substring(emailIndex + email.length))
                } else {
                    append(fullText)
                }
            }

            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.forgot_password_code_expiry, timerText),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val attemptsColor by animateColorAsState(
                targetValue = if (attemptsRemaining <= 1) colors.error else colors.onSurface,
                label = "attemptsColor"
            )

            Text(
                text = stringResource(R.string.forgot_password_code_attempts, attemptsRemaining),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = attemptsColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            InputFieldLabel(stringResource(R.string.forgot_password_code_label))

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
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
                    text = stringResource(R.string.forgot_password_code_expired_q),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(R.string.forgot_password_code_resend),
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
                text = stringResource(R.string.forgot_password_verify_button),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                isDark = isDark,
                isErrorMode = error != null,
                errorText = error,
                onClick = onVerify
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HardShadowButton(
            text = stringResource(R.string.forgot_password_cancel_button),
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
fun RecoveryNewPasswordStep(
    isDark: Boolean,
    isLoading: Boolean,
    error: String? = null,
    onUpdate: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    val panelShadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary
    
    var pass by rememberSaveable { mutableStateOf("") }
    var confirmPass by rememberSaveable { mutableStateOf("") }
    var isPassVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPassVisible by rememberSaveable { mutableStateOf(false) }

    val passMatches = pass.isNotEmpty() && pass == confirmPass

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = panelShadowColor,
            isError = error != null
        ) {
            Text(
                text = stringResource(R.string.forgot_password_pass_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            CuidaleValidationPanel(
                validations = listOf(
                    stringResource(R.string.password_req_length) to (pass.length >= 8),
                    stringResource(R.string.password_req_upper) to pass.any { it.isUpperCase() },
                    stringResource(R.string.password_req_special) to pass.any { !it.isLetterOrDigit() },
                    stringResource(R.string.password_req_match) to passMatches
                ),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(24.dp))

            InputFieldLabel(stringResource(R.string.forgot_password_pass_label))
            CuidaleTextField(
                value = pass,
                onValueChange = { pass = it },
                placeholder = stringResource(R.string.forgot_password_pass_placeholder),
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = isPassVisible,
                onPasswordVisibilityChange = { isPassVisible = !isPassVisible },
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.forgot_password_confirm_pass_label))
            CuidaleTextField(
                value = confirmPass,
                onValueChange = { confirmPass = it },
                placeholder = stringResource(R.string.forgot_password_confirm_pass_placeholder),
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = isConfirmPassVisible,
                onPasswordVisibilityChange = { isConfirmPassVisible = !isConfirmPassVisible },
                enabled = !isLoading
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = colors.primary, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            HardShadowButton(
                text = stringResource(R.string.forgot_password_update_button),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                isDark = isDark,
                isErrorMode = error != null,
                errorText = error,
                enabled = passMatches && pass.length >= 8 && pass.any { it.isUpperCase() } && pass.any { !it.isLetterOrDigit() },
                onClick = { onUpdate(pass) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HardShadowButton(
            text = stringResource(R.string.forgot_password_cancel_button),
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
fun RecoverySuccessStep(
    onLogin: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val shadows = CuidaleThemeExtras.shadows

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.forgot_password_success_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = onGradientText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = Color.Transparent
        ) {
            Text(
                text = stringResource(R.string.forgot_password_success_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        HardShadowButton(
            text = stringResource(R.string.forgot_password_success_button),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            shadowColor = shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            isDark = false,
            onClick = onLogin
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecoveryPreviewLight() {
    CuidaleAppTheme(darkTheme = false) {
        RecoveryPasswordScreen(onNavigateBack = {}, onFinish = {})
    }
}

@Preview(showBackground = true)
@Composable
fun RecoveryPreviewDark() {
    CuidaleAppTheme(darkTheme = true) {
        RecoveryPasswordScreen(onNavigateBack = {}, onFinish = {})
    }
}

