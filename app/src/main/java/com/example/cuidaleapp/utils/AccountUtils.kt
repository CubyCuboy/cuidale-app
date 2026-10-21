package com.example.cuidaleapp.utils

import android.util.Patterns
import com.example.cuidaleapp.models.LinkedAccountDetail

object AccountUtils {

    fun validateEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return false
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) return false
        val parts = trimmed.split("@")
        if (parts.size != 2) return false
        val domain = parts[1]
        return domain.contains(".") && domain.substringAfterLast(".").length >= 2
    }

    fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "La contraseña debe tener al menos 8 caracteres"
        }
        if (!password.any { it.isUpperCase() }) {
            return "La contraseña debe incluir al menos una letra mayúscula"
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            return "La contraseña debe incluir al menos un carácter especial (ej. @, #, $)"
        }
        return null
    }

    fun formatRut(rut: String): String {
        val clean = rut.replace(".", "").replace("-", "").replace(" ", "").uppercase()
        if (clean.length < 2) return clean
        val body = clean.substring(0, clean.length - 1)
        val dv = clean.substring(clean.length - 1)
        return "$body-$dv"
    }

    fun validateRut(rut: String): Boolean {
        val cleanRut = rut.replace(".", "").replace("-", "").replace(" ", "").uppercase()
        if (cleanRut.length < 7 || cleanRut.length > 9) return false
        val body = cleanRut.substring(0, cleanRut.length - 1)
        val dv = cleanRut.substring(cleanRut.length - 1)

        return try {
            val mBody = body.toInt()
            calculateDV(mBody) == dv
        } catch (_: Exception) {
            false
        }
    }

    private fun calculateDV(rut: Int): String {
        var mRut = rut
        var m = 0
        var s = 1
        while (mRut != 0) {
            s = (s + mRut % 10 * (9 - m++ % 6)) % 11
            mRut /= 10
        }
        return if (s != 0) (s + 47).toChar().toString() else "K"
    }

    fun parseAccountError(e: Exception, context: android.content.Context): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        val clean = when {
            msg.contains("requires-recent-login", ignoreCase = true) ||
            msg.contains("recent authentication", ignoreCase = true) ->
                context.getString(com.example.cuidaleapp.R.string.error_recent_login)

            msg.contains("email-already-in-use", ignoreCase = true) ||
            msg.contains("already in use", ignoreCase = true) ||
            msg.contains("collision", ignoreCase = true) ->
                context.getString(com.example.cuidaleapp.R.string.error_email_in_use)

            msg.contains("weak-password", ignoreCase = true) ->
                context.getString(com.example.cuidaleapp.R.string.error_weak_password)

            msg.contains("invalid-credential", ignoreCase = true) ||
            msg.contains("wrong-password", ignoreCase = true) ||
            msg.contains("user-not-found", ignoreCase = true) ->
                context.getString(com.example.cuidaleapp.R.string.error_credentials)

            else -> ErrorMessages.get(context, msg)
        }
        return clean
    }

    fun normalizeProviderId(providerId: String): String {
        return when (providerId.trim().lowercase()) {
            "google.com", "google" -> "google.com"
            "microsoft.com", "microsoft" -> "microsoft.com"
            "password", "email" -> "email"
            else -> providerId.trim()
        }
    }

    fun providerDisplayName(providerId: String): String {
        return when (normalizeProviderId(providerId)) {
            "google.com" -> "Google"
            "microsoft.com" -> "Microsoft"
            "email" -> "Correo y Contraseña"
            else -> providerId
        }
    }

    fun mergeLinkedAccount(
        currentList: List<LinkedAccountDetail>,
        providerId: String,
        providerName: String,
        accountName: String,
        verificado: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): List<LinkedAccountDetail> {
        val cleanProviderId = normalizeProviderId(providerId)
        val cleanProviderName = if (providerName.isNotBlank()) providerName else providerDisplayName(cleanProviderId)
        val existing = currentList.find { it.providerId == cleanProviderId }
        val newEntry = LinkedAccountDetail(
            providerId = cleanProviderId,
            providerName = cleanProviderName,
            accountName = accountName.ifBlank { existing?.accountName ?: "" },
            linkedAt = if (existing != null && existing.linkedAt > 0) existing.linkedAt else timestamp,
            verificado = verificado || (existing?.verificado == true)
        )
        return (currentList.filter { it.providerId != cleanProviderId } + newEntry).sortedBy { it.linkedAt }
    }

    fun removeLinkedAccount(
        currentList: List<LinkedAccountDetail>,
        providerId: String
    ): List<LinkedAccountDetail> {
        val cleanProviderId = normalizeProviderId(providerId)
        return currentList.filter { it.providerId != cleanProviderId }
    }

    fun migrateLegacyToLinkedAccounts(
        currentList: List<LinkedAccountDetail>,
        rawTimestamps: Map<String, Long>,
        fallbackEmail: String,
        defaultVerified: Boolean
    ): List<LinkedAccountDetail> {
        var result = currentList
        rawTimestamps.entries
            .sortedBy { it.value }
            .forEach { (key, ts) ->
                val providerId = when (key.trim().lowercase()) {
                    "google", "google.com" -> "google.com"
                    "microsoft", "microsoft.com" -> "microsoft.com"
                    "password", "email" -> "email"
                    else -> key.replace("_", ".")
                }
                if (result.none { it.providerId == providerId }) {
                    val verified = when (providerId) {
                        "google.com", "microsoft.com" -> true
                        else -> defaultVerified
                    }
                    result = mergeLinkedAccount(result, providerId, "", fallbackEmail, verified, ts)
                }
            }
        return result.sortedBy { it.linkedAt }
    }
}
