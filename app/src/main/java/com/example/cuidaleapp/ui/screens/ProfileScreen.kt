package com.example.cuidaleapp.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.activities.EditProfileActivity
import com.example.cuidaleapp.activities.FichaActivity
import com.example.cuidaleapp.activities.PhoneVerificationActivity
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.ProfileDetailView
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    fichaViewModel: FichaViewModel,
    isOtherProfile: Boolean = false,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val myUserData by viewModel.userData.collectAsState()
    val otherUserData by viewModel.otherUserData.collectAsState()
    val otherProfileState by viewModel.otherProfileState.collectAsState()
    
    val myFichaState by fichaViewModel.state.collectAsState()
    val otherFichaState by viewModel.otherFichaState.collectAsState()
    
    val myFichaRole by fichaViewModel.role.collectAsState()
    
    // Resolve which data to show
    val displayUser: UserData? = if (isOtherProfile) otherUserData else myUserData
    val displayFichaState = if (isOtherProfile) otherFichaState else myFichaState
    val displayFichaRole = if (isOtherProfile) displayUser?.rol ?: "" else myFichaRole

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows

    if (isOtherProfile && otherProfileState is ProfileViewModel.RemoteState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (displayUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se pudo cargar el perfil")
        }
        return
    }

    val accentColor = if (displayUser.rol == "cuidador") MaterialTheme.colorScheme.primary else customColors.successStrong
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val panelBg = customColors.panelBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    Text(
                        text = if (isOtherProfile) "PERFIL" else "MI PERFIL",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isOtherProfile) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Settings, "Ajustes", tint = Color.White)
                        }
                    }
                }
            }

            // Profile Content Overlay
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-40).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centered Avatar
                Surface(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(4.dp),
                    shape = CircleShape,
                    color = Color.White,
                    tonalElevation = 8.dp
                ) {
                    if (displayUser.fotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = displayUser.fotoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = secondaryTextColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User Info Card
                HardShadowPanel(
                    backgroundColor = panelBg,
                    shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${displayUser.nombre} ${displayUser.apellido_pa}",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor
                                ),
                                textAlign = TextAlign.Center
                            )
                            
                            Text(
                                text = displayUser.correo,
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )

                            if (!isOtherProfile && displayUser.telefono.isNotEmpty() && !displayUser.telefonoVerificado) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = customColors.warningContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = {
                                        context.startActivity(Intent(context, PhoneVerificationActivity::class.java))
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(14.dp), tint = customColors.warning)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Verificar ahora",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = customColors.warning
                                        )
                                    }
                                }
                            }
                        }

                        if (!isOtherProfile) {
                            Spacer(modifier = Modifier.height(24.dp))
                            HardShadowButton(
                                text = "EDITAR PERFIL",
                                onClick = { context.startActivity(Intent(context, EditProfileActivity::class.java)) },
                                containerColor = accentColor,
                                contentColor = Color.White,
                                shadowColor = shadowPalette.hardShadowPrimary,
                                isDark = customColors.isDark,
                                icon = Icons.Default.Edit,
                                cornerRadius = 16.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(20.dp))

                        InfoItem(Icons.Default.Badge, "RUT", displayUser.rut, textColor, secondaryTextColor)
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InfoItem(
                                Icons.Default.Phone, 
                                "TELÉFONO", 
                                displayUser.telefono.ifEmpty { "No registrado" }, 
                                textColor, 
                                secondaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isOtherProfile && displayUser.telefono.isNotEmpty() && !displayUser.telefonoVerificado) {
                                Text(
                                    "NO VERIFICADO",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = customColors.warning,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        
                        InfoItem(Icons.Default.Place, "UBICACIÓN", "${displayUser.comuna}, ${displayUser.region}", textColor, secondaryTextColor)
                        InfoItem(Icons.Default.Work, "ROL", displayUser.rol.replaceFirstChar { it.uppercase() }, textColor, secondaryTextColor)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Ficha Section
                Text(
                    text = if (displayUser.rol == "cuidador") "FICHA TÉCNICA" else "FICHA DEL FAMILIAR",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp)
                )

                when (displayFichaState) {
                    is FichaState.Loading -> {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.padding(24.dp))
                    }
                    is FichaState.View -> {
                        ProfileDetailView(
                            role = displayFichaRole,
                            data = (displayFichaState as FichaState.View).data,
                            onEdit = { 
                                if (!isOtherProfile) {
                                    val intent = Intent(context, FichaActivity::class.java)
                                    intent.putExtra("edit_mode", true)
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                    is FichaState.NotFound -> {
                        FichaPlaceholder(
                            role = displayUser.rol,
                            accentColor = accentColor,
                            panelBg = panelBg,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isOtherProfile = isOtherProfile,
                            onCreateClick = {
                                context.startActivity(Intent(context, FichaActivity::class.java))
                            }
                        )
                    }
                    else -> { /* Error handle? */ }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun FichaPlaceholder(
    role: String,
    accentColor: Color,
    panelBg: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isOtherProfile: Boolean = false,
    onCreateClick: () -> Unit
) {
    val shadowPalette = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    
    HardShadowPanel(
        backgroundColor = panelBg,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (role == "cuidador") Icons.Default.Description else Icons.Default.FamilyRestroom,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = accentColor.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isOtherProfile) {
                    "Este usuario aún no tiene ficha técnica"
                } else {
                    if (role == "cuidador") "Parece ser que aún no sabemos mucho de ti" 
                    else "Aún no tenemos información de tu familiar"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = textColor
            )
            
            if (!isOtherProfile) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (role == "cuidador")
                        "Ingresa tus datos en la ficha técnica para que las familias puedan conocer tu experiencia."
                    else
                        "Ingresa sus datos en la ficha técnica para que podamos ayudarte mejor a encontrar el cuidado ideal.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(24.dp))
                HardShadowButton(
                    text = "CREAR FICHA",
                    onClick = onCreateClick,
                    containerColor = accentColor,
                    contentColor = Color.White,
                    shadowColor = shadowPalette.hardShadowPrimary,
                    isDark = customColors.isDark,
                    icon = Icons.Default.Add
                )
            }
        }
    }
}

@Composable
fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    value: String, 
    primary: Color, 
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = secondary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = secondary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = secondary, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = primary)
        }
    }
}
