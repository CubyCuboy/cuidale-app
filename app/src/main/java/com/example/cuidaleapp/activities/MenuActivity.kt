package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.example.cuidaleapp.viewmodel.VinculosViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cuidaleapp.activities.MenuNavigation.*

sealed class MenuNavigation {
    object Main : MenuNavigation()
    object Profile : MenuNavigation()
    object EditProfile : MenuNavigation()
    data class Ficha(val startInEditMode: Boolean = false, val returnTo: MenuNavigation = Profile) : MenuNavigation()
    object Search : MenuNavigation()
    data class Settings(val returnTo: MenuNavigation = Profile) : MenuNavigation()
    object Chats : MenuNavigation()
    object Notifications : MenuNavigation()
    object SocialManagement : MenuNavigation()
    data class OtherFicha(val uid: String, val returnTo: MenuNavigation = Main) : MenuNavigation()
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
            val vinculosViewModel: VinculosViewModel = viewModel()

            val isDark by menuViewModel.isDarkMode.collectAsState()
            val userData by menuViewModel.userData.collectAsState()
            val isLoggedOut by menuViewModel.isLoggedOut.collectAsState()
            
            var currentScreen by remember { mutableStateOf<MenuNavigation>(Main) }

            LaunchedEffect(intent) {
                val target = intent.getStringExtra("TARGET_SCREEN")
                val uid = intent.getStringExtra("USER_UID")
                if (target == "OTHER_FICHA" && uid != null) {
                    currentScreen = OtherFicha(uid, returnTo = SocialManagement)
                }
            }

            // Handle system back button
            BackHandler(enabled = currentScreen != Main) {
                currentScreen = when (val cs = currentScreen) {
                    Profile -> Main
                    EditProfile -> Profile
                    is Ficha -> cs.returnTo
                    Search -> Main
                    is Settings -> cs.returnTo
                    Chats -> Main
                    Notifications -> Main
                    is OtherFicha -> cs.returnTo
                    SocialManagement -> Profile
                    Main -> Main
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
                when (val cs = currentScreen) {
                    Main -> {
                        MenuScreen(
                            userData = userData,
                            searchViewModel = searchViewModel,
                            isDark = isDark,
                            onToggleTheme = { menuViewModel.toggleTheme() },
                            onLogout = { menuViewModel.logout() },
                            onViewProfile = { currentScreen = Profile },
                            onViewFicha = { currentScreen = Ficha(startInEditMode = false, returnTo = Main) },
                            onViewSettings = { currentScreen = Settings(returnTo = Main) },
                            onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = Main) },
                            onTabSelected = { tab ->
                                currentScreen = when (tab) {
                                    "chats" -> Chats
                                    "notifications" -> Notifications
                                    else -> Main
                                }
                            }
                        )
                    }
                    Profile -> {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            fichaViewModel = fichaViewModel,
                            onNavigateBack = { currentScreen = Main },
                            onSettingsClick = { currentScreen = Settings() },
                            onSocialClick = {
                                currentScreen = SocialManagement
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
                        FichaScreen(
                            viewModel = fichaViewModel,
                            profileViewModel = profileViewModel,
                            startInEditMode = cs.startInEditMode,
                            onNavigateBack = { currentScreen = cs.returnTo }
                        )
                    }
                    Search -> {
                        SearchProfileScreen(
                            onNavigateBack = { currentScreen = Main },
                            onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = Search) }
                        )
                    }
                    is Settings -> {
                        SettingsScreen(
                            viewModel = profileViewModel,
                            isDark = isDark,
                            onBack = { currentScreen = cs.returnTo },
                            onLogoutClick = { menuViewModel.logout() },
                            onResetPass = { /* Handle reset pass */ }
                        )
                    }
                    Chats -> {
                        ChatsScreen(onTabSelected = { tab ->
                            currentScreen = when (tab) {
                                "notifications" -> Notifications
                                "home" -> Main
                                else -> Chats
                            }
                        })
                    }
                    Notifications -> {
                        NotificationsScreen(onTabSelected = { tab ->
                            currentScreen = when (tab) {
                                "chats" -> Chats
                                "home" -> Main
                                else -> Notifications
                            }
                        })
                    }
                    is OtherFicha -> {
                        LaunchedEffect(cs.uid) {
                            profileViewModel.loadOtherUserProfile(cs.uid)
                        }
                        OtherFichaScreen(
                            profileViewModel = profileViewModel,
                            vinculosViewModel = vinculosViewModel,
                            onNavigateBack = { currentScreen = cs.returnTo }
                        )
                    }
                    SocialManagement -> {
                        SocialManagementScreen(
                            onNavigateBack = { currentScreen = Profile },
                            onUserClick = { uid -> currentScreen = OtherFicha(uid, returnTo = SocialManagement) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
