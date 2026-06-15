package com.example.cuidaleapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.CuidaleExposedDropdown
import com.example.cuidaleapp.ui.components.CuidaleTextField
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.InputFieldLabel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import java.io.File

@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val remoteState by viewModel.remoteState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var nombre by remember { mutableStateOf("") }
    var nombreS by remember { mutableStateOf("") }
    var apellidoPa by remember { mutableStateOf("") }
    var apellidoMa by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var rut by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var comuna by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    // Sincronizar estados locales solo si no estamos guardando
    LaunchedEffect(userData) {
        if (userData.uid.isNotEmpty() && remoteState !is ProfileViewModel.RemoteState.Loading) {
            nombre = userData.nombre
            nombreS = userData.nombre_s
            apellidoPa = userData.apellido_pa
            apellidoMa = userData.apellido_ma
            telefono = userData.telefono
            rut = userData.rut
            region = userData.region
            comuna = userData.comuna
            descripcion = userData.descripcion
        }
    }

    // Observar estado remoto para navegar tras el éxito
    LaunchedEffect(remoteState) {
        if (remoteState is ProfileViewModel.RemoteState.Success) {
            viewModel.resetRemoteState()
            onNavigateBack()
        }
    }

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showConfirmPhotoDialog by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf(false) }

    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val regionsList = ChileData.regions.map { it.name }
    val communesForSelectedRegion = remember(region) {
        ChileData.regions.find { it.name == region }?.comunas?.sorted() ?: emptyList()
    }

    val isLoading = remoteState is ProfileViewModel.RemoteState.Loading
    val canSave = nombre.isNotBlank() && apellidoPa.isNotBlank() && !isLoading

    // Image Pickers
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            selectedImageUri = it
            showConfirmPhotoDialog = true
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempPhotoUri?.let { 
                selectedImageUri = it
                showConfirmPhotoDialog = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "temp_profile_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    if (showConfirmPhotoDialog && selectedImageUri != null) {
        AlertDialog(
            onDismissRequest = { 
                showConfirmPhotoDialog = false
                selectedImageUri = null
            },
            title = { Text("Confirmar foto", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("¿Deseas usar esta imagen como vista previa?", textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPhotoUri = selectedImageUri
                    pendingDeletePhoto = false // Si confirma nueva, ya no queremos eliminar
                    showConfirmPhotoDialog = false
                    selectedImageUri = null
                }) {
                    Text("Usar esta")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    // Si era de la cámara, borramos el temporal físico
                    if (selectedImageUri.toString().contains(context.packageName)) {
                        try { context.contentResolver.delete(selectedImageUri!!, null, null) } catch (e: Exception) {}
                    }
                    showConfirmPhotoDialog = false
                    selectedImageUri = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Cambiar foto de perfil", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Selecciona una opción para tu nueva foto", style = MaterialTheme.typography.bodyMedium)
                    
                    Button(
                        onClick = {
                            val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                val file = File(context.cacheDir, "temp_profile_${System.currentTimeMillis()}.jpg")
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

                    if (userData.fotoUrl.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Button(
                            onClick = {
                                pendingDeletePhoto = true
                                pendingPhotoUri = null
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
                            Text("Quitar foto actual (pendiente)")
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

    val gradient = CuidaleThemeExtras.gradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(gradient.top, gradient.bottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Editar Perfil",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Profile Picture Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(4.dp, Color.White, CircleShape)
                        .clickable(enabled = !isLoading) { showImageSourceDialog = true }
                ) {
                    // Lógica de visualización:
                    // 1. Si hay una nueva seleccionada -> mostrarla
                    // 2. Si marcó eliminar -> mostrar icono de persona (nada)
                    // 3. Si no hay cambios -> mostrar la de userData
                    val imageModel = when {
                        pendingPhotoUri != null -> pendingPhotoUri
                        pendingDeletePhoto -> null
                        userData.fotoUrl.isNotEmpty() -> userData.fotoUrl
                        else -> null
                    }
                    
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(90.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Overlay de carga (Estilo Discord)
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // Botón de edición flotante
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-8).dp, y = (-8).dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, if (customColors.isDark) Color(0xFF1B263B) else Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (userData.fotoUrl.isEmpty()) Icons.Default.CameraAlt else Icons.Default.Edit,
                            contentDescription = "Cambiar foto",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // Form inside a Panel
            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InputFieldLabel(text = "Primer Nombre", color = textColor)
                    CuidaleTextField(
                        value = nombre,
                        onValueChange = { input -> 
                            nombre = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                        },
                        placeholder = "Tu nombre",
                        containerColor = customColors.inputBackground,
                        textColor = textColor,
                        enabled = !isLoading
                    )

                    InputFieldLabel(text = "Segundo Nombre (Opcional)", color = textColor)
                    CuidaleTextField(
                        value = nombreS,
                        onValueChange = { input ->
                            nombreS = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                        },
                        placeholder = "Segundo nombre",
                        containerColor = customColors.inputBackground,
                        textColor = textColor,
                        enabled = !isLoading
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            InputFieldLabel(text = "Apellido Paterno", color = textColor)
                            CuidaleTextField(
                                value = apellidoPa,
                                onValueChange = { input ->
                                    apellidoPa = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                                },
                                placeholder = "Apellido",
                                containerColor = customColors.inputBackground,
                                textColor = textColor,
                                enabled = !isLoading
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InputFieldLabel(text = "Apellido Materno", color = textColor)
                            CuidaleTextField(
                                value = apellidoMa,
                                onValueChange = { input ->
                                    apellidoMa = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                                },
                                placeholder = "Apellido",
                                containerColor = customColors.inputBackground,
                                textColor = textColor,
                                enabled = !isLoading
                            )
                        }
                    }

                    InputFieldLabel(text = "RUT (No editable)", color = secondaryTextColor)
                    CuidaleTextField(
                        value = rut,
                        onValueChange = { },
                        placeholder = "12.345.678-9",
                        containerColor = customColors.inputBackground.copy(alpha = 0.5f),
                        enabled = false,
                        textColor = secondaryTextColor
                    )

                    InputFieldLabel(text = "Teléfono", color = textColor)
                    CuidaleTextField(
                        value = telefono,
                        onValueChange = { input ->
                            val clean = input.replace(Regex("[^0-9]"), "")
                            if (clean.length <= 9) {
                                telefono = clean
                            }
                        },
                        placeholder = "Ej: 912345678",
                        containerColor = customColors.inputBackground,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                        textColor = textColor,
                        enabled = !isLoading
                    )

                    CuidaleExposedDropdown(
                        label = "Región",
                        options = regionsList,
                        selectedOption = region,
                        onOptionSelected = { 
                            region = it
                            comuna = ""
                        },
                        placeholder = "Seleccionar Región",
                        enabled = !isLoading
                    )

                    CuidaleExposedDropdown(
                        label = "Comuna",
                        options = communesForSelectedRegion,
                        selectedOption = comuna,
                        onOptionSelected = { comuna = it },
                        placeholder = "Seleccionar Comuna",
                        enabled = region.isNotBlank() && !isLoading
                    )

                    InputFieldLabel(text = "Sobre mí", color = textColor)
                    CuidaleTextField(
                        value = descripcion,
                        onValueChange = { if(it.length <= 300) descripcion = it },
                        placeholder = "Escribe algo sobre ti...",
                        containerColor = customColors.inputBackground,
                        singleLine = false,
                        modifier = Modifier.height(100.dp),
                        textColor = textColor,
                        enabled = !isLoading
                    )
                    Text(
                        text = "${descripcion.length}/300",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor,
                        modifier = Modifier.align(Alignment.End)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (remoteState is ProfileViewModel.RemoteState.Error) {
                        Text(
                            text = (remoteState as ProfileViewModel.RemoteState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    HardShadowButton(
                        text = if (remoteState is ProfileViewModel.RemoteState.Loading) "Guardando..." else "Guardar Cambios",
                        onClick = {
                            if (canSave) {
                                val updatedUser = userData.copy(
                                    nombre = nombre.trim(),
                                    nombre_s = nombreS.trim(),
                                    apellido_pa = apellidoPa.trim(),
                                    apellido_ma = apellidoMa.trim(),
                                    rut = rut,
                                    telefono = telefono,
                                    region = region,
                                    comuna = comuna,
                                    descripcion = descripcion
                                )
                                // Enviamos los datos, la nueva foto y si queremos eliminar la actual
                                viewModel.updateProfileWithPhoto(
                                    updatedUser = updatedUser, 
                                    newPhotoUri = pendingPhotoUri,
                                    shouldDeleteOld = pendingDeletePhoto
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowColor = shadowPalette.hardShadowPrimary,
                        isDark = customColors.isDark,
                        enabled = remoteState !is ProfileViewModel.RemoteState.Loading && canSave,
                        icon = if (remoteState is ProfileViewModel.RemoteState.Loading) null else Icons.Default.Save
                    )
                }
            }

            if (remoteState is ProfileViewModel.RemoteState.Loading) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)), 
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
