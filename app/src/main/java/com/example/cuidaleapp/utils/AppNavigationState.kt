package com.example.cuidaleapp.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado del flujo de recarga con Mercado Pago (overlay de pantalla completa).
 * - Redirigiendo: todavía no se abrió el checkout; se puede cancelar.
 * - Procesando: el checkout ya se abrió; ya no se puede cancelar.
 * - Final: resultado de la transacción (aprobado | rechazado | pendiente | error).
 */
sealed class RecargaUi {
    object Redirigiendo : RecargaUi()
    object Procesando : RecargaUi()
    data class Final(val estado: String, val mensaje: String? = null) : RecargaUi()
}

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

    private val _recargaUi = MutableStateFlow<RecargaUi?>(null)
    val recargaUi = _recargaUi.asStateFlow()

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

    fun iniciarRecarga() {
        _recargaUi.value = RecargaUi.Redirigiendo
    }

    fun marcarProcesando() {
        _recargaUi.value = RecargaUi.Procesando
    }

    fun finalizarRecarga(estado: String, mensaje: String? = null) {
        _recargaUi.value = RecargaUi.Final(estado, mensaje)
    }

    fun limpiarRecarga() {
        _recargaUi.value = null
    }
}
