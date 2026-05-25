package com.example.cuidaleapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.data.ChileData
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.text.input.KeyboardType
import com.example.cuidaleapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.RegisterStatus
import com.example.cuidaleapp.viewmodel.RegisterStep
import com.example.cuidaleapp.viewmodel.RegisterViewModel

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onBackToLogin: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val status by viewModel.status.collectAsState()
    val gradient = CuidaleThemeExtras.gradient
    val onGradientText = CuidaleThemeExtras.customColors.onGradient
    val context = LocalContext.current
    
    val registrationSteps = listOf(
        stringResource(R.string.register_step_personal),
        stringResource(R.string.register_step_geo),
        stringResource(R.string.register_step_account)
    )

    LaunchedEffect(status) {
        if (status is RegisterStatus.Error) {
            Toast.makeText(context, (status as RegisterStatus.Error).message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = if (currentStep is RegisterStep.Success) "¡Bienvenido!" else "Registro ${userData.rol.replaceFirstChar { it.uppercase() }}",
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

            if (currentStep != RegisterStep.Success) {
                CuidaleStepProgressBar(
                    steps = registrationSteps,
                    currentStepIndex = when (currentStep) {
                        RegisterStep.PersonalData -> 0
                        RegisterStep.GeographicData -> 1
                        RegisterStep.Password -> 2
                        else -> 2 // Para Verification/Success en esta actividad, marcamos el último paso
                    }
                )
                Spacer(modifier = Modifier.height(40.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Crossfade(targetState = currentStep, label = "registerStepTransition", animationSpec = tween(500)) { step ->
                    when (step) {
                        RegisterStep.PersonalData -> {
                            PersonalDataStep(
                                viewModel = viewModel,
                                onNext = { viewModel.goToStep(RegisterStep.GeographicData) }
                            )
                        }

                        RegisterStep.GeographicData -> {
                            GeographicDataStep(
                                viewModel = viewModel,
                                onNext = { viewModel.goToStep(RegisterStep.Password) },
                                onBack = { viewModel.goToStep(RegisterStep.PersonalData) }
                            )
                        }

                        RegisterStep.Password -> {
                            RegisterPasswordStep(
                                viewModel = viewModel,
                                onBack = { viewModel.goToStep(RegisterStep.GeographicData) }
                            )
                        }

                        RegisterStep.Verification, RegisterStep.Success -> {
                            // Pantalla de transición mientras se navega a VerificationActivity
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (currentStep == RegisterStep.PersonalData) {
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
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PersonalDataStep(
    viewModel: RegisterViewModel,
    onNext: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val status by viewModel.status.collectAsState()
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    
    val initialNames = if (userData.nombre_s == "N/A" || userData.nombre_s.isEmpty()) userData.nombre else "${userData.nombre} ${userData.nombre_s}"
    val initialApellidos = if (userData.apellido_ma == "N/A" || userData.apellido_ma.isEmpty()) userData.apellido_pa else "${userData.apellido_pa} ${userData.apellido_ma}"

    var names by remember { mutableStateOf(initialNames) }
    var apellidos by remember { mutableStateOf(initialApellidos) }
    var email by remember { mutableStateOf(userData.correo) }
    var rut by remember { mutableStateOf(userData.rut) }

    val isError = status is RegisterStatus.Error
    val isLoading = status is RegisterStatus.Loading

    val canGoNext = names.isNotBlank() && apellidos.isNotBlank() && email.isNotBlank() && rut.isNotBlank() && !isLoading

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.register_personal_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary,
            isError = isError
        ) {
            InputFieldLabel(stringResource(R.string.register_name_label))
            CuidaleTextField(
                value = names,
                onValueChange = { 
                    names = it
                    if (isError) viewModel.clearError()
                },
                placeholder = stringResource(R.string.register_names_placeholder),
                containerColor = customColors.inputBackground,
                isError = isError && names.isBlank()
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_lastname_label))
            CuidaleTextField(
                value = apellidos,
                onValueChange = { 
                    apellidos = it
                    if (isError) viewModel.clearError()
                },
                placeholder = stringResource(R.string.register_apellidos_placeholder),
                containerColor = customColors.inputBackground,
                isError = isError && apellidos.isBlank()
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_email_label))
            CuidaleTextField(
                value = email,
                onValueChange = { 
                    email = it
                    if (isError) viewModel.clearError()
                },
                placeholder = "ejemplo@correo.cl",
                containerColor = customColors.inputBackground,
                isError = isError && (email.isBlank() || !email.contains("@")),
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel(stringResource(R.string.register_rut_label))
            CuidaleTextField(
                value = rut,
                onValueChange = { 
                    rut = it
                    viewModel.onRutChanged(it)
                    if (isError) viewModel.clearError()
                },
                placeholder = stringResource(R.string.register_rut_placeholder),
                containerColor = customColors.inputBackground,
                isError = isError && rut.isBlank()
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowButton(
                text = if (isLoading) "Verificando..." else stringResource(R.string.register_next_button),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowPrimary,
                isDark = customColors.isDark,
                enabled = canGoNext,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = {
                    viewModel.updatePersonalData(names, apellidos, email, rut, onNext)
                }
            )
        }
    }
}

@Composable
fun GeographicDataStep(
    viewModel: RegisterViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val status by viewModel.status.collectAsState()
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    
    val regions = ChileData.regions.sortedBy { it.name }
    var selectedRegionName by remember { mutableStateOf(userData.region) }
    var selectedComunaName by remember { mutableStateOf(userData.comuna) }

    val selectedRegion = regions.find { it.name == selectedRegionName }
    val comunas = selectedRegion?.comunas?.sorted() ?: emptyList()

    val isError = status is RegisterStatus.Error
    val isLoading = status is RegisterStatus.Loading

    val canGoNext = selectedRegionName.isNotEmpty() && selectedComunaName.isNotEmpty() && !isLoading

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.register_geo_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary,
            isError = isError
        ) {
            CuidaleExposedDropdown(
                label = stringResource(R.string.register_region_label),
                options = regions.map { it.name },
                selectedOption = selectedRegionName,
                onOptionSelected = { 
                    selectedRegionName = it 
                    selectedComunaName = "" 
                    if (isError) viewModel.clearError()
                },
                placeholder = stringResource(R.string.register_region_placeholder),
                isError = isError && selectedRegionName.isEmpty()
            )

            Spacer(modifier = Modifier.height(24.dp))

            CuidaleExposedDropdown(
                label = stringResource(R.string.register_comuna_label),
                options = comunas,
                selectedOption = selectedComunaName,
                onOptionSelected = { 
                    selectedComunaName = it 
                    if (isError) viewModel.clearError()
                },
                enabled = selectedRegionName.isNotEmpty(),
                placeholder = if (selectedRegionName.isEmpty()) "Primero elija una región" else stringResource(R.string.register_comuna_placeholder),
                isError = isError && selectedComunaName.isEmpty()
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowButton(
                text = stringResource(R.string.register_next_button),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowPrimary,
                isDark = customColors.isDark,
                enabled = canGoNext,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = {
                    val error = viewModel.updateGeographicData(
                        selectedRegionName, 
                        selectedComunaName, 
                        selectedRegion?.id ?: ""
                    )
                    if (error == null) {
                        onNext()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = stringResource(R.string.register_back_button),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowSecondary,
                isDark = customColors.isDark,
                enabled = !isLoading,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack
            )
        }
    }
}

@Composable
fun RegisterPasswordStep(
    viewModel: RegisterViewModel,
    onBack: () -> Unit
) {
    val status by viewModel.status.collectAsState()
    val unmetRequirements by viewModel.unmetRequirementsTrigger.collectAsState()
    val customColors = CuidaleThemeExtras.customColors
    val isDark = customColors.isDark
    
    NewPasswordStep(
        isLoading = status is RegisterStatus.Loading,
        error = (status as? RegisterStatus.Error)?.message,
        unmetRequirements = unmetRequirements,
        isDark = isDark,
        onUpdate = { p, _ -> viewModel.finalizeRegistration(p) },
        onRestart = onBack,
        onClearError = { viewModel.clearError() },
        title = stringResource(R.string.register_pass_title),
        description = stringResource(R.string.register_pass_desc),
        buttonText = stringResource(R.string.register_pass_button)
    )
}

@Composable
fun RegisterVerificationStep(
    viewModel: RegisterViewModel,
    onBack: (() -> Unit)? = null
) {
    val status by viewModel.status.collectAsState()
    val userData by viewModel.userData.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val verificationState by viewModel.verificationState.collectAsState()
    val customColors = CuidaleThemeExtras.customColors
    val isDark = customColors.isDark

    CodeStep(
        email = userData.correo,
        timerSeconds = timerSeconds,
        isLoading = status is RegisterStatus.Loading,
        error = (status as? RegisterStatus.Error)?.message,
        isDark = isDark,
        onVerify = { viewModel.verifyCode(it) },
        onResend = { viewModel.resendCode() },
        onCancel = onBack,
        onClearError = { viewModel.clearError() },
        title = "Código de verificación",
        remainingAttempts = verificationState.remainingAttempts
    )
}

@Composable
fun RegistrationSuccessStep(onLogin: () -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = shadows.hardShadowPrimary,
            isError = false
        ) {
            Text(
                text = stringResource(R.string.register_success_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (customColors.isDark) customColors.success else Color(0xFF178F17),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.register_success_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            HardShadowButton(
                text = stringResource(R.string.forgot_password_login_button),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowPrimary,
                isDark = customColors.isDark,
                onClick = onLogin
            )
        }
    }
}
