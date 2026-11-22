package com.example.cuidaleapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.utils.ConnectivityObserver
import com.example.cuidaleapp.utils.ErrorMessages
import com.example.cuidaleapp.utils.PhoneUtils
import java.io.File
import kotlin.math.roundToInt

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
    var region by remember { mutableStateOf("") }
    var comuna by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    var fechaNacimiento by remember { mutableStateOf(userData.fechaNacimiento) }
    var discapacidadBool by remember { mutableStateOf(userData.discapacidad) }
    var tipoDiscapacidad by remember { mutableStateOf(userData.tipoDiscapacidad) }
    var tiempoDiscapacidad by remember { mutableStateOf(userData.tiempoDiscapacidad) }
    var valorHoraReferencial by remember { mutableStateOf(userData.valorHoraReferencial) }
    var formacionTexto by remember { mutableStateOf(formacionesATexto(userData.formacionAcademica)) }
    var experienciaTexto by remember { mutableStateOf(experienciasATexto(userData.experienciasLaborales)) }
    var referenciasTexto by remember { mutableStateOf(referenciasATexto(userData.referenciasLaborales)) }

    LaunchedEffect(userData) {
        if (userData.uid.isNotEmpty() && remoteState !is ProfileViewModel.RemoteState.Loading) {
            nombre = userData.nombre
            nombreS = userData.nombre_s
            apellidoPa = userData.apellido_pa
            apellidoMa = userData.apellido_ma
            telefono = userData.telefono
            region = userData.region
            comuna = userData.comuna
            descripcion = userData.descripcion
        }
    }

    LaunchedEffect(remoteState) {
        if (remoteState is ProfileViewModel.RemoteState.Success) {
            Toast.makeText(context, context.getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()
            viewModel.resetRemoteState()
            onNavigateBack()
        }
    }

    val isNombreValid = nombre.length >= 2
    val isApellidoPaValid = apellidoPa.length >= 2
    val isPhoneValid = telefono.isEmpty() || (PhoneUtils.isValidChileanPhone(telefono) && telefono.length == 9)
    val isFormValid = isNombreValid && isApellidoPaValid && isPhoneValid && region.isNotEmpty() && comuna.isNotEmpty()

    val nombreShake = remember { Animatable(0f) }
    val apellidoShake = remember { Animatable(0f) }
    val phoneShake = remember { Animatable(0f) }

    LaunchedEffect(isNombreValid) {
        if (!isNombreValid && nombre.isNotEmpty()) {
            repeat(4) { i -> nombreShake.animateTo(if (i % 2 == 0) 8f else -8f, tween(50, easing = LinearEasing)) }
            nombreShake.animateTo(0f)
        }
    }
    LaunchedEffect(isApellidoPaValid) {
        if (!isApellidoPaValid && apellidoPa.isNotEmpty()) {
            repeat(4) { i -> apellidoShake.animateTo(if (i % 2 == 0) 8f else -8f, tween(50, easing = LinearEasing)) }
            apellidoShake.animateTo(0f)
        }
    }
    LaunchedEffect(isPhoneValid) {
        if (!isPhoneValid && telefono.isNotEmpty()) {
            repeat(4) { i -> phoneShake.animateTo(if (i % 2 == 0) 8f else -8f, tween(50, easing = LinearEasing)) }
            phoneShake.animateTo(0f)
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
    val accentColor = if (userData.rol == "cuidador") MaterialTheme.colorScheme.primary else customColors.successStrong

    val regionsList = ChileData.regions.map { it.name }
    val communesForSelectedRegion = remember(region) {
        ChileData.regions.find { it.name == region }?.comunas?.sorted() ?: emptyList()
    }

    val isLoading = remoteState is ProfileViewModel.RemoteState.Loading

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
            title = { Text(stringResource(R.string.edit_profile_confirm_photo), fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.size(150.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.edit_profile_confirm_photo_question), textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPhotoUri = selectedImageUri
                    pendingDeletePhoto = false
                    showConfirmPhotoDialog = false
                    selectedImageUri = null
                }) { Text(stringResource(R.string.edit_profile_use_this)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (selectedImageUri.toString().contains(context.packageName)) {
                        try { context.contentResolver.delete(selectedImageUri!!, null, null) } catch (e: Exception) {}
                    }
                    showConfirmPhotoDialog = false
                    selectedImageUri = null
                }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text(stringResource(R.string.edit_profile_change_photo_title), fontWeight = FontWeight.Bold) },
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
                        Text(stringResource(R.string.source_camera))
                    }
                    Button(onClick = {
                        galleryLauncher.launch("image/*")
                        showImageSourceDialog = false
                    }, modifier = Modifier.fillMaxWidth(), enabled = isOnline) {
                        Icon(Icons.Default.Collections, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.source_gallery))
                    }
                    if (userData.fotoUrl.isNotEmpty()) {
                        Button(onClick = {
                            pendingDeletePhoto = true
                            pendingPhotoUri = null
                            showImageSourceDialog = false
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), enabled = isOnline) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.edit_profile_remove_photo))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    val gradient = CuidaleThemeExtras.gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                }
                Text(stringResource(R.string.edit_profile_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White), modifier = Modifier.padding(start = 12.dp))
            }

            if (!isOnline) {
                Surface(color = Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WifiOff, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.edit_profile_offline), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(4.dp, Color.White, CircleShape)
                        .clickable(enabled = !isLoading && isOnline) { showImageSourceDialog = true }
                ) {
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
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .size(44.dp),
                            shape = CircleShape,
                            color = accentColor,
                            shadowElevation = 4.dp,
                            border = BorderStroke(3.dp, if (customColors.isDark) Color(0xFF1B263B) else Color.White)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = if (userData.fotoUrl.isEmpty() && pendingPhotoUri == null) Icons.Default.CameraAlt else Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 28.dp
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    
                    EditSectionHeader(icon = Icons.Default.Person, title = stringResource(R.string.edit_profile_section_personal), accentColor = accentColor)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EditFieldWrapper(label = stringResource(R.string.register_first_name), error = if (!isNombreValid && nombre.isNotEmpty()) stringResource(R.string.edit_profile_error_min_2) else null) {
                            CuidaleTextField(
                                value = nombre, 
                                onValueChange = { nombre = it }, 
                                placeholder = stringResource(R.string.edit_profile_placeholder_name), 
                                leadingIcon = { Icon(Icons.Default.Badge, null, tint = accentColor.copy(alpha = 0.6f)) },
                                isError = !isNombreValid && nombre.isNotEmpty(), 
                                modifier = Modifier.offset { IntOffset(nombreShake.value.roundToInt(), 0) },
                                enabled = !isLoading && isOnline
                            )
                        }

                        EditFieldWrapper(label = stringResource(R.string.register_second_name)) {
                            CuidaleTextField(
                                value = nombreS, 
                                onValueChange = { nombreS = it }, 
                                placeholder = stringResource(R.string.edit_profile_placeholder_second_name), 
                                leadingIcon = { Icon(Icons.Default.Badge, null, tint = accentColor.copy(alpha = 0.3f)) },
                                enabled = !isLoading && isOnline
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                EditFieldWrapper(label = stringResource(R.string.register_last_name_paternal), error = if (!isApellidoPaValid && apellidoPa.isNotEmpty()) stringResource(R.string.edit_profile_error_min_2_short) else null) {
                                    CuidaleTextField(
                                        value = apellidoPa, 
                                        onValueChange = { apellidoPa = it }, 
                                        placeholder = stringResource(R.string.edit_profile_placeholder_paternal), 
                                        isError = !isApellidoPaValid && apellidoPa.isNotEmpty(), 
                                        modifier = Modifier.offset { IntOffset(apellidoShake.value.roundToInt(), 0) },
                                        enabled = !isLoading && isOnline
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                EditFieldWrapper(label = stringResource(R.string.register_last_name_maternal)) {
                                    CuidaleTextField(
                                        value = apellidoMa, 
                                        onValueChange = { apellidoMa = it }, 
                                        placeholder = stringResource(R.string.edit_profile_placeholder_maternal), 
                                        enabled = !isLoading && isOnline
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = textColor.copy(alpha = 0.08f))

                    EditSectionHeader(icon = Icons.Default.LocationOn, title = stringResource(R.string.edit_profile_section_contact), accentColor = accentColor)

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EditFieldWrapper(label = stringResource(R.string.edit_profile_phone_label), error = if (!isPhoneValid && telefono.isNotEmpty()) stringResource(R.string.edit_profile_error_phone) else null) {
                            CuidaleTextField(
                                value = telefono, 
                                onValueChange = { if(it.length <= 9) telefono = it }, 
                                placeholder = stringResource(R.string.register_phone_placeholder), 
                                keyboardType = KeyboardType.Phone, 
                                leadingIcon = { Icon(Icons.Default.Phone, null, tint = accentColor.copy(alpha = 0.6f)) },
                                isError = !isPhoneValid && telefono.isNotEmpty(), 
                                modifier = Modifier.offset { IntOffset(phoneShake.value.roundToInt(), 0) },
                                enabled = !isLoading && isOnline
                            )
                        }

                        CuidaleExposedDropdown(
                            label = stringResource(R.string.edit_profile_region_label),
                            options = regionsList,
                            selectedOption = region,
                            onOptionSelected = { region = it; comuna = "" },
                            enabled = !isLoading && isOnline
                        )

                        AnimatedVisibility(visible = region.isNotEmpty()) {
                            CuidaleExposedDropdown(
                                label = stringResource(R.string.edit_profile_commune_label),
                                options = communesForSelectedRegion,
                                selectedOption = comuna,
                                onOptionSelected = { comuna = it },
                                enabled = !isLoading && isOnline
                            )
                        }
                    }

                    HorizontalDivider(color = textColor.copy(alpha = 0.08f))

                    EditSectionHeader(icon = Icons.Default.FormatQuote, title = stringResource(R.string.edit_profile_section_about), accentColor = accentColor)

                    Column {
                        CuidaleTextField(
                            value = descripcion, 
                            onValueChange = { if(it.length <= 300) descripcion = it }, 
                            placeholder = stringResource(R.string.edit_profile_placeholder_about), 
                            modifier = Modifier.height(140.dp), 
                            singleLine = false, 
                            enabled = !isLoading && isOnline
                        )
                        Text(
                            stringResource(R.string.edit_profile_char_count, descripcion.length),
                            style = MaterialTheme.typography.labelSmall, 
                            color = textColor.copy(alpha = 0.4f), 
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), 
                            textAlign = TextAlign.End
                        )
                    }

                    if (userData.rol == "cuidador") {
                        HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                        EditSectionHeader(icon = Icons.Default.Badge, title = stringResource(R.string.edit_profile_section_caregiver), accentColor = accentColor)

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            EditFieldWrapper(label = stringResource(R.string.edit_profile_label_birthdate)) {
                                CuidaleTextField(
                                    value = fechaNacimiento,
                                    onValueChange = { if (it.length <= 10) fechaNacimiento = it },
                                    placeholder = stringResource(R.string.edit_profile_placeholder_birthdate_example),
                                    enabled = !isLoading && isOnline
                                )
                            }
                            Text(stringResource(R.string.edit_profile_age_visible, com.example.cuidaleapp.ui.components.calcularEdad(fechaNacimiento)), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = discapacidadBool, onCheckedChange = { discapacidadBool = it })
                                Text(stringResource(R.string.edit_profile_ask_disability), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            if (discapacidadBool) {
                                CuidaleTextField(value = tipoDiscapacidad, onValueChange = { tipoDiscapacidad = it }, placeholder = stringResource(R.string.edit_profile_placeholder_disability_type), enabled = !isLoading && isOnline)
                                Spacer(Modifier.height(8.dp))
                                CuidaleTextField(value = tiempoDiscapacidad, onValueChange = { tiempoDiscapacidad = it }, placeholder = stringResource(R.string.edit_profile_placeholder_disability_time), enabled = !isLoading && isOnline)
                            }

                            EditFieldWrapper(label = stringResource(R.string.edit_profile_label_hourly_rate)) {
                                CuidaleTextField(
                                    value = if (valorHoraReferencial > 0) valorHoraReferencial.toString() else "",
                                    onValueChange = { valorHoraReferencial = it.filter(Char::isDigit).take(6).toIntOrNull() ?: 0 },
                                    placeholder = stringResource(R.string.edit_profile_placeholder_hourly_rate_example),
                                    enabled = !isLoading && isOnline
                                )
                            }

                            EditFieldWrapper(label = stringResource(R.string.edit_profile_label_education)) {
                                CuidaleTextField(
                                    value = formacionTexto,
                                    onValueChange = { formacionTexto = it },
                                    placeholder = stringResource(R.string.edit_profile_placeholder_education_example),
                                    singleLine = false,
                                    modifier = Modifier.height(110.dp),
                                    enabled = !isLoading && isOnline
                                )
                            }

                            EditFieldWrapper(label = stringResource(R.string.edit_profile_label_experience)) {
                                CuidaleTextField(
                                    value = experienciaTexto,
                                    onValueChange = { experienciaTexto = it },
                                    placeholder = stringResource(R.string.edit_profile_placeholder_experience_example),
                                    singleLine = false,
                                    modifier = Modifier.height(120.dp),
                                    enabled = !isLoading && isOnline
                                )
                            }

                            EditFieldWrapper(label = stringResource(R.string.edit_profile_label_references)) {
                                CuidaleTextField(
                                    value = referenciasTexto,
                                    onValueChange = { referenciasTexto = it },
                                    placeholder = stringResource(R.string.edit_profile_placeholder_references_example),
                                    singleLine = false,
                                    modifier = Modifier.height(110.dp),
                                    enabled = !isLoading && isOnline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val errorMessage = (remoteState as? ProfileViewModel.RemoteState.Error)?.message
                    if (errorMessage != null) {
                        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ErrorMessages.get(context, errorMessage),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HardShadowButton(
                        text = if (isLoading) stringResource(R.string.edit_profile_saving) else stringResource(R.string.edit_profile_save_upper),
                        onClick = {
                            if (isFormValid) {
                                viewModel.updateProfileWithPhoto(
                                    userData.copy(
                                        nombre = nombre,
                                        nombre_s = nombreS,
                                        apellido_pa = apellidoPa,
                                        apellido_ma = apellidoMa,
                                        telefono = telefono,
                                        region = region,
                                        comuna = comuna,
                                        descripcion = descripcion,
                                        fechaNacimiento = fechaNacimiento.trim(),
                                        discapacidad = discapacidadBool,
                                        tipoDiscapacidad = tipoDiscapacidad.trim(),
                                        tiempoDiscapacidad = tiempoDiscapacidad.trim(),
                                        valorHoraReferencial = valorHoraReferencial,
                                        formacionAcademica = parseFormaciones(formacionTexto),
                                        experienciasLaborales = parseExperiencias(experienciaTexto),
                                        referenciasLaborales = parseReferencias(referenciasTexto)
                                    ), 
                                    pendingPhotoUri, 
                                    pendingDeletePhoto
                                )
                            }
                        },
                        containerColor = if (isFormValid && isOnline) accentColor else Color.Gray.copy(alpha = 0.5f),
                        enabled = isFormValid && isOnline && !isLoading,
                        icon = if (isLoading) null else Icons.Default.Save,
                        cornerRadius = 16.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun EditSectionHeader(icon: ImageVector, title: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = accentColor
        )
    }
}

@Composable
fun EditFieldWrapper(
    label: String,
    error: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InputFieldLabel(label)
        content()
        AnimatedVisibility(visible = error != null) {
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}

private fun formacionesATexto(list: List<com.example.cuidaleapp.models.FormacionAcademica>): String =
    list.joinToString("\n") { f ->
        listOf(f.titulo, f.institucion, f.anioInicio, f.anioFin).joinToString(" | ")
    }

private fun experienciasATexto(list: List<com.example.cuidaleapp.models.ExperienciaLaboral>): String =
    list.joinToString("\n") { e ->
        listOf(e.cargo, e.empresa, e.desde, e.hasta, e.tiempoExperiencia, e.descripcion).joinToString(" | ")
    }

private fun referenciasATexto(list: List<com.example.cuidaleapp.models.ReferenciaLaboral>): String =
    list.joinToString("\n") { r ->
        listOf(r.nombre, r.empresa, r.telefono, r.cargo, r.detalle).joinToString(" | ")
    }

private fun parseFormaciones(text: String): List<com.example.cuidaleapp.models.FormacionAcademica> =
    text.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split("|").map { it.trim() }
        com.example.cuidaleapp.models.FormacionAcademica(
            titulo = p.getOrNull(0) ?: "",
            institucion = p.getOrNull(1) ?: "",
            anioInicio = p.getOrNull(2) ?: "",
            anioFin = p.getOrNull(3) ?: ""
        )
    }

private fun parseExperiencias(text: String): List<com.example.cuidaleapp.models.ExperienciaLaboral> =
    text.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split("|").map { it.trim() }
        com.example.cuidaleapp.models.ExperienciaLaboral(
            cargo = p.getOrNull(0) ?: "",
            empresa = p.getOrNull(1) ?: "",
            desde = p.getOrNull(2) ?: "",
            hasta = p.getOrNull(3) ?: "",
            tiempoExperiencia = p.getOrNull(4) ?: "",
            descripcion = p.getOrNull(5) ?: ""
        )
    }

private fun parseReferencias(text: String): List<com.example.cuidaleapp.models.ReferenciaLaboral> =
    text.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split("|").map { it.trim() }
        com.example.cuidaleapp.models.ReferenciaLaboral(
            nombre = p.getOrNull(0) ?: "",
            empresa = p.getOrNull(1) ?: "",
            telefono = p.getOrNull(2) ?: "",
            cargo = p.getOrNull(3) ?: "",
            detalle = p.getOrNull(4) ?: ""
        )
    }
