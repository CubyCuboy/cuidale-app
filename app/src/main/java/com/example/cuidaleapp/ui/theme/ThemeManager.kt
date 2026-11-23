package com.example.cuidaleapp.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fuente única de verdad del tema (claro/oscuro) en toda la app.
 *
 * Todos los ViewModels (ProfileViewModel, LoginViewModel, MenuViewModel, ThemeViewModel)
 * delegan en este objeto, de modo que al alternar el tema desde cualquier pantalla,
 * TODAS las actividades activas (incluso las que están en el back stack) se
 * recomponen con el nuevo valor al reanudarse.
 */
object ThemeManager {
    private const val PREFS_NAME = "cuidale_prefs"
    private const val KEY_DARK = "dark_mode"

    private var prefs: android.content.SharedPreferences? = null
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun ensureInit(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isDarkMode.value = prefs?.getBoolean(KEY_DARK, false) ?: false
        }
    }

    fun setDark(dark: Boolean) {
        if (_isDarkMode.value != dark) {
            _isDarkMode.value = dark
            prefs?.edit()?.putBoolean(KEY_DARK, dark)?.apply()
        }
    }

    fun toggleTheme() {
        setDark(!_isDarkMode.value)
    }
}
