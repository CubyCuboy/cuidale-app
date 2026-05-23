package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.screens.PlaceholderScreen
import com.example.cuidaleapp.ui.screens.RegisterScreen
import com.example.cuidaleapp.ui.screens.RoleSelectionScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.RegisterViewModel
import com.example.cuidaleapp.viewmodel.ThemeViewModel

class RoleSelectionActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by themeViewModel.isDarkMode.collectAsState()
            val registerViewModel: RegisterViewModel = viewModel()

            CuidaleAppTheme(darkTheme = isDark) {
                RoleSelectionApp(
                    isDark = isDark,
                    onToggleTheme = { themeViewModel.toggleTheme() },
                    registerViewModel = registerViewModel,
                    onBackToLogin = { finish() }
                )
            }
        }
    }
}

@Composable
fun RoleSelectionApp(
    isDark: Boolean,
    onToggleTheme: () -> Unit = {},
    registerViewModel: RegisterViewModel,
    onBackToLogin: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "role_selection") {
        composable("role_selection") {
            RoleSelectionScreen(
                isDark = isDark,
                onToggleTheme = onToggleTheme,
                onNavigateToCaregiver = {
                    registerViewModel.setRole("cuidador")
                    navController.navigate("register_form")
                },
                onNavigateToDependent = {
                    registerViewModel.setRole("representante")
                    navController.navigate("register_form")
                }
            )
        }
        composable("register_form") {
            RegisterScreen(
                viewModel = registerViewModel,
                onBackToLogin = onBackToLogin
            )
        }
    }
}
