package com.example.cuidaleapp.utils

/**
 * Mensajes amigables para errores de las Cloud Functions de pagos.
 * Funciones puras (testeables en JVM); no dependen del runtime de Android.
 *
 * El ViewModel convierte [com.google.firebase.functions.FirebaseFunctionsException]
 * a (código, mensaje del servidor) y llama a estas funciones.
 */
object PagoErrorMessages {

    const val SALDO_INSUFICIENTE = "saldo insuficiente"
    const val YA_PAGADO = "ya fue pagado"

    /** Devuelve el mensaje amigable según el código de la Cloud Function. */
    fun mensaje(codigo: String?, serverMessage: String?): String {
        val msg = serverMessage?.trim().orEmpty()
        return when (codigo) {
            "unauthenticated" -> "Tu sesión expiró. Vuelve a iniciar sesión."
            "invalid-argument" -> msg.ifBlank { "Los datos enviados no son válidos." }
            "failed-precondition" -> when {
                esSaldoInsuficiente(codigo, msg) ->
                    "Saldo insuficiente en tu billetera. Recarga para completar el pago."
                esYaPagado(codigo, msg) -> "Este servicio ya fue pagado."
                else -> msg.ifBlank { "No se puede completar la operación en este momento." }
            }
            "already-exists" -> msg.ifBlank { "Esta operación ya fue realizada." }
            "permission-denied" -> msg.ifBlank { "No tienes permiso para realizar esta acción." }
            "not-found" -> msg.ifBlank { "No se encontró el elemento solicitado." }
            "unavailable" -> "Sin conexión con el servidor. Inténtalo nuevamente."
            "deadline-exceeded" -> "El servidor tardó demasiado. Inténtalo nuevamente."
            "internal" -> "Ocurrió un error inesperado. Inténtalo más tarde."
            else -> msg.ifBlank { "Ocurrió un error inesperado. Inténtalo más tarde." }
        }
    }

    fun esSaldoInsuficiente(codigo: String?, serverMessage: String?): Boolean =
        codigo == "failed-precondition" &&
            serverMessage?.contains(SALDO_INSUFICIENTE, ignoreCase = true) == true

    fun esYaPagado(codigo: String?, serverMessage: String?): Boolean =
        codigo == "failed-precondition" &&
            serverMessage?.contains(YA_PAGADO, ignoreCase = true) == true

    fun esCobroActivo(codigo: String?): Boolean = codigo == "already-exists"

    fun esConflicto(estado: String?): Boolean =
        estado == "en_conflicto" || estado == "en_disputa"
}
