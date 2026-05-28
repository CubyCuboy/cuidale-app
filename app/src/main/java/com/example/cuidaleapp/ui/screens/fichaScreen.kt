package com.example.cuidaleapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaScreen(
    viewModel: FichaViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val role by viewModel.role.collectAsState()
    val availableCommunes by viewModel.availableCommunes.collectAsState()
    val gradient = CuidaleThemeExtras.gradient

    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = state is FichaState.Loading

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshProfile() }
    ) {
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
                is FichaState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                is FichaState.View -> {
                    ProfileDetailView(
                        role = role,
                        data = (state as FichaState.View).data,
                        onEdit = { viewModel.startEditing() },
                        onBack = onBack
                    )
                }
                is FichaState.NotFound, is FichaState.Editing -> {
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
                is FichaState.Error -> {
                    Text(
                        text = (state as FichaState.Error).message,
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
}

@Composable
fun CaregiverEditFlow(
    viewModel: FichaViewModel,
    availableCommunes: List<String>,
    onCancel: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    
    // Step 1 Data
    var spec by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    var selectedComunas by remember { mutableStateOf(listOf<String>()) }
    var selectedHorarios by remember { mutableStateOf(listOf<String>()) }
    
    // Step 2 Data
    var fotoUris by remember { mutableStateOf(listOf<Uri>()) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && fotoUris.size < 6) {
            fotoUris = fotoUris + uri
        }
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
                    fotoUris = fotoUris, 
                    onAddImage = { photoLauncher.launch("image/*") },
                    onRemoveImage = { uri -> fotoUris = fotoUris - uri },
                    phone = phone, onPhoneChange = { phone = it },
                    email = email, onEmailChange = { email = it },
                    desc = desc, onDescChange = { desc = it },
                    onBack = { currentStep = 1 },
                    onSave = {
                        viewModel.saveCaregiverProfile(spec, exp, selectedComunas, selectedHorarios, fotoUris, phone, email, desc)
                    }
                )
            }
        }
    }
}

@Composable
fun FamilyEditFlow(
    viewModel: FichaViewModel,
    availableCommunes: List<String>,
    onCancel: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    
    // Step 1 Data
    var fotoUris by remember { mutableStateOf(listOf<Uri>()) }
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
    ) { uri: Uri? ->
        if (uri != null && fotoUris.size < 6) {
            fotoUris = fotoUris + uri
        }
    }

    val title = stringResource(R.string.profile_familiar_title)
    val subtitle = stringResource(R.string.profile_familiar_subtitle)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileHeader(title, subtitle)

        Crossfade(targetState = currentStep, label = "familyFlow") { step ->
            if (step == 1) {
                FamilyStep1(
                    fotoUris = fotoUris, 
                    onAddImage = { photoLauncher.launch("image/*") },
                    onRemoveImage = { uri -> fotoUris = fotoUris - uri },
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
                            fotoUris, fullName, age, gender, selectedComuna, address,
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
    fotoUris: List<Uri>, 
    onAddImage: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
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
        CuidaleMultiImagePicker(
            label = stringResource(R.string.profile_label_photo),
            imageUris = fotoUris,
            onAddImage = onAddImage,
            onRemoveImage = onRemoveImage,
            maxImages = 6
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
    fotoUris: List<Uri>, 
    onAddImage: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
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
        CuidaleMultiImagePicker(
            label = stringResource(R.string.profile_label_photo),
            imageUris = fotoUris,
            onAddImage = onAddImage,
            onRemoveImage = onRemoveImage,
            maxImages = 6
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
    val fotos = data["fotos"] as? List<*> ?: emptyList<Any>()
    var selectedImageForZoom by remember { mutableStateOf<String?>(null) }
    
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
            
            if (fotos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "FOTOS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(fotos) { foto ->
                        AsyncImage(
                            model = foto.toString(),
                            contentDescription = "Ficha Image",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Gray.copy(alpha = 0.2f))
                                .clickable { selectedImageForZoom = foto.toString() },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            data.forEach { (key, value) ->
                if (key != "fotos") {
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
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(onClick = onBack) {
            Text("Cerrar", color = Color.White)
        }
    }

    if (selectedImageForZoom != null) {
        Dialog(onDismissRequest = { selectedImageForZoom = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = selectedImageForZoom,
                    contentDescription = "Zoomed Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { selectedImageForZoom = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }
    }
}
