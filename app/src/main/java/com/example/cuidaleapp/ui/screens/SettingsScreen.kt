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
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onBack: () -> Unit,
    onLogoutClick: () -> Unit,
    onResetPass: () -> Unit
) {
    val context = LocalContext.current
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

            // Phone Verification Notification (Improved Visuals)
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
        }
    }
}
