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
    
    val registrationSteps = listOf("Personal", "Geográfico", "Cuenta")

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
                text = if (currentStep is RegisterStep.Success) "¡Bienvenido!" else "Registro ${userData.role.replaceFirstChar { it.uppercase() }}",
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
                        else -> 0
                    }
                )
                Spacer(modifier = Modifier.height(40.dp))
            }

            Crossfade(targetState = currentStep, label = "registerStepTransition") { step ->
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
                        PasswordStep(
                            viewModel = viewModel,
                            onBack = { viewModel.goToStep(RegisterStep.GeographicData) }
                        )
                    }
                    RegisterStep.Success -> {
                        RegistrationSuccessStep(onLogin = onBackToLogin)
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Bienvenido, Ingrese sus datos personales",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = if (customColors.isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            InputFieldLabel("Nombres")
            CuidaleTextField(
                value = names,
                onValueChange = { names = it },
                placeholder = "Primer y Segundo nombre",
                containerColor = customColors.inputBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Apellidos")
            CuidaleTextField(
                value = apellidos,
                onValueChange = { apellidos = it },
                placeholder = "Apellido paterno y materno",
                containerColor = customColors.inputBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Correo electrónico")
            CuidaleTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "ejemplo@correo.cl",
                containerColor = customColors.inputBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("RUT")
            CuidaleTextField(
                value = rut,
                onValueChange = { rut = it },
                placeholder = "12345678-9",
                containerColor = customColors.inputBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowButton(
                text = if (status is RegisterStatus.Loading) "Verificando..." else "Siguiente",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowPrimary,
                isDark = customColors.isDark,
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
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    
    val regions = ChileData.regions.sortedBy { it.name }
    var selectedRegionName by remember { mutableStateOf(userData.region) }
    var selectedComunaName by remember { mutableStateOf(userData.comuna) }

    val selectedRegion = regions.find { it.name == selectedRegionName }
    val comunas = selectedRegion?.comunas?.sorted() ?: emptyList()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ingrese sus datos Geográficos",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = if (customColors.isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            CuidaleExposedDropdown(
                label = "Elige la región donde vives actualmente",
                options = regions.map { it.name },
                selectedOption = selectedRegionName,
                onOptionSelected = { 
                    selectedRegionName = it 
                    selectedComunaName = "" 
                },
                placeholder = "Seleccionar Región"
            )

            Spacer(modifier = Modifier.height(24.dp))

            CuidaleExposedDropdown(
                label = "Elige la comuna donde vives",
                options = comunas,
                selectedOption = selectedComunaName,
                onOptionSelected = { selectedComunaName = it },
                enabled = selectedRegionName.isNotEmpty(),
                placeholder = if (selectedRegionName.isEmpty()) "Primero elija una región" else "Seleccionar Comuna"
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowButton(
                text = "Siguiente",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowPrimary,
                isDark = customColors.isDark,
                onClick = {
                    val error = viewModel.updateGeographicData(
                        selectedRegionName, 
                        selectedComunaName, 
                        selectedRegion?.id ?: ""
                    )
                    if (error == null) onNext()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = "Retroceder",
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowSecondary,
                isDark = customColors.isDark,
                onClick = onBack
            )
        }
    }
}

@Composable
fun PasswordStep(
    viewModel: RegisterViewModel,
    onBack: () -> Unit
) {
    val status by viewModel.status.collectAsState()
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val hasMinLength = password.length >= 6
    val hasUppercase = password.any { it.isUpperCase() }
    val hasLowercase = password.any { it.isLowerCase() }
    val hasSpecialChar = password.any { it == '.' || it == '-' || !it.isLetterOrDigit() }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    val validations = listOf(
        "Mínimo 6 caracteres" to hasMinLength,
        "Al menos una mayúscula y minúscula" to (hasUppercase && hasLowercase),
        "Un símbolo como punto o guion" to hasSpecialChar
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Establece la contraseña para tu cuenta",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        CuidaleValidationPanel(validations = validations, isDark = customColors.isDark)

        Spacer(modifier = Modifier.height(24.dp))

        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = if (customColors.isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            InputFieldLabel("Ingrese nueva contraseña")
            CuidaleTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Contraseña",
                containerColor = customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = passwordVisible,
                onPasswordVisibilityChange = { passwordVisible = !passwordVisible }
            )

            Spacer(modifier = Modifier.height(16.dp))

            InputFieldLabel("Repita la contraseña")
            CuidaleTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = "Repetir contraseña",
                containerColor = customColors.inputBackground,
                isPassword = true,
                isPasswordVisible = confirmPasswordVisible,
                onPasswordVisibilityChange = { confirmPasswordVisible = !confirmPasswordVisible }
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowButton(
                text = if (status is RegisterStatus.Loading) "Registrando..." else "Finalizar registro",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowPrimary,
                isDark = customColors.isDark,
                onClick = {
                    if (passwordsMatch && hasMinLength && hasUppercase && hasLowercase && hasSpecialChar) {
                        viewModel.finalizeRegistration(password)
                    } else {
                        // Toast or local error handling
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = "Retroceder",
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowSecondary,
                isDark = customColors.isDark,
                onClick = onBack
            )
        }
    }
}

@Composable
fun RegistrationSuccessStep(onLogin: () -> Unit) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HardShadowPanel(
            backgroundColor = customColors.panelBackground,
            shadowColor = if (customColors.isDark) shadows.hardShadowAccent else shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            Text(
                text = "¡Registro completado con éxito!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (customColors.isDark) customColors.success else Color(0xFF178F17),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Ya puedes iniciar sesión con tu cuenta nueva para empezar a usar Cuidale App.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            HardShadowButton(
                text = "Ir al Login",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = shadows.hardShadowPrimary,
                isDark = customColors.isDark,
                onClick = onLogin
            )
        }
    }
}
