package com.example.cuidaleapp.utils

import android.content.Context
import com.example.cuidaleapp.R

object ErrorMessages {

    fun get(context: Context, message: String): String {
        val msg = message.lowercase()
        return when {
            msg.contains("network-request-failed") ||
            msg.contains("network") ||
            msg.contains("unavailable") ||
            msg.contains("host") ||
            msg.contains("failed to connect") ->
                context.getString(R.string.error_network)

            msg.contains("permission-denied") ||
            msg.contains("permission_denied") ||
            msg.contains("missing or insufficient permissions") ->
                context.getString(R.string.error_permissions)

            msg.contains("requires-recent-login") ||
            msg.contains("recent authentication") ->
                context.getString(R.string.error_recent_login)

            msg.contains("email-already-in-use") ||
            msg.contains("already in use") ||
            msg.contains("collision") ->
                context.getString(R.string.error_email_in_use)

            msg.contains("weak-password") ->
                context.getString(R.string.error_weak_password)

            msg.contains("invalid-credential") ||
            msg.contains("wrong-password") ||
            msg.contains("user-not-found") ||
            msg.contains("credenciales incorrectas") ->
                context.getString(R.string.error_credentials)

            msg.contains("expirado") ||
            msg.contains("expired") ->
                context.getString(R.string.error_code_expired)

            msg.contains("código incorrecto") ||
            msg.contains("code incorrect") ->
                context.getString(R.string.error_code_incorrect)

            msg.contains("máximo de intentos") ||
            msg.contains("agotado") ||
            msg.contains("resource-exhausted") ->
                context.getString(R.string.error_attempts_exhausted)

            msg.contains("correo electrónico no válido") ||
            msg.contains("invalid email") ->
                context.getString(R.string.error_invalid_email)

            else -> if (msg.isNotBlank()) message else context.getString(R.string.error_generic)
        }
    }
}
