package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.ui.screens.AddEmailPasswordScreen
import com.example.cuidaleapp.ui.theme.CuidaleAppTheme
import com.example.cuidaleapp.viewmodel.AddEmailPasswordViewModel
import com.example.cuidaleapp.viewmodel.LoginViewModel

class AddEmailPasswordActivity : ComponentActivity() {
    private val viewModel: AddEmailPasswordViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark by loginViewModel.isDarkMode.collectAsState()

            CuidaleAppTheme(darkTheme = isDark) {
                AddEmailPasswordScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() },
                    onSuccess = { finish() }
                )
            }
        }
    }
}
