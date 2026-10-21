package com.example.cuidaleapp.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cuidaleapp.ui.screens.AccountHistoryScreen
import com.example.cuidaleapp.ui.components.CuidaleAppRoot
import com.example.cuidaleapp.viewmodel.ProfileViewModel

class AccountHistoryActivity : ComponentActivity() {
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            val userData by viewModel.userData.collectAsState()
            val historyList by viewModel.accountHistory.collectAsState()

            CuidaleAppRoot(darkTheme = isDark) {
                AccountHistoryScreen(
                    userData = userData,
                    historyList = historyList.ifEmpty { userData.accountHistory },
                    isDark = isDark,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
