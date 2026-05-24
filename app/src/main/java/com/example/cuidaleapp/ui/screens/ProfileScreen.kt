package com.example.cuidaleapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileState
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val role by viewModel.role.collectAsState()
    val availableCommunes by viewModel.availableCommunes.collectAsState()
    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            when (state) {
                is ProfileState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                is ProfileState.View -> {
                    ProfileDetailView(
                        role = role,
                        data = (state as ProfileState.View).data,
                        onEdit = { viewModel.startEditing() },
                        onBack = onBack
                    )
                }
                is ProfileState.NotFound, is ProfileState.Editing -> {
                    if (role == "cuidador") {
                        CaregiverEditFlow(
                            viewModel = viewModel,
                            availableCommunes = availableCommunes,
                            onCancel = onBack
                        )
                    } else {
                        FamilyEditFlow(
                            viewModel = viewModel,
                            availableCommunes = availableCommunes,
                            onCancel = onBack
                        )
                    }
                }
                is ProfileState.Error -> {
                    Text(
                        text = (state as ProfileState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { viewModel.checkProfile() }) {
                        Text(text = "Reintentar")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CaregiverEditFlow(
    viewModel: ProfileViewModel,
    availableCommunes: List<String>,
    onCancel: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    
    // Step 1 Data
    var spec by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    var selectedComunas by remember { mutableStateOf(listOf<String>()) }
    var selectedHorarios by remember { mutableStateOf(listOf<String>()) }
    
    // Step 2 Data
    var fotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        fotoUri = uri
    }

    val title = stringResource(R.string.profile_cuidador_title)
    val subtitle = stringResource(R.string.profile_cuidador_subtitle)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileHeader(title, subtitle)

        Crossfade(targetState = currentStep, label = "caregiverFlow") { step ->
            if (step == 1) {
                CaregiverStep1(
                    spec = spec, onSpecChange = { spec = it },
                    exp = exp, onExpChange = { exp = it },
                    selectedComunas = selectedComunas, onComunasChange = { selectedComunas = it },
                    selectedHorarios = selectedHorarios, onHorariosChange = { selectedHorarios = it },
                    availableCommunes = availableCommunes,
                    onNext = { currentStep = 2 }
                )
            } else {
                CaregiverStep2(
                    fotoUri = fotoUri, 
                    onImageClick = { photoLauncher.launch("image/*") },
                    phone = phone, onPhoneChange = { phone = it },
                    email = email, onEmailChange = { email = it },
                    desc = desc, onDescChange = { desc = it },
                    onBack = { currentStep = 1 },
                    onSave = {
                        viewModel.saveCaregiverProfile(spec, exp, selectedComunas, selectedHorarios, fotoUri, phone, email, desc)
                    }
                )
            }
        }
    }
}

@Composable
fun FamilyEditFlow(
    viewModel: ProfileViewModel,
    availableCommunes: List<String>,
    onCancel: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    
    // Step 1 Data
    var fotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var fullName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var selectedComuna by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    
    // Step 2 Data
    var hasDisability by remember { mutableStateOf(false) }
    var disabilityText by remember { mutableStateOf("") }
    
    var hasChronic by remember { mutableStateOf(false) }
    var chronicText by remember { mutableStateOf("") }
    
    var hasDiet by remember { mutableStateOf(false) }
    var dietText by remember { mutableStateOf("") }
    
    var useMedication by remember { mutableStateOf(false) }
    var conditions by remember { mutableStateOf("") }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        fotoUri = uri
    }

    val title = stringResource(R.string.profile_familiar_title)
    val subtitle = stringResource(R.string.profile_familiar_subtitle)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileHeader(title, subtitle)

        Crossfade(targetState = currentStep, label = "familyFlow") { step ->
            if (step == 1) {
                FamilyStep1(
                    fotoUri = fotoUri, 
                    onImageClick = { photoLauncher.launch("image/*") },
                    fullName = fullName, onFullNameChange = { fullName = it },
                    age = age, onAgeChange = { age = it },
                    gender = gender, onGenderChange = { gender = it },
                    comuna = selectedComuna, onComunaChange = { selectedComuna = it },
                    address = address, onAddressChange = { address = it },
                    availableCommunes = availableCommunes,
                    onNext = { currentStep = 2 }
                )
            } else {
                FamilyStep2(
                    hasDisability = hasDisability, onDisabilityStatusChange = { hasDisability = it },
                    disabilityText = disabilityText, onDisabilityTextChange = { disabilityText = it },
                    hasChronic = hasChronic, onChronicStatusChange = { hasChronic = it },
                    chronicText = chronicText, onChronicTextChange = { chronicText = it },
                    hasDiet = hasDiet, onDietStatusChange = { hasDiet = it },
                    dietText = dietText, onDietTextChange = { dietText = it },
                    useMedication = useMedication, onMedicationChange = { useMedication = it },
                    conditions = conditions, onConditionsChange = { conditions = it },
                    onBack = { currentStep = 1 },
                    onSave = {
                        viewModel.saveFamilyProfile(
                            fotoUri, fullName, age, gender, selectedComuna, address,
                            if (hasDisability) disabilityText else "No",
                            if (hasChronic) chronicText else "No",
                            if (hasDiet) dietText else "No",
                            if (useMedication) "Si" else "No",
                            conditions
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileHeader(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun CaregiverStep1(
    spec: String, onSpecChange: (String) -> Unit,
    exp: String, onExpChange: (String) -> Unit,
    selectedComunas: List<String>, onComunasChange: (List<String>) -> Unit,
    selectedHorarios: List<String>, onHorariosChange: (List<String>) -> Unit,
    availableCommunes: List<String>,
    onNext: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    
    val specOptions = listOf("Practica de enfermeria", "Enfermeria Titulada", "Estudios en Curso", "Persona Cuidadora", "Otro")
    val scheduleOptions = listOf("Mañana", "Tarde", "Noche")

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        CuidaleExposedDropdown(
            label = stringResource(R.string.profile_label_specialization),
            options = specOptions,
            selectedOption = spec,
            onOptionSelected = onSpecChange
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_experience))
        CuidaleTextField(
            value = exp,
            onValueChange = onExpChange,
            placeholder = stringResource(R.string.profile_label_experience_placeholder),
            containerColor = customColors.inputBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CuidaleMultiSelectDropdown(
            label = stringResource(R.string.profile_label_communes),
            options = availableCommunes,
            selectedOptions = selectedComunas,
            onOptionToggled = { option ->
                if (selectedComunas.contains(option)) {
                    onComunasChange(selectedComunas - option)
                } else if (selectedComunas.size < 3) {
                    onComunasChange(selectedComunas + option)
                }
            },
            maxSelection = 3,
            placeholder = stringResource(R.string.profile_label_communes_max)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CuidaleMultiSelectDropdown(
            label = stringResource(R.string.profile_label_schedule),
            options = scheduleOptions,
            selectedOptions = selectedHorarios,
            onOptionToggled = { option ->
                if (selectedHorarios.contains(option)) {
                    onHorariosChange(selectedHorarios - option)
                } else {
                    onHorariosChange(selectedHorarios + option)
                }
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        HardShadowButton(
            text = stringResource(R.string.register_next_button),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = customColors.isDark,
            onClick = onNext
        )
    }
}

@Composable
fun CaregiverStep2(
    fotoUri: android.net.Uri?, 
    onImageClick: () -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    email: String, onEmailChange: (String) -> Unit,
    desc: String, onDescChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        CuidaleImagePicker(
            label = stringResource(R.string.profile_label_photo),
            imageUri = fotoUri,
            onImageClick = onImageClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_phone))
        CuidaleTextField(
            value = phone,
            onValueChange = onPhoneChange,
            placeholder = "+569...",
            containerColor = customColors.inputBackground,
            keyboardType = KeyboardType.Phone
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_email))
        CuidaleTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "correo@ejemplo.cl",
            containerColor = customColors.inputBackground,
            keyboardType = KeyboardType.Email
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_description))
        CuidaleTextField(
            value = desc,
            onValueChange = onDescChange,
            placeholder = stringResource(R.string.profile_label_description_placeholder),
            containerColor = customColors.inputBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        HardShadowButton(
            text = stringResource(R.string.profile_save_changes),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = customColors.isDark,
            onClick = onSave
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HardShadowButton(
            text = stringResource(R.string.register_back_button),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White,
            shadowColor = shadows.hardShadowSecondary,
            isDark = customColors.isDark,
            onClick = onBack
        )
    }
}

@Composable
fun FamilyStep1(
    fotoUri: android.net.Uri?, 
    onImageClick: () -> Unit,
    fullName: String, onFullNameChange: (String) -> Unit,
    age: String, onAgeChange: (String) -> Unit,
    gender: String, onGenderChange: (String) -> Unit,
    comuna: String, onComunaChange: (String) -> Unit,
    address: String, onAddressChange: (String) -> Unit,
    availableCommunes: List<String>,
    onNext: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    
    val genderOptions = listOf("Masculino", "Femenino", "No Especificar")

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        CuidaleImagePicker(
            label = stringResource(R.string.profile_label_photo),
            imageUri = fotoUri,
            onImageClick = onImageClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_fullname))
        CuidaleTextField(
            value = fullName,
            onValueChange = onFullNameChange,
            placeholder = "Nombre y Apellidos",
            containerColor = customColors.inputBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_age))
        CuidaleTextField(
            value = age,
            onValueChange = onAgeChange,
            placeholder = "Ej: 75",
            containerColor = customColors.inputBackground,
            keyboardType = KeyboardType.Number
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CuidaleExposedDropdown(
            label = stringResource(R.string.profile_label_gender),
            options = genderOptions,
            selectedOption = gender,
            onOptionSelected = onGenderChange
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CuidaleExposedDropdown(
            label = "Comuna",
            options = availableCommunes,
            selectedOption = comuna,
            onOptionSelected = onComunaChange
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_address))
        CuidaleTextField(
            value = address,
            onValueChange = onAddressChange,
            placeholder = "Calle #123...",
            containerColor = customColors.inputBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        HardShadowButton(
            text = stringResource(R.string.register_next_button),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = customColors.isDark,
            onClick = onNext
        )
    }
}

@Composable
fun FamilyStep2(
    hasDisability: Boolean, onDisabilityStatusChange: (Boolean) -> Unit,
    disabilityText: String, onDisabilityTextChange: (String) -> Unit,
    hasChronic: Boolean, onChronicStatusChange: (Boolean) -> Unit,
    chronicText: String, onChronicTextChange: (String) -> Unit,
    hasDiet: Boolean, onDietStatusChange: (Boolean) -> Unit,
    dietText: String, onDietTextChange: (String) -> Unit,
    useMedication: Boolean, onMedicationChange: (Boolean) -> Unit,
    conditions: String, onConditionsChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

    HardShadowPanel(
        backgroundColor = customColors.panelBackground,
        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        CuidaleBinaryChoice(
            label = stringResource(R.string.profile_label_disability),
            selectedOption = if (hasDisability) "Si" else "No",
            onOptionSelected = { onDisabilityStatusChange(it == "Si") },
            extraContent = {
                CuidaleTextField(
                    value = disabilityText,
                    onValueChange = onDisabilityTextChange,
                    placeholder = "Especifique...",
                    containerColor = customColors.inputBackground
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CuidaleBinaryChoice(
            label = stringResource(R.string.profile_label_chronic),
            selectedOption = if (hasChronic) "Si" else "No",
            onOptionSelected = { onChronicStatusChange(it == "Si") },
            extraContent = {
                CuidaleTextField(
                    value = chronicText,
                    onValueChange = onChronicTextChange,
                    placeholder = "Especifique...",
                    containerColor = customColors.inputBackground
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CuidaleBinaryChoice(
            label = stringResource(R.string.profile_label_diet),
            selectedOption = if (hasDiet) "Si" else "No",
            onOptionSelected = { onDietStatusChange(it == "Si") },
            extraContent = {
                CuidaleTextField(
                    value = dietText,
                    onValueChange = onDietTextChange,
                    placeholder = "Especifique...",
                    containerColor = customColors.inputBackground
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CuidaleBinaryChoice(
            label = stringResource(R.string.profile_label_medication),
            selectedOption = if (useMedication) "Si" else "No",
            onOptionSelected = { onMedicationChange(it == "Si") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputFieldLabel(stringResource(R.string.profile_label_conditions))
        CuidaleTextField(
            value = conditions,
            onValueChange = onConditionsChange,
            placeholder = stringResource(R.string.profile_label_conditions_placeholder),
            containerColor = customColors.inputBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        HardShadowButton(
            text = stringResource(R.string.profile_save_changes),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shadowColor = shadows.hardShadowPrimary,
            isDark = customColors.isDark,
            onClick = onSave
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HardShadowButton(
            text = stringResource(R.string.register_back_button),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White,
            shadowColor = shadows.hardShadowSecondary,
            isDark = customColors.isDark,
            onClick = onBack
        )
    }
}

@Composable
fun ProfileDetailView(
    role: String,
    data: Map<String, Any>,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileHeader(
            if (role == "cuidador") "Datos Cuidador" else "Datos Familiar",
            "Información guardada en tu perfil"
        )
        
        HardShadowPanel(
            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
            shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f)
        ) {
            HardShadowButton(
                text = stringResource(R.string.profile_edit_data),
                containerColor = colors.secondary,
                contentColor = Color.White,
                shadowColor = CuidaleThemeExtras.shadows.hardShadowSecondary,
                isDark = CuidaleThemeExtras.customColors.isDark,
                onClick = onEdit
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            data.forEach { (key, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = "${key.replace("_", " ").uppercase()}: ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                        modifier = Modifier.width(120.dp)
                    )
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(onClick = onBack) {
            Text("Cerrar", color = Color.White)
        }
    }
}
