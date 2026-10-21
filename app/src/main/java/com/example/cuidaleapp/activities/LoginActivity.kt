package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.ui.components.CuidaleAppRoot
import com.example.cuidaleapp.ui.screens.LoginScreen
import com.example.cuidaleapp.viewmodel.LoginUiState
import com.example.cuidaleapp.viewmodel.LoginViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_CANCELED) {
            viewModel.clearError()
            return@registerForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email
            if (!idToken.isNullOrBlank() && !email.isNullOrBlank()) {
                viewModel.loginWithGoogle(idToken, email, account.displayName)
            } else if (!email.isNullOrBlank()) {
                viewModel.setError("No se pudo obtener el token de autenticación de Google.")
            } else {
                viewModel.setError("No se pudo obtener la información de la cuenta de Google.")
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501 && e.statusCode != 16) {
                viewModel.setError("Error en Google Sign-In: ${e.message}")
            } else {
                viewModel.clearError()
            }
        }
    }

    private fun startGoogleSignIn() {
        viewModel.clearError()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("397845476517-4uhbn38sqf9lhu5b8r075r3ns3glfful.apps.googleusercontent.com")
            .requestEmail()
            .requestProfile()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        try {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        } catch (_: Exception) {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Login en modo inmersivo: oculta la barra de navegación del sistema
        // (se muestra de forma transitoria al deslizar el borde).
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            val uiState by viewModel.uiState.collectAsState()

            var wasCheckingAuth by remember { mutableStateOf(false) }

            LaunchedEffect(uiState) {
                if (uiState is LoginUiState.CheckingAuth) {
                    wasCheckingAuth = true
                }
                when (uiState) {
                    is LoginUiState.Success -> {
                        val intent = Intent(this@LoginActivity, MenuActivity::class.java)
                        if (wasCheckingAuth) {
                            intent.putExtra("SKIP_SPLASH", true)
                        }
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                    is LoginUiState.Unverified -> {
                        val intent = Intent(this@LoginActivity, EmailVerificationActivity::class.java)
                        intent.putExtra("email", (uiState as LoginUiState.Unverified).email)
                        startActivity(intent)
                        viewModel.clearError()
                    }
                    is LoginUiState.ExternalRegistrationNeeded -> {
                        val state = uiState as LoginUiState.ExternalRegistrationNeeded
                        val intent = Intent(this@LoginActivity, RegisterActivity::class.java).apply {
                            putExtra("IS_EXTERNAL", true)
                            putExtra("EXTERNAL_EMAIL", state.email)
                            putExtra("EXTERNAL_UID", state.uid)
                            putExtra("EXTERNAL_PROVIDER", state.providerId)
                            putExtra("EXTERNAL_DISPLAY_NAME", state.displayName)
                        }
                        startActivity(intent)
                        viewModel.clearError()
                    }
                    else -> {}
                }
            }
            
            CuidaleAppRoot(darkTheme = isDark) {
                if (uiState is LoginUiState.CheckingAuth) {
                    com.example.cuidaleapp.ui.screens.LoginLoadingScreen()
                } else {
                    LoginScreen(
                        isDark = isDark,
                        onToggleTheme = { viewModel.toggleTheme() },
                        viewModel = viewModel,
                        onForgotPassword = {
                            startActivity(Intent(this@LoginActivity, RecoveryPasswordActivity::class.java))
                        },
                        onRegister = {
                            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                        },
                        onVerify = { identifier ->
                            viewModel.resolveEmailForVerification(identifier) { resolvedEmail ->
                                val intent = Intent(this@LoginActivity, EmailVerificationActivity::class.java)
                                intent.putExtra("email", resolvedEmail)
                                startActivity(intent)
                            }
                        },
                        onGoogleLogin = {
                            startGoogleSignIn()
                        },
                        onMicrosoftLogin = {
                            viewModel.loginWithProvider(this@LoginActivity, "microsoft.com")
                        }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

