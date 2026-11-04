package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.activities.AddEmailPasswordActivity
import com.example.cuidaleapp.activities.EmailVerificationActivity
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.ProviderBrandIcon
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.json.JSONObject
import java.security.SecureRandom
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountManagementScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val userData by viewModel.userData.collectAsState()
    val gradient = CuidaleThemeExtras.gradient

    val currentUser = FirebaseAuth.getInstance().currentUser
    var userProviders by remember {
        mutableStateOf(
            (currentUser?.providerData ?: emptyList()).filter {
                !it.providerId.equals("firebase", ignoreCase = true) && it.providerId.isNotBlank()
            }
        )
    }

    fun refreshProviders() {
        currentUser?.reload()
        userProviders = (currentUser?.providerData ?: emptyList()).filter {
            !it.providerId.equals("firebase", ignoreCase = true) && it.providerId.isNotBlank()
        }
    }

    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var successDialogMessage by remember { mutableStateOf<String?>(null) }
    var isChangingGoogle by remember { mutableStateOf(false) }

    LaunchedEffect(userData) {
        refreshProviders()
    }

    if (successDialogMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(gradient.top, gradient.bottom)))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400))
            ) {
                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = shadowPalette.hardShadowPrimary,
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(90.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "¡Operación Exitosa!",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = successDialogMessage ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        HardShadowButton(
                            text = "Continuar en Gestión de Cuentas",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            isDark = isDark,
                            onClick = {
                                successDialogMessage = null
                                viewModel.refreshFromFirestore()
                                refreshProviders()
                            }
                        )
                    }
                }
            }
        }
        return
    }

    fun sendAccountNotification(text: String) {
        val uid = currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("usuarios").whereEqualTo("uid", uid).get().addOnSuccessListener { query ->
            if (!query.isEmpty) {
                val userDocRef = query.documents[0].reference
                val notifRef = userDocRef.collection("notificaciones").document()
                val data = mapOf("title" to "Gestión de Cuenta", "text" to text)
                val resolved = com.example.cuidaleapp.utils.NotificationTemplates.resolve(context, "configuracion_cuenta", data)
                val notif = mapOf(
                    "id" to notifRef.id,
                    "tipo" to "configuracion_cuenta",
                    "emisor" to uid,
                    "receptor" to uid,
                    "timestamp" to System.currentTimeMillis(),
                    "leida" to false,
                    "data" to mapOf(
                        "title" to resolved.title,
                        "text" to resolved.body
                    )
                )
                notifRef.set(notif)
            }
        }
    }

    fun parseLinkingError(e: Exception): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("requires-recent-login", ignoreCase = true) ||
            msg.contains("recent authentication", ignoreCase = true) ||
            e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException ->
                "Por seguridad, debes cerrar sesión y volver a ingresar antes de vincular un nuevo método de acceso."
            
            msg.contains("already-in-use", ignoreCase = true) ||
            msg.contains("collision", ignoreCase = true) ||
            e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                "Este correo o cuenta ya pertenece a otro usuario registrado. Si intentas vincular una cuenta nueva, usa un correo diferente o desvincula el anterior."
                
            msg.contains("permission-denied", ignoreCase = true) ||
            msg.contains("Missing or insufficient permissions", ignoreCase = true) ->
                "Error de permisos en el servidor. Inténtalo nuevamente."

            // Cancelación de la ventana OAuth (p. ej. Microsoft/Google) -> mensaje en español.
            msg.contains("operation was cancelled", ignoreCase = true) ||
            msg.contains("cancelled", ignoreCase = true) ||
            msg.contains("canceled", ignoreCase = true) ||
            msg.contains("popup closed", ignoreCase = true) ||
            msg.contains("popup-closed-by-user", ignoreCase = true) ||
            msg.contains("USER_CANCELLED", ignoreCase = true) ||
            msg.contains("auth/cancelled", ignoreCase = true) ||
            msg.contains("auth/signin-cancelled", ignoreCase = true) ->
                "Vinculación cancelada."

            else -> if (msg.isNotBlank()) msg else "Error al vincular la cuenta"
        }
    }

    suspend fun updateProviderTimestampAndHistory(providerId: String, providerName: String, actionType: String, accountEmail: String = "") {
        val uid = currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        try {
            val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
            val userDocRef = if (!query.isEmpty) {
                query.documents[0].reference
            } else {
                val currentRut = userData.rut
                if (currentRut.isNotBlank()) {
                    val hashedRut = com.example.cuidaleapp.utils.HashUtils.hashRut(currentRut)
                    db.collection("usuarios").document(hashedRut)
                } else null
            }

            if (userDocRef != null) {
                val updates = mutableMapOf<String, Any>()
                if (actionType == "unlink") {
                    updates["linkedAccounts"] = com.example.cuidaleapp.utils.AccountUtils.removeLinkedAccount(userData.linkedAccounts, providerId)
                } else {
                    updates["linkedAccounts"] = com.example.cuidaleapp.utils.AccountUtils.mergeLinkedAccount(
                        userData.linkedAccounts,
                        providerId,
                        providerName,
                        accountEmail.ifBlank { userData.correo },
                        verificado = true,
                        timestamp = now
                    )
                }
                userDocRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()

                val title = when (actionType) {
                    "link" -> "Cuenta de $providerName Vinculada"
                    "change" -> "Cuenta de $providerName Cambiada"
                    "unlink" -> "Cuenta de $providerName Desvinculada"
                    else -> "Actualización de Cuenta"
                }
                val desc = when (actionType) {
                    "link" -> "Has vinculado tu cuenta de $providerName (${accountEmail.ifEmpty { "vinculación exitosa" }})"
                    "change" -> "Has cambiado tu cuenta de $providerName (${accountEmail.ifEmpty { "cambio exitoso" }})"
                    "unlink" -> "Has desvinculado tu cuenta de $providerName"
                    else -> "Modificación de proveedor $providerName"
                }
                viewModel.logAccountHistory("provider_$actionType", title, desc)
            }
        } catch (e: Exception) {
            android.util.Log.e("AccountMgmt", "Error updating history/timestamp: ${e.message}")
        }
    }

    suspend fun syncPrimaryEmailFromAuth(detachedProviderId: String? = null) {
        val user = currentUser ?: return
        val remainingAccounts = userData.linkedAccounts
            .filter { com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(it.providerId) != com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(detachedProviderId ?: "") }
        val newEmail = user.email
            ?: remainingAccounts.firstOrNull { it.accountName.contains("@") }?.accountName
            ?: userData.correo
        if (newEmail.isNotBlank() && newEmail != userData.correo) {
            try {
                val db = FirebaseFirestore.getInstance()
                val updates = mapOf("correo" to newEmail)
                val currentRut = userData.rut
                if (currentRut.isNotBlank()) {
                    val hashedRut = com.example.cuidaleapp.utils.HashUtils.hashRut(currentRut)
                    db.collection("usuarios").document(hashedRut).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                } else {
                    val query = db.collection("usuarios").whereEqualTo("uid", user.uid).get().await()
                    if (!query.isEmpty) {
                        query.documents[0].reference.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AccountMgmt", "Error actualizando correo principal: ${e.message}")
            }
        }
    }

    fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    fun decodeJwtPayload(token: String?): JSONObject? {
        if (token.isNullOrBlank()) return null
        return try {
            val parts = token.split('.')
            if (parts.size < 2) null else {
                val normalized = parts[1].replace('-', '+').replace('_', '/')
                val padding = (4 - normalized.length % 4) % 4
                val bytes = Base64.decode(normalized + "=".repeat(padding), Base64.DEFAULT)
                JSONObject(String(bytes, Charsets.UTF_8))
            }
        } catch (e: Exception) { null }
    }

    fun handleGoogleCredential(response: GetCredentialResponse) {
        val credential = response.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleCredential.idToken
                val payload = decodeJwtPayload(idToken)
                val newEmail = payload?.optString("email")?.takeIf { it.isNotBlank() }
                if (idToken != null && newEmail != null) {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    successMessage = null
                    try {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        try {
                            if (isChangingGoogle) {
                                try {
                                    currentUser?.linkWithCredential(credential)?.await()
                                } catch (e: Exception) {
                                    if (currentUser?.providerData?.any { it.providerId == "google.com" } == true) {
                                        currentUser.unlink("google.com").await()
                                        currentUser.linkWithCredential(credential).await()
                                    } else {
                                        throw e
                                    }
                                }
                            } else {
                                currentUser?.linkWithCredential(credential)?.await()
                            }
                        } catch (e: Exception) {
                            val isCollision = e is com.google.firebase.auth.FirebaseAuthUserCollisionException || 
                                              e.message?.contains("already in use", ignoreCase = true) == true ||
                                              e.message?.contains("collision", ignoreCase = true) == true

                            if (isCollision) {
                                val db = FirebaseFirestore.getInstance()
                                val query = db.collection("usuarios").whereEqualTo("correo", newEmail).get().await()
                                val userBUid = currentUser?.uid ?: ""
                                val incompleteDoc = query.documents.firstOrNull { 
                                    it.getBoolean("completado") != true && it.getString("uid") != userBUid 
                                }

                                if (incompleteDoc != null) {
                                    incompleteDoc.reference.delete().await()

                                    try {
                                        val auth = FirebaseAuth.getInstance()
                                        val incompleteAuthUser = auth.signInWithCredential(credential).await().user
                                        incompleteAuthUser?.delete()?.await()
                                    } catch (eAuth: Exception) {}

                                    errorMessage = "Se detectó y eliminó un registro incompleto previo de la cuenta $newEmail. Vuelve a presionar 'Vincular' para asociarla a tu cuenta actual."
                                    return@launch
                                } else {
                                    throw Exception("La cuenta $newEmail ya pertenece a un usuario registrado y completado.")
                                }
                            } else {
                                throw e
                            }
                        }
                        currentUser?.reload()?.await()

                        val uid = currentUser?.uid ?: ""
                        if (uid.isNotBlank()) {
                            // Solo se actualiza el correo; NO se tocan nombre/apellidos/foto
                            // (los datos de perfil pertenecen al usuario, no al auth externo).
                            val updates = mapOf<String, Any>(
                                "correo" to newEmail
                            )
                            val db = FirebaseFirestore.getInstance()
                            val currentRut = userData.rut
                            if (currentRut.isNotBlank()) {
                                val hashedRut = com.example.cuidaleapp.utils.HashUtils.hashRut(currentRut)
                                db.collection("usuarios")
                                    .document(hashedRut)
                                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                                    .await()
                            } else if (uid.isNotBlank()) {
                                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                                if (!query.isEmpty) {
                                    query.documents[0].reference.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                                }
                            }
                        }

                        updateProviderTimestampAndHistory("google.com", "Google", if (isChangingGoogle) "change" else "link", newEmail)

                        viewModel.refreshFromFirestore()
                        refreshProviders()
                        isLoading = false
                        successDialogMessage = if (isChangingGoogle) "Cuenta de Google cambiada con éxito" else "Cuenta de Google vinculada con éxito"
                        sendAccountNotification(successDialogMessage!!)
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = e.localizedMessage ?: "Error al actualizar cuenta de Google"
                    } finally {
                        isChangingGoogle = false
                    }
                }
                } else {
                    errorMessage = "No se pudo obtener la información de la cuenta de Google"
                    isLoading = false
                    isChangingGoogle = false
                }
            } catch (e: GoogleIdTokenParsingException) {
                errorMessage = "Error en Google Sign-In: ${e.message}"
                isLoading = false
                isChangingGoogle = false
            }
        } else {
            errorMessage = "No se pudo obtener la información de la cuenta de Google"
            isLoading = false
            isChangingGoogle = false
        }
    }

    fun startNativeGoogleAuth(changing: Boolean) {
        val totalLinkedCount = (userData.linkedAccounts.map { it.providerId } + userProviders.map { com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(it.providerId) }).distinct().size
        if (changing && totalLinkedCount <= 1) {
            errorMessage = "Debes tener al menos 2 métodos de autenticación vinculados para poder cambiar este método de acceso."
            return
        }
        isChangingGoogle = changing
        isLoading = true
        errorMessage = null
        successMessage = null

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(context.getString(com.example.cuidaleapp.R.string.google_client_id))
                    .setNonce(generateSecureRandomNonce())
                    .build()
            )
            .build()

        scope.launch {
            try {
                val response = credentialManager.getCredential(MutableContextWrapper(context), request)
                handleGoogleCredential(response)
            } catch (e: GetCredentialException) {
                if (e !is GetCredentialCancellationException) {
                    errorMessage = "No se pudo vincular la cuenta de Google."
                }
                isLoading = false
                isChangingGoogle = false
            }
        }
    }


    fun formatTimestamp(timestamp: Long): String {
        val validTime = if (timestamp <= 0L) System.currentTimeMillis() else timestamp
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(validTime))
    }

    fun linkProvider(providerId: String) {
        if (providerId == "google.com") {
            startNativeGoogleAuth(false)
            return
        }
        if (providerId == "password" || providerId == "email") {
            val intent = Intent(context, AddEmailPasswordActivity::class.java)
            context.startActivity(intent)
            return
        }
        if (activity == null || currentUser == null) {
            errorMessage = "No se pudo iniciar la acción"
            return
        }
        isLoading = true
        errorMessage = null
        successMessage = null

        val providerName = when (providerId) {
            "microsoft.com" -> "Microsoft"
            else -> providerId
        }

        val provider = OAuthProvider.newBuilder(providerId)
        if (providerId == "microsoft.com") {
            provider.addCustomParameter("prompt", "select_account")
            provider.scopes = listOf("openid", "email", "profile", "User.Read")
        }
        currentUser.startActivityForLinkWithProvider(activity, provider.build())
            .addOnSuccessListener {
                scope.launch {
                    try {
                        // Timeout de 25s para la sincronización post-vinculación.
                        val ok = kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                            currentUser.reload().await()
                            syncPrimaryEmailFromAuth(providerId)
                            val linkedEmail = currentUser.email ?: currentUser.providerData.firstOrNull { it.providerId == providerId }?.email ?: ""
                            updateProviderTimestampAndHistory(providerId, providerName, "link", linkedEmail)
                            viewModel.refreshFromFirestore()
                            refreshProviders()
                            true
                        }
                        isLoading = false
                        if (ok == true) {
                            successDialogMessage = "Cuenta de $providerName vinculada con éxito"
                            sendAccountNotification(successDialogMessage!!)
                        } else {
                            errorMessage = "La vinculación está tardando demasiado. Revisa tu conexión e inténtalo de nuevo."
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = e.localizedMessage ?: "Error al finalizar vinculación"
                    }
                }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = parseLinkingError(e)
            }
    }

    fun unlinkProvider(providerId: String) {
        if (currentUser == null) return
        isLoading = true
        errorMessage = null
        successMessage = null
        val totalLinkedCount = (userData.linkedAccounts.map { it.providerId } + userProviders.map { com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(it.providerId) }).distinct().size
        if (totalLinkedCount <= 1) {
            isLoading = false
            errorMessage = "No puedes desvincular el único método de acceso. Tu cuenta quedaría inaccesible."
            return
        }
        val providerName = when (providerId) {
            "google.com" -> "Google"
            "microsoft.com" -> "Microsoft"
            "password", "email" -> "Correo y Contraseña"
            else -> providerId
        }
        scope.launch {
            try {
                val firebaseProviderId = if (providerId == "email" || providerId == "password") "password" else providerId
                currentUser.unlink(firebaseProviderId).await()
                currentUser.reload().await()

                syncPrimaryEmailFromAuth(providerId)

                updateProviderTimestampAndHistory(providerId, providerName, "unlink")

                viewModel.refreshFromFirestore()
                refreshProviders()
                isLoading = false
                successDialogMessage = "Cuenta desvinculada con éxito"
                sendAccountNotification(successDialogMessage!!)
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.localizedMessage ?: "Error al desvincular cuenta"
            }
        }
    }

    fun changeProvider(providerId: String) {
        if (providerId == "google.com") {
            startNativeGoogleAuth(true)
            return
        }
        if (providerId == "password" || providerId == "email") {
            val intent = Intent(context, AddEmailPasswordActivity::class.java)
            context.startActivity(intent)
            return
        }
        if (activity == null || currentUser == null) {
            errorMessage = "No se pudo iniciar la acción"
            return
        }
        val totalLinkedCount = (userData.linkedAccounts.map { it.providerId } + userProviders.map { com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(it.providerId) }).distinct().size
        if (totalLinkedCount <= 1) {
            errorMessage = "Debes tener al menos 2 métodos de autenticación vinculados para poder cambiar este método de acceso."
            return
        }
        isLoading = true
        errorMessage = null
        successMessage = null

        val providerName = when (providerId) {
            "microsoft.com" -> "Microsoft"
            "password", "email" -> "Correo y Contraseña"
            else -> providerId
        }

        val provider = OAuthProvider.newBuilder(providerId)
        if (providerId == "microsoft.com") {
            provider.addCustomParameter("prompt", "select_account")
            provider.scopes = listOf("openid", "email", "profile", "User.Read")
        }
        currentUser.startActivityForLinkWithProvider(activity, provider.build())
            .addOnSuccessListener {
                scope.launch {
                    try {
                        val ok = kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                            currentUser.reload().await()
                            syncPrimaryEmailFromAuth(providerId)
                            val updatedEmail = currentUser.email ?: currentUser.providerData.firstOrNull { it.providerId == providerId }?.email ?: ""
                            updateProviderTimestampAndHistory(providerId, providerName, "change", updatedEmail)
                            viewModel.refreshFromFirestore()
                            refreshProviders()
                            true
                        }
                        isLoading = false
                        if (ok == true) {
                            successDialogMessage = "Cuenta actualizada con éxito"
                            sendAccountNotification(successDialogMessage!!)
                        } else {
                            errorMessage = "El cambio de cuenta está tardando demasiado. Revisa tu conexión e inténtalo de nuevo."
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = e.localizedMessage ?: "Error al actualizar cuenta"
                    }
                }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = parseLinkingError(e)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Text(
                    text = "Gestión de Cuenta",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowButton(
                text = "Ver Historial de Cambios",
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                icon = androidx.compose.material.icons.Icons.Default.History,
                isDark = isDark,
                onClick = {
                    val intent = Intent(context, com.example.cuidaleapp.activities.AccountHistoryActivity::class.java)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Información del Usuario",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    AccountDetailItem("Nombre", "${userData.nombre} ${userData.apellido_pa}")
                    AccountDetailItem("Correo", userData.correo.ifEmpty { currentUser?.email ?: "N/A" })
                    AccountDetailItem("RUT", userData.rut.ifEmpty { "N/A" })
                    AccountDetailItem("Teléfono", userData.telefono.ifEmpty { "N/A" })
                    AccountDetailItem("Rol", userData.rol.uppercase().ifEmpty { "N/A" })
                    AccountDetailItem("Región / Comuna", "${userData.region} / ${userData.comuna}")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Cuentas Vinculadas",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val appCreationTime = userData.createdAt?.toDate()?.time
                        ?.takeIf { it > 0L }
                        ?: userData.linkedAccounts.minByOrNull { it.linkedAt }?.linkedAt
                        ?.takeIf { it > 0L }
                        ?: System.currentTimeMillis()

                    val linkedCards = buildList {
                        val existingIds = userData.linkedAccounts.map { it.providerId }.toMutableSet()

                        userData.linkedAccounts.sortedBy { it.linkedAt }.forEach { acc ->
                            add(acc)
                        }

                        userProviders.forEach { info ->
                            val cleanId = com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(info.providerId)
                            if (cleanId !in existingIds) {
                                existingIds.add(cleanId)
                                add(
                                    com.example.cuidaleapp.models.LinkedAccountDetail(
                                        providerId = cleanId,
                                        providerName = com.example.cuidaleapp.utils.AccountUtils.providerDisplayName(cleanId),
                                        accountName = info.email ?: info.displayName ?: userData.correo,
                                        linkedAt = appCreationTime,
                                        verificado = when (cleanId) {
                                            "google.com", "microsoft.com" -> true
                                            else -> userData.verificado
                                        }
                                    )
                                )
                            }
                        }
                    }.sortedBy { it.linkedAt }

                    val totalLinkedCount = linkedCards.size
                    val canUnlinkAny = totalLinkedCount > 1
                    val canChangeAny = totalLinkedCount > 1

                    linkedCards.forEach { acc ->
                        val accountName = acc.accountName.ifBlank {
                            userProviders.firstOrNull { com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(it.providerId) == acc.providerId }
                                ?.email ?: userData.correo
                        }

                        LinkedAccountCard(
                            providerId = acc.providerId,
                            providerName = acc.providerName.ifBlank { com.example.cuidaleapp.utils.AccountUtils.providerDisplayName(acc.providerId) },
                            accountName = accountName,
                            dateStr = formatTimestamp(acc.linkedAt.takeIf { it > 0L } ?: appCreationTime),
                            canUnlink = canUnlinkAny,
                            canChange = canChangeAny || acc.providerId == "email",
                            isVerified = if (acc.providerId == "google.com" || acc.providerId == "microsoft.com") true else acc.verificado,
                            onUnlink = { unlinkProvider(acc.providerId) },
                            onChangeAccount = { changeProvider(acc.providerId) },
                            onVerify = {
                                val resolvedEmail = if (accountName.contains("@")) accountName else userData.correo
                                val intent = Intent(context, EmailVerificationActivity::class.java).apply {
                                    putExtra("email", resolvedEmail)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    val isGoogleLinked = userData.hasLinkedAccount("google.com") || userProviders.any { it.providerId == "google.com" }
                    val isMicrosoftLinked = userData.hasLinkedAccount("microsoft.com") || userProviders.any { it.providerId == "microsoft.com" }
                    val isPasswordLinked = userData.hasLinkedAccount("email") || userProviders.any { it.providerId == "password" }
                    val allProvidersLinked = isGoogleLinked && isMicrosoftLinked && isPasswordLinked

                    if (!allProvidersLinked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Agregar nuevos proveedores:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!isGoogleLinked) {
                            Button(
                                onClick = { linkProvider("google.com") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) {
                                ProviderBrandIcon("google.com")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vincular cuenta de Google", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isMicrosoftLinked) {
                            Button(
                                onClick = { linkProvider("microsoft.com") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F2F2F))
                            ) {
                                ProviderBrandIcon("microsoft.com")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vincular cuenta de Microsoft", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isPasswordLinked) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, AddEmailPasswordActivity::class.java)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Agregar Correo y Contraseña", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    successMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AccountDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LinkedAccountCard(
    providerId: String,
    providerName: String,
    accountName: String,
    dateStr: String,
    canUnlink: Boolean,
    canChange: Boolean,
    isVerified: Boolean,
    onUnlink: () -> Unit,
    onChangeAccount: () -> Unit,
    onVerify: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderBrandIcon(providerId)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(providerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Surface(
                    color = if (isVerified) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isVerified) "Verificado" else "No verificado",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text("Cuenta: $accountName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Vinculación: $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isVerified) {
                    TextButton(
                        onClick = onVerify,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verificar", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (canChange) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onChangeAccount) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cambiar", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (canUnlink) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onUnlink,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Desvincular", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
