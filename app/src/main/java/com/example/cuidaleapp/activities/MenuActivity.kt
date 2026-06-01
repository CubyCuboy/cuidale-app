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
    data class Ficha(val startInEditMode: Boolean = false, val returnTo: MenuNavigation = Profile) : MenuNavigation()
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
                            onNavigateToFicha = { currentScreen = Ficha(startInEditMode = false, returnTo = Main) },
                            onNavigateToSearch = { currentScreen = Search }
                        )
                    }
                    Profile -> {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            fichaViewModel = fichaViewModel,
                            isDark = isDark,
                            onEditProfile = { currentScreen = EditProfile },
                            onNavigateToFicha = { currentScreen = Ficha(startInEditMode = true, returnTo = Profile) },
                            onNavigateBack = {
                                menuViewModel.updateUserData()
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
                    is Ficha -> {
                        val fichaNav = currentScreen as Ficha
                        FichaScreen(
                            viewModel = fichaViewModel,
                            profileViewModel = profileViewModel,
                            startInEditMode = fichaNav.startInEditMode,
                            onNavigateBack = { 
                                currentScreen = fichaNav.returnTo
                            }
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
                            fichaViewModel = fichaViewModel,
                            isDark = isDark,
                            onEditProfile = { },
                            onNavigateToFicha = { },
                            onNavigateBack = { currentScreen = Search },
                            onSettingsClick = { }
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
