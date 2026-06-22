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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver
import java.io.File

@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val remoteState by viewModel.remoteState.collectAsState()
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
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
    val canSave = nombre.isNotBlank() && apellidoPa.isNotBlank() && !isLoading && isOnline

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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
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
                    AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.size(150.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(12.dp))
                    Text("¿Deseas usar esta imagen como vista previa?", textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPhotoUri = selectedImageUri
                    pendingDeletePhoto = false
                    showConfirmPhotoDialog = false
                    selectedImageUri = null
                }) { Text("Usar esta") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (selectedImageUri.toString().contains(context.packageName)) {
                        try { context.contentResolver.delete(selectedImageUri!!, null, null) } catch (e: Exception) {}
                    }
                    showConfirmPhotoDialog = false
                    selectedImageUri = null
                }) { Text("Cancelar") }
            }
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Cambiar foto de perfil", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
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
                    }, modifier = Modifier.fillMaxWidth(), enabled = isOnline) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cámara")
                    }
                    Button(onClick = {
                        galleryLauncher.launch("image/*")
                        showImageSourceDialog = false
                    }, modifier = Modifier.fillMaxWidth(), enabled = isOnline) {
                        Icon(Icons.Default.Collections, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Galería")
                    }
                    if (userData.fotoUrl.isNotEmpty()) {
                        Button(onClick = {
                            pendingDeletePhoto = true
                            pendingPhotoUri = null
                            showImageSourceDialog = false
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), enabled = isOnline) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Quitar foto")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false }) { Text("Cancelar") } }
        )
    }

    val gradient = CuidaleThemeExtras.gradient
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(scrollState).padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                }
                Text("Editar Perfil", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White), modifier = Modifier.padding(start = 12.dp))
            }

            if (!isOnline) {
                Surface(color = Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WifiOff, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("Estás offline. No puedes guardar cambios.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(4.dp, Color.White, CircleShape).clickable(enabled = !isLoading && isOnline) { showImageSourceDialog = true }) {
                    val imageModel = when {
                        pendingPhotoUri != null -> pendingPhotoUri
                        pendingDeletePhoto -> null
                        userData.fotoUrl.isNotEmpty() -> userData.fotoUrl
                        else -> null
                    }
                    if (imageModel != null) {
                        AsyncImage(model = imageModel, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(90.dp).align(Alignment.Center), tint = Color.White.copy(alpha = 0.7f))
                    }
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                        }
                    }
                    if (isOnline) {
                        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp).size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(3.dp, if (customColors.isDark) Color(0xFF1B263B) else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (userData.fotoUrl.isEmpty()) Icons.Default.CameraAlt else Icons.Default.Edit, null, modifier = Modifier.size(20.dp), tint = Color.White)
                        }
                    }
                }
            }

            HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f), cornerRadius = 24.dp) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InputFieldLabel("Primer Nombre", color = textColor)
                    CuidaleTextField(value = nombre, onValueChange = { nombre = it }, placeholder = "Nombre", enabled = !isLoading && isOnline)
                    InputFieldLabel("Apellido Paterno", color = textColor)
                    CuidaleTextField(value = apellidoPa, onValueChange = { apellidoPa = it }, placeholder = "Apellido", enabled = !isLoading && isOnline)
                    InputFieldLabel("Teléfono", color = textColor)
                    CuidaleTextField(value = telefono, onValueChange = { if(it.length <= 9) telefono = it }, placeholder = "Teléfono", enabled = !isLoading && isOnline)
                    InputFieldLabel("Sobre mí", color = textColor)
                    CuidaleTextField(value = descripcion, onValueChange = { if(it.length <= 300) descripcion = it }, placeholder = "Sobre mí", modifier = Modifier.height(100.dp), singleLine = false, enabled = !isLoading && isOnline)
                    
                    HardShadowButton(
                        text = if (isLoading) "Guardando..." else if (!isOnline) "Sin conexión" else "Guardar Cambios",
                        onClick = {
                            if (canSave) {
                                viewModel.updateProfileWithPhoto(userData.copy(nombre = nombre, apellido_pa = apellidoPa, telefono = telefono, descripcion = descripcion), pendingPhotoUri, pendingDeletePhoto)
                            }
                        },
                        containerColor = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray,
                        enabled = canSave && isOnline,
                        icon = if (isLoading) null else Icons.Default.Save
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
