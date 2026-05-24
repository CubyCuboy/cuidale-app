package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.ui.screens.ProfileScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.viewmodel.ThemeViewModel

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()
            val isDark by themeViewModel.isDarkMode.collectAsState()

            CuidaleAppTheme(darkTheme = isDark) {
                ProfileScreen(
                    viewModel = profileViewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
