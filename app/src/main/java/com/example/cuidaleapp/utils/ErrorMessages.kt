package com.example.cuidaleapp.utils

import android.content.Context
import com.example.cuidaleapp.R

/**
 * Resuelve un código de error (ver [ErrorCodes]) a un recurso de string.
 */
object ErrorMessages {

    private val MAP: Map<String, Int> = mapOf(
        // Auth
        ErrorCodes.Auth.GENERIC to R.string.error_auth_generic,
        ErrorCodes.Auth.EMAIL_IN_USE to R.string.error_auth_email_in_use,
        ErrorCodes.Auth.INVALID_CREDENTIALS to R.string.error_auth_invalid_credentials,
        ErrorCodes.Auth.WEAK_PASSWORD to R.string.error_auth_weak_password,
        ErrorCodes.Auth.USER_DISABLED to R.string.error_auth_user_disabled,
        ErrorCodes.Auth.TOO_MANY_REQUESTS to R.string.error_auth_too_many_requests,
        ErrorCodes.Auth.EMAIL_NOT_VERIFIED to R.string.error_auth_email_not_verified,
        ErrorCodes.Auth.SESSION_EXPIRED to R.string.error_auth_session_expired,
        ErrorCodes.Auth.PERMISSION_DENIED to R.string.error_auth_permission_denied,
        ErrorCodes.Auth.RUT_ALREADY_REGISTERED to R.string.error_auth_rut_already_registered,
        ErrorCodes.Auth.INVALID_RUT to R.string.error_auth_invalid_rut,
        ErrorCodes.Auth.CODE_INVALID to R.string.error_auth_code_invalid,
        ErrorCodes.Auth.CODE_EXPIRED to R.string.error_auth_code_expired,
        ErrorCodes.Auth.USER_NOT_FOUND to R.string.error_auth_user_not_found,

        // Database
        ErrorCodes.Database.GENERIC to R.string.error_db_generic,
        ErrorCodes.Database.NOT_FOUND to R.string.error_db_not_found,
        ErrorCodes.Database.PERMISSION_DENIED to R.string.error_db_permission_denied,
        ErrorCodes.Database.OFFLINE to R.string.error_db_offline,
        ErrorCodes.Database.TIMEOUT to R.string.error_db_timeout,
        ErrorCodes.Database.CONFLICT to R.string.error_db_conflict,
        ErrorCodes.Database.INVALID_DATA to R.string.error_db_invalid_data,

        // Storage
        ErrorCodes.Storage.GENERIC to R.string.error_storage_generic,
        ErrorCodes.Storage.UPLOAD_FAILED to R.string.error_storage_upload_failed,
        ErrorCodes.Storage.DOWNLOAD_FAILED to R.string.error_storage_download_failed,
        ErrorCodes.Storage.FILE_TOO_LARGE to R.string.error_storage_file_too_large,
        ErrorCodes.Storage.INVALID_FORMAT to R.string.error_storage_invalid_format,
        ErrorCodes.Storage.FILE_NOT_FOUND to R.string.error_storage_file_not_found,
        ErrorCodes.Storage.PERMISSION_DENIED to R.string.error_storage_permission_denied,

        // Network
        ErrorCodes.Network.GENERIC to R.string.error_net_generic,
        ErrorCodes.Network.OFFLINE to R.string.error_net_offline,
        ErrorCodes.Network.TIMEOUT to R.string.error_net_timeout,
        ErrorCodes.Network.SERVER_ERROR to R.string.error_net_server_error,

        // Validation
        ErrorCodes.Validation.GENERIC to R.string.error_val_generic,
        ErrorCodes.Validation.REQUIRED_FIELD to R.string.error_val_required_field,
        ErrorCodes.Validation.INVALID_EMAIL to R.string.error_val_invalid_email,
        ErrorCodes.Validation.INVALID_PHONE to R.string.error_val_invalid_phone,
        ErrorCodes.Validation.INVALID_RUT to R.string.error_val_invalid_rut,
        ErrorCodes.Validation.SHORT_PASSWORD to R.string.error_val_short_password,
        ErrorCodes.Validation.PASSWORD_MISMATCH to R.string.error_val_password_mismatch,
        ErrorCodes.Validation.INVALID_DATE to R.string.error_val_invalid_date,
        ErrorCodes.Validation.MIN_AGE to R.string.error_val_min_age,
        ErrorCodes.Validation.MAX_LENGTH to R.string.error_val_max_length,
        ErrorCodes.Validation.INVALID_FILE to R.string.error_val_invalid_file,

        // Service
        ErrorCodes.Service.GENERIC to R.string.error_svc_generic,
        ErrorCodes.Service.SOLICITUD_NOT_FOUND to R.string.error_svc_solicitud_not_found,
        ErrorCodes.Service.POSTULACION_FAILED to R.string.error_svc_postulacion_failed,
        ErrorCodes.Service.VINCULO_EXISTS to R.string.error_svc_vinculo_exists,
        ErrorCodes.Service.VINCULO_NOT_FOUND to R.string.error_svc_vinculo_not_found,
        ErrorCodes.Service.TAREA_NOT_FOUND to R.string.error_svc_tarea_not_found,
        ErrorCodes.Service.TAREA_ALREADY_COMPLETED to R.string.error_svc_tarea_already_completed,
        ErrorCodes.Service.CHAT_NOT_FOUND to R.string.error_svc_chat_not_found,
        ErrorCodes.Service.SELF_REQUEST to R.string.error_svc_self_request,

        // Payments
        ErrorCodes.Payments.GENERIC to R.string.error_pay_generic,
        ErrorCodes.Payments.SALDO_INSUFICIENTE to R.string.error_pay_saldo_insuficiente,
        ErrorCodes.Payments.CONFLICTO to R.string.error_pay_conflicto,
        ErrorCodes.Payments.DUPLICADO to R.string.error_pay_duplicado,
        ErrorCodes.Payments.NOT_FOUND to R.string.error_pay_no_encontrado,
        ErrorCodes.Payments.PERMISSION_DENIED to R.string.error_pay_permiso,
        ErrorCodes.Payments.ACUERDO_PENDIENTE to R.string.error_pay_acuerdo_pendiente,

        // Unknown
        ErrorCodes.Unknown.GENERIC to R.string.error_unknown_generic,
        ErrorCodes.Unknown.CONVERSION_FAILED to R.string.error_unknown_conversion_failed,
        ErrorCodes.Unknown.ILLEGAL_STATE to R.string.error_unknown_illegal_state
    )

    /** Devuelve el string resuelto para un código. */
    fun resolve(context: Context, code: String): String {
        val resId = MAP[code] ?: MAP[ErrorCodes.genericFor(code)] ?: R.string.error_unknown_generic
        return context.getString(resId)
    }

    /** Devuelve el string resuelto a partir de una excepción. */
    fun fromException(context: Context, e: Throwable?): String =
        resolve(context, ErrorCodes.fromException(e))

    /**
     * Devuelve un string de error legible a partir de un mensaje crudo.
     * Usado como fallback genérico en capturadores de errores existentes.
     */
    fun get(context: Context, message: String?): String {
        if (message.isNullOrBlank()) return resolve(context, ErrorCodes.Unknown.GENERIC)
        return when {
            message.contains("offline", ignoreCase = true) || message.contains("network", ignoreCase = true) ||
                message.contains("internet", ignoreCase = true) -> resolve(context, ErrorCodes.Network.OFFLINE)
            message.contains("timeout", ignoreCase = true) -> resolve(context, ErrorCodes.Network.TIMEOUT)
            message.contains("no se pudo enviar", ignoreCase = true) ||
                message.contains("enviar el correo", ignoreCase = true) -> resolve(context, ErrorCodes.Network.SERVER_ERROR)
            message.contains("no registrado", ignoreCase = true) ||
                message.contains("user not found", ignoreCase = true) -> resolve(context, ErrorCodes.Auth.USER_NOT_FOUND)
            message.contains("no solicitado", ignoreCase = true) -> resolve(context, ErrorCodes.Auth.CODE_INVALID)
            message.contains("incorrecto", ignoreCase = true) &&
                (message.contains("código", ignoreCase = true) || message.contains("codigo", ignoreCase = true)) ->
                resolve(context, ErrorCodes.Auth.CODE_INVALID)
            message.contains("expirado", ignoreCase = true) || message.contains("caducado", ignoreCase = true) ||
                message.contains("vencido", ignoreCase = true) -> resolve(context, ErrorCodes.Auth.CODE_EXPIRED)
            message.contains("máximo de intentos", ignoreCase = true) ||
                message.contains("maximo de intentos", ignoreCase = true) -> resolve(context, ErrorCodes.Auth.TOO_MANY_REQUESTS)
            message.contains("correo electrónico no válido", ignoreCase = true) ||
                message.contains("email inválido", ignoreCase = true) -> resolve(context, ErrorCodes.Validation.INVALID_EMAIL)
            message.contains("permission denied", ignoreCase = true) ||
                message.contains("insufficient permissions", ignoreCase = true) -> resolve(context, ErrorCodes.Database.PERMISSION_DENIED)
            message.contains("not found", ignoreCase = true) -> resolve(context, ErrorCodes.Database.NOT_FOUND)
            message.contains("email already in use", ignoreCase = true) -> resolve(context, ErrorCodes.Auth.EMAIL_IN_USE)
            message.contains("weak password", ignoreCase = true) -> resolve(context, ErrorCodes.Auth.WEAK_PASSWORD)
            message.contains("invalid credential", ignoreCase = true) || message.contains("wrong password", ignoreCase = true) ->
                resolve(context, ErrorCodes.Auth.INVALID_CREDENTIALS)
            message.contains("too many requests", ignoreCase = true) -> resolve(context, ErrorCodes.Auth.TOO_MANY_REQUESTS)
            message.contains("storage", ignoreCase = true) -> resolve(context, ErrorCodes.Storage.GENERIC)
            message.contains("debes esperar", ignoreCase = true) ||
                message.contains("esperar un momento", ignoreCase = true) ||
                message.contains("límite de", ignoreCase = true) ||
                message.contains("limite de", ignoreCase = true) ||
                message.contains("demasiadas solicitudes", ignoreCase = true) ->
                resolve(context, ErrorCodes.Auth.TOO_MANY_REQUESTS)
            else -> resolve(context, ErrorCodes.Unknown.GENERIC)
        }
    }
}
