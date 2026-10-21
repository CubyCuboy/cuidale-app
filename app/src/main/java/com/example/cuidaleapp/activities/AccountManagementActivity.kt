package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.ui.screens.AccountManagementScreen
import com.example.cuidaleapp.ui.components.CuidaleAppRoot
import com.example.cuidaleapp.viewmodel.ProfileViewModel

class AccountManagementActivity : ComponentActivity() {
    private val viewModel: ProfileViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.reload()?.addOnCompleteListener {
            viewModel.refreshFromFirestore()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()

            CuidaleAppRoot(darkTheme = isDark) {
                AccountManagementScreen(
                    viewModel = viewModel,
                    isDark = isDark,
                    onBack = { finish() }
                )
            }
        }
    }
}
