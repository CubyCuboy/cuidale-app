package com.example.cuidaleapp.models

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@Immutable
@IgnoreExtraProperties
data class UserData(
    val uid: String = "",
    val nombre: String = "",
    val nombre_s: String = "",
    val apellido_pa: String = "",
    val apellido_ma: String = "",
    val comuna: String = "",
    val correo: String = "",
    val fotoUrl: String = "",
    val id_region: String = "",
    val region: String = "",
    val rol: String = "",
    val rut: String = "",
    val telefono: String = "",
    val telefonoVerificado: Boolean = false,
    val verificado: Boolean = false,
    val completado: Boolean = false,
    val descripcion: String = "",
    val favoritos: List<String> = emptyList(),
    val no_deseados: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val fcmToken: String = "",
    val guardados: List<String> = emptyList(),
    val bloqueados: List<String> = emptyList(),
    val linkedAccounts: List<LinkedAccountDetail> = emptyList(),
    var accountHistory: List<AccountHistoryEntry> = emptyList(),
    val rateLimits: List<RateLimitEntry> = emptyList()
) {
    fun findLinkedAccount(providerId: String): LinkedAccountDetail? {
        val cleanId = com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(providerId)
        return linkedAccounts.firstOrNull { it.providerId == cleanId }
    }
    fun hasLinkedAccount(providerId: String): Boolean = findLinkedAccount(providerId) != null
}
