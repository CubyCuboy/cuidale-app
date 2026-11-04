package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.ui.components.CuidaleAppRoot
import com.example.cuidaleapp.ui.components.CuidaleWelcomeLoading
import com.example.cuidaleapp.ui.screens.LoginScreen
import com.example.cuidaleapp.viewmodel.LoginUiState
import com.example.cuidaleapp.viewmodel.LoginViewModel
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    private val credentialManager = CredentialManager.create(this)

    private fun handleGoogleCredential(response: GetCredentialResponse) {
        val credential = response.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleCredential.idToken
                val payload = decodeJwtPayload(idToken)
                val email = payload?.optString("email")?.takeIf { it.isNotBlank() }
                val displayName = payload?.optString("name")?.takeIf { it.isNotBlank() }
                if (!idToken.isNullOrBlank() && !email.isNullOrBlank()) {
                    viewModel.loginWithGoogle(idToken, email, displayName)
                } else if (!email.isNullOrBlank()) {
                    viewModel.setError("No se pudo obtener el token de autenticación de Google.")
                } else {
                    viewModel.setError("No se pudo obtener la información de la cuenta de Google.")
                }
            } catch (e: GoogleIdTokenParsingException) {
                viewModel.setError("Error en Google Sign-In")
            }
        } else {
            viewModel.setError("Error en Google Sign-In")
        }
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun decodeJwtPayload(token: String?): JSONObject? {
        if (token.isNullOrBlank()) return null
        return try {
            val parts = token.split('.')
            if (parts.size < 2) null else {
                val normalized = parts[1].replace('-', '+').replace('_', '/')
                val padding = (4 - normalized.length % 4) % 4
                val bytes = Base64.decode(normalized + "=".repeat(padding), Base64.DEFAULT)
                JSONObject(String(bytes, Charsets.UTF_8))
            }
        } catch (e: Exception) { null }
    }

    private fun startGoogleSignIn() {
        viewModel.clearError()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(getString(com.example.cuidaleapp.R.string.google_client_id))
                    .setNonce(generateSecureRandomNonce())
                    .build()
            )
            .build()
        lifecycleScope.launch {
            try {
                val response = credentialManager.getCredential(MutableContextWrapper(this@LoginActivity), request)
                handleGoogleCredential(response)
            } catch (e: GetCredentialException) {
                if (e !is GetCredentialCancellationException) {
                    viewModel.setError("No se pudo iniciar sesión con Google.")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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
                            putExtra("EXTERNAL_PHOTO_URL", state.photoUrl ?: "")
                        }
                        startActivity(intent)
                        viewModel.clearError()
                    }
                    else -> {}
                }
            }
            
            CuidaleAppRoot(darkTheme = isDark) {
                if (uiState is LoginUiState.CheckingAuth) {
                    CuidaleWelcomeLoading()
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

