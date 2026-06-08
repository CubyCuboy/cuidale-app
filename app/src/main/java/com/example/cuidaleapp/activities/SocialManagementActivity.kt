package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.ui.screens.SocialManagementScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.ProfileViewModel

class SocialManagementActivity : ComponentActivity() {
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark by profileViewModel.isDarkMode.collectAsState()

            CuidaleAppTheme(darkTheme = isDark) {
                SocialManagementScreen(
                    onNavigateBack = { finish() },
                    onUserClick = { uid ->
                        val intent = Intent(this@SocialManagementActivity, MenuActivity::class.java).apply {
                            putExtra("TARGET_SCREEN", "OTHER_FICHA")
                            putExtra("USER_UID", uid)
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}
