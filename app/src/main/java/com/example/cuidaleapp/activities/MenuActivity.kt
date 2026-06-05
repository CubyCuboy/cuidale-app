package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.ui.screens.MenuScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.MenuViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MenuActivity : ComponentActivity() {
    private lateinit var menuViewModel: MenuViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            menuViewModel = viewModel()
            
            LaunchedEffect(Unit) {
                menuViewModel.updateUserData()
            }

            val isDark by menuViewModel.isDarkMode.collectAsState()
            val userData by menuViewModel.userData.collectAsState()
            val isLoggedOut by menuViewModel.isLoggedOut.collectAsState()

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
                    onToggleTheme = { menuViewModel.toggleTheme() },
                    onLogout = { menuViewModel.logout() },
                    onResetPassword = { menuViewModel.resetPassword(userData.correo) },
                    onViewProfile = {
                        startActivity(Intent(this@MenuActivity, ProfileActivity::class.java))
                    },
                    onNavigateToFicha = {
                        startActivity(Intent(this@MenuActivity, FichaActivity::class.java))
                    },
                    onNavigateToSearch = { /* TODO: Implement search if needed */ }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::menuViewModel.isInitialized) {
            menuViewModel.updateUserData()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}
