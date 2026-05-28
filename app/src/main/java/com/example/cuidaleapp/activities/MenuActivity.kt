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
import com.example.cuidaleapp.ui.screens.ProfileScreen
import com.example.cuidaleapp.ui.screens.EditProfileScreen
import com.example.cuidaleapp.ui.screens.FichaScreen
import com.example.cuidaleapp.ui.screens.SearchProfileScreen
import com.example.cuidaleapp.ui.screens.SettingsScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.MenuViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.viewmodel.FichaViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.activities.MenuNavigation.*

sealed class MenuNavigation {
    object Main : MenuNavigation()
    object Profile : MenuNavigation()
    object EditProfile : MenuNavigation()
    object Ficha : MenuNavigation()
    object Search : MenuNavigation()
    object Settings : MenuNavigation()
    data class OtherProfile(val uid: String) : MenuNavigation()
}

class MenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            val menuViewModel: MenuViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()
            val fichaViewModel: FichaViewModel = viewModel()

            val isDark by menuViewModel.isDarkMode.collectAsState()
            val userData by menuViewModel.userData.collectAsState()
            val isLoggedOut by menuViewModel.isLoggedOut.collectAsState()
            
            var currentScreen by remember { mutableStateOf<MenuNavigation>(Main) }

            if (isLoggedOut) {
                LaunchedEffect(Unit) {
                    val intent = Intent(this@MenuActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }

            CuidaleAppTheme(darkTheme = isDark) {
                when (currentScreen) {
                    Main -> {
                        MenuScreen(
                            userData = userData,
                            isDark = isDark,
                            onToggleTheme = { menuViewModel.toggleTheme() },
                            onLogout = { menuViewModel.logout() },
                            onResetPassword = { menuViewModel.resetPassword(userData.correo) },
                            onViewProfile = { currentScreen = Profile },
                            onNavigateToFicha = { currentScreen = Ficha },
                            onNavigateToSearch = { currentScreen = Search }
                        )
                    }
                    Profile -> {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            isDark = isDark,
                            onEditProfile = { currentScreen = EditProfile },
                            onNavigateBack = {
                                menuViewModel.updateUserData() // Refrescar datos antes de volver
                                currentScreen = Main
                            },
                            onSettingsClick = { currentScreen = Settings }
                        )
                    }
                    Settings -> {
                        SettingsScreen(
                            viewModel = profileViewModel,
                            isDark = isDark,
                            onBack = { currentScreen = Profile },
                            onLogoutClick = { menuViewModel.logout() },
                            onResetPass = {
                                val intent = Intent(this@MenuActivity, RecoveryPasswordActivity::class.java)
                                intent.putExtra("FROM_PROFILE", true)
                                startActivity(intent)
                            }
                        )
                    }
                    EditProfile -> {
                        EditProfileScreen(
                            viewModel = profileViewModel,
                            isDark = isDark,
                            onNavigateBack = { currentScreen = Profile }
                        )
                    }
                    Ficha -> {
                        FichaScreen(
                            viewModel = fichaViewModel,
                            onBack = { currentScreen = Main }
                        )
                    }
                    Search -> {
                        SearchProfileScreen(
                            onNavigateBack = { currentScreen = Main },
                            onUserClick = { uid -> currentScreen = OtherProfile(uid) }
                        )
                    }

                    is OtherProfile -> {
                        val otherScreen = currentScreen as OtherProfile
                        LaunchedEffect(otherScreen.uid) {
                            profileViewModel.loadOtherUserProfile(otherScreen.uid)
                        }
                        ProfileScreen(
                            viewModel = profileViewModel,
                            isDark = isDark,
                            onEditProfile = { /* No-op in visitor view */ },
                            onNavigateBack = { currentScreen = Search },
                            onSettingsClick = { /* No-op */ }
                        )
                    }
                }
            }
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
