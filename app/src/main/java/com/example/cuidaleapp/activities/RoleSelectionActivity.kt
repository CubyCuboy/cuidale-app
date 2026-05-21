package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.screens.PlaceholderScreen
import com.example.cuidaleapp.ui.screens.RoleSelectionScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme

class RoleSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDarkTheme) }

            CuidaleAppTheme(darkTheme = darkTheme) {
                RoleSelectionApp(isDark = darkTheme)
            }
        }
    }
}

@Composable
fun RoleSelectionApp(isDark: Boolean) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "role_selection") {
        composable("role_selection") {
            RoleSelectionScreen(
                isDark = isDark,
                onNavigateToCaregiver = {
                    navController.navigate("register_caregiver")
                },
                onNavigateToDependent = {
                    navController.navigate("register_dependent")
                }
            )
        }
        composable("register_caregiver") {
            PlaceholderScreen(title = stringResource(R.string.register_caregiver_soon))
        }
        composable("register_representative") {
            PlaceholderScreen(title = stringResource(R.string.register_dependent_soon))
        }
    }
}
