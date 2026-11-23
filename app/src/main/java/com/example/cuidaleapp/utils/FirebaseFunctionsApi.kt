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
     * Devuelve la URL del Checkout Pro de Mercado Pago.
     */
    suspend fun crearRecarga(monto: Long): Result<String> {
        return try {
            val result = functions.getHttpsCallable("crearRecarga")
                .call(mapOf("monto" to monto))
                .await()
            val data = result.data as? Map<*, *>
            val url = data?.get("url") as? String
            if (url.isNullOrBlank()) {
                Result.failure(IllegalStateException("Respuesta inválida de crearRecarga"))
            } else {
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
