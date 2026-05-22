package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@Composable
fun RoleSelectionScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit = {},
    onNavigateToCaregiver: () -> Unit,
    onNavigateToDependent: () -> Unit
) {
    val gradient = CuidaleThemeExtras.gradient
    val shadows = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(gradient.top, gradient.bottom)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.role_selection_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                HardShadowButton(
                    text = stringResource(R.string.role_caregiver),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shadowColor = shadows.hardShadowPrimary,
                    isDark = isDark,
                    onClick = onNavigateToCaregiver
                )

                Spacer(modifier = Modifier.height(24.dp))

                HardShadowButton(
                    text = stringResource(R.string.role_representative),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    shadowColor = shadows.hardShadowSecondary,
                    isDark = isDark,
                    onClick = onNavigateToDependent
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(
    title: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoleSelectionScreenPreview() {
    CuidaleAppTheme(darkTheme = false) {
        RoleSelectionScreen(
            isDark = false,
            onNavigateToCaregiver = {},
            onNavigateToDependent = {}
        )
    }
}
