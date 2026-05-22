package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.ui.screens.ForgotPasswordScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.ForgotPasswordViewModel

class ForgotPasswordActivity : ComponentActivity() {
    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()

            CuidaleAppTheme(darkTheme = isDark) {
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onBackToLogin = { finish() }
                )
            }
        }
    }
}
