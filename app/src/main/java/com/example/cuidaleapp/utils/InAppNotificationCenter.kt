package com.example.cuidaleapp.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class InAppNotification(
    val id: String = UUID.randomUUID().toString(),
    val tipo: String = "generica",
    val title: String = "",
    val body: String = "",
    val targetScreen: String = "MAIN",
    val payload: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

object InAppNotificationCenter {

    private val _events = MutableStateFlow<List<InAppNotification>>(emptyList())
    val events = _events.asStateFlow()

    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()

    private const val DEDUPE_WINDOW_MS = 12000L
    private val recentKeys = linkedMapOf<String, Long>()
    private val recentTypes = linkedMapOf<String, Long>()

    fun show(notification: InAppNotification) {
        val key = buildKey(notification)
        val now = System.currentTimeMillis()

        recentKeys.entries.removeAll { now - it.value > DEDUPE_WINDOW_MS }
        recentTypes.entries.removeAll { now - it.value > DEDUPE_WINDOW_MS }

        val isGeneric = notification.tipo == "generica" || NotificationTemplates.isGeneric(notification.title, notification.body)

        // Si llega una genérica y ya hubo (o hay) una específica del mismo tipo, se descarta.
        if (isGeneric) {
            val recentSpecificOfSameType = recentTypes[notification.tipo] != null || _events.value.any { it.tipo == notification.tipo }
            if (recentSpecificOfSameType) return
        }

        val alreadyShown = recentKeys.containsKey(key)
        val alreadyVisible = _events.value.any { it.id == notification.id || buildKey(it) == key }
        if (alreadyShown || alreadyVisible) return

        // Si llega una específica y hay una genérica del mismo tipo en cola, la específica la reemplaza.
        if (!isGeneric) {
            val hasPendingGenericOfSameType = _events.value.any { it.tipo == notification.tipo && NotificationTemplates.isGeneric(it.title, it.body) }
            if (hasPendingGenericOfSameType) {
                _events.value = _events.value.filterNot { it.tipo == notification.tipo && NotificationTemplates.isGeneric(it.title, it.body) }
            }
        }

        recentKeys[key] = now
        recentTypes[notification.tipo] = now
        _events.value = listOf(notification) + _events.value.filter { it.id != notification.id }
        _visible.value = true
    }

    fun show(
        tipo: String,
        title: String,
        body: String,
        targetScreen: String = "MAIN",
        payload: Map<String, String> = emptyMap()
    ) {
        show(InAppNotification(tipo = tipo, title = title, body = body, targetScreen = targetScreen, payload = payload))
    }

    fun dismiss(id: String) {
        _events.value = _events.value.filter { it.id != id }
        if (_events.value.isEmpty()) {
            _visible.value = false
        }
    }

    fun dismissAll() {
        _events.value = emptyList()
        _visible.value = false
        recentKeys.clear()
        recentTypes.clear()
    }

    private fun buildKey(notification: InAppNotification): String {
        val normalizedTitle = notification.title.trim().lowercase()
        val normalizedBody = notification.body.trim().lowercase()
        return "${notification.tipo}|$normalizedTitle|$normalizedBody"
    }
}
