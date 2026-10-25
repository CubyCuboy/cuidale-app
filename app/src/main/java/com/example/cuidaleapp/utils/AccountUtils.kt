package com.example.cuidaleapp.utils

import android.util.Patterns
import com.example.cuidaleapp.models.LinkedAccountDetail
import kotlinx.coroutines.tasks.await

object AccountUtils {

    // ── Proveedores canónicos (evita hardcodeo) ──
    const val PROVIDER_GOOGLE = "google.com"
    const val PROVIDER_MICROSOFT = "microsoft.com"
    const val PROVIDER_EMAIL = "email"
    const val PROVIDER_FIREBASE = "firebase"
    const val PROVIDER_DISPLAY_GOOGLE = "Google"
    const val PROVIDER_DISPLAY_MICROSOFT = "Microsoft"
    const val PROVIDER_DISPLAY_EMAIL = "Correo y Contraseña"

    fun normalizeEmail(email: String): String = email.trim().lowercase()

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
            "google.com", "google" -> PROVIDER_GOOGLE
            "microsoft.com", "microsoft" -> PROVIDER_MICROSOFT
            "password", "email" -> PROVIDER_EMAIL
            else -> providerId.trim()
        }
    }

    fun providerDisplayName(providerId: String): String {
        return when (normalizeProviderId(providerId)) {
            PROVIDER_GOOGLE -> PROVIDER_DISPLAY_GOOGLE
            PROVIDER_MICROSOFT -> PROVIDER_DISPLAY_MICROSOFT
            PROVIDER_EMAIL -> PROVIDER_DISPLAY_EMAIL
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
                    "google", "google.com" -> PROVIDER_GOOGLE
                    "microsoft", "microsoft.com" -> PROVIDER_MICROSOFT
                    "password", "email" -> PROVIDER_EMAIL
                    else -> key.replace("_", ".")
                }
                if (result.none { it.providerId == providerId }) {
                    val verified = when (providerId) {
                        PROVIDER_GOOGLE, PROVIDER_MICROSOFT -> true
                        else -> defaultVerified
                    }
                    result = mergeLinkedAccount(result, providerId, "", fallbackEmail, verified, ts)
                }
            }
        return result.sortedBy { it.linkedAt }
    }

    // ── Resolución de identidad (anti-duplicación) ──

    /**
     * Busca el documento canónico de un usuario (prefiriendo el que está
     * completado con RUT y correo), primero por correo normalizado y luego
     * por uid. Devuelve la referencia más confiable o [null] si no existe.
     */
    suspend fun findCanonicalUserDocument(
        db: com.google.firebase.firestore.FirebaseFirestore,
        email: String?,
        uid: String?
    ): com.google.firebase.firestore.DocumentSnapshot? {
        val e = email?.let { normalizeEmail(it) }?.ifBlank { null }
        if (e != null) {
            val byEmail = db.collection("usuarios")
                .whereEqualTo("correo", e)
                .limit(30)
                .get()
                .await()
            val canonical = byEmail.documents.firstOrNull { snap ->
                snap.getBoolean("completado") == true && !snap.getString("rut").isNullOrBlank()
            } ?: byEmail.documents.firstOrNull()
            if (canonical != null) return canonical
        }
        if (uid != null && uid.isNotBlank()) {
            val byUid = db.collection("usuarios")
                .whereEqualTo("uid", uid)
                .limit(30)
                .get()
                .await()
            return byUid.documents.firstOrNull { snap ->
                snap.getBoolean("completado") == true && !snap.getString("rut").isNullOrBlank()
            } ?: byUid.documents.firstOrNull()
        }
        return null
    }

    /**
     * Fusiona un proveedor recién autenticado en el documento existente y
     * sincroniza el uid de auth. Devuelve los datos actualizados del usuario.
     */
    suspend fun attachProviderToExistingAccount(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        providerId: String,
        accountName: String,
        verified: Boolean,
        newUid: String? = null
    ): com.example.cuidaleapp.models.UserData? {
        val existing = doc.toObject(com.example.cuidaleapp.models.UserData::class.java) ?: return null
        val now = System.currentTimeMillis()
        val merged = mergeLinkedAccount(existing.linkedAccounts, providerId, "", accountName, verified, now)
        val updates = mutableMapOf<String, Any>(
            "linkedAccounts" to merged,
            "updatedAt" to com.google.firebase.Timestamp(now / 1000, 0)
        )
        if (!newUid.isNullOrBlank() && existing.uid != newUid) {
            updates["uid"] = newUid
        }
        doc.reference.update(updates).await()
        return existing.copy(uid = newUid ?: existing.uid, linkedAccounts = merged)
    }

    /**
     * Elimina documentos fantasma duplicados (ghost por uid/correo) que no
     * sean el canónico, moviendo sus restos a basura y borrándolos. Evita
     * que queden cuentas espejo cuando se inicia sesión con otro proveedor.
     */
    suspend fun cleanupDuplicateGhostDocs(
        db: com.google.firebase.firestore.FirebaseFirestore,
        canonicalRef: com.google.firebase.firestore.DocumentReference,
        canonicalId: String,
        email: String?,
        uid: String?
    ) {
        val e = email?.let { normalizeEmail(it) }?.ifBlank { null }
        val targets = mutableListOf<String>()
        if (uid != null && uid.isNotBlank()) targets.add(uid)
        if (e != null) targets.add(e)
        if (targets.isEmpty()) return

        for (id in targets.distinct()) {
            if (id == canonicalId) continue
            runCatching {
                val ref = db.collection("usuarios").document(id)
                val snap = ref.get().await()
                if (snap.exists()) {
                    // Solo borrar si es un ghost (incompleto) o apunta al mismo uid/correo.
                    val uidField = snap.getString("uid")
                    val correoField = snap.getString("correo")
                    val esGhost = snap.getBoolean("completado") != true ||
                        (uid != null && uidField == uid && id != canonicalId) ||
                        (e != null && correoField == e && id != canonicalId)
                    if (esGhost) {
                        ref.delete().await()
                    }
                }
            }
        }
    }
}
