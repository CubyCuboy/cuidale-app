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
}
