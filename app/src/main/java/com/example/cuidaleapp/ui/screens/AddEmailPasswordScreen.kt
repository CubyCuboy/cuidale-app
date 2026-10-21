package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.CuidaleTextField
import com.example.cuidaleapp.ui.components.CuidaleValidationPanel
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.InputFieldLabel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.AddEmailPasswordState
import com.example.cuidaleapp.viewmodel.AddEmailPasswordViewModel
import kotlin.math.roundToInt

@Composable
fun AddEmailPasswordScreen(
    viewModel: AddEmailPasswordViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()

    var step by rememberSaveable { mutableStateOf(1) }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isPassVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPassVisible by rememberSaveable { mutableStateOf(false) }

    val gradient = CuidaleThemeExtras.gradient
    val typography = MaterialTheme.typography

    val error = (uiState as? AddEmailPasswordState.Error)?.message
    val isLoading = uiState is AddEmailPasswordState.Loading

    LaunchedEffect(uiState) {
        if (uiState is AddEmailPasswordState.Success) {
            onSuccess()
        }
    }

    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error != null && step == 2) {
            repeat(6) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 10f else -10f,
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (step == 2) step = 1 else onNavigateBack()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Agregar Correo y Contraseña",
                style = typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (step == 1) {
                EmailStep(
                    email = email,
                    onEmailChange = viewModel::onEmailChange,
                    onNext = { step = 2 },
                    error = error
                )
            } else {
                PasswordStep(
                    password = password,
                    confirmPassword = confirmPassword,
                    isPassVisible = isPassVisible,
                    isConfirmPassVisible = isConfirmPassVisible,
                    onPasswordChange = {
                        viewModel.onPasswordChange(it)
                        viewModel.clearError()
                    },
                    onConfirmPasswordChange = {
                        confirmPassword = it
                        viewModel.clearError()
                    },
                    onPassVisibilityChange = { isPassVisible = !isPassVisible },
                    onConfirmVisibilityChange = { isConfirmPassVisible = !isConfirmPassVisible },
                    isLoading = isLoading,
                    error = error,
                    shakeOffset = shakeOffset,
                    onLink = viewModel::linkEmailPassword,
                    onBack = { step = 1 }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EmailStep(
    email: String,
    onEmailChange: (String) -> Unit,
    onNext: () -> Unit,
    error: String?
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val typography = MaterialTheme.typography

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
        cornerRadius = 24.dp,
        isError = error != null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Paso 1: Correo Electrónico",
                style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Ingresa el correo electrónico que deseas asociar a tu cuenta.",
                style = typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            InputFieldLabel("Correo electrónico")
            CuidaleTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = "ejemplo@correo.cl",
                containerColor = customColors.inputBackground,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val isEmailValid = com.example.cuidaleapp.utils.AccountUtils.validateEmail(email)

            HardShadowButton(
                text = "Siguiente",
                onClick = onNext,
                enabled = isEmailValid,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primary,
                shadowColor = shadowPalette.hardShadowPrimary,
                cornerRadius = 12.dp
            )
        }
    }
}

@Composable
private fun PasswordStep(
    password: String,
    confirmPassword: String,
    isPassVisible: Boolean,
    isConfirmPassVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPassVisibilityChange: () -> Unit,
    onConfirmVisibilityChange: () -> Unit,
    isLoading: Boolean,
    error: String?,
    shakeOffset: Animatable<Float, *>,
    onLink: () -> Unit,
    onBack: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val typography = MaterialTheme.typography

    val passMatches = password.isNotEmpty() && password == confirmPassword
    val isPassValid =
        password.length >= 8 &&
        password.any { it.isUpperCase() } &&
        password.any { !it.isLetterOrDigit() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
            cornerRadius = 24.dp,
            isError = error != null,
            modifier = Modifier.offset {
                IntOffset(shakeOffset.value.roundToInt(), 0)
            },
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Paso 2: Contraseña de Acceso",
                    style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Establece una contraseña segura para tu cuenta.",
                    style = typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CuidaleValidationPanel(
                    validations = listOf(
                        stringResource(R.string.password_req_length) to (password.length >= 8),
                        stringResource(R.string.password_req_upper) to password.any { it.isUpperCase() },
                        stringResource(R.string.password_req_special) to password.any { !it.isLetterOrDigit() },
                        stringResource(R.string.password_req_match) to passMatches
                    ),
                    isDark = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                InputFieldLabel("Contraseña")
                CuidaleTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = "Ingrese su contraseña",
                    containerColor = customColors.inputBackground,
                    isPassword = true,
                    isPasswordVisible = isPassVisible,
                    onPasswordVisibilityChange = onPassVisibilityChange,
                    enabled = !isLoading,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )

                Spacer(modifier = Modifier.height(4.dp))

                InputFieldLabel("Repetir contraseña")
                CuidaleTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    placeholder = "Reingrese su contraseña",
                    containerColor = customColors.inputBackground,
                    isPassword = true,
                    isPasswordVisible = isConfirmPassVisible,
                    onPasswordVisibilityChange = onConfirmVisibilityChange,
                    enabled = !isLoading,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (isPassValid && passMatches && !isLoading) {
                                onLink()
                            }
                        }
                    )
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            HardShadowButton(
                text = "Vincular y Finalizar",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowColor = shadowPalette.hardShadowPrimary,
                icon = Icons.Default.CheckCircle,
                enabled = isPassValid && passMatches,
                isErrorMode = error != null,
                errorText = error,
                onClick = onLink
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        HardShadowButton(
            text = "Volver",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.3f),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            enabled = !isLoading,
            onClick = onBack
        )
    }
}
