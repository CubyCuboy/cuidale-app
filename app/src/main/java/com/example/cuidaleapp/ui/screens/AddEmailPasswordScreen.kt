package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.AddEmailPasswordState
import com.example.cuidaleapp.viewmodel.AddEmailPasswordViewModel

@Composable
fun AddEmailPasswordScreen(
    viewModel: AddEmailPasswordViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()

    var step by rememberSaveable { mutableStateOf(1) }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isPassVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPassVisible by rememberSaveable { mutableStateOf(false) }

    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val typography = MaterialTheme.typography

    LaunchedEffect(uiState) {
        if (uiState is AddEmailPasswordState.Success) {
            onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (step == 2) step = 1 else onNavigateBack()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Agregar Correo y Contraseña",
                style = typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (step == 1) {
                        Text(
                            text = "Paso 1: Correo Electrónico",
                            style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ingresa el correo electrónico que deseas asociar. Puede ser el mismo u otro diferente al de tu cuenta externa.",
                            style = typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { viewModel.onEmailChange(it) },
                            label = { Text("Correo electrónico") },
                            placeholder = { Text("ejemplo@correo.cl") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        val isEmailValid = viewModel.validateEmail(email)

                        Spacer(modifier = Modifier.height(16.dp))

                        HardShadowButton(
                            text = "Siguiente",
                            onClick = {
                                if (isEmailValid) {
                                    step = 2
                                }
                            },
                            enabled = isEmailValid,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.primary,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            cornerRadius = 12.dp
                        )
                    } else {
                        Text(
                            text = "Paso 2: Contraseña de Acceso",
                            style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Establece una contraseña segura para tu cuenta.",
                            style = typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { viewModel.onPasswordChange(it) },
                            label = { Text("Contraseña") },
                            placeholder = { Text("Ingrese su contraseña") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isPassVisible = !isPassVisible }) {
                                    Icon(
                                        imageVector = if (isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Repetir contraseña") },
                            placeholder = { Text("Reingrese su contraseña") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPassVisible = !isConfirmPassVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isConfirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Requirements checklist
                        val reqLength = password.length >= 8
                        val reqUpper = password.any { it.isUpperCase() }
                        val reqSpecial = password.any { !it.isLetterOrDigit() }
                        val reqMatch = password.isNotEmpty() && password == confirmPassword

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RequirementRow("Mínimo 8 caracteres", reqLength)
                            RequirementRow("Al menos una mayúscula", reqUpper)
                            RequirementRow("Un carácter especial", reqSpecial)
                            RequirementRow("Las contraseñas coinciden", reqMatch)
                        }

                        val isFormValid = reqLength && reqUpper && reqSpecial && reqMatch

                        Spacer(modifier = Modifier.height(16.dp))

                        val isLoading = uiState is AddEmailPasswordState.Loading

                        HardShadowButton(
                            text = if (isLoading) "Guardando..." else "Vincular y Finalizar",
                            onClick = { viewModel.linkEmailPassword() },
                            enabled = isFormValid && !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.primary,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            cornerRadius = 12.dp
                        )
                    }

                    if (uiState is AddEmailPasswordState.Error) {
                        Text(
                            text = (uiState as AddEmailPasswordState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RequirementRow(text: String, met: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (met) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (met) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
