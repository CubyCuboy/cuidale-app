package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onBack: () -> Unit,
    onLogoutClick: () -> Unit,
    onResetPass: () -> Unit
) {
    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = customColors.onGradient
                    )
                }
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.headlineMedium,
                    color = customColors.onGradient,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Cuenta y Seguridad",
                    style = MaterialTheme.typography.titleLarge,
                    color = customColors.onGradient,
                    modifier = Modifier.padding(bottom = 8.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sesión",
                    style = MaterialTheme.typography.titleLarge,
                    color = customColors.onGradient,
                    modifier = Modifier.padding(bottom = 8.dp)
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
            }
        }
    }
}
