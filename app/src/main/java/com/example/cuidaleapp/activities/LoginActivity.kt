package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.ui.screens.LoginScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.LoginUiState
import com.example.cuidaleapp.viewmodel.LoginViewModel

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState) {
                when (uiState) {
                    is LoginUiState.Success -> {
                        startActivity(Intent(this@LoginActivity, MenuActivity::class.java))
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
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
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

