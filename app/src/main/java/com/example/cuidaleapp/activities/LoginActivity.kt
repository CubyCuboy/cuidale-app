package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.R
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

            LaunchedEffect(uiState) {
                if (uiState is LoginUiState.Success) {
                    val userData = viewModel.getCachedUser()
                    val role = userData.rol.lowercase().trim()

                    if (role == "cuidador" || role == "representante") {
                        startActivity(Intent(this@LoginActivity, MenuActivity::class.java))
                        finish()
                    } else if (role == "") {
                        Toast.makeText(this@LoginActivity, getString(R.string.error_no_role), Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    } else {
                        Toast.makeText(this@LoginActivity, getString(R.string.error_invalid_role, role), Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
            
            CuidaleAppTheme(darkTheme = isDark) {
                LoginScreen(
                    isDark = isDark,
                    onToggleTheme = { viewModel.toggleTheme() },
                    viewModel = viewModel,
                    onNavigateToRegister = {
                        startActivity(Intent(this@LoginActivity, RoleSelectionActivity::class.java))
                    },
                    onForgotPassword = {
                        startActivity(Intent(this@LoginActivity, ForgotPasswordActivity::class.java))
                    }
                )
            }
        }
    }
}
