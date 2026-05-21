package com.example.cuidaleapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.CuidaleTextField
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ForgotPasswordUiState
import com.example.cuidaleapp.viewmodel.ForgotPasswordViewModel

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val gradient = CuidaleThemeExtras.gradient

    LaunchedEffect(uiState) {
        when (uiState) {
            is ForgotPasswordUiState.Success -> {
                Toast.makeText(context, "Correo de recuperación enviado con éxito", Toast.LENGTH_LONG).show()
                onBackToLogin()
            }
            is ForgotPasswordUiState.Error -> {
                Toast.makeText(context, (uiState as ForgotPasswordUiState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Recuperar Contraseña",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ingresa correo de tu cuenta",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            CuidaleTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "ejemplo@correo.com",
                containerColor = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            HardShadowButton(
                text = if (uiState is ForgotPasswordUiState.Loading) "Enviando..." else "Enviar",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary,
                isDark = false,
                onClick = {
                    if (email.isNotBlank()) {
                        viewModel.sendResetEmail(email)
                    } else {
                        Toast.makeText(context, "Por favor ingresa un correo", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onBackToLogin) {
                Text(text = "Volver al Login", color = Color.White)
            }
        }
    }
}
