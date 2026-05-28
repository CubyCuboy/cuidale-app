package com.example.cuidaleapp.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.ImageUtils
import com.example.cuidaleapp.viewmodel.*
import com.example.cuidaleapp.models.*
import java.io.File

@Composable
fun PrivacyToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun getSocialIcon(platform: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (platform.lowercase()) {
        "instagram" -> Icons.Default.CameraAlt
        "facebook" -> Icons.Default.Facebook
        "x", "twitter" -> Icons.Default.Close
        "tiktok" -> Icons.Default.MusicNote
        "linkedin" -> Icons.Default.Work
        "youtube" -> Icons.Default.PlayCircle
        "whatsapp" -> Icons.Default.Phone
        else -> Icons.Default.Link
    }
}

@Composable
fun VisibilityPolicyDropdown(
    currentPolicy: VisibilityPolicy,
    onPolicyChange: (VisibilityPolicy) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(currentPolicy) {
                        VisibilityPolicy.PUBLIC -> Icons.Default.Public
                        VisibilityPolicy.CONTACTS_ONLY -> Icons.Default.Group
                        VisibilityPolicy.PRIVATE -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when(currentPolicy) {
                        VisibilityPolicy.PUBLIC -> "Público"
                        VisibilityPolicy.CONTACTS_ONLY -> "Contactos"
                        VisibilityPolicy.PRIVATE -> "Privado"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VisibilityPolicy.entries.forEach { policy ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when(policy) {
                                    VisibilityPolicy.PUBLIC -> Icons.Default.Public
                                    VisibilityPolicy.CONTACTS_ONLY -> Icons.Default.Group
                                    VisibilityPolicy.PRIVATE -> Icons.Default.Lock
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(when(policy) {
                                VisibilityPolicy.PUBLIC -> "Público"
                                VisibilityPolicy.CONTACTS_ONLY -> "Solo Contactos"
                                VisibilityPolicy.PRIVATE -> "Privado"
                            })
                        }
                    },
                    onClick = {
                        onPolicyChange(policy)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onNavigateBack: () -> Unit
) {
    val draftState by viewModel.draftState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    val context = LocalContext.current

    // Dialog states
    var showAddSocialDialog by remember { mutableStateOf(false) }
    var newPlatform by remember { mutableStateOf(SocialPlatform.INSTAGRAM) }
    var customPlatformName by remember { mutableStateOf("") }
    var newLink by remember { mutableStateOf("") }
    var newVisibility by remember { mutableStateOf(VisibilityPolicy.PRIVATE) }

    var showOtpDialog by remember { mutableStateOf(false) }
    var otpValue by remember { mutableStateOf("") }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    
    var showImageConfirmDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var isAdditionalPhotoUpload by remember { mutableStateOf(false) }
    var imageValidationError by remember { mutableStateOf<String?>(null) }

    val defaultIcons = listOf("abuelita", "abuelito", "enfermera", "enfermero", "profile_default")

    LaunchedEffect(Unit) {
        viewModel.startEditing()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val validation = ImageUtils.validateImage(context, uri)
            pendingImageUri = uri
            imageValidationError = if (validation.isFailure) validation.exceptionOrNull()?.message else null
            showImageConfirmDialog = true
        }
    }

    val tempUri = remember {
        val file = File(context.cacheDir, "temp_profile.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val validation = ImageUtils.validateImage(context, tempUri)
            pendingImageUri = tempUri
            imageValidationError = if (validation.isFailure) validation.exceptionOrNull()?.message else null
            showImageConfirmDialog = true
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ProfileUiState.Success -> {
                isRefreshing = false
                onNavigateBack()
                viewModel.clearState()
            }
            is ProfileUiState.CodeSent -> showOtpDialog = true
            is ProfileUiState.SuccessVerification -> {
                showOtpDialog = false
                viewModel.clearState()
            }
            is ProfileUiState.Idle -> isRefreshing = false
            is ProfileUiState.Error -> isRefreshing = false
            else -> {}
        }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshProfile()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = customColors.onGradient
                        )
                    }
                    Text(
                        text = stringResource(R.string.edit_profile_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = customColors.onGradient,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Panel (Similar to ProfileScreen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(customColors.panelBackground)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Profile Image with camera overlay
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { 
                                    isAdditionalPhotoUpload = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val profilePhoto = draftState.profilePhoto
                            val imageSource: Any? = when (profilePhoto?.sourceType) {
                                ImageSourceType.DEFAULT_AVATAR -> {
                                    val iconName = profilePhoto.cacheKey.substringAfter("res:")
                                    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                    if (resId != 0) resId else R.drawable.profile_default
                                }
                                ImageSourceType.LOCAL_PENDING -> profilePhoto.localUri
                                ImageSourceType.REMOTE -> profilePhoto.remoteUrl ?: R.drawable.profile_default
                                else -> R.drawable.profile_default
                            }

                            AsyncImage(
                                model = imageSource,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            if (profilePhoto?.uploadState is ImageOperationState.Processing) {
                                val progress = (profilePhoto.uploadState as ImageOperationState.Processing).progress
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.size(48.dp),
                                        color = Color.White,
                                        strokeWidth = 4.dp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row {
                                    IconButton(
                                        onClick = { 
                                            isAdditionalPhotoUpload = false
                                            galleryLauncher.launch("image/*") 
                                        },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(0.8f), CircleShape).padding(4.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { 
                                            isAdditionalPhotoUpload = false
                                            cameraLauncher.launch(tempUri)
                                        },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(0.8f), CircleShape).padding(4.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Avatar Selector
                        Text("Elegir Avatar", style = MaterialTheme.typography.labelSmall)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            items(defaultIcons) { iconName ->
                                val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                val isSelected = draftState.profilePhoto?.sourceType == ImageSourceType.DEFAULT_AVATAR && 
                                               draftState.profilePhoto?.remoteUrl == "res:$iconName"
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.updateDraftField { it.copy(profilePhoto = ProfileImage(
                                                cacheKey = "res:$iconName",
                                                sourceType = ImageSourceType.DEFAULT_AVATAR,
                                                isProfilePhoto = true
                                            )) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = resId,
                                        contentDescription = iconName,
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Fields
                        InputFieldLabel(text = "Información Personal")
                        CuidaleTextField(
                            value = draftState.nombre,
                            onValueChange = { newValue -> viewModel.updateDraftField { it.copy(nombre = newValue) } },
                            placeholder = "Nombre *",
                            isError = draftState.nombre.isBlank(),
                            containerColor = customColors.inputBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CuidaleTextField(
                            value = draftState.nombre_s,
                            onValueChange = { newValue -> viewModel.updateDraftField { it.copy(nombre_s = newValue) } },
                            placeholder = "Segundo Nombre",
                            containerColor = customColors.inputBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CuidaleTextField(
                            value = draftState.apellido_pa,
                            onValueChange = { newValue -> viewModel.updateDraftField { it.copy(apellido_pa = newValue) } },
                            placeholder = "Apellido Paterno *",
                            isError = draftState.apellido_pa.isBlank(),
                            containerColor = customColors.inputBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CuidaleTextField(
                            value = draftState.apellido_ma,
                            onValueChange = { newValue -> viewModel.updateDraftField { it.copy(apellido_ma = newValue) } },
                            placeholder = "Apellido Materno",
                            containerColor = customColors.inputBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        InputFieldLabel(text = "Sobre Mí")
                        CuidaleTextField(
                            value = draftState.descripcion,
                            onValueChange = { newValue -> 
                                if (newValue.length <= 300) {
                                    viewModel.updateDraftField { it.copy(descripcion = newValue) }
                                }
                            },
                            placeholder = "Breve biografía (máx. 300 caracteres)",
                            containerColor = customColors.inputBackground,
                            maxLines = 5,
                            singleLine = false
                        )
                        Text(
                            text = "${draftState.descripcion.length}/300",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (draftState.descripcion.length >= 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                            textAlign = TextAlign.End
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        InputFieldLabel(text = "Ubicación")

                        var regionExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = regionExpanded,
                            onExpandedChange = { regionExpanded = !regionExpanded }
                        ) {
                            OutlinedTextField(
                                value = draftState.region,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Región") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = customColors.inputBackground,
                                    unfocusedContainerColor = customColors.inputBackground
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = regionExpanded,
                                onDismissRequest = { regionExpanded = false }
                            ) {
                                ChileData.regions.forEach { region ->
                                    DropdownMenuItem(
                                        text = { Text(region.name) },
                                        onClick = {
                                            viewModel.updateDraftField { it.copy(
                                                region = region.name,
                                                id_region = region.id,
                                                comuna = "" // Reset comuna when region changes
                                            ) }
                                            regionExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        var comunaExpanded by remember { mutableStateOf(false) }
                        val availableComunas = ChileData.regions.find { it.id == draftState.id_region }?.comunas ?: emptyList()

                        ExposedDropdownMenuBox(
                            expanded = comunaExpanded,
                            onExpandedChange = { if (draftState.id_region.isNotEmpty()) comunaExpanded = !comunaExpanded }
                        ) {
                            OutlinedTextField(
                                value = draftState.comuna,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Comuna") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = comunaExpanded) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                enabled = draftState.id_region.isNotEmpty(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = customColors.inputBackground,
                                    unfocusedContainerColor = customColors.inputBackground,
                                    disabledContainerColor = customColors.inputBackground.copy(alpha = 0.5f)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = comunaExpanded,
                                onDismissRequest = { comunaExpanded = false }
                            ) {
                                availableComunas.forEach { comuna ->
                                    DropdownMenuItem(
                                        text = { Text(comuna) },
                                        onClick = {
                                            viewModel.updateDraftField { it.copy(comuna = comuna) }
                                            comunaExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Contact Section
                        InputFieldLabel(text = "Contacto")
                        Text(
                            "Tus métodos de contacto permiten a otros comunicarse contigo según tu configuración de privacidad.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            draftState.contactMethods.forEach { method ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (method.type) {
                                            ContactType.EMAIL -> Icons.Default.Email
                                            ContactType.PHONE -> Icons.Default.Phone
                                            ContactType.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
                                            ContactType.TELEGRAM -> Icons.AutoMirrored.Filled.Send
                                            ContactType.OTHER -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            method.label ?: method.type.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(method.value, style = MaterialTheme.typography.bodySmall)
                                    }

                                    VisibilityPolicyDropdown(
                                        currentPolicy = method.visibility,
                                        onPolicyChange = { newVisibility ->
                                            viewModel.updateDraftField { it.copy(
                                                contactMethods = it.contactMethods.map { m ->
                                                    if (m.id == method.id) m.copy(visibility = newVisibility) else m
                                                }
                                            ) }
                                        }
                                    )

                                    if (!method.isPrimary) {
                                        IconButton(onClick = {
                                            viewModel.updateDraftField { state ->
                                                state.copy(
                                                    contactMethods = state.contactMethods.filter { it.id != method.id }
                                                )
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Privacy Switches
                        Spacer(modifier = Modifier.height(16.dp))
                        InputFieldLabel(text = "Privacidad de Datos")
                        
                        PrivacyToggle(
                            label = "Mostrar mi RUT a mis contactos",
                            checked = draftState.mostrarRut,
                            onCheckedChange = { newValue -> viewModel.updateDraftField { it.copy(mostrarRut = newValue) } }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        InputFieldLabel(text = "Lista de Contactos (Vínculos)")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = draftState.privacidadVinculos == "privado",
                                onClick = { viewModel.updateDraftField { it.copy(privacidadVinculos = "privado") } }
                            )
                            Text("Oculto para todos", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            RadioButton(
                                selected = draftState.privacidadVinculos == "contactos",
                                onClick = { viewModel.updateDraftField { it.copy(privacidadVinculos = "contactos") } }
                            )
                            Text("Solo contactos", style = MaterialTheme.typography.bodyMedium)
                        }

                        // Social Networks Section
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Redes Sociales (Max 10)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (draftState.socialLinks.size < 10) {
                                IconButton(onClick = { showAddSocialDialog = true }) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Agregar red", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            draftState.socialLinks.forEach { social ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = getSocialIcon(social.platform.displayName), 
                                        contentDescription = null, 
                                        tint = MaterialTheme.colorScheme.primary, 
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (social.platform == SocialPlatform.OTHER) social.customPlatform ?: "Otro" else social.platform.displayName, 
                                            style = MaterialTheme.typography.labelSmall, 
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(social.link, style = MaterialTheme.typography.bodySmall)
                                    }
                                    
                                    VisibilityPolicyDropdown(
                                        currentPolicy = social.visibility,
                                        onPolicyChange = { newVisibility ->
                                            viewModel.updateDraftField { it.copy(
                                                socialLinks = it.socialLinks.map { s ->
                                                    if (s == social) s.copy(visibility = newVisibility) else s
                                                }
                                            ) }
                                        }
                                    )

                                    IconButton(onClick = { 
                                        viewModel.updateDraftField { state -> 
                                            state.copy(socialLinks = state.socialLinks.filter { it != social })
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // Additional Photos Section
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Fotos Adicionales (Max 6)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (draftState.galleryPhotos.size < 6) {
                                Row {
                                    IconButton(onClick = { 
                                        isAdditionalPhotoUpload = true
                                        galleryLauncher.launch("image/*") 
                                    }) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Subir foto")
                                    }
                                    IconButton(onClick = { 
                                        isAdditionalPhotoUpload = true
                                        cameraLauncher.launch(tempUri)
                                    }) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Tomar foto")
                                    }
                                }
                            }
                        }
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(draftState.galleryPhotos) { photo ->
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                ) {
                                    AsyncImage(
                                        model = photo.remoteUrl ?: photo.localUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Privacy Toggle
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .background(Color.Black.copy(0.6f), CircleShape)
                                            .clickable { viewModel.togglePhotoPrivacy(photo) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (photo.isPrivate) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Upload Progress Overlay
                                    if (photo.uploadState is ImageOperationState.Processing) {
                                        val progress = (photo.uploadState as ImageOperationState.Processing).progress
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                progress = progress,
                                                modifier = Modifier.size(32.dp),
                                                color = Color.White,
                                                strokeWidth = 3.dp,
                                            )
                                        }
                                    }

                                    // Delete
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .background(Color.Black.copy(0.6f), CircleShape)
                                            .clickable { viewModel.deleteGalleryPhoto(photo) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (photo.uploadState is ImageOperationState.Deleting) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                        } else {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                HardShadowButton(
                    text = stringResource(R.string.edit_profile_save),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shadowColor = shadows.hardShadowPrimary,
                    icon = Icons.Default.Save,
                    isDark = isDark,
                    enabled = uiState !is ProfileUiState.Loading && draftState.nombre.isNotBlank() && draftState.apellido_pa.isNotBlank(),
                    onClick = {
                        viewModel.saveProfileChanges()
                    }
                )
                
                Spacer(modifier = Modifier.height(40.dp))
            }

            if (uiState is ProfileUiState.Loading) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }

    // Dialogs
    if (showAddSocialDialog) {
        val platforms = SocialPlatform.entries
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddSocialDialog = false },
            title = { Text("Agregar Red Social") },
            text = {
                Column {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = newPlatform.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Plataforma") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = customColors.inputBackground,
                                unfocusedContainerColor = customColors.inputBackground
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            platforms.forEach { platform ->
                                DropdownMenuItem(
                                    text = { Text(platform.displayName) },
                                    onClick = {
                                        newPlatform = platform
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (newPlatform == SocialPlatform.OTHER) {
                        Spacer(Modifier.height(8.dp))
                        CuidaleTextField(
                            value = customPlatformName,
                            onValueChange = { customPlatformName = it },
                            placeholder = "Nombre de la plataforma",
                            containerColor = customColors.inputBackground
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    CuidaleTextField(
                        value = newLink, 
                        onValueChange = { newLink = it }, 
                        placeholder = if (newPlatform == SocialPlatform.WHATSAPP) "Número (Ej: +569...)" else "Usuario o Link",
                        containerColor = customColors.inputBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Visibilidad", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VisibilityPolicy.entries.forEach { policy ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp).clickable { newVisibility = policy }) {
                                RadioButton(selected = newVisibility == policy, onClick = { newVisibility = policy })
                                Text(
                                    text = when(policy) {
                                        VisibilityPolicy.PUBLIC -> "Púb"
                                        VisibilityPolicy.CONTACTS_ONLY -> "Con"
                                        VisibilityPolicy.PRIVATE -> "Priv"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Text(
                        "Las redes sociales son privadas hasta que aceptes a un contacto.", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newLink.isNotBlank()) {
                        viewModel.updateDraftField { it.copy(
                            socialLinks = it.socialLinks + SocialLink(
                                platform = newPlatform,
                                customPlatform = if (newPlatform == SocialPlatform.OTHER) customPlatformName else null,
                                link = newLink,
                                visibility = newVisibility
                            )
                        ) }
                        newPlatform = SocialPlatform.INSTAGRAM
                        customPlatformName = ""
                        newLink = ""
                        newVisibility = VisibilityPolicy.PRIVATE
                        showAddSocialDialog = false
                    }
                }) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { showAddSocialDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false },
            title = { Text("Verificar Teléfono") },
            text = {
                Column {
                    Text("Ingresa el código enviado por SMS")
                    Spacer(Modifier.height(16.dp))
                    OtpInputField(otpText = otpValue, onOtpTextChange = { otpValue = it }, otpCount = 6)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.verifyOtp(otpValue) }, enabled = otpValue.length == 6) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showOtpDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showImageConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImageConfirmDialog = false },
            title = { Text(if (isAdditionalPhotoUpload) "Subir Foto Adicional" else "Cambiar Foto de Perfil") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (imageValidationError != null) {
                        Text(imageValidationError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    } else {
                        AsyncImage(model = pendingImageUri, contentDescription = null, modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        if (isAdditionalPhotoUpload) {
                            Text("¿Deseas subir esta foto?", modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text("¿Deseas usar esta imagen como foto de perfil?", modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                if (imageValidationError == null) {
                    Button(onClick = {
                        if (isAdditionalPhotoUpload) {
                            pendingImageUri?.let { viewModel.uploadGalleryPhoto(it, false) }
                        } else {
                            pendingImageUri?.let { viewModel.uploadProfilePhoto(it) }
                        }
                        showImageConfirmDialog = false
                    }) { Text("Aceptar") }
                }
            },
            dismissButton = { TextButton(onClick = { showImageConfirmDialog = false }) { Text(if (imageValidationError != null) "Cerrar" else "Cancelar") } }
        )
    }
}
