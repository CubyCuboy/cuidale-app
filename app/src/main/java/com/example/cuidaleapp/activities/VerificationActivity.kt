package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.cuidaleapp.ui.screens.RegisterVerificationStep
import com.example.cuidaleapp.ui.screens.RegisterVerificationStep
import com.example.cuidaleapp.ui.screens.RegistrationSuccessStep
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.RegisterStatus
import com.example.cuidaleapp.viewmodel.RegisterViewModel
import com.example.cuidaleapp.viewmodel.ThemeViewModel
import com.example.cuidaleapp.viewmodel.RegisterStep

class VerificationActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()
    private val registerViewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val emailToVerify = intent.getStringExtra("EMAIL_TO_VERIFY")
        if (emailToVerify == null) {
            finish()
            return
        }

        registerViewModel.prepareForVerification(emailToVerify)

        setContent {
            val isDark by themeViewModel.isDarkMode.collectAsState()
            val status by registerViewModel.status.collectAsState()
            val currentStep by registerViewModel.currentStep.collectAsState()
            val context = LocalContext.current
            val gradient = CuidaleThemeExtras.gradient

            LaunchedEffect(status) {
                if (status is RegisterStatus.Error) {
                    Toast.makeText(context, (status as RegisterStatus.Error).message, Toast.LENGTH_LONG).show()
                    registerViewModel.clearError()
                }
            }

            LaunchedEffect(currentStep) {
                // No navegamos automáticamente, dejamos que se muestre RegistrationSuccessStep
            }

            CuidaleAppTheme(darkTheme = isDark) {
                val onGradientText = CuidaleThemeExtras.customColors.onGradient
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(80.dp))

                        // Logo Placeholder
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LOGO",
                                style = MaterialTheme.typography.headlineSmall,
                                color = onGradientText
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        if (currentStep == RegisterStep.Success) {
                            RegistrationSuccessStep(onLogin = {
                                val intent = Intent(this@VerificationActivity, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            })
                        } else {
                            RegisterVerificationStep(
                                viewModel = registerViewModel,
                                onBack = null // Sin botón de cancelar/volver
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        // Deshabilitar el botón atrás físico para obligar a usar el botón de la UI
        // que redirige al Login (cancelando el proceso) o simplemente no hacer nada.
        // El usuario pidió que "debe no poder salir de esta por ningún motivo"
        // Pero usualmente se permite volver al login si se arrepienten.
        // Si el usuario dice "no poder salir por ningún motivo", tal vez se refiere a no saltarse la verificación.
        super.onBackPressed()
    }
}
