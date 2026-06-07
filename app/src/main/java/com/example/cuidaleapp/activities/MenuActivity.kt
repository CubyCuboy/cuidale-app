package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cuidaleapp.ui.screens.*
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.MenuViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.SearchViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.activities.MenuNavigation.*

sealed class MenuNavigation {
    object Main : MenuNavigation()
    object Profile : MenuNavigation()
    object EditProfile : MenuNavigation()
    data class Ficha(val startInEditMode: Boolean = false, val returnTo: MenuNavigation = Profile) : MenuNavigation()
    object Search : MenuNavigation()
    object Settings : MenuNavigation()
    data class OtherFicha(val uid: String) : MenuNavigation()
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
            val searchViewModel: SearchViewModel = viewModel()

            val isDark by menuViewModel.isDarkMode.collectAsState()
            val userData by menuViewModel.userData.collectAsState()
            val isLoggedOut by menuViewModel.isLoggedOut.collectAsState()
            
            var currentScreen by remember { mutableStateOf<MenuNavigation>(Main) }

            // Handle system back button
            BackHandler(enabled = currentScreen != Main) {
                currentScreen = when (currentScreen) {
                    Profile -> Main
                    EditProfile -> Profile
                    is Ficha -> (currentScreen as Ficha).returnTo
                    Search -> Main
                    Settings -> Profile
                    is OtherFicha -> Main
                    Main -> Main 
                }
            }

            BackHandler(enabled = currentScreen != Main) {
                currentScreen = when (currentScreen) {
                    Profile -> Main
                    EditProfile -> Profile
                    is Ficha -> (currentScreen as Ficha).returnTo
                    Search -> Main
                    Settings -> Profile
                    is OtherFicha -> Main
                    Main -> Main // Should not happen due to enabled condition
                }
            }

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
                            searchViewModel = searchViewModel,
                            fichaViewModel = fichaViewModel,
                            isDark = isDark,
                            onToggleTheme = { menuViewModel.toggleTheme() },
                            onLogout = { menuViewModel.logout() },
                            onViewProfile = { currentScreen = Profile },
                            onViewFicha = { currentScreen = Ficha(startInEditMode = false, returnTo = Main) },
                            onUserClick = { uid -> currentScreen = OtherFicha(uid) }
                        )
                    }
                    Profile -> {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            fichaViewModel = fichaViewModel,
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
                            onUserClick = { uid -> currentScreen = OtherFicha(uid) }
                        )
                    }

                    is OtherFicha -> {
                        val otherScreen = currentScreen as OtherFicha
                        LaunchedEffect(otherScreen.uid) {
                            profileViewModel.loadOtherUserProfile(otherScreen.uid)
                        }
                        OtherFichaScreen(
                            profileViewModel = profileViewModel,
                            onNavigateBack = { currentScreen = Main }
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
