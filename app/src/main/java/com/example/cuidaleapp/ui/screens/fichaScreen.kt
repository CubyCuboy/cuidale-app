package com.example.cuidaleapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ListAlt
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.data.ChileData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.cuidaleapp.models.*
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaScreen(
    viewModel: FichaViewModel,
    profileViewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTasks: (() -> Unit)? = null,
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
                            onClick = {
                                if (state is FichaState.Editing) {
                                    viewModel.cancelEditing()
                                } else {
                                    onNavigateBack()
                                }
                            },
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
                                text = if (state is FichaState.Editing || (state is FichaState.NotFound)) "Completando información" else "Información registrada",
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
                                    null
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = accentColor)
                                    }
                                }
                            }
                            is FichaState.View -> {
                                val tareasPlantilla by viewModel.tareasPlantilla.collectAsState()
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ProfileDetailView(
                                        role = role,
                                        data = targetState.data,
                                        tareasPlantilla = tareasPlantilla,
                                        onEdit = { 
                                            viewModel.startEditing()
                                        },
                                        onEditTasks = if (role == "representante") {
                                            {
                                                onNavigateToTasks?.invoke()
                                            }
                                        } else null,
                                        isEditMode = false
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
                                val editingData = (targetState as FichaState.Editing).data
                                val tareasTemporales by viewModel.tareasTemporales.collectAsState()
                                
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
                                        tareasTemporales = tareasTemporales,
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
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Color.Gray.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, primaryText.copy(alpha = 0.1f))
            ) {
                if (fotoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fotoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(16.dp), tint = secondaryText)
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nombre, 
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold, 
                        color = primaryText,
                        fontSize = if (nombre.length > 25) 18.sp else 22.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (nombre.length > 25) 22.sp else 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Badge, null, modifier = Modifier.size(12.dp), tint = secondaryText.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "RUT: $rut", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp), tint = secondaryText.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "TEL: $telefono", style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyEditFlow(
    viewModel: FichaViewModel,
    profileViewModel: ProfileViewModel,
    availableCommunes: List<String>,
    initialData: Map<String, Any>?,
    tareasTemporales: List<Tarea>,
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
    
    var condicionesExtra by remember { mutableStateOf(initialData?.get("condiciones") as? String ?: "") }
    var selectedCommunesPref by remember { mutableStateOf(initialData?.get("comunas_preferencia") as? List<String> ?: emptyList()) }
    var selectedHorariosPref by remember { mutableStateOf(initialData?.get("horarios_preferencia") as? List<String> ?: emptyList()) }
    
    val pendingPhoto by viewModel.pendingPatientPhotoUri.collectAsState()
    
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf(false) }
    var showConfirmPhotoDialog by remember { mutableStateOf(false) }
    var photoToConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> 
        uri?.let {
            photoToConfirm = it
            showConfirmPhotoDialog = true
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let {
                photoToConfirm = it
                showConfirmPhotoDialog = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "temp_patient_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    if (showConfirmPhotoDialog && photoToConfirm != null) {
        AlertDialog(
            onDismissRequest = { 
                showConfirmPhotoDialog = false
                photoToConfirm = null
            },
            title = { Text("Confirmar foto", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = photoToConfirm,
                        contentDescription = null,
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("¿Deseas usar esta imagen para el paciente?", textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPendingPatientPhoto(photoToConfirm)
                    pendingDeletePhoto = false
                    showConfirmPhotoDialog = false
                    photoToConfirm = null
                }) {
                    Text("Usar esta")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showConfirmPhotoDialog = false
                    photoToConfirm = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Foto del paciente", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                val file = File(context.cacheDir, "temp_patient_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                tempPhotoUri = uri
                                cameraLauncher.launch(uri)
                                showImageSourceDialog = false
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                                showImageSourceDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cámara")
                    }

                    Button(
                        onClick = {
                            galleryLauncher.launch("image/*")
                            showImageSourceDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Collections, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Galería")
                    }

                    val currentPhoto = initialData?.get("fotoUrl")?.toString() ?: ""
                    if (currentPhoto.isNotEmpty() || pendingPhoto != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Button(
                            onClick = {
                                pendingDeletePhoto = true
                                viewModel.setPendingPatientPhoto(null)
                                showImageSourceDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Quitar foto")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

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
                                nombreCompleto = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
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
                    HardShadowPanel(
                        backgroundColor = cardColor,
                        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                        cornerRadius = 24.dp,
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = accentColor, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("OBSERVACIONES Y FOTO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            
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
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            InputFieldLabel(text = "FOTO DEL PACIENTE", color = textColor)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(customColors.inputBackground)
                                        .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape)
                                        .clickable { showImageSourceDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val currentUrl = initialData?.get("fotoUrl")?.toString() ?: ""
                                    val photoToShow = when {
                                        pendingDeletePhoto -> null
                                        pendingPhoto != null -> pendingPhoto
                                        currentUrl.isNotEmpty() -> currentUrl
                                        else -> null
                                    }

                                    if (photoToShow != null) {
                                        AsyncImage(
                                            model = photoToShow,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.AddAPhoto, null, tint = textColor.copy(alpha = 0.3f))
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = if (pendingDeletePhoto) "Se eliminará la foto" else if (pendingPhoto != null || (initialData?.get("fotoUrl")?.toString()?.isNotEmpty() == true)) "Foto del paciente" else "Sin foto",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    TextButton(
                                        onClick = { showImageSourceDialog = true },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("GESTIONAR FOTO", color = accentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                                    condicionesExtra,
                                    selectedCommunesPref, selectedHorariosPref,
                                    shouldDeletePhoto = pendingDeletePhoto,
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
