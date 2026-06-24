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
import com.example.cuidaleapp.ui.components.CuidaleWelcomeLoading
import com.example.cuidaleapp.ui.screens.LoginScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.LoginUiState
import com.example.cuidaleapp.viewmodel.LoginViewModel

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    else -> {}
                }
            }
            
            CuidaleAppTheme(darkTheme = isDark) {
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
                        onVerify = { email ->
                            val intent = Intent(this@LoginActivity, EmailVerificationActivity::class.java)
                            intent.putExtra("email", email)
                            startActivity(intent)
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

