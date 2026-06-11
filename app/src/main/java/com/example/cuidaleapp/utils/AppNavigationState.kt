package com.example.cuidaleapp.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppNavigationState {
    private val _currentScreen = MutableStateFlow<String>("MAIN")
    val currentScreen = _currentScreen.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    fun setScreen(screen: String) {
        _currentScreen.value = screen
    }

    fun setActiveChat(chatId: String?) {
        _activeChatId.value = chatId
    }
}
