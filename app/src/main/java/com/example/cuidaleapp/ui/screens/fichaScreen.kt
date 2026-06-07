package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.data.ChileData
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaScreen(
    viewModel: FichaViewModel,
    profileViewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    startInEditMode: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val role by viewModel.role.collectAsState()
    val availableCommunes by viewModel.availableCommunes.collectAsState()
    val userData by profileViewModel.userData.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isPartialLoading by viewModel.isPartialLoading.collectAsState()
    val scrollState = rememberScrollState()

    val customColors = CuidaleThemeExtras.customColors
    val accentColor = if (role == "cuidador") MaterialTheme.colorScheme.primary else customColors.successStrong
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val panelBg = customColors.panelBackground

    LaunchedEffect(startInEditMode) {
        if (startInEditMode) {
            viewModel.startEditing()
        }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.checkProfile() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Modern Header / Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(accentColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (role == "cuidador") "FICHA DE CUIDADOR" else "FICHA DEL PACIENTE",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = if (state is FichaState.Editing || state is FichaState.NotFound) "Completando información" else "Información registrada",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-30).dp)
                ) {
                    if (role == "representante" && userData.uid.isNotEmpty() && state !is FichaState.Loading) {
                        ModernPersonalDataCard(
                            nombre = "${userData.nombre} ${userData.apellido_pa}",
                            rut = userData.rut,
                            telefono = userData.telefono,
                            fotoUrl = userData.fotoUrl,
                            cardColor = panelBg,
                            primaryText = textColor,
                            secondaryText = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                        },
                        label = "fichaContent"
                    ) { targetState ->
                        when (targetState) {
                            is FichaState.Loading -> {
                                if (isRefreshing) {
                                    // Si estamos refrescando (ya hay datos), no mostrar loading a pantalla completa
                                    // Esto previene el parpadeo
                                    null
                                } else {
                                    // Skeleton loading animation for ficha screen
                                    FichaSkeletonLoader(fullScreen = true, accentColor = accentColor)
                                }
                            }
                            is FichaState.View -> {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ProfileDetailView(
                                        role = role,
                                        data = targetState.data,
                                        onEdit = { viewModel.startEditing() }
                                    )
                                    SearchPreferencesCard(
                                        role = role,
                                        data = targetState.data,
                                        availableCommunes = availableCommunes,
                                        onUpdate = { c, h -> viewModel.updateSearchPreferences(c, h) },
                                        accentColor = accentColor,
                                        panelBg = panelBg,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor,
                                        isLoading = isPartialLoading
                                    )
                                }
                            }
                            is FichaState.NotFound -> {
                                FichaPlaceholder(
                                    role = role,
                                    accentColor = accentColor,
                                    panelBg = panelBg,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onCreateClick = { viewModel.startEditing() }
                                )
                            }
                            is FichaState.Editing -> {
                                val editingData = (targetState as? FichaState.Editing)?.data
                                if (role == "cuidador") {
                                    CaregiverEditFlow(
                                        viewModel = viewModel,
                                        availableCommunes = availableCommunes,
                                        initialData = editingData,
                                        cardColor = panelBg,
                                        accentColor = accentColor,
                                        onFinishSuccess = { if (startInEditMode) onNavigateBack() }
                                    )
                                } else {
                                    FamilyEditFlow(
                                        viewModel = viewModel,
                                        profileViewModel = profileViewModel,
                                        availableCommunes = availableCommunes,
                                        initialData = editingData,
                                        cardColor = panelBg,
                                        accentColor = accentColor,
                                        onFinishSuccess = { if (startInEditMode) onNavigateBack() }
                                    )
                                }
                            }
                            is FichaState.Error -> {
                                ModernErrorState(targetState.message, panelBg, accentColor) { viewModel.checkProfile() }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
fun ModernPersonalDataCard(
    nombre: String, rut: String, telefono: String, fotoUrl: String,
    cardColor: Color, primaryText: Color, secondaryText: Color
) {
    val shadowPalette = CuidaleThemeExtras.shadows
    HardShadowPanel(
        backgroundColor = cardColor,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = Color.Gray.copy(alpha = 0.2f)
            ) {
                if (fotoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = fotoUrl,
                        contentDescription = null,
                        modifier = Modifier.clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(14.dp), tint = secondaryText)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(text = nombre, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = primaryText))
                Text(text = "RUT: $rut", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                Text(text = "TEL: $telefono", style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
    }
}

@Composable
fun ModernErrorState(message: String, cardColor: Color, accentColor: Color, onRetry: () -> Unit) {
    val shadowPalette = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val textColor = MaterialTheme.colorScheme.onSurface
    HardShadowPanel(backgroundColor = cardColor, shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, textAlign = TextAlign.Center, color = textColor)
            Spacer(modifier = Modifier.height(24.dp))
            HardShadowButton(
                text = "REINTENTAR",
                onClick = onRetry,
                containerColor = accentColor,
                contentColor = Color.White,
                shadowColor = shadowPalette.hardShadowPrimary,
                isDark = customColors.isDark
            )
        }
    }
}

@Composable
fun CaregiverEditFlow(
    viewModel: FichaViewModel,
    availableCommunes: List<String>,
    initialData: Map<String, Any>?,
    cardColor: Color,
    accentColor: Color,
    onFinishSuccess: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isDark = customColors.isDark
    
    var step by remember { mutableIntStateOf(1) }
    var especializacion by remember { mutableStateOf(initialData?.get("especializacion") as? String ?: "") }
    var experiencia by remember { mutableStateOf(initialData?.get("experiencia") as? String ?: "") }
    var selectedCommunes by remember { mutableStateOf<List<String>>(initialData?.get("comunas") as? List<String> ?: emptyList()) }
    var selectedHorarios by remember { mutableStateOf<List<String>>(initialData?.get("horarios") as? List<String> ?: emptyList()) }
    var descripcion by remember { mutableStateOf(initialData?.get("descripcion") as? String ?: "") }

    Column {
        if (step == 1) {
            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("FORMACIÓN Y ÁREA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    CuidaleExposedDropdown(
                        label = "ESPECIALIZACIÓN",
                        options = listOf("Enfermero/a", "Técnico en Enfermería", "Cuidador de Adulto Mayor", "Cuidador de Niños", "Otro"),
                        selectedOption = especializacion,
                        onOptionSelected = { especializacion = it }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    InputFieldLabel(text = "EXPERIENCIA", color = textColor)
                    CuidaleTextField(
                        value = experiencia, onValueChange = { experiencia = it },
                        placeholder = "Años de experiencia, lugares...",
                        singleLine = false, modifier = Modifier.height(100.dp),
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                        HardShadowButton(
                            text = "SIGUIENTE", onClick = { step = 2 },
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            isDark = isDark
                        )
                }
            }
        } else {
            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EditNote, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("PRESENTACIÓN PERSONAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    InputFieldLabel(text = "SOBRE TI", color = textColor)
                    CuidaleTextField(
                        value = descripcion, onValueChange = { if(it.length <= 300) descripcion = it },
                        placeholder = "Cuéntanos más sobre tu vocación...",
                        singleLine = false, modifier = Modifier.height(150.dp),
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )
                    Text("${descripcion.length}/300", modifier = Modifier.align(Alignment.End).padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = secondaryTextColor.copy(alpha = 0.8f))
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HardShadowButton(
                            text = "VOLVER",
                            onClick = { step = 1 },
                            modifier = Modifier.weight(1f).height(58.dp),
                            containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                            contentColor = if (isDark) Color.White else Color.DarkGray,
                            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = if (isDark) 0.15f else 0.1f),
                            isDark = isDark
                        )
                        HardShadowButton(
                            text = "FINALIZAR",
                            onClick = {
                                viewModel.createCaregiverFicha(
                                    especializacion, 
                                    experiencia, 
                                    selectedCommunes, 
                                    selectedHorarios, 
                                    descripcion,
                                    onSuccess = onFinishSuccess
                                )
                            },
                            modifier = Modifier.weight(1.5f).height(58.dp),
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun FamilyEditFlow(
    viewModel: FichaViewModel,
    profileViewModel: ProfileViewModel,
    availableCommunes: List<String>,
    initialData: Map<String, Any>?,
    cardColor: Color,
    accentColor: Color,
    onFinishSuccess: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val isDark = customColors.isDark
    
    var step by remember { mutableIntStateOf(1) }
    val currentUserData by profileViewModel.userData.collectAsState()
    
    var nombreCompleto by remember { mutableStateOf(initialData?.get("nombre_completo") as? String ?: "") }
    var edad by remember { mutableStateOf(initialData?.get("edad") as? String ?: "") }
    var genero by remember { mutableStateOf(initialData?.get("genero") as? String ?: "") }
    var vivesConFamiliar by remember { mutableStateOf(initialData?.get("vives_con_familiar") as? Boolean ?: false) }
    var region by remember { mutableStateOf(initialData?.get("region") as? String ?: "") }
    var comuna by remember { mutableStateOf(initialData?.get("comuna") as? String ?: "") }
    var calle by remember { mutableStateOf(initialData?.get("calle") as? String ?: "") }
    var numero by remember { mutableStateOf(initialData?.get("numero") as? String ?: "") }
    var depto by remember { mutableStateOf(initialData?.get("depto") as? String ?: "") }
    var nivelDependencia by remember { mutableStateOf(initialData?.get("nivel_dependencia") as? String ?: "Independiente") }
    var movilidad by remember { mutableStateOf(initialData?.get("movilidad") as? String ?: "Normal") }
    var comunicacion by remember { mutableStateOf(initialData?.get("comunicacion") as? String ?: "Normal") }
    var discapacidades by remember { mutableStateOf(initialData?.get("discapacidades") as? List<String> ?: emptyList()) }
    var discapacidadOtro by remember { mutableStateOf(initialData?.get("discapacidad_otro") as? String ?: "") }
    var enfermedades by remember { mutableStateOf(initialData?.get("enfermedades") as? List<String> ?: emptyList()) }
    var enfermedadOtro by remember { mutableStateOf(initialData?.get("enfermedad_otro") as? String ?: "") }
    
    val defaultDieta = mapOf(
        "desayuno" to "",
        "colacion_manana" to "",
        "almuerzo" to "",
        "colacion_tarde" to "",
        "once" to "",
        "cena" to "",
        "colacion_noche" to ""
    )
    var dieta by remember { mutableStateOf(initialData?.get("dieta") as? Map<String, String> ?: defaultDieta) }
    var medicamentos by remember { mutableStateOf(initialData?.get("medicamentos") as? List<Map<String, Any>> ?: emptyList()) }
    var condicionesExtra by remember { mutableStateOf(initialData?.get("condiciones") as? String ?: "") }
    var selectedCommunesPref by remember { mutableStateOf(initialData?.get("comunas_preferencia") as? List<String> ?: emptyList()) }
    var selectedHorariosPref by remember { mutableStateOf(initialData?.get("horarios_preferencia") as? List<String> ?: emptyList()) }

    LaunchedEffect(vivesConFamiliar, currentUserData.region, currentUserData.comuna) {
        if (vivesConFamiliar) {
            region = currentUserData.region
            comuna = currentUserData.comuna
        }
    }

    val regionsList = ChileData.regions.map { it.name }
    val communesForRegion = ChileData.regions.find { it.name == region }?.comunas?.sorted() ?: emptyList()

    val isStep1Valid = nombreCompleto.isNotBlank() && edad.isNotBlank() && genero.isNotBlank() &&
        region.isNotBlank() && comuna.isNotBlank() && calle.isNotBlank() && numero.isNotBlank()

    Column {
        when (step) {
            1 -> {
                HardShadowPanel(
                    backgroundColor = cardColor,
                    shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = accentColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("DATOS DEL PACIENTE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        InputFieldLabel(text = "NOMBRE DEL PACIENTE", color = textColor)
                        CuidaleTextField(
                            value = nombreCompleto,
                            onValueChange = { input -> 
                                nombreCompleto = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                            },
                            placeholder = "Nombre Completo",
                            containerColor = customColors.inputBackground,
                            textColor = textColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                InputFieldLabel(text = "EDAD", color = textColor)
                                CuidaleTextField(
                                    value = edad,
                                    onValueChange = { if ((it.isEmpty() || it.all { char -> char.isDigit() }) && it.length <= 3) edad = it },
                                    placeholder = "Ej: 70",
                                    containerColor = customColors.inputBackground,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    textColor = textColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(modifier = Modifier.weight(1.5f)) {
                                CuidaleExposedDropdown(label = "GÉNERO", options = listOf("Masculino", "Femenino", "Otro"), selectedOption = genero, onOptionSelected = { genero = it })
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = textColor.copy(alpha = if (isDark) 0.15f else 0.1f))
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("UBICACIÓN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("¿VIVE CONTIGO?", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = vivesConFamiliar, 
                                onCheckedChange = { vivesConFamiliar = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                            )
                        }
                        
                        if (!vivesConFamiliar) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CuidaleExposedDropdown(label = "REGIÓN", options = regionsList, selectedOption = region, onOptionSelected = { region = it })
                            Spacer(modifier = Modifier.height(12.dp))
                            CuidaleExposedDropdown(label = "COMUNA", options = communesForRegion, selectedOption = comuna, onOptionSelected = { comuna = it }, enabled = region.isNotBlank())
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = accentColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Se usará tu dirección actual: ${currentUserData.comuna}, ${currentUserData.region}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        InputFieldLabel(text = "DIRECCIÓN DEL PACIENTE", color = textColor)
                        CuidaleTextField(
                            value = calle,
                            onValueChange = { input -> 
                                calle = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                            },
                            placeholder = "Calle / Pasaje",
                            containerColor = customColors.inputBackground,
                            textColor = textColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CuidaleTextField(
                                value = numero,
                                onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 6) numero = it },
                                placeholder = "N°",
                                modifier = Modifier.weight(1f),
                                containerColor = customColors.inputBackground,
                                textColor = textColor,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                            CuidaleTextField(
                                value = depto,
                                onValueChange = { input ->
                                    depto = input.uppercase()
                                },
                                placeholder = "Depto/Block (Opcional)",
                                modifier = Modifier.weight(1f),
                                containerColor = customColors.inputBackground,
                                textColor = textColor
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        HardShadowButton(
                            text = "SIGUIENTE",
                            onClick = { step = 2 },
                            enabled = isStep1Valid,
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            isDark = isDark
                        )
                    }
                }
            }
            2 -> {
                HardShadowPanel(
                    backgroundColor = cardColor,
                    shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HealthAndSafety, null, tint = accentColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("ESTADO DE SALUD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        CuidaleExposedDropdown(label = "DEPENDENCIA", options = listOf("Independiente", "Leve", "Moderada", "Severa"), selectedOption = nivelDependencia, onOptionSelected = { nivelDependencia = it })
                        Spacer(modifier = Modifier.height(16.dp))
                        CuidaleExposedDropdown(label = "MOVILIDAD", options = listOf("Normal", "Reducida", "Silla de Ruedas", "Postrado"), selectedOption = movilidad, onOptionSelected = { movilidad = it })
                        Spacer(modifier = Modifier.height(20.dp))

                        CuidaleMultiSelectDropdown(
                            label = "DISCAPACIDADES",
                            options = listOf("Visual", "Auditiva", "Motriz", "Intelectual", "Psíquica", "Otro"),
                            selectedOptions = discapacidades,
                            onOptionToggled = {
                                if (discapacidades.contains(it)) discapacidades -= it
                                else discapacidades += it
                            },
                            placeholder = "Seleccionar discapacidades..."
                        )
                        if (discapacidades.contains("Otro")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CuidaleTextField(value = discapacidadOtro, onValueChange = { discapacidadOtro = it }, placeholder = "Especifique discapacidad...", containerColor = customColors.inputBackground, textColor = textColor)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        CuidaleMultiSelectDropdown(
                            label = "ENFERMEDADES CRÓNICAS",
                            options = listOf("Diabetes", "Hipertensión", "Alzheimer", "Parkinson", "Artrosis", "Otro"),
                            selectedOptions = enfermedades,
                            onOptionToggled = {
                                if (enfermedades.contains(it)) enfermedades -= it
                                else enfermedades += it
                            },
                            placeholder = "Seleccionar enfermedades..."
                        )
                        if (enfermedades.contains("Otro")) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CuidaleTextField(value = enfermedadOtro, onValueChange = { enfermedadOtro = it }, placeholder = "Especifique enfermedades...", containerColor = customColors.inputBackground, textColor = textColor)
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HardShadowButton(
                                text = "VOLVER",
                                onClick = { step = 1 },
                                modifier = Modifier.weight(1f).height(58.dp),
                                containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                                contentColor = if (isDark) Color.White else Color.DarkGray,
                                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = if (isDark) 0.15f else 0.1f),
                                isDark = isDark
                            )
                            HardShadowButton(
                                text = "SIGUIENTE",
                                onClick = { step = 3 },
                                modifier = Modifier.weight(1f).height(58.dp),
                                containerColor = accentColor,
                                contentColor = Color.White,
                                shadowColor = shadowPalette.hardShadowPrimary,
                                isDark = isDark
                            )
                        }
                    }
                }
            }
            3 -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "DIETA Y MEDICAMENTOS", 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
                        color = accentColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    HardShadowPanel(
                        backgroundColor = cardColor,
                        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                        cornerRadius = 24.dp,
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Restaurant, null, tint = accentColor, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("PLAN DE ALIMENTACIÓN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                            }
                            
                            val dietFields = listOf(
                                "desayuno" to "DESAYUNO",
                                "colacion_manana" to "COLACIÓN MAÑANA",
                                "almuerzo" to "ALMUERZO",
                                "colacion_tarde" to "COLACIÓN TARDE",
                                "once" to "ONCE",
                                "cena" to "CENA",
                                "colacion_noche" to "COLACIÓN NOCHE"
                            )

                            dietFields.forEach { (key, label) ->
                                Column {
                                    InputFieldLabel(text = label, color = textColor)
                                    CuidaleTextField(
                                        value = dieta[key] ?: "",
                                        onValueChange = { v ->
                                            val newDieta = dieta.toMutableMap()
                                            newDieta[key] = v
                                            dieta = newDieta
                                        },
                                        placeholder = "Indicaciones para $label...",
                                        containerColor = customColors.inputBackground,
                                        textColor = textColor
                                    )
                                }
                            }
                        }
                    }
                    
                    ModernMedicamentosSection(medicamentos, onMedicamentosChange = { medicamentos = it }, cardColor, accentColor)
                    
                    HardShadowPanel(
                        backgroundColor = cardColor,
                        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                        cornerRadius = 24.dp,
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Column {
                            InputFieldLabel(text = "OBSERVACIONES ADICIONALES", color = textColor)
                            CuidaleTextField(
                                value = condicionesExtra,
                                onValueChange = { condicionesExtra = it },
                                placeholder = "Indique cualquier otra condición o cuidado especial...",
                                singleLine = false,
                                modifier = Modifier.height(100.dp),
                                containerColor = customColors.inputBackground,
                                textColor = textColor
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                HardShadowButton(
                                    text = "VOLVER",
                                    onClick = { step = 2 },
                                    modifier = Modifier.weight(1f).height(58.dp),
                                    containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                                    contentColor = if (isDark) Color.White else Color.DarkGray,
                                    shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = if (isDark) 0.15f else 0.1f),
                                    isDark = isDark
                                )
                                HardShadowButton(
                                    text = "FINALIZAR",
                                    onClick = {
                                        viewModel.createFamilyFicha(
                                            nombreCompleto, edad, genero, vivesConFamiliar, 
                                            region, comuna, calle, numero, depto, 
                                            nivelDependencia, movilidad, comunicacion, 
                                            discapacidades, discapacidadOtro, enfermedades, enfermedadOtro, 
                                            dieta, medicamentos, condicionesExtra, 
                                            selectedCommunesPref, selectedHorariosPref,
                                            onSuccess = onFinishSuccess
                                        )
                                    },
                                    modifier = Modifier.weight(1.5f).height(58.dp),
                                    containerColor = accentColor,
                                    contentColor = Color.White,
                                    shadowColor = shadowPalette.hardShadowPrimary,
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernMedicamentosSection(
    medicamentos: List<Map<String, Any>>,
    onMedicamentosChange: (List<Map<String, Any>>) -> Unit,
    cardColor: Color,
    accentColor: Color
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val isDark = customColors.isDark
    
    var showTimePickerForMed by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val timeState = rememberTimePickerState()

    if (showTimePickerForMed != null) {
        TimePickerDialog(
            onDismissRequest = { showTimePickerForMed = null },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timeState.hour.toString().padStart(2, '0')
                    val min = timeState.minute.toString().padStart(2, '0')
                    val (mIdx, aIdx) = showTimePickerForMed!!
                    val newList = medicamentos.toMutableList()
                    val med = newList[mIdx].toMutableMap()
                    val admins = (med["administraciones"] as? List<Map<String, String>> ?: listOf()).toMutableList()
                    val admin = admins[aIdx].toMutableMap()
                    admin["hora"] = "$hour:$min"
                    admins[aIdx] = admin
                    med["administraciones"] = admins
                    newList[mIdx] = med
                    onMedicamentosChange(newList)
                    showTimePickerForMed = null
                }) { Text("OK", fontWeight = FontWeight.Bold, color = accentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerForMed = null }) {
                    Text("CANCELAR", color = textColor.copy(alpha = 0.6f))
                }
            }
        ) {
            TimePicker(state = timeState)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        medicamentos.forEachIndexed { mIdx, med ->
            HardShadowPanel(
                backgroundColor = if (isDark) customColors.panelBackground.copy(alpha = 0.7f) else Color.White,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 16.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = accentColor.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Medication, null, tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "MEDICAMENTO #${mIdx + 1}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                            color = textColor.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { onMedicamentosChange(medicamentos.toMutableList().apply { removeAt(mIdx) }) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    CuidaleTextField(
                        value = med["nombre"] as? String ?: "",
                        onValueChange = { v ->
                            val nl = medicamentos.toMutableList()
                            val nm = nl[mIdx].toMutableMap()
                            nm["nombre"] = v
                            nl[mIdx] = nm
                            onMedicamentosChange(nl)
                        },
                        placeholder = "Nombre (ej: Paracetamol)",
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        "HORARIOS Y DOSIS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = textColor.copy(alpha = 0.5f)
                    )
                    
                    val admins = med["administraciones"] as? List<Map<String, String>> ?: listOf()
                    admins.forEachIndexed { aIdx, admin ->
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { showTimePickerForMed = mIdx to aIdx },
                                color = customColors.inputBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Icon(Icons.Default.Schedule, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = admin["hora"]?.ifEmpty { "Hora" } ?: "Hora",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (admin["hora"].isNullOrEmpty()) textColor.copy(alpha = 0.4f) else textColor
                                    )
                                }
                            }

                            CuidaleTextField(
                                value = admin["dosis"] ?: "",
                                onValueChange = { v ->
                                    val nl = medicamentos.toMutableList()
                                    val nm = nl[mIdx].toMutableMap()
                                    val na = (nm["administraciones"] as? List<Map<String, String>>)?.toMutableList() ?: mutableListOf()
                                    val nad = na[aIdx].toMutableMap()
                                    nad["dosis"] = v
                                    na[aIdx] = nad
                                    nm["administraciones"] = na
                                    nl[mIdx] = nm
                                    onMedicamentosChange(nl)
                                },
                                placeholder = "Dosis",
                                modifier = Modifier.weight(1.5f),
                                containerColor = customColors.inputBackground,
                                textColor = textColor
                            )
                            
                            if (admins.size > 1) {
                                IconButton(onClick = {
                                    val nl = medicamentos.toMutableList()
                                    val nm = nl[mIdx].toMutableMap()
                                    val na = (nm["administraciones"] as? List<Map<String, String>>)?.toMutableList() ?: mutableListOf()
                                    na.removeAt(aIdx)
                                    nm["administraciones"] = na
                                    nl[mIdx] = nm
                                    onMedicamentosChange(nl)
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.RemoveCircleOutline, "Remover", tint = textColor.copy(alpha = 0.4f)) }
                            }
                        }
                    }
                    TextButton(onClick = {
                        val nl = medicamentos.toMutableList()
                        val nm = nl[mIdx].toMutableMap()
                        val na = (nm["administraciones"] as? List<Map<String, String>>)?.toMutableList() ?: mutableListOf()
                        na.add(mapOf("hora" to "", "dosis" to ""))
                        nm["administraciones"] = na
                        nl[mIdx] = nm
                        onMedicamentosChange(nl)
                    }, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AGREGAR HORARIO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accentColor)
                    }
                }
            }
        }
        
        HardShadowButton(
            text = "AÑADIR MEDICAMENTO",
            onClick = { onMedicamentosChange(medicamentos + mapOf("nombre" to "", "administraciones" to listOf(mapOf("hora" to "", "dosis" to "")), "observaciones" to "")) },
            containerColor = accentColor,
            contentColor = Color.White,
            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = if (isDark) 0.2f else 0.3f),
            icon = Icons.Default.Add,
            isDark = isDark
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                content()
            }
        }
    )
}
