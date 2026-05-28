package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.models.UserData

import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.cuidaleapp.R

@Composable
fun MenuScreen(
    userData: UserData,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onResetPassword: () -> Unit,
    onViewProfile: () -> Unit,
    onNavigateToFicha: () -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    val gradient = CuidaleThemeExtras.gradient
    val colors = MaterialTheme.colorScheme
    val customColors = CuidaleThemeExtras.customColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Cambiar modo",
                        tint = customColors.onGradient
                    )
                }
            }
            
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = if (isDark) CuidaleThemeExtras.shadows.hardShadowAccent else CuidaleThemeExtras.shadows.hardShadowPrimary,
                    modifier = Modifier.padding(28.dp)
                ) {
            val roleTitle = when (userData.rol.lowercase().trim()) {
                "cuidador" -> stringResource(R.string.home_title_caregiver)
                "representante" -> stringResource(R.string.home_title_representative)
                else -> ""
            }

            if (roleTitle.isNotEmpty()) {
                Text(
                    text = roleTitle,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = colors.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Text(
                text = stringResource(R.string.menu_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = stringResource(R.string.menu_greeting, userData.nombre),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            HardShadowButton(
                text = stringResource(R.string.menu_item_ficha),
                containerColor = colors.primary,
                contentColor = Color.White,
                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary,
                icon = Icons.Default.Badge,
                isDark = customColors.isDark,
                onClick = { onNavigateToFicha() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = stringResource(R.string.menu_item_profile),
                containerColor = colors.primary,
                contentColor = Color.White,
                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary,
                icon = Icons.Default.Person,
                isDark = customColors.isDark,
                onClick = onViewProfile
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = "Buscar Usuarios",
                containerColor = colors.primary,
                contentColor = Color.White,
                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary,
                icon = Icons.Default.Search,
                isDark = customColors.isDark,
                onClick = onNavigateToSearch
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = stringResource(R.string.menu_forgot_password),
                containerColor = colors.secondary,
                contentColor = colors.onSecondary,
                shadowColor = colors.secondaryContainer,
                isDark = customColors.isDark,
                onClick = onResetPassword
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = stringResource(R.string.menu_logout),
                containerColor = colors.error,
                contentColor = Color.White,
                shadowColor = colors.errorContainer,
                isDark = customColors.isDark,
                onClick = onLogout
            )
                }
            }
        }
    }
}
