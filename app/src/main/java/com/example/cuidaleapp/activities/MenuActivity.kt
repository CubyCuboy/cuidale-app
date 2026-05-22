package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.ui.screens.MenuScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.MenuViewModel

class MenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MenuViewModel = viewModel()
            val isDark by viewModel.isDarkMode.collectAsState()
            val userData by viewModel.userData.collectAsState()
            val isLoggedOut by viewModel.isLoggedOut.collectAsState()

            if (isLoggedOut) {
                LaunchedEffect(Unit) {
                    val intent = Intent(this@MenuActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }

            CuidaleAppTheme(darkTheme = isDark) {
                MenuScreen(
                    userData = userData,
                    isDark = isDark,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onLogout = { viewModel.logout() },
                    onResetPassword = { viewModel.resetPassword(userData.correo) }
                )
            }
        }
    }
}
