package com.example.cuidaleapp.utils

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Resultado de resolver una cuenta a partir de un RUT.
 */
data class ResolvedAccount(
    val email: String,
    val hasPassword: Boolean,
    val verified: Boolean,
    val rutKey: String = ""
)

/**
 * Recarga iniciada: id del movimiento creado por el backend y URL del
 * Checkout Pro (sandbox en modo prueba).
 */
data class RecargaIniciada(
    val recargaId: String,
    val url: String
)

/**
 * Retiro iniciado: estado inicial y datos del payout creado en Mercado Pago.
 */
data class RetiroIniciado(
    val estado: String,
    val movimientoId: String,
    val payoutId: String? = null,
    val comision: Long = 0L
)

/**
 * Envoltorio de las Cloud Functions usadas por la app.
 * Las functions viven en la región southamerica-west1.
 */
object FirebaseFunctionsApi {

    private val functions = FirebaseFunctions.getInstance("southamerica-west1")

    /**
     * Resuelve la cuenta asociada a un RUT usando una Cloud Function.
     * El RUT se valida y hashea en el servidor (HMAC con secreto), por lo que
     * el cliente nunca calcula ni expone el hash de identificación.
     */
    suspend fun resolveAccountByRut(rut: String): ResolvedAccount? {
        val result = functions.getHttpsCallable("resolveAccountByRut")
            .call(mapOf("rut" to rut))
            .await()
        val data = result.data as? Map<*, *> ?: return null
        if (data["ok"] != true) return null
        return ResolvedAccount(
            email = data["email"] as? String ?: "",
            hasPassword = data["hasPassword"] as? Boolean ?: false,
            verified = data["verified"] as? Boolean ?: false,
            rutKey = data["rutKey"] as? String ?: ""
        )
    }

    /**
     * Inicia el cobro del turno de un contrato (Cloud Function `iniciarCobroTurno`).
     * La función crea el turno y el cobro borrador; la app solo lee.
     */
    suspend fun iniciarCobroTurno(
        solicitudId: String,
        horas: Double,
        comentario: String
    ): Result<Map<String, String>> {
        return try {
            val result = functions.getHttpsCallable("iniciarCobroTurno")
                .call(mapOf(
                    "solicitudId" to solicitudId,
                    "horas" to horas,
                    "comentario" to comentario
                ))
                .await()
            val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            Result.success(data.entries.associate { it.key.toString() to (it.value?.toString() ?: "") })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Crea una recarga de billetera (Cloud Function `crearRecarga`).
     * Devuelve el id de la recarga y la URL del Checkout Pro de Mercado Pago.
     */
    suspend fun crearRecarga(monto: Long): Result<RecargaIniciada> {
        return try {
            val result = functions.getHttpsCallable("crearRecarga")
                .call(mapOf("monto" to monto))
                .await()
            val data = result.data as? Map<*, *>
            val url = data?.get("url") as? String
            val recargaId = data?.get("recargaId") as? String
            if (url.isNullOrBlank() || recargaId.isNullOrBlank()) {
                Result.failure(IllegalStateException("Respuesta inválida de crearRecarga"))
            } else {
                Result.success(RecargaIniciada(recargaId, url))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registra un destino de retiro (cuenta bancaria o cuenta de Mercado Pago).
     * Devuelve el id y una máscara para mostrar.
     */
    suspend fun registrarCuentaDestino(
        tipoDestino: String,
        banco: String? = null,
        tipoCuenta: String? = null,
        numeroCuenta: String? = null,
        titular: String? = null,
        rut: String? = null,
        mpIdentificador: String? = null
    ): Result<Map<String, String>> {
        return try {
            val payload = mutableMapOf<String, Any>("tipoDestino" to tipoDestino)
            if (!banco.isNullOrBlank()) payload["banco"] = banco
            if (!tipoCuenta.isNullOrBlank()) payload["tipoCuenta"] = tipoCuenta
            if (!numeroCuenta.isNullOrBlank()) payload["numeroCuenta"] = numeroCuenta
            if (!titular.isNullOrBlank()) payload["titular"] = titular
            if (!rut.isNullOrBlank()) payload["rut"] = rut
            if (!mpIdentificador.isNullOrBlank()) payload["mpIdentificador"] = mpIdentificador
            val result = functions.getHttpsCallable("registrarCuentaDestino")
                .call(payload)
                .await()
            val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            Result.success(data.entries.associate { it.key.toString() to (it.value?.toString() ?: "") })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Solicita un retiro de billetera hacia una cuenta destino (Cloud Function `retirarSaldo`).
     */
    suspend fun retirarSaldo(monto: Long, cuentaId: String): Result<RetiroIniciado> {
        return try {
            val result = functions.getHttpsCallable("retirarSaldo")
                .call(mapOf("monto" to monto, "cuentaId" to cuentaId))
                .await()
            val data = result.data as? Map<*, *>
            val estado = data?.get("estado") as? String
            val movimientoId = data?.get("movimientoId") as? String
            if (estado.isNullOrBlank() || movimientoId.isNullOrBlank()) {
                Result.failure(IllegalStateException("Respuesta inválida de retirarSaldo"))
            } else {
                Result.success(
                    RetiroIniciado(
                        estado = estado,
                        movimientoId = movimientoId,
                        payoutId = data["payoutId"] as? String,
                        comision = (data["comision"] as? Number)?.toLong() ?: 0L
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Paga un cobro pendiente desde la billetera (Cloud Function `pagarCobro`).
     * Devuelve el estado resultante del cobro (p. ej. "pagado").
     */
    suspend fun pagarCobro(cobroId: String): Result<String> {
        return try {
            val result = functions.getHttpsCallable("pagarCobro")
                .call(mapOf("cobroId" to cobroId))
                .await()
            val data = result.data as? Map<*, *>
            val estado = data?.get("estado") as? String ?: ""
            if (estado.isBlank()) {
                Result.failure(IllegalStateException("No se pudo procesar el pago."))
            } else {
                Result.success(estado)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicia la vinculación OAuth de Mercado Pago Connect
     * (Cloud Function `iniciarOauthMp`). Devuelve la URL de autorización si hay
     * client_id configurado; si no, ok=true sin authUrl (fallback manual).
     */
    suspend fun mpOauthIniciar(): Result<Map<String, String>> {
        return try {
            val result = functions.getHttpsCallable("iniciarOauthMp")
                .call(emptyMap<String, Any>())
                .await()
            val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            Result.success(data.entries.associate { it.key.toString() to (it.value?.toString() ?: "") })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Canjea el código OAuth de Mercado Pago por el token y vincula la cuenta
     * (Cloud Function `canjearOauthMp`).
     */
    suspend fun mpOauthCanjear(code: String): Result<Boolean> {
        return try {
            val result = functions.getHttpsCallable("canjearOauthMp")
                .call(mapOf("code" to code))
                .await()
            val ok = (result.data as? Map<*, *>)?.get("ok") as? Boolean ?: false
            if (ok) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("No se pudo vincular la cuenta de Mercado Pago."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Desvincula la cuenta de Mercado Pago (Cloud Function `desvincularMp`).
     * Elimina el destino y limpia la conexión en Firestore.
     */
    suspend fun mpOauthDesvincular(): Result<Boolean> {
        return try {
            val result = functions.getHttpsCallable("desvincularMp")
                .call(emptyMap<String, Any>())
                .await()
            val ok = (result.data as? Map<*, *>)?.get("ok") as? Boolean ?: false
            if (ok) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("No se pudo desvincular la cuenta de Mercado Pago."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancela una recarga pendiente (Cloud Function `cancelarRecarga`).
     * Marca el movimiento como rechazada en Firestore.
     */
    suspend fun cancelarRecarga(recargaId: String): Result<Boolean> {
        return try {
            val result = functions.getHttpsCallable("cancelarRecarga")
                .call(mapOf("recargaId" to recargaId))
                .await()
            val ok = (result.data as? Map<*, *>)?.get("ok") as? Boolean ?: false
            if (ok) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("No se pudo cancelar la recarga."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
