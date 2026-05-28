package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.ui.screens.RecoveryPasswordScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme

import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import com.example.cuidaleapp.viewmodel.RecoveryViewModel
import com.example.cuidaleapp.viewmodel.ThemeViewModel

class RecoveryPasswordActivity : ComponentActivity() {
    
    private val recoveryViewModel: RecoveryViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            val isDark by themeViewModel.isDarkMode.collectAsState()
            val fromProfile = intent.getBooleanExtra("FROM_PROFILE", false)
            
            CuidaleAppTheme(darkTheme = isDark) {
                RecoveryPasswordScreen(
                    viewModel = recoveryViewModel,
                    onNavigateBack = { finish() },
                    onFinish = { finish() },
                    fromProfile = fromProfile
                )
            }
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}
