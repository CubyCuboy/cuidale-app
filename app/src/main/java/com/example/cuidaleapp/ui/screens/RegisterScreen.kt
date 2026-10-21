package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.PhoneUtils
import com.example.cuidaleapp.viewmodel.RegisterViewModel
import com.example.cuidaleapp.viewmodel.RegisterUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class RegisterStep {
    ROLE, PERSONAL, LOCATION, PASSWORD, SUCCESS
}

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel = viewModel(),
    isExternal: Boolean = false,
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val uiState by viewModel.uiState.collectAsState()
    
    var currentStep by rememberSaveable { mutableStateOf(RegisterStep.ROLE) }

    LaunchedEffect(uiState) {
        if (uiState is RegisterUiState.Success) {
            currentStep = RegisterStep.SUCCESS
        }
    }
    
    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val typography = MaterialTheme.typography

    val stepsLabels = if (isExternal) {
        listOf(
            stringResource(R.string.register_step_role),
            stringResource(R.string.register_step_personal),
            stringResource(R.string.register_step_location)
        )
    } else {
        listOf(
            stringResource(R.string.register_step_role),
            stringResource(R.string.register_step_personal),
            stringResource(R.string.register_step_location),
            stringResource(R.string.register_step_pass)
        )
    }
    
    val currentStepIndex = when(currentStep) {
        RegisterStep.ROLE -> 0
        RegisterStep.PERSONAL -> 1
        RegisterStep.LOCATION -> 2
        RegisterStep.PASSWORD -> 3
        RegisterStep.SUCCESS -> if (isExternal) 2 else 3
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
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.register_title),
                style = typography.displaySmall,
                color = onGradientText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            if (currentStep != RegisterStep.SUCCESS) {
                CuidaleStepProgressBar(
                    steps = stepsLabels,
                    currentStepIndex = currentStepIndex
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                        isExternal = isExternal,
                        isLoading = uiState is RegisterUiState.Loading,
                        error = error,
                        onNext = { 
                            if (isExternal) {
                                viewModel.registerExternal()
                            } else {
                                currentStep = RegisterStep.PASSWORD
                            }
                        },
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
                    RegisterStep.SUCCESS -> RegisterSuccessStep(
                        isExternal = isExternal,
                        onFinish = onFinish
                    )
                }
            }
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

        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
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
    val telefono by viewModel.telefono.collectAsState()
    
    val isEmailValid by viewModel.isEmailValid.collectAsState()
    val isRutValid by viewModel.isRutValid.collectAsState()
    val isFormValid by viewModel.isFormValid.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowPrimary else CuidaleThemeExtras.shadows.hardShadowPrimary,
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
                isError = nombre.isNotEmpty() && nombre.length < 2,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Segundo Nombre (Opcional)")
            CuidaleTextField(
                value = nombreS,
                onValueChange = viewModel::onNombreSChange,
                placeholder = "Ej: Antonio",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Apellido Paterno")
            CuidaleTextField(
                value = apellidoPa,
                onValueChange = viewModel::onApellidoPaChange,
                placeholder = "Ej: Pérez",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isError = apellidoPa.isNotEmpty() && apellidoPa.length < 2,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Apellido Materno (Opcional)")
            CuidaleTextField(
                value = apellidoMa,
                onValueChange = viewModel::onApellidoMaChange,
                placeholder = "Ej: González",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_email_label))
            if (email.isNotEmpty() && !isEmailValid) {
                val emailError = when {
                    !email.contains("@") -> "Falta el '@'"
                    !email.substringAfter("@").contains(".") -> "Falta el dominio (ej: .com)"
                    email.substringAfterLast(".").length < 2 -> "Dominio incompleto"
                    else -> "Correo inválido"
                }
                Text(
                    text = emailError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
            CuidaleTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                placeholder = "ejemplo@correo.com",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                keyboardType = KeyboardType.Email,
                isError = email.isNotEmpty() && !isEmailValid,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_rut_label))
            if (rut.isNotEmpty() && !isRutValid) {
                Text(
                    text = "RUT inválido (ej: 12.345.678-9)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
            CuidaleTextField(
                value = rut,
                onValueChange = viewModel::onRutChange,
                placeholder = "12.345.678-9",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                isError = rut.isNotEmpty() && !isRutValid,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Teléfono (9 dígitos)")
            if (telefono.isNotEmpty() && !PhoneUtils.isValidChileanPhone(telefono)) {
                Text(
                    text = "Debe tener 9 dígitos y empezar con 9 o 2",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
            CuidaleTextField(
                value = telefono,
                onValueChange = viewModel::onTelefonoChange,
                placeholder = "Ej: 9 8765 4321",
                containerColor = CuidaleThemeExtras.customColors.inputBackground,
                keyboardType = KeyboardType.Phone,
                isError = telefono.isNotEmpty() && !PhoneUtils.isValidChileanPhone(telefono),
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (isFormValid) {
                            onNext()
                        }
                    }
                )
            )

            AnimatedVisibility(visible = error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        var isCheckingRut by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        if (isCheckingRut) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            HardShadowButton(
                text = stringResource(R.string.register_button_next),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowPrimary else CuidaleThemeExtras.shadows.hardShadowPrimary,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                enabled = isFormValid,
                isDark = isDark,
                onClick = {
                    scope.launch {
                        isCheckingRut = true
                        val isAvailable = viewModel.validateRutAvailability()
                        isCheckingRut = false
                        if (isAvailable) {
                            onNext()
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.Center, 
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(text = stringResource(R.string.forgot_password_footer_prefix), style = MaterialTheme.typography.bodyMedium, color = CuidaleThemeExtras.customColors.onGradient)
            Spacer(modifier = Modifier.width(4.dp))
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
    isExternal: Boolean = false,
    isLoading: Boolean = false,
    error: String? = null,
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
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowPrimary else CuidaleThemeExtras.shadows.hardShadowPrimary,
            isError = error != null
        ) {
            Text(
                text = stringResource(R.string.register_location_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            val regionColor = if (selectedRegion.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedRegion.isEmpty()) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = regionColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (selectedRegion.isEmpty()) "Selecciona tu Región" else "Región seleccionada",
                    style = MaterialTheme.typography.labelLarge,
                    color = regionColor,
                    fontWeight = if (selectedRegion.isEmpty()) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            CuidaleExposedDropdown(
                label = stringResource(R.string.register_region_label),
                options = regions,
                selectedOption = selectedRegion,
                onOptionSelected = { regionName ->
                    val regionId = ChileData.regions.find { it.name == regionName }?.id ?: "99"
                    viewModel.onRegionChange(regionName, regionId)
                },
                placeholder = stringResource(R.string.register_region_placeholder)
            )

            AnimatedVisibility(
                visible = selectedRegion.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))

                    val comunaColor = if (selectedComuna.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedComuna.isEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = comunaColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (selectedComuna.isEmpty()) "Ahora elige tu Comuna" else "Comuna",
                            style = MaterialTheme.typography.labelLarge,
                            color = comunaColor,
                            fontWeight = if (selectedComuna.isEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CuidaleExposedDropdown(
                        label = stringResource(R.string.register_comuna_label),
                        options = comunas,
                        selectedOption = selectedComuna,
                        onOptionSelected = viewModel::onComunaChange,
                        enabled = selectedRegion.isNotEmpty(),
                        placeholder = stringResource(R.string.register_comuna_placeholder)
                    )
                }
            }
        }

        AnimatedVisibility(visible = error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            HardShadowButton(
                text = if (isExternal) "Finalizar Registro" else stringResource(R.string.register_button_next),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowPrimary else CuidaleThemeExtras.shadows.hardShadowPrimary,
                icon = if (isExternal) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                enabled = selectedRegion.isNotEmpty() && selectedComuna.isNotEmpty(),
                isDark = isDark,
                onClick = onNext
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HardShadowButton(
            text = stringResource(R.string.register_button_back),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowPrimary else CuidaleThemeExtras.shadows.hardShadowPrimary,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            isDark = isDark,
            onClick = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.Center, 
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(text = stringResource(R.string.forgot_password_footer_prefix), style = MaterialTheme.typography.bodyMedium, color = CuidaleThemeExtras.customColors.onGradient)
            Spacer(modifier = Modifier.width(4.dp))
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
    val isPassValid =
        password.length >= 8 &&
                password.any { it.isUpperCase() } &&
                password.any { !it.isLetterOrDigit() }

    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error != null) {
            repeat(6) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 10f else -10f,
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HardShadowPanel(
                backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
                shadowColor = if (isDark) {
                    CuidaleThemeExtras.shadows.hardShadowPrimary
                } else {
                    CuidaleThemeExtras.shadows.hardShadowPrimary
                },
                isError = error != null,
                modifier = Modifier.offset {
                    IntOffset(shakeOffset.value.roundToInt(), 0)
                },
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.register_pass_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                InputFieldLabel(stringResource(R.string.register_pass_label))
                CuidaleTextField(
                    value = password,
                    onValueChange = {
                        viewModel.onPasswordChange(it)
                        viewModel.clearError()
                    },
                    placeholder = stringResource(R.string.register_pass_placeholder),
                    containerColor = CuidaleThemeExtras.customColors.inputBackground,
                    isPassword = true,
                    isPasswordVisible = isPassVisible,
                    onPasswordVisibilityChange = { isPassVisible = !isPassVisible },
                    enabled = !isLoading,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                )

                Spacer(modifier = Modifier.height(12.dp))

                InputFieldLabel(stringResource(R.string.register_confirm_pass_label))
                CuidaleTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        viewModel.clearError()
                    },
                    placeholder = stringResource(R.string.register_confirm_pass_placeholder),
                    containerColor = CuidaleThemeExtras.customColors.inputBackground,
                    isPassword = true,
                    isPasswordVisible = isConfirmVisible,
                    onPasswordVisibilityChange = { isConfirmVisible = !isConfirmVisible },
                    enabled = !isLoading,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (isPassValid && passMatches) {
                                onRegister()
                            }
                        }
                    )
                )
            }

            AnimatedVisibility(visible = error != null) {
                Text(
                    text = error.orEmpty(),
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
                    text = stringResource(R.string.register_button_finish),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowColor = if (isDark) {
                        CuidaleThemeExtras.shadows.hardShadowPrimary
                    } else {
                        CuidaleThemeExtras.shadows.hardShadowPrimary
                    },
                    icon = Icons.Default.CheckCircle,
                    enabled = isPassValid && passMatches,
                    isDark = isDark,
                    isErrorMode = error != null,
                    errorText = error,
                    onClick = onRegister
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HardShadowButton(
                text = stringResource(R.string.register_button_back),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowColor = if (isDark) {
                    CuidaleThemeExtras.shadows.hardShadowAccent
                } else {
                    CuidaleThemeExtras.shadows.hardShadowPrimary
                },
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                enabled = !isLoading,
                isDark = isDark,
                onClick = onBack
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.forgot_password_footer_prefix),
                style = MaterialTheme.typography.bodyMedium,
                color = CuidaleThemeExtras.customColors.onGradient
            )
            Spacer(modifier = Modifier.width(4.dp))
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
fun RegisterSuccessStep(
    isExternal: Boolean = false,
    onFinish: () -> Unit
) {
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
            text = if (isExternal) "Tu cuenta ha sido creada correctamente. Ya puedes comenzar a usar Cuidale."
                   else "Tu cuenta ha sido creada correctamente. Ingresa con tu correo y contraseña para comenzar.",
            style = MaterialTheme.typography.bodyLarge,
            color = CuidaleThemeExtras.customColors.onGradient,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        HardShadowButton(
            text = if (isExternal) "Comenzar" else "Ir a Iniciar Sesión",
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

