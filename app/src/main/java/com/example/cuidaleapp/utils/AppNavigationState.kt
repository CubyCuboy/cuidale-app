package com.example.cuidaleapp.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppNavigationState {
    private val _currentScreen = MutableStateFlow<String>("MAIN")
    val currentScreen = _currentScreen.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    private val _isAppVisible = MutableStateFlow(false)
    val isAppVisible = _isAppVisible.asStateFlow()

    private val _isScreenScrolled = MutableStateFlow(false)
    val isScreenScrolled = _isScreenScrolled.asStateFlow()

    private val _pagoEstado = MutableStateFlow<String?>(null)
    val pagoEstado = _pagoEstado.asStateFlow()

    fun setScreen(screen: String) {
        _currentScreen.value = screen
    }

    fun setActiveChat(chatId: String?) {
        _activeChatId.value = chatId
    }

    fun setAppVisible(visible: Boolean) {
        _isAppVisible.value = visible
    }

    fun setScreenScrolled(scrolled: Boolean) {
        _isScreenScrolled.value = scrolled
    }

    /** Estado del retorno del checkout (aprobado | pendiente | rechazado). */
    fun setPagoEstado(estado: String?) {
        _pagoEstado.value = estado
    }
}
