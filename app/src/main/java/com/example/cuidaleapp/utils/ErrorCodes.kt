package com.example.cuidaleapp.utils

/**
 * Códigos de error centralizados de CuidaleApp.
 *
 * Cada dominio usa un prefijo propio y códigos estables de 3 dígitos.
 * El 000 de cada dominio es el código genérico de ese dominio.
 *
 * Uso en UI: `context.getString(ErrorCodes.resolveMessage(code))`
 * Uso en logs: `Log.e(TAG, "${ErrorCodes.Auth.INVALID_CREDENTIALS} - ...")`
 */
object ErrorCodes {

    /** Errores de autenticación (Firebase Auth). */
    object Auth {
        const val GENERIC = "AUTH_000"
        const val EMAIL_IN_USE = "AUTH_001"
        const val INVALID_CREDENTIALS = "AUTH_002"
        const val WEAK_PASSWORD = "AUTH_003"
        const val USER_DISABLED = "AUTH_004"
        const val TOO_MANY_REQUESTS = "AUTH_005"
        const val EMAIL_NOT_VERIFIED = "AUTH_006"
        const val SESSION_EXPIRED = "AUTH_007"
        const val PERMISSION_DENIED = "AUTH_008"
        const val RUT_ALREADY_REGISTERED = "AUTH_009"
        const val INVALID_RUT = "AUTH_010"
        const val CODE_INVALID = "AUTH_011"
        const val CODE_EXPIRED = "AUTH_012"
        const val USER_NOT_FOUND = "AUTH_013"
    }

    /** Errores de base de datos (Cloud Firestore). */
    object Database {
        const val GENERIC = "DB_000"
        const val NOT_FOUND = "DB_001"
        const val PERMISSION_DENIED = "DB_002"
        const val OFFLINE = "DB_003"
        const val TIMEOUT = "DB_004"
        const val CONFLICT = "DB_005"
        const val INVALID_DATA = "DB_006"
    }

    /** Errores de almacenamiento (Firebase Storage). */
    object Storage {
        const val GENERIC = "STORAGE_000"
        const val UPLOAD_FAILED = "STORAGE_001"
        const val DOWNLOAD_FAILED = "STORAGE_002"
        const val FILE_TOO_LARGE = "STORAGE_003"
        const val INVALID_FORMAT = "STORAGE_004"
        const val FILE_NOT_FOUND = "STORAGE_005"
        const val PERMISSION_DENIED = "STORAGE_006"
    }

    /** Errores de red / conectividad. */
    object Network {
        const val GENERIC = "NET_000"
        const val OFFLINE = "NET_001"
        const val TIMEOUT = "NET_002"
        const val SERVER_ERROR = "NET_003"
    }

    /** Errores de validación de entrada del usuario. */
    object Validation {
        const val GENERIC = "VAL_000"
        const val REQUIRED_FIELD = "VAL_001"
        const val INVALID_EMAIL = "VAL_002"
        const val INVALID_PHONE = "VAL_003"
        const val INVALID_RUT = "VAL_004"
        const val SHORT_PASSWORD = "VAL_005"
        const val PASSWORD_MISMATCH = "VAL_006"
        const val INVALID_DATE = "VAL_007"
        const val MIN_AGE = "VAL_008"
        const val MAX_LENGTH = "VAL_009"
        const val INVALID_FILE = "VAL_010"
    }

    /** Errores de servicios / solicitudes / vínculos. */
    object Service {
        const val GENERIC = "SVC_000"
        const val SOLICITUD_NOT_FOUND = "SVC_001"
        const val POSTULACION_FAILED = "SVC_002"
        const val VINCULO_EXISTS = "SVC_003"
        const val VINCULO_NOT_FOUND = "SVC_004"
        const val TAREA_NOT_FOUND = "SVC_005"
        const val TAREA_ALREADY_COMPLETED = "SVC_006"
        const val CHAT_NOT_FOUND = "SVC_007"
        const val SELF_REQUEST = "SVC_008"
    }

    /** Errores de pagos / billetera / cobros. */
    object Payments {
        const val GENERIC = "PAY_000"
        const val SALDO_INSUFICIENTE = "PAY_001"
        const val CONFLICTO = "PAY_002"
        const val DUPLICADO = "PAY_003"
        const val NOT_FOUND = "PAY_004"
        const val PERMISSION_DENIED = "PAY_005"
        const val ACUERDO_PENDIENTE = "PAY_006"
    }

    /** Errores inesperados / desconocidos. */
    object Unknown {
        const val GENERIC = "UNKNOWN_000"
        const val CONVERSION_FAILED = "UNKNOWN_001"
        const val ILLEGAL_STATE = "UNKNOWN_002"
    }

    /**
     * Devuelve el código genérico de un dominio.
     * Útil para no exponer nunca un código inexistente.
     */
    fun genericFor(code: String): String = when {
        code.startsWith("AUTH_") -> Auth.GENERIC
        code.startsWith("DB_") -> Database.GENERIC
        code.startsWith("STORAGE_") -> Storage.GENERIC
        code.startsWith("NET_") -> Network.GENERIC
        code.startsWith("VAL_") -> Validation.GENERIC
        code.startsWith("SVC_") -> Service.GENERIC
        else -> Unknown.GENERIC
    }

    /**
     * Normaliza un error a un código conocido.
     * Si no se puede clasificar, devuelve UNKNOWN_000.
     */
    fun fromException(e: Throwable?): String {
        val message = e?.message?.lowercase() ?: return Unknown.GENERIC
        return when {
            message.contains("network") || message.contains("internet") ||
                message.contains("offline") -> Network.OFFLINE
            message.contains("timeout") -> Network.TIMEOUT
            message.contains("permission denied") ||
                message.contains("insufficient permissions") -> Database.PERMISSION_DENIED
            message.contains("not found") || message.contains("no document") -> Database.NOT_FOUND
            message.contains("email already in use") -> Auth.EMAIL_IN_USE
            message.contains("weak password") -> Auth.WEAK_PASSWORD
            message.contains("user disabled") -> Auth.USER_DISABLED
            message.contains("too many requests") -> Auth.TOO_MANY_REQUESTS
            message.contains("invalid credential") || message.contains("wrong password") ||
                message.contains("user not found") -> Auth.INVALID_CREDENTIALS
            message.contains("storage") && message.contains("file") -> Storage.GENERIC
            else -> Unknown.GENERIC
        }
    }
}
