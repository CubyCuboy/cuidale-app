package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.ui.screens.ProfileScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel

class ProfileActivity : ComponentActivity() {
    private val viewModel: ProfileViewModel by viewModels()
    private val fichaViewModel: FichaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()

            CuidaleAppTheme(darkTheme = isDark) {
                ProfileScreen(
                    viewModel = viewModel,
                    fichaViewModel = fichaViewModel,
                    onNavigateBack = { finish() },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onSocialClick = {
                        startActivity(Intent(this, SocialManagementActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUserData()
        fichaViewModel.checkProfile()
    }
}
