package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.ui.screens.EmailVerificationScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.EmailVerificationViewModel
import com.example.cuidaleapp.viewmodel.LoginViewModel

class EmailVerificationActivity : ComponentActivity() {
    private val verificationViewModel: EmailVerificationViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialEmail = intent.getStringExtra("email") ?: ""

        setContent {
            val isDark by loginViewModel.isDarkMode.collectAsState()
            
            CuidaleAppTheme(darkTheme = isDark) {
                EmailVerificationScreen(
                    isDark = isDark,
                    initialEmail = initialEmail,
                    onNavigateBack = { finish() },
                    onVerificationSuccess = { finish() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }
}
