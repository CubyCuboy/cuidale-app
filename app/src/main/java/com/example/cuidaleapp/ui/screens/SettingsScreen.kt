package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.example.cuidaleapp.activities.PhoneVerificationActivity
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.ConnectivityBanner
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.AppCacheManager
import com.example.cuidaleapp.viewmodel.ChatViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onBack: () -> Unit,
    onLogoutClick: () -> Unit,
    onResetPass: () -> Unit,
    chatViewModel: ChatViewModel? = null
) {
    val context = LocalContext.current
    val settingsScope = rememberCoroutineScope()
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val userData by viewModel.userData.collectAsState()

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (userData.telefono.isNotEmpty() && !userData.telefonoVerificado) {
                HardShadowPanel(
                    backgroundColor = customColors.warningContainer,
                    shadowColor = customColors.warning.copy(alpha = 0.2f),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = customColors.warning,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Verifica tu número",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = customColors.warning
                            )
                            Text(
                                "Tu número ${userData.telefono} aún no está verificado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.warning.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(
                            onClick = { context.startActivity(Intent(context, PhoneVerificationActivity::class.java)) },
                            colors = ButtonDefaults.buttonColors(containerColor = customColors.warning),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("VERIFICAR AHORA", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            val emailToCheck = userData.correo
            if (emailToCheck.isNotEmpty() && !userData.verificado) {
                HardShadowPanel(
                    backgroundColor = customColors.warningContainer,
                    shadowColor = customColors.warning.copy(alpha = 0.2f),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = customColors.warning,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Verifica tu correo",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = customColors.warning
                            )
                            Text(
                                "Tu correo $emailToCheck aún no está verificado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = customColors.warning.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(context, com.example.cuidaleapp.activities.EmailVerificationActivity::class.java).apply {
                                    putExtra("email", emailToCheck)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = customColors.warning),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("VERIFICAR CORREO AHORA", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Cuenta y Seguridad",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val isPasswordLinked = (currentUser?.providerData ?: emptyList()).any { it.providerId == "password" }

                    if (isPasswordLinked) {
                        HardShadowButton(
                            text = stringResource(R.string.profile_reset_pass_button),
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White,
                            shadowColor = MaterialTheme.colorScheme.secondaryContainer,
                            icon = Icons.Default.LockReset,
                            isDark = isDark,
                            onClick = onResetPass
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    HardShadowButton(
                        text = "Gestión de Cuenta",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.primaryContainer,
                        icon = Icons.Default.AccountBox,
                        isDark = isDark,
                        onClick = {
                            context.startActivity(Intent(context, com.example.cuidaleapp.activities.AccountManagementActivity::class.java))
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Sesión",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    HardShadowButton(
                        text = stringResource(R.string.profile_logout_button),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.errorContainer,
                        icon = Icons.AutoMirrored.Filled.Logout,
                        isDark = isDark,
                        onClick = {
                            viewModel.logout {
                                onLogoutClick()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "v${stringResource(R.string.app_version)} - Cuidale App",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Almacenamiento y Caché",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    var cacheBytes by remember { mutableLongStateOf(AppCacheManager.cacheSizeBytes(context)) }
                    var clearingCache by remember { mutableStateOf(false) }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Multimedia e imágenes",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${AppCacheManager.formatBytes(cacheBytes)} en caché local",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HardShadowButton(
                        text = if (clearingCache) "Limpiando..." else "Limpiar caché",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        icon = Icons.Default.DeleteSweep,
                        isDark = isDark,
                        enabled = !clearingCache,
                        onClick = {
                            clearingCache = true
                            settingsScope.launch {
                                AppCacheManager.clearMultimediaCaches(context)
                                cacheBytes = AppCacheManager.cacheSizeBytes(context)
                                clearingCache = false
                            }
                        }
                    )
                    Text(
                        text = "Los archivos se vuelven a descargar cuando los abras de nuevo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (chatViewModel != null) {
                val manualStatus by chatViewModel.myManualStatus.collectAsState()
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Estado de conexión",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = manualStatus == null,
                                onClick = { chatViewModel.setManualStatus(null) },
                                label = { Text("Conectado") }
                            )
                            FilterChip(
                                selected = manualStatus == "desconectado",
                                onClick = { chatViewModel.setManualStatus("desconectado") },
                                label = { Text("Desconectado") }
                            )
                            FilterChip(
                                selected = manualStatus == "no_molestar",
                                onClick = { chatViewModel.setManualStatus("no_molestar") },
                                label = { Text("No molestar") }
                            )
                        }

                        Text(
                            text = "Inactivo se activa automáticamente tras 5 minutos sin actividad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
