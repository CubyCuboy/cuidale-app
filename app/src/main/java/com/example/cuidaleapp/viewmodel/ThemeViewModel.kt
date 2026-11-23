package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    init {
        com.example.cuidaleapp.ui.theme.ThemeManager.ensureInit(application)
    }

    val isDarkMode: StateFlow<Boolean> get() = com.example.cuidaleapp.ui.theme.ThemeManager.isDarkMode

    fun toggleTheme() {
        com.example.cuidaleapp.ui.theme.ThemeManager.toggleTheme()
    }
}
