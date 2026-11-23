package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.activities.AddEmailPasswordActivity
import com.example.cuidaleapp.activities.EmailVerificationActivity
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.ProviderBrandIcon
import androidx.compose.material.icons.filled.ChevronRight
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.ErrorCodes
import com.example.cuidaleapp.utils.ErrorMessages
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

    val distinctEmailCount = buildSet {
        userData.correo.takeIf { it.isNotBlank() }?.let { add(it) }
        userData.linkedAccounts.forEach { acc ->
            acc.accountName.takeIf { it.contains("@") }?.let { add(it) }
        }
    }.size

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
                            text = stringResource(R.string.account_management_operation_success),
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
                            text = stringResource(R.string.account_management_continue_account_management),
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
                val data = mapOf("title" to context.getString(R.string.settings_account_management), "text" to text)
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
                ErrorMessages.resolve(context, ErrorCodes.Auth.SESSION_EXPIRED)

            msg.contains("already-in-use", ignoreCase = true) ||
            msg.contains("collision", ignoreCase = true) ||
            e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                ErrorMessages.resolve(context, ErrorCodes.Auth.EMAIL_IN_USE)

            msg.contains("permission-denied", ignoreCase = true) ||
            msg.contains("Missing or insufficient permissions", ignoreCase = true) ->
                ErrorMessages.resolve(context, ErrorCodes.Auth.PERMISSION_DENIED)

            msg.contains("operation was cancelled", ignoreCase = true) ||
            msg.contains("cancelled", ignoreCase = true) ||
            msg.contains("canceled", ignoreCase = true) ||
            msg.contains("popup closed", ignoreCase = true) ||
            msg.contains("popup-closed-by-user", ignoreCase = true) ||
            msg.contains("USER_CANCELLED", ignoreCase = true) ||
            msg.contains("auth/cancelled", ignoreCase = true) ||
            msg.contains("auth/signin-cancelled", ignoreCase = true) ->
                context.getString(R.string.account_management_link_cancelled)

            else -> if (msg.isNotBlank()) ErrorMessages.get(context, msg) else ErrorMessages.resolve(context, ErrorCodes.Unknown.GENERIC)
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
                    "link" -> context.getString(R.string.account_management_history_linked, providerName)
                    "change" -> context.getString(R.string.account_management_history_changed, providerName)
                    "unlink" -> context.getString(R.string.account_management_history_unlinked, providerName)
                    else -> context.getString(R.string.account_management_history_update)
                }
                val desc = when (actionType) {
                    "link" -> context.getString(R.string.account_management_history_link_desc, providerName, accountEmail.ifEmpty { context.getString(R.string.account_management_link_success_placeholder) })
                    "change" -> context.getString(R.string.account_management_history_change_desc, providerName, accountEmail.ifEmpty { context.getString(R.string.account_management_change_success_placeholder) })
                    "unlink" -> context.getString(R.string.account_management_history_unlink_desc, providerName)
                    else -> context.getString(R.string.account_management_history_modify_desc, providerName)
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

                                    errorMessage = context.getString(R.string.account_management_removed_incomplete_record, newEmail)
                                    return@launch
                                } else {
                                    throw Exception(context.getString(R.string.account_management_email_already_registered, newEmail))
                                }
                            } else {
                                throw e
                            }
                        }
                        currentUser?.reload()?.await()

                        val uid = currentUser?.uid ?: ""
                        if (uid.isNotBlank()) {
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

                        updateProviderTimestampAndHistory("google.com", context.getString(R.string.provider_google), if (isChangingGoogle) "change" else "link", newEmail)

                        viewModel.refreshFromFirestore()
                        refreshProviders()
                        isLoading = false
                        successDialogMessage = if (isChangingGoogle) context.getString(R.string.account_management_google_change_success) else context.getString(R.string.account_management_google_link_success)
                        sendAccountNotification(successDialogMessage!!)
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = ErrorMessages.get(context, e.localizedMessage ?: e.message)
                    } finally {
                        isChangingGoogle = false
                    }
                }
                } else {
                    errorMessage = context.getString(R.string.account_management_google_info_failed)
                    isLoading = false
                    isChangingGoogle = false
                }
            } catch (e: GoogleIdTokenParsingException) {
                errorMessage = ErrorMessages.get(context, e.message)
                isLoading = false
                isChangingGoogle = false
            }
        } else {
            errorMessage = context.getString(R.string.account_management_google_info_failed)
            isLoading = false
            isChangingGoogle = false
        }
    }

    fun startNativeGoogleAuth(changing: Boolean) {
        val totalLinkedCount = (userData.linkedAccounts.map { it.providerId } + userProviders.map { com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(it.providerId) }).distinct().size
        if (changing && totalLinkedCount <= 1) {
            errorMessage = context.getString(R.string.account_management_min_two_methods)
            return
        }
        isChangingGoogle = changing
        isLoading = true
        errorMessage = null
        successMessage = null

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(context.getString(R.string.google_client_id))
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
                    errorMessage = ErrorMessages.get(context, e.localizedMessage ?: e.message)
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
            errorMessage = context.getString(R.string.account_management_action_failed)
            return
        }
        isLoading = true
        errorMessage = null
        successMessage = null

        val providerName = when (providerId) {
            "microsoft.com" -> context.getString(R.string.provider_microsoft)
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
                            val linkedEmail = currentUser.email ?: currentUser.providerData.firstOrNull { it.providerId == providerId }?.email ?: ""
                            updateProviderTimestampAndHistory(providerId, providerName, "link", linkedEmail)
                            viewModel.refreshFromFirestore()
                            refreshProviders()
                            true
                        }
                        isLoading = false
                        if (ok == true) {
                            successDialogMessage = context.getString(R.string.account_management_provider_link_success, providerName)
                            sendAccountNotification(successDialogMessage!!)
                        } else {
                            errorMessage = context.getString(R.string.account_management_link_timeout)
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = ErrorMessages.get(context, e.localizedMessage ?: e.message)
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
            errorMessage = context.getString(R.string.account_management_cannot_unlink_only)
            return
        }
        val providerName = when (providerId) {
            "google.com" -> context.getString(R.string.provider_google)
            "microsoft.com" -> context.getString(R.string.provider_microsoft)
            "password", "email" -> context.getString(R.string.account_management_email_password)
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
                successDialogMessage = context.getString(R.string.account_management_unlink_success)
                sendAccountNotification(successDialogMessage!!)
            } catch (e: Exception) {
                isLoading = false
                errorMessage = ErrorMessages.get(context, e.localizedMessage ?: e.message)
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
            errorMessage = context.getString(R.string.account_management_action_failed)
            return
        }
        val totalLinkedCount = (userData.linkedAccounts.map { it.providerId } + userProviders.map { com.example.cuidaleapp.utils.AccountUtils.normalizeProviderId(it.providerId) }).distinct().size
        if (totalLinkedCount <= 1) {
            errorMessage = context.getString(R.string.account_management_min_two_methods)
            return
        }
        isLoading = true
        errorMessage = null
        successMessage = null

        val providerName = when (providerId) {
            "microsoft.com" -> context.getString(R.string.provider_microsoft)
            "password", "email" -> context.getString(R.string.account_management_email_password)
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
                            successDialogMessage = context.getString(R.string.account_management_update_success)
                            sendAccountNotification(successDialogMessage!!)
                        } else {
                            errorMessage = context.getString(R.string.account_management_change_timeout)
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = ErrorMessages.get(context, e.localizedMessage ?: e.message)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_account_management),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                    Text(
                        text = if (distinctEmailCount > 1) stringResource(R.string.settings_account_management_subtitle_plural) else stringResource(R.string.account_management_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Surface(
                    onClick = {
                        val intent = Intent(context, com.example.cuidaleapp.activities.AccountHistoryActivity::class.java)
                        context.startActivity(intent)
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.account_management_view_change_history),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.account_history_see_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                    }
                }
            }
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
                    AccountGroupTitle(stringResource(R.string.account_management_user_info), MaterialTheme.colorScheme.primary)

                    AccountDetailItem(Icons.Default.Person, stringResource(R.string.account_management_name), "${userData.nombre} ${userData.apellido_pa}")
                    AccountDetailItem(Icons.Default.Email, stringResource(R.string.account_management_email), userData.correo.ifEmpty { currentUser?.email ?: stringResource(R.string.account_management_not_available) })
                    AccountDetailItem(Icons.Default.Badge, stringResource(R.string.profile_label_rut), userData.rut.ifEmpty { stringResource(R.string.account_management_not_available) })
                    AccountDetailItem(Icons.Default.Phone, stringResource(R.string.profile_label_phone), userData.telefono.ifEmpty { stringResource(R.string.account_management_not_available) })
                    AccountDetailItem(Icons.Default.Work, stringResource(R.string.profile_label_role), userData.rol.uppercase().ifEmpty { stringResource(R.string.account_management_not_available) })
                    AccountDetailItem(Icons.Default.Place, stringResource(R.string.account_management_region_comuna), "${userData.region} / ${userData.comuna}")
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
                    AccountGroupTitle(stringResource(R.string.account_management_linked_accounts), MaterialTheme.colorScheme.secondary)

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
                            text = stringResource(R.string.account_management_add_new_providers),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!isGoogleLinked) {
                            Button(
                                onClick = { linkProvider("google.com") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
                                    contentColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.Black
                                )
                            ) {
                                ProviderBrandIcon("google.com")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.account_management_link_google), color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isMicrosoftLinked) {
                            Button(
                                onClick = { linkProvider("microsoft.com") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFF2F2F2F),
                                    contentColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            ) {
                                ProviderBrandIcon("microsoft.com")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.account_management_link_microsoft), color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.account_management_add_email_password), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
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
private fun AccountGroupTitle(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp
        ),
        color = color,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun AccountDetailItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            shape = CircleShape,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                        text = if (isVerified) stringResource(R.string.account_management_verified) else stringResource(R.string.account_management_not_verified),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(stringResource(R.string.account_management_account_format, accountName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.account_management_linked_at_format, dateStr), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                        Text(stringResource(R.string.account_management_verify), style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (canChange) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onChangeAccount) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.account_management_change), style = MaterialTheme.typography.labelMedium)
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
                        Text(stringResource(R.string.account_management_unlink), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
