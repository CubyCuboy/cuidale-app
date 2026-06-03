package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.data.ChileData
import com.example.cuidaleapp.ui.components.CuidaleExposedDropdown
import com.example.cuidaleapp.ui.components.CuidaleTextField
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.InputFieldLabel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.PhoneUtils
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val remoteState by viewModel.remoteState.collectAsState()
    val scrollState = rememberScrollState()

    var nombre by remember { mutableStateOf(userData.nombre) }
    var nombreS by remember { mutableStateOf(userData.nombre_s) }
    var apellidoPa by remember { mutableStateOf(userData.apellido_pa) }
    var apellidoMa by remember { mutableStateOf(userData.apellido_ma) }
    var telefono by remember { mutableStateOf(userData.telefono) }
    var rut by remember { mutableStateOf(userData.rut) }
    var region by remember { mutableStateOf(userData.region) }
    var comuna by remember { mutableStateOf(userData.comuna) }
    var descripcion by remember { mutableStateOf(userData.descripcion) }

    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val regionsList = ChileData.regions.map { it.name }
    val communesForSelectedRegion = remember(region) {
        ChileData.regions.find { it.name == region }?.comunas?.sorted() ?: emptyList()
    }

    val canSave = nombre.isNotBlank() && apellidoPa.isNotBlank()

    LaunchedEffect(remoteState) {
        if (remoteState is ProfileViewModel.RemoteState.Success) {
            onNavigateBack()
        }
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
                            nombre = input.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        },
                        placeholder = "Tu nombre",
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )

                    InputFieldLabel(text = "Segundo Nombre (Opcional)", color = textColor)
                    CuidaleTextField(
                        value = nombreS,
                        onValueChange = { input ->
                            nombreS = input.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        },
                        placeholder = "Segundo nombre",
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            InputFieldLabel(text = "Apellido Paterno", color = textColor)
                            CuidaleTextField(
                                value = apellidoPa,
                                onValueChange = { input ->
                                    apellidoPa = input.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                },
                                placeholder = "Apellido",
                                containerColor = customColors.inputBackground,
                                textColor = textColor
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InputFieldLabel(text = "Apellido Materno", color = textColor)
                            CuidaleTextField(
                                value = apellidoMa,
                                onValueChange = { input ->
                                    apellidoMa = input.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                },
                                placeholder = "Apellido",
                                containerColor = customColors.inputBackground,
                                textColor = textColor
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
                            if (input.isEmpty() || input.all { it.isDigit() || it == '+' }) {
                                telefono = input
                            }
                        },
                        placeholder = "+569...",
                        containerColor = customColors.inputBackground,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                        textColor = textColor,
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { 
                                telefono = PhoneUtils.formatChileanPhone(telefono)
                            }
                        )
                    )

                    CuidaleExposedDropdown(
                        label = "Región",
                        options = regionsList,
                        selectedOption = region,
                        onOptionSelected = { 
                            region = it
                            comuna = ""
                        },
                        placeholder = "Seleccionar Región"
                    )

                    CuidaleExposedDropdown(
                        label = "Comuna",
                        options = communesForSelectedRegion,
                        selectedOption = comuna,
                        onOptionSelected = { comuna = it },
                        placeholder = "Seleccionar Comuna",
                        enabled = region.isNotBlank()
                    )

                    InputFieldLabel(text = "Sobre mí", color = textColor)
                    CuidaleTextField(
                        value = descripcion,
                        onValueChange = { if(it.length <= 300) descripcion = it },
                        placeholder = "Escribe algo sobre ti...",
                        containerColor = customColors.inputBackground,
                        singleLine = false,
                        modifier = Modifier.height(100.dp),
                        textColor = textColor
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
                                viewModel.updateProfile(updatedUser)
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
