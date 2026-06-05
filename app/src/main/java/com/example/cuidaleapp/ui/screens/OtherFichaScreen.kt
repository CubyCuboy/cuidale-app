package com.example.cuidaleapp.ui.screens

import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.ProfileDetailView
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherFichaScreen(
    profileViewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val otherUserData by profileViewModel.otherUserData.collectAsState()
    val otherFichaState by profileViewModel.otherFichaState.collectAsState()
    val otherProfileState by profileViewModel.otherProfileState.collectAsState()

    val scrollState = rememberScrollState()
    val customColors = CuidaleThemeExtras.customColors
    
    // Use the role from the loaded user data
    val displayRole = otherUserData?.rol ?: ""
    val accentColor = when (displayRole) {
        "cuidador" -> MaterialTheme.colorScheme.primary
        "representante" -> customColors.successStrong
        else -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (otherProfileState is ProfileViewModel.RemoteState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (otherProfileState is ProfileViewModel.RemoteState.Error) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        (otherProfileState as ProfileViewModel.RemoteState.Error).message,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateBack) { Text("VOLVER") }
                }
            }
        } else if (otherUserData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando perfil...")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Header / Banner
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
                                text = if (displayRole == "cuidador") "FICHA DE CUIDADOR" else "FICHA DEL PACIENTE",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "${otherUserData?.nombre} ${otherUserData?.apellido_pa}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-40).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Standard Avatar Header
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(customColors.avatarBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            if (otherUserData?.fotoUrl?.isNotEmpty() == true && !otherUserData?.fotoUrl?.startsWith("res:")!!) {
                                AsyncImage(
                                    model = otherUserData?.fotoUrl,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (val targetState = otherFichaState) {
                        is FichaState.Loading -> {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        }
                        is FichaState.View -> {
                            ProfileDetailView(
                                role = displayRole,
                                data = targetState.data,
                                onEdit = { /* No edit for others */ }
                            )
                        }
                        is FichaState.NotFound -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                HardShadowPanel(
                                    backgroundColor = customColors.panelBackground,
                                    shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Info, null, modifier = Modifier.size(48.dp), tint = accentColor.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Este usuario aún no ha completado su ficha técnica.",
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                        is FichaState.Error -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(targetState.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> {}
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
