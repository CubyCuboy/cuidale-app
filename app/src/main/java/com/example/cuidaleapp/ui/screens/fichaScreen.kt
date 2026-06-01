package com.example.cuidaleapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.models.ProfileImage
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val remoteState by profileViewModel.remoteState.collectAsState()
    val userData = remoteState.data
    val profilePhoto = userData?.profilePhoto
    val galleryPhotos = userData?.galleryPhotos ?: emptyList()

    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val scrollState = rememberScrollState()

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
                .background(Brush.verticalGradient(listOf(CuidaleThemeExtras.gradient.top, CuidaleThemeExtras.gradient.bottom)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (role == "cuidador") "Cuidador" else "Representante",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
                            color = Color.White
                        )
                        Text(
                            text = if (role == "cuidador") "Ingrese sus datos como Cuidador" else "Ingrese los datos del paciente",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    label = "fichaContent"
                ) { targetState ->
                    Column {
                        if (role == "representante" && userData != null && targetState !is FichaState.Loading) {
                            PersonalDataCard(
                                userData = userData,
                                onEditClick = null,
                                onPhotoClick = {
                                    userData.profilePhoto?.let { photo ->
                                        profileViewModel.openGallery(0, listOf(photo) + userData.galleryPhotos)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        when (targetState) {
                            is FichaState.Loading -> {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                            is FichaState.View -> {
                                ProfileDetailView(
                                    role = role,
                                    data = targetState.data,
                                    onEdit = { viewModel.startEditing() },
                                    onPhotoClick = { index, photos ->
                                        profileViewModel.openGallery(index, photos.mapNotNull { it as? ProfileImage ?: (it as? String)?.let { url -> ProfileImage(remoteUrl = url) } })
                                    }
                                )
                            }
                            is FichaState.NotFound, is FichaState.Editing -> {
                                val editingData = (targetState as? FichaState.Editing)?.data
                                if (role == "cuidador") {
                                    CaregiverEditFlow(
                                        viewModel = viewModel,
                                        availableCommunes = availableCommunes,
                                        profilePhoto = profilePhoto,
                                        galleryPhotos = galleryPhotos,
                                        initialData = editingData,
                                        onCancel = { 
                                            if (startInEditMode) {
                                                onNavigateBack()
                                            } else if (targetState is FichaState.Editing) {
                                                viewModel.cancelEditing()
                                            } else {
                                                onNavigateBack()
                                            }
                                        },
                                        onFinishSuccess = {
                                            if (startInEditMode) {
                                                onNavigateBack()
                                            }
                                        }
                                    )
                                } else {
                                    FamilyEditFlow(
                                        viewModel = viewModel,
                                        profileViewModel = profileViewModel,
                                        availableCommunes = availableCommunes,
                                        profilePhoto = profilePhoto,
                                        galleryPhotos = galleryPhotos,
                                        initialData = editingData,
                                        onCancel = {
                                            if (startInEditMode) {
                                                onNavigateBack()
                                            } else if (targetState is FichaState.Editing) {
                                                viewModel.cancelEditing()
                                            } else {
                                                onNavigateBack()
                                            }
                                        },
                                        onFinishSuccess = {
                                            if (startInEditMode) {
                                                onNavigateBack()
                                            }
                                        }
                                    )
                                }
                            }
                            is FichaState.Error -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = targetState.message,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.checkProfile() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                    ) {
                                        Text(text = "Reintentar")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaregiverEditFlow(
    viewModel: FichaViewModel,
    availableCommunes: List<String>,
    profilePhoto: ProfileImage?,
    galleryPhotos: List<ProfileImage>,
    initialData: Map<String, Any>? = null,
    onCancel: () -> Unit,
    onFinishSuccess: () -> Unit = {}
) {
    var step by remember { mutableIntStateOf(1) }
    var especializacion by remember { mutableStateOf(initialData?.get("especializacion") as? String ?: "") }
    var experiencia by remember { mutableStateOf(initialData?.get("experiencia") as? String ?: "") }
    var selectedCommunes by remember { mutableStateOf<List<String>>(initialData?.get("comunas") as? List<String> ?: emptyList()) }
    var descripcion by remember { mutableStateOf(initialData?.get("descripcion") as? String ?: "") }
    var photosUris by remember { 
        mutableStateOf<List<Uri>>((initialData?.get("fotos") as? List<*>)?.mapNotNull { it.toString().toUri() } ?: emptyList()) 
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(targetState = step, label = "caregiverFlow") { currentStep ->
            if (currentStep == 1) {
                CaregiverStep1(
                    especializacion = especializacion,
                    onEspecializacionChange = { especializacion = it },
                    experiencia = experiencia,
                    onExperienciaChange = { experiencia = it },
                    availableCommunes = availableCommunes,
                    selectedCommunes = selectedCommunes,
                    onCommuneToggle = { commune ->
                        selectedCommunes = if (selectedCommunes.contains(commune)) {
                            selectedCommunes - commune
                        } else {
                            if (selectedCommunes.size < 3) selectedCommunes + commune else selectedCommunes
                        }
                    },
                    onNext = { step = 2 }
                )
            } else {
                CaregiverStep2(
                    photos = photosUris,
                    profilePhoto = profilePhoto,
                    galleryPhotos = galleryPhotos,
                    onAddPhoto = { uri -> photosUris = photosUris + uri },
                    onRemovePhoto = { uri -> photosUris = photosUris - uri },
                    descripcion = descripcion,
                    onDescripcionChange = { descripcion = it },
                    onBack = { step = 1 },
                    onFinish = {
                        viewModel.createCaregiverFicha(especializacion, experiencia, selectedCommunes, descripcion, photosUris)
                        onFinishSuccess()
                    }
                )
            }
        }
    }
}

@Composable
fun FamilyEditFlow(
    viewModel: FichaViewModel,
    profileViewModel: ProfileViewModel,
    availableCommunes: List<String>,
    profilePhoto: ProfileImage?,
    galleryPhotos: List<ProfileImage>,
    initialData: Map<String, Any>? = null,
    onCancel: () -> Unit,
    onFinishSuccess: () -> Unit = {}
) {
    var step by remember { mutableIntStateOf(1) }
    val userProfile by profileViewModel.remoteState.collectAsState()
    
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
    
    var dieta by remember { 
        mutableStateOf(initialData?.get("dieta") as? Map<String, String> ?: mapOf(
            "desayuno" to "", "break" to "", "almuerzo" to "", "once" to "", "cena" to ""
        ))
    }
    
    var medicamentos by remember { 
        mutableStateOf(initialData?.get("medicamentos") as? List<Map<String, Any>> ?: emptyList()) 
    }

    var condicionesExtra by remember { mutableStateOf(initialData?.get("condiciones") as? String ?: "") }
    
    var selectedCommunesPref by remember { mutableStateOf<List<String>>(initialData?.get("comunas_preferencia") as? List<String> ?: emptyList()) }
    var selectedHorariosPref by remember { mutableStateOf<List<String>>(initialData?.get("horarios_preferencia") as? List<String> ?: emptyList()) }

    var photosUris by remember { 
        mutableStateOf<List<Uri>>((initialData?.get("fotos") as? List<*>)?.mapNotNull { it.toString().toUri() } ?: emptyList()) 
    }

    LaunchedEffect(vivesConFamiliar, userProfile.data) {
        if (vivesConFamiliar && userProfile.data != null) {
            region = userProfile.data?.region ?: ""
            comuna = userProfile.data?.comuna ?: ""
        }
    }

    val regionsList = ChileData.regions.map { it.name }
    val communesForSelectedRegion = remember(region) {
        ChileData.regions.find { it.name == region }?.comunas?.sorted() ?: emptyList()
    }

    Crossfade(targetState = step, label = "familyFlow") { currentStep ->
        when (currentStep) {
            1 -> {
                FamilyStep1(
                    photos = photosUris,
                    profilePhoto = profilePhoto,
                    galleryPhotos = galleryPhotos,
                    onAddPhoto = { uri -> photosUris = photosUris + uri },
                    onRemovePhoto = { uri -> photosUris = photosUris - uri },
                    nombreCompleto = nombreCompleto,
                    onNombreChange = { nombreCompleto = it },
                    edad = edad,
                    onEdadChange = { edad = it },
                    genero = genero,
                    onGeneroChange = { genero = it },
                    vivesConFamiliar = vivesConFamiliar,
                    onVivesConFamiliarChange = { vivesConFamiliar = it },
                    region = region,
                    onRegionChange = { region = it },
                    comuna = comuna,
                    onComunaChange = { comuna = it },
                    calle = calle,
                    onCalleChange = { calle = it },
                    numero = numero,
                    onNumeroChange = { numero = it },
                    depto = depto,
                    onDeptoChange = { depto = it },
                    availableRegions = regionsList,
                    availableCommunes = communesForSelectedRegion,
                    selectedCommunesPref = selectedCommunesPref,
                    onCommunesPrefChange = { selectedCommunesPref = it },
                    selectedHorariosPref = selectedHorariosPref,
                    onHorariosPrefChange = { selectedHorariosPref = it },
                    onNext = { step = 2 }
                )
            }
            2 -> {
                FamilyStep2(
                    nivelDependencia = nivelDependencia,
                    onNivelDependenciaChange = { nivelDependencia = it },
                    movilidad = movilidad,
                    onMovilidadChange = { movilidad = it },
                    comunicacion = comunicacion,
                    onComunicacionChange = { comunicacion = it },
                    discapacidades = discapacidades,
                    onDiscapacidadesChange = { discapacidades = it },
                    discapacidadOtro = discapacidadOtro,
                    onDiscapacidadOtroChange = { discapacidadOtro = it },
                    enfermedades = enfermedades,
                    onEnfermedadesChange = { enfermedades = it },
                    enfermedadOtro = enfermedadOtro,
                    onEnfermedadOtroChange = { enfermedadOtro = it },
                    condicionesExtra = condicionesExtra,
                    onCondicionesChange = { condicionesExtra = it },
                    onBack = { step = 1 },
                    onNext = { step = 3 }
                )
            }
            3 -> {
                FamilyStep3(
                    dieta = dieta,
                    onDietaChange = { dieta = it },
                    medicamentos = medicamentos,
                    onMedicamentosChange = { medicamentos = it },
                    onBack = { step = 2 },
                    onFinish = {
                        viewModel.createFamilyFicha(
                            nombreCompleto, edad, genero, vivesConFamiliar, region, comuna,
                            calle, numero, depto, nivelDependencia, movilidad, comunicacion,
                            discapacidades, discapacidadOtro,
                            enfermedades, enfermedadOtro,
                            dieta, medicamentos,
                            condicionesExtra, photosUris,
                            selectedCommunesPref, selectedHorariosPref
                        )
                        onFinishSuccess()
                    }
                )
            }
        }
    }
}

@Composable
fun FichaPhotoPicker(
    profilePhoto: ProfileImage?,
    galleryPhotos: List<ProfileImage>,
    selectedUris: List<Uri>,
    onAddUri: (Uri) -> Unit,
    onRemoveUri: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAddUri(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            val availableFromProfile = (listOfNotNull(profilePhoto) + galleryPhotos)
            
            items(availableFromProfile) { img ->
                val uriStr = img.remoteUrl ?: ""
                if (uriStr.isNotBlank()) {
                    val uri = uriStr.toUri()
                    val isSelected = selectedUris.contains(uri)
                    FichaPhotoItem(
                        model = uriStr,
                        label = if (img.isProfilePhoto) "Perfil" else "Galería",
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) onRemoveUri(uri) else onAddUri(uri)
                        }
                    )
                }
            }

            val profileAndGalleryUris = availableFromProfile.mapNotNull { it.remoteUrl?.toUri() }
            val localUris = selectedUris.filter { !profileAndGalleryUris.contains(it) }

            items(localUris) { uri ->
                FichaPhotoItem(
                    model = uri,
                    isSelected = true,
                    isRemovable = true,
                    onRemove = { onRemoveUri(uri) }
                )
            }
        }
    }
}

@Composable
fun FichaPhotoItem(
    model: Any,
    label: String? = null,
    isSelected: Boolean = false,
    isRemovable: Boolean = false,
    onClick: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Gray.copy(alpha = 0.1f))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = if (isSelected) 1f else 0.4f
        )

        if (label != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (isRemovable) {
            IconButton(
                onClick = { onRemove() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .padding(4.dp)
                    .background(Color.Red.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        
        if (isSelected && !isRemovable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaregiverStep1(
    especializacion: String,
    onEspecializacionChange: (String) -> Unit,
    experiencia: String,
    onExperienciaChange: (String) -> Unit,
    availableCommunes: List<String>,
    selectedCommunes: List<String>,
    onCommuneToggle: (String) -> Unit,
    onNext: () -> Unit
) {
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CuidaleExposedDropdown(
                label = "Especialización o formación",
                options = listOf("Enfermero/a", "Técnico en Enfermería", "Cuidador de Adulto Mayor", "Cuidador de Niños", "Otro"),
                selectedOption = especializacion,
                onOptionSelected = onEspecializacionChange,
                placeholder = "Ej: Técnico en Enfermería"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Experiencia en cuidados", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            CuidaleTextField(
                value = experiencia, 
                onValueChange = onExperienciaChange, 
                placeholder = "Ej: 3 años trabajando en residencia de adultos mayores...", 
                containerColor = MaterialTheme.colorScheme.surface,
                singleLine = false,
                modifier = Modifier.height(120.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            CuidaleMultiSelectDropdown(
                label = "Comunas de preferencia (Máx 3)",
                options = availableCommunes,
                selectedOptions = selectedCommunes,
                onOptionToggled = onCommuneToggle,
                maxSelection = 3,
                placeholder = "Ej: Providencia, Las Condes..."
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            HardShadowButton(
                text = "Siguiente", 
                onClick = onNext, 
                modifier = Modifier.fillMaxWidth(), 
                containerColor = Color(0xFF5C6BC0), 
                contentColor = Color.White, 
                shadowColor = Color(0xFF3949AB), 
                isDark = false,
                icon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    }
}

@Composable
fun CaregiverStep2(
    photos: List<Uri>,
    profilePhoto: ProfileImage?,
    galleryPhotos: List<ProfileImage>,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    descripcion: String,
    onDescripcionChange: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Foto de la Ficha", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            FichaPhotoPicker(profilePhoto, galleryPhotos, photos, onAddPhoto, onRemovePhoto)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Descripción sobre mí", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Text("${descripcion.length}/300", style = MaterialTheme.typography.labelSmall, color = if(descripcion.length > 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CuidaleTextField(
                value = descripcion, 
                onValueChange = { if(it.length <= 300) onDescripcionChange(it) }, 
                placeholder = "Ej: Soy una persona paciente y dedicada con vocación de servicio...", 
                containerColor = MaterialTheme.colorScheme.surface, 
                singleLine = false, 
                modifier = Modifier.height(150.dp),
                isError = descripcion.length > 300
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HardShadowButton(text = "Volver", onClick = onBack, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shadowColor = Color.Gray, isDark = false)
                HardShadowButton(text = "Finalizar", onClick = onFinish, modifier = Modifier.weight(1f), containerColor = Color(0xFF5C6BC0), contentColor = Color.White, shadowColor = Color(0xFF3949AB), isDark = false)
            }
        }
    }
}

@Composable
fun FamilyStep1(
    photos: List<Uri>,
    profilePhoto: ProfileImage?,
    galleryPhotos: List<ProfileImage>,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    nombreCompleto: String,
    onNombreChange: (String) -> Unit,
    edad: String,
    onEdadChange: (String) -> Unit,
    genero: String,
    onGeneroChange: (String) -> Unit,
    vivesConFamiliar: Boolean,
    onVivesConFamiliarChange: (Boolean) -> Unit,
    region: String,
    onRegionChange: (String) -> Unit,
    comuna: String,
    onComunaChange: (String) -> Unit,
    calle: String,
    onCalleChange: (String) -> Unit,
    numero: String,
    onNumeroChange: (String) -> Unit,
    depto: String,
    onDeptoChange: (String) -> Unit,
    availableRegions: List<String>,
    availableCommunes: List<String>,
    selectedCommunesPref: List<String>,
    onCommunesPrefChange: (List<String>) -> Unit,
    selectedHorariosPref: List<String>,
    onHorariosPrefChange: (List<String>) -> Unit,
    onNext: () -> Unit
) {
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Text("Fotos para la Ficha", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            FichaPhotoPicker(profilePhoto, galleryPhotos, photos, onAddPhoto, onRemovePhoto)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InputFieldLabel(text = "Nombre de la persona a cuidar")
            CuidaleTextField(
                value = nombreCompleto,
                onValueChange = onNombreChange,
                placeholder = "Ej: Juan Pérez",
                containerColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    InputFieldLabel(text = "Edad")
                    CuidaleTextField(
                        value = edad,
                        onValueChange = onEdadChange,
                        placeholder = "Ej: 75",
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
                Column(modifier = Modifier.weight(1.5f)) {
                    CuidaleExposedDropdown(label = "Género", options = listOf("Masculino", "Femenino", "Otro"), selectedOption = genero, onOptionSelected = onGeneroChange, placeholder = "Seleccionar")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¿Vives con la persona a cuidar?", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Switch(checked = vivesConFamiliar, onCheckedChange = onVivesConFamiliarChange)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (!vivesConFamiliar) {
                CuidaleExposedDropdown(label = "Región", options = availableRegions, selectedOption = region, onOptionSelected = onRegionChange, placeholder = "Seleccionar Región")
                Spacer(modifier = Modifier.height(12.dp))
                CuidaleExposedDropdown(label = "Comuna", options = availableCommunes, selectedOption = comuna, onOptionSelected = onComunaChange, placeholder = "Seleccionar Comuna", enabled = region.isNotBlank())
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text("Ubicación: $comuna, $region", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            InputFieldLabel(text = "Calle / Pasaje")
            CuidaleTextField(
                value = calle,
                onValueChange = onCalleChange,
                placeholder = "Ej: Av. Siempreviva",
                containerColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    InputFieldLabel(text = "Número")
                    CuidaleTextField(
                        value = numero,
                        onValueChange = onNumeroChange,
                        placeholder = "Ej: 123",
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    InputFieldLabel(text = "Depto / Block")
                    CuidaleTextField(
                        value = depto,
                        onValueChange = onDeptoChange,
                        placeholder = "Opcional",
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Preferencias de Búsqueda", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            CuidaleMultiSelectDropdown(
                label = "Comunas de interés (Donde buscas cuidador)",
                options = availableCommunes,
                selectedOptions = selectedCommunesPref,
                onOptionToggled = { opt ->
                    val newList = if (selectedCommunesPref.contains(opt)) selectedCommunesPref - opt else selectedCommunesPref + opt
                    onCommunesPrefChange(newList)
                },
                placeholder = "Ej: Providencia, Las Condes..."
            )
            Spacer(modifier = Modifier.height(16.dp))
            CuidaleMultiSelectDropdown(
                label = "Horarios de preferencia",
                options = listOf("Mañana", "Tarde", "Noche", "Fin de semana", "Puertas adentro", "Puertas afuera"),
                selectedOptions = selectedHorariosPref,
                onOptionToggled = { opt ->
                    val newList = if (selectedHorariosPref.contains(opt)) selectedHorariosPref - opt else selectedHorariosPref + opt
                    onHorariosPrefChange(newList)
                },
                placeholder = "Seleccionar..."
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            HardShadowButton(
                text = "Siguiente", 
                onClick = onNext, 
                modifier = Modifier.fillMaxWidth(), 
                containerColor = Color(0xFF5C6BC0), 
                contentColor = Color.White, 
                shadowColor = Color(0xFF3949AB), 
                isDark = false,
                icon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    }
}

@Composable
fun FamilyStep2(
    nivelDependencia: String,
    onNivelDependenciaChange: (String) -> Unit,
    movilidad: String,
    onMovilidadChange: (String) -> Unit,
    comunicacion: String,
    onComunicacionChange: (String) -> Unit,
    discapacidades: List<String>,
    onDiscapacidadesChange: (List<String>) -> Unit,
    discapacidadOtro: String,
    onDiscapacidadOtroChange: (String) -> Unit,
    enfermedades: List<String>,
    onEnfermedadesChange: (List<String>) -> Unit,
    enfermedadOtro: String,
    onEnfermedadOtroChange: (String) -> Unit,
    condicionesExtra: String,
    onCondicionesChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
                Text("Estado de Salud y Cuidados", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                CuidaleExposedDropdown(
                    label = "Nivel de Dependencia", 
                    options = listOf("Independiente", "Dependencia Leve", "Dependencia Moderada", "Dependencia Severa"), 
                    selectedOption = nivelDependencia, 
                    onOptionSelected = onNivelDependenciaChange, 
                    placeholder = "Seleccionar"
                )
                Spacer(modifier = Modifier.height(12.dp))
                CuidaleExposedDropdown(
                    label = "Movilidad", 
                    options = listOf("Normal", "Reducida", "Silla de Ruedas", "Postrado"), 
                    selectedOption = movilidad, 
                    onOptionSelected = onMovilidadChange, 
                    placeholder = "Seleccionar"
                )
                Spacer(modifier = Modifier.height(12.dp))
                CuidaleExposedDropdown(
                    label = "Comunicación", 
                    options = listOf("Normal", "Dificultad de habla", "No verbal"), 
                    selectedOption = comunicacion, 
                    onOptionSelected = onComunicacionChange, 
                    placeholder = "Seleccionar"
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Discapacidades
                Text("Discapacidades", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                CuidaleMultiSelectDropdown(
                    label = "Seleccione una o más",
                    options = listOf("Visual", "Auditiva", "Motriz", "Intelectual", "Psíquica", "Otro"),
                    selectedOptions = discapacidades,
                    onOptionToggled = { opt ->
                        val newList = if (discapacidades.contains(opt)) discapacidades - opt else discapacidades + opt
                        onDiscapacidadesChange(newList)
                    },
                    placeholder = "Seleccionar..."
                )
                if (discapacidades.contains("Otro")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CuidaleTextField(
                        value = discapacidadOtro,
                        onValueChange = onDiscapacidadOtroChange,
                        placeholder = "Especifique otra discapacidad",
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Enfermedades
                Text("Enfermedades Crónicas", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                CuidaleMultiSelectDropdown(
                    label = "Seleccione una o más",
                    options = listOf("Diabetes", "Hipertensión", "Alzheimer", "Parkinson", "Artrosis", "Otro"),
                    selectedOptions = enfermedades,
                    onOptionToggled = { opt ->
                        val newList = if (enfermedades.contains(opt)) enfermedades - opt else enfermedades + opt
                        onEnfermedadesChange(newList)
                    },
                    placeholder = "Seleccionar..."
                )
                if (enfermedades.contains("Otro")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CuidaleTextField(
                        value = enfermedadOtro,
                        onValueChange = onEnfermedadOtroChange,
                        placeholder = "Especifique otra enfermedad",
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                InputFieldLabel(text = "Otras observaciones")
                CuidaleTextField(
                    value = condicionesExtra,
                    onValueChange = onCondicionesChange,
                    placeholder = "Ej: Alergias, preferencias personales...",
                    containerColor = MaterialTheme.colorScheme.surface,
                    singleLine = false,
                    modifier = Modifier.height(100.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HardShadowButton(text = "Volver", onClick = onBack, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shadowColor = Color.Gray, isDark = false)
                    HardShadowButton(text = "Siguiente", onClick = onNext, modifier = Modifier.weight(1f), containerColor = Color(0xFF5C6BC0), contentColor = Color.White, shadowColor = Color(0xFF3949AB), isDark = false, icon = Icons.AutoMirrored.Filled.ArrowForward)
                }
            }
        }
}

@Composable
fun FamilyStep3(
    dieta: Map<String, String>,
    onDietaChange: (Map<String, String>) -> Unit,
    medicamentos: List<Map<String, Any>>,
    onMedicamentosChange: (List<Map<String, Any>>) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Plan de Alimentación", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            DietInputSection(dieta = dieta, onDietaChange = onDietaChange)

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Control de Medicamentos", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            MedicamentosInputSection(medicamentos = medicamentos, onMedicamentosChange = onMedicamentosChange)

            Spacer(modifier = Modifier.height(40.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HardShadowButton(text = "Volver", onClick = onBack, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shadowColor = Color.Gray, isDark = false)
                HardShadowButton(text = "Finalizar", onClick = onFinish, modifier = Modifier.weight(1f), containerColor = Color(0xFF5C6BC0), contentColor = Color.White, shadowColor = Color(0xFF3949AB), isDark = false)
            }
        }
    }
}

@Composable
fun DietInputSection(dieta: Map<String, String>, onDietaChange: (Map<String, String>) -> Unit) {
    val items = listOf(
        "desayuno" to "Desayuno",
        "break" to "Media Mañana / Break",
        "almuerzo" to "Almuerzo",
        "once" to "Once / Merienda",
        "cena" to "Cena",
        "colaciones" to "Colaciones / Notas de Dieta"
    )

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items.forEach { (key, label) ->
            Column {
                InputFieldLabel(
                    text = label,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                CuidaleTextField(
                    value = dieta[key] ?: "",
                    onValueChange = { newValue ->
                        val newMap = dieta.toMutableMap()
                        newMap[key] = newValue
                        onDietaChange(newMap)
                    },
                    placeholder = "Ej: Qué alimentos consume o restricciones...",
                    containerColor = MaterialTheme.colorScheme.surface,
                    singleLine = false,
                    maxLines = 4
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicamentosInputSection(
    medicamentos: List<Map<String, Any>>,
    onMedicamentosChange: (List<Map<String, Any>>) -> Unit
) {
    var showTimePickerForMed by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val timePickerState = rememberTimePickerState()

    if (showTimePickerForMed != null) {
        TimePickerDialog(
            onDismissRequest = { showTimePickerForMed = null },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timePickerState.hour.toString().padStart(2, '0')
                    val minute = timePickerState.minute.toString().padStart(2, '0')
                    val (mIdx, aIdx) = showTimePickerForMed!!

                    val newList = medicamentos.toMutableList()
                    val med = newList[mIdx].toMutableMap()
                    val admins = (med["administraciones"] as? List<Map<String, String>> ?: listOf(mapOf("hora" to "", "dosis" to ""))).toMutableList()
                    val admin = admins[aIdx].toMutableMap()
                    admin["hora"] = "$hour:$minute"
                    admins[aIdx] = admin
                    med["administraciones"] = admins
                    newList[mIdx] = med
                    onMedicamentosChange(newList)
                    showTimePickerForMed = null
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerForMed = null }) { Text("Cancelar") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    Column {
        medicamentos.forEachIndexed { medIdx, med ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Medication,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Medicamento #${medIdx + 1}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            val newList = medicamentos.toMutableList()
                            newList.removeAt(medIdx)
                            onMedicamentosChange(newList)
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }

                    CuidaleTextField(
                        value = med["nombre"] as? String ?: "",
                        onValueChange = { v ->
                            updateMedField(medicamentos, medIdx, "nombre", v, onMedicamentosChange)
                        },
                        placeholder = "Nombre (Ej: Paracetamol)",
                        containerColor = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Horarios y Dosis/Cantidad",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    val admins = med["administraciones"] as? List<Map<String, String>> ?: listOf(mapOf("hora" to "", "dosis" to ""))
                    admins.forEachIndexed { adminIdx, admin ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CuidaleTextField(
                                    value = admin["hora"] ?: "",
                                    onValueChange = { v ->
                                        if (v.length <= 5) updateAdminField(medicamentos, medIdx, adminIdx, "hora", v, onMedicamentosChange)
                                    },
                                    placeholder = "00:00",
                                    containerColor = Color.White,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { showTimePickerForMed = medIdx to adminIdx },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                            Box(modifier = Modifier.weight(1.5f)) {
                                CuidaleTextField(
                                    value = admin["dosis"] ?: "",
                                    onValueChange = { v ->
                                        updateAdminField(medicamentos, medIdx, adminIdx, "dosis", v, onMedicamentosChange)
                                    },
                                    placeholder = "Dosis (Ej: 1 comp)",
                                    containerColor = Color.White
                                )
                            }
                            if (admins.size > 1) {
                                IconButton(
                                    onClick = {
                                        val newList = medicamentos.toMutableList()
                                        val m = newList[medIdx].toMutableMap()
                                        val a = (m["administraciones"] as? List<Map<String, String>> ?: emptyList()).toMutableList()
                                        a.removeAt(adminIdx)
                                        m["administraciones"] = a
                                        newList[medIdx] = m
                                        onMedicamentosChange(newList)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.RemoveCircleOutline, 
                                        contentDescription = "Eliminar horario", 
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            val newList = medicamentos.toMutableList()
                            val m = newList[medIdx].toMutableMap()
                            val a = (m["administraciones"] as? List<Map<String, String>> ?: listOf(mapOf("hora" to "", "dosis" to ""))).toMutableList()
                            a.add(mapOf("hora" to "", "dosis" to ""))
                            m["administraciones"] = a
                            newList[medIdx] = m
                            onMedicamentosChange(newList)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Agregar horario", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CuidaleTextField(
                        value = med["observaciones"] as? String ?: "",
                        onValueChange = { v ->
                            updateMedField(medicamentos, medIdx, "observaciones", v, onMedicamentosChange)
                        },
                        placeholder = "Observaciones (Ej: Tomar con mucha agua)",
                        containerColor = Color.White,
                        singleLine = false,
                        maxLines = 2
                    )
                }
            }
        }

        Button(
            onClick = {
                val newList = medicamentos + mapOf(
                    "nombre" to "",
                    "administraciones" to listOf(mapOf("hora" to "", "dosis" to "")),
                    "observaciones" to ""
                )
                onMedicamentosChange(newList)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Agregar Medicamento")
        }
    }
}

private fun updateMedField(
    list: List<Map<String, Any>>,
    index: Int,
    key: String,
    value: String,
    onUpdate: (List<Map<String, Any>>) -> Unit
) {
    val newList = list.toMutableList()
    val newMed = newList[index].toMutableMap()
    newMed[key] = value
    newList[index] = newMed
    onUpdate(newList)
}

private fun updateAdminField(
    list: List<Map<String, Any>>,
    medIdx: Int,
    adminIdx: Int,
    key: String,
    value: String,
    onUpdate: (List<Map<String, Any>>) -> Unit
) {
    val newList = list.toMutableList()
    val med = newList[medIdx].toMutableMap()
    val admins = (med["administraciones"] as? List<Map<String, String>> ?: listOf(mapOf("hora" to "", "dosis" to ""))).toMutableList()
    val admin = admins[adminIdx].toMutableMap()
    admin[key] = value
    admins[adminIdx] = admin
    med["administraciones"] = admins
    newList[medIdx] = med
    onUpdate(newList)
}


@Composable
fun HealthConditionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        AnimatedVisibility(visible = checked) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                CuidaleTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = "Describa brevemente...",
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    )
}

