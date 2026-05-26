package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.RegisterViewModel
import com.example.cuidaleapp.viewmodel.RegisterUiState

enum class RegisterStep {
    ROLE, PERSONAL, LOCATION, PASSWORD, SUCCESS
}

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val uiState by viewModel.uiState.collectAsState()
    
    var currentStep by rememberSaveable { mutableStateOf(RegisterStep.ROLE) }
    
    // Sync with ViewModel for success
    LaunchedEffect(uiState) {
        if (uiState is RegisterUiState.Success) {
            currentStep = RegisterStep.SUCCESS
        }
    }
    
    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val typography = MaterialTheme.typography

    val stepsLabels = listOf(
        stringResource(R.string.register_step_role),
        stringResource(R.string.register_step_personal),
        stringResource(R.string.register_step_location),
        stringResource(R.string.register_step_pass)
    )
    
    val currentStepIndex = when(currentStep) {
        RegisterStep.ROLE -> 0
        RegisterStep.PERSONAL -> 1
        RegisterStep.LOCATION -> 2
        RegisterStep.PASSWORD -> 3
        RegisterStep.SUCCESS -> 3
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.register_title),
                style = typography.displaySmall,
                color = onGradientText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            if (currentStep != RegisterStep.SUCCESS) {
                CuidaleStepProgressBar(
                    steps = stepsLabels,
                    currentStepIndex = currentStepIndex
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Crossfade(targetState = currentStep, animationSpec = tween(500), label = "registerStepTransition") { step ->
                val error = if (uiState is RegisterUiState.Error) (uiState as RegisterUiState.Error).message else null
                
                when (step) {
                    RegisterStep.ROLE -> RegisterRoleStep(
                        selectedRole = viewModel.role.collectAsState().value,
                        onRoleSelected = viewModel::onRoleChange,
                        onNext = { currentStep = RegisterStep.PERSONAL },
                        isDark = isDark
                    )
                    RegisterStep.PERSONAL -> RegisterPersonalStep(
                        viewModel = viewModel,
                        error = error,
                        onNext = { currentStep = RegisterStep.LOCATION },
                        onLoginRedirect = onNavigateBack,
                        isDark = isDark
                    )
                    RegisterStep.LOCATION -> RegisterLocationStep(
                        viewModel = viewModel,
                        error = error,
                        onNext = { currentStep = RegisterStep.PASSWORD },
                        onBack = { currentStep = RegisterStep.PERSONAL },
                        isDark = isDark
                    )
                    RegisterStep.PASSWORD -> RegisterPasswordStep(
                        viewModel = viewModel,
                        isLoading = uiState is RegisterUiState.Loading,
                        error = error,
                        onRegister = viewModel::register,
                        onBack = { currentStep = RegisterStep.LOCATION },
                        isDark = isDark
                    )
                    RegisterStep.SUCCESS -> RegisterSuccessStep(onFinish = onFinish)
                }
            }
            
            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

@Composable
fun RegisterRoleStep(
    selectedRole: String,
    onRoleSelected: (String) -> Unit,
    onNext: () -> Unit,
    isDark: Boolean
) {
    val shadows = CuidaleThemeExtras.shadows

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.register_role_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = CuidaleThemeExtras.customColors.onGradient,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        RoleCard(
            title = stringResource(R.string.register_role_caregiver),
            description = stringResource(R.string.register_role_caregiver_desc),
            icon = Icons.Default.Badge,
            isSelected = selectedRole == "cuidador",
            onClick = { onRoleSelected("cuidador") },
            isDark = isDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        RoleCard(
            title = stringResource(R.string.register_role_representative),
            description = stringResource(R.string.register_role_representative_desc),
            icon = Icons.Default.Group,
            isSelected = selectedRole == "representante",
            onClick = { onRoleSelected("representante") },
            isDark = isDark
        )

        Spacer(modifier = Modifier.height(48.dp))

        HardShadowButton(
            text = stringResource(R.string.register_button_next),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            enabled = selectedRole.isNotEmpty(),
            isDark = isDark,
            onClick = onNext
        )
    }
}

@Composable
fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val shadows = CuidaleThemeExtras.shadows
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else Color.Transparent,
        label = "roleBorder"
    )
    
    val shadowColor = if (isSelected) {
        if (isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary
    } else {
        Color.Transparent
    }

    Box(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        // Shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(shadowColor.copy(alpha = 0.5f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CuidaleThemeExtras.customColors.panelBackground)
                .border(2.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = colors.primary)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            
            RadioButton(selected = isSelected, onClick = null)
        }
    }
}

@Composable
fun RegisterPersonalStep(
    viewModel: RegisterViewModel,
    error: String?,
    onNext: () -> Unit,
    onLoginRedirect: () -> Unit,
    isDark: Boolean
) {
    val nombre by viewModel.nombre.collectAsState()
    val nombreS by viewModel.nombreS.collectAsState()
    val apellidoPa by viewModel.apellidoPa.collectAsState()
    val apellidoMa by viewModel.apellidoMa.collectAsState()
    val email by viewModel.email.collectAsState()
    val rut by viewModel.rut.collectAsState()
    
    val isEmailValid by viewModel.isEmailValid.collectAsState()
    val isRutValid by viewModel.isRutValid.collectAsState()
    val isFormValid by viewModel.isFormValid.collectAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            isError = error != null
        ) {
            Text(
                text = stringResource(R.string.register_personal_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            InputFieldLabel("Primer Nombre")
            CuidaleTextField(
                value = nombre,
                onValueChange = viewModel::onNombreChange,
                placeholder = "Ej: Juan",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isError = nombre.isNotEmpty() && nombre.length < 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Segundo Nombre (Opcional)")
            CuidaleTextField(
                value = nombreS,
                onValueChange = viewModel::onNombreSChange,
                placeholder = "Ej: Antonio",
                containerColor = CuidaleThemeExtras.customColors.inputBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Apellido Paterno")
            CuidaleTextField(
                value = apellidoPa,
                onValueChange = viewModel::onApellidoPaChange,
                placeholder = "Ej: Pérez",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isError = apellidoPa.isNotEmpty() && apellidoPa.length < 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Apellido Materno (Opcional)")
            CuidaleTextField(
                value = apellidoMa,
                onValueChange = viewModel::onApellidoMaChange,
                placeholder = "Ej: González",
                containerColor = CuidaleThemeExtras.customColors.inputBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_email_label))
            CuidaleTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                placeholder = "ejemplo@correo.com",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                keyboardType = KeyboardType.Email,
                isError = email.isNotEmpty() && !isEmailValid
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_rut_label))
            CuidaleTextField(
                value = rut,
                onValueChange = viewModel::onRutChange,
                placeholder = "12.345.678-9",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isError = rut.isNotEmpty() && !isRutValid
            )
            if (rut.isNotEmpty() && !isRutValid) {
                Text(
                    text = "RUT inválido (ej: 12.345.678-9)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowButton(
            text = stringResource(R.string.register_button_next),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            enabled = isFormValid,
            isDark = isDark,
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.forgot_password_footer_prefix), style = MaterialTheme.typography.bodyMedium, color = CuidaleThemeExtras.customColors.onGradient)
            Text(
                text = "Ingresa aquí",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = CuidaleThemeExtras.customColors.onGradient,
                modifier = Modifier.clickable { onLoginRedirect() }
            )
        }
    }
}

@Composable
fun RegisterLocationStep(
    viewModel: RegisterViewModel,
    error: String?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean
) {
    val selectedRegion by viewModel.region.collectAsState()
    val selectedComuna by viewModel.comuna.collectAsState()
    val regions = ChileData.regions.map { it.name }
    val comunas = ChileData.regions.find { it.name == selectedRegion }?.comunas ?: emptyList()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary
        ) {
            Text(
                text = stringResource(R.string.register_location_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            CuidaleExposedDropdown(
                label = stringResource(R.string.register_region_label),
                options = regions,
                selectedOption = selectedRegion,
                onOptionSelected = { regionName ->
                    val regionId = ChileData.regions.find { it.name == regionName }?.id?.toString() ?: "99"
                    viewModel.onRegionChange(regionName, regionId)
                },
                placeholder = stringResource(R.string.register_region_placeholder)
            )

            Spacer(modifier = Modifier.height(16.dp))

            CuidaleExposedDropdown(
                label = stringResource(R.string.register_comuna_label),
                options = comunas,
                selectedOption = selectedComuna,
                onOptionSelected = viewModel::onComunaChange,
                enabled = selectedRegion.isNotEmpty(),
                placeholder = stringResource(R.string.register_comuna_placeholder)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        HardShadowButton(
            text = stringResource(R.string.register_button_next),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            enabled = selectedRegion.isNotEmpty() && selectedComuna.isNotEmpty(),
            isDark = isDark,
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(16.dp))

        HardShadowButton(
            text = stringResource(R.string.register_button_back),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            isDark = isDark,
            onClick = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.forgot_password_footer_prefix), style = MaterialTheme.typography.bodyMedium, color = CuidaleThemeExtras.customColors.onGradient)
            Text(
                text = "Ingresa aquí",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = CuidaleThemeExtras.customColors.onGradient,
                modifier = Modifier.clickable { onBack() }
            )
        }
    }
}



@Composable
fun RegisterPasswordStep(
    viewModel: RegisterViewModel,
    isLoading: Boolean,
    error: String?,
    onRegister: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean
) {
    val password by viewModel.password.collectAsState()
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isPassVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmVisible by rememberSaveable { mutableStateOf(false) }
    
    val passMatches = password.isNotEmpty() && password == confirmPassword
    val isPassValid = password.length >= 8 && password.any { it.isUpperCase() } && password.any { !it.isLetterOrDigit() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            isError = error != null
        ) {
            Text(
                text = stringResource(R.string.register_pass_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            CuidaleValidationPanel(
                validations = listOf(
                    stringResource(R.string.password_req_length) to (password.length >= 8),
                    stringResource(R.string.password_req_upper) to password.any { it.isUpperCase() },
                    stringResource(R.string.password_req_special) to password.any { !it.isLetterOrDigit() },
                    stringResource(R.string.password_req_match) to passMatches
                ),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(24.dp))

            InputFieldLabel(stringResource(R.string.register_pass_label))
            CuidaleTextField(
                value = password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = stringResource(R.string.register_pass_placeholder),
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = isPassVisible,
                onPasswordVisibilityChange = { isPassVisible = !isPassVisible },
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_confirm_pass_label))
            CuidaleTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = stringResource(R.string.register_confirm_pass_placeholder),
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = isConfirmVisible,
                onPasswordVisibilityChange = { isConfirmVisible = !isConfirmVisible },
                enabled = !isLoading
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            HardShadowButton(
                text = stringResource(R.string.register_button_finish),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
                icon = Icons.Default.CheckCircle,
                enabled = isPassValid && passMatches,
                isDark = isDark,
                isErrorMode = error != null,
                errorText = error,
                onClick = onRegister
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HardShadowButton(
            text = stringResource(R.string.register_button_back),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            enabled = !isLoading,
            isDark = isDark,
            onClick = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.forgot_password_footer_prefix), style = MaterialTheme.typography.bodyMedium, color = CuidaleThemeExtras.customColors.onGradient)
            Text(
                text = "Ingresa aquí",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = CuidaleThemeExtras.customColors.onGradient,
                modifier = Modifier.clickable { onBack() }
            )
        }
    }
}

@Composable
fun RegisterSuccessStep(onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = CuidaleThemeExtras.customColors.onGradient
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "¡Registro Exitoso!",
            style = MaterialTheme.typography.displaySmall,
            color = CuidaleThemeExtras.customColors.onGradient,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tu cuenta ha sido creada correctamente. Ya puedes comenzar a usar Cuidale.",
            style = MaterialTheme.typography.bodyLarge,
            color = CuidaleThemeExtras.customColors.onGradient,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        HardShadowButton(
            text = "Comenzar",
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.primary,
            shadowColor = Color.Black.copy(alpha = 0.1f),
            isDark = false,
            onClick = onFinish
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterPreviewLight() {
    CuidaleAppTheme(darkTheme = false) {
        RegisterScreen(onNavigateBack = {}, onFinish = {})
    }
}
