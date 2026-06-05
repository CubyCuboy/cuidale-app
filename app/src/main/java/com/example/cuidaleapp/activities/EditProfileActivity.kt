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
import com.example.cuidaleapp.ui.screens.EditProfileScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.ProfileViewModel

class EditProfileActivity : ComponentActivity() {
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()

            CuidaleAppTheme(darkTheme = isDark) {
                EditProfileScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
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
