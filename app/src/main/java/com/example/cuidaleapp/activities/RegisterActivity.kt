package com.example.cuidaleapp.activities

import android.content.Intent
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
import com.example.cuidaleapp.ui.screens.RegisterScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.RegisterViewModel
import com.example.cuidaleapp.viewmodel.LoginViewModel

class RegisterActivity : ComponentActivity() {
    private val registerViewModel: RegisterViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isExternal = intent.getBooleanExtra("IS_EXTERNAL", false)
        val externalEmail = intent.getStringExtra("EXTERNAL_EMAIL") ?: ""
        val externalUid = intent.getStringExtra("EXTERNAL_UID") ?: ""
        val externalProvider = intent.getStringExtra("EXTERNAL_PROVIDER") ?: ""
        val externalDisplayName = intent.getStringExtra("EXTERNAL_DISPLAY_NAME") ?: ""

        if (isExternal) {
            registerViewModel.initExternal(externalEmail, externalUid, externalProvider, externalDisplayName)
        }

        setContent {
            val isDark by loginViewModel.isDarkMode.collectAsState()
            
            CuidaleAppTheme(darkTheme = isDark) {
                RegisterScreen(
                    viewModel = registerViewModel,
                    isExternal = isExternal,
                    onNavigateBack = { finish() },
                    onFinish = {
                        val intent = Intent(this@RegisterActivity, MenuActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }
}
