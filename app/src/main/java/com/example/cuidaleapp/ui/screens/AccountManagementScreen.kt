package com.example.cuidaleapp.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.activities.AddEmailPasswordActivity
import com.example.cuidaleapp.activities.EmailVerificationActivity
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
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
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isChangingGoogle by remember { mutableStateOf(false) }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val newEmail = account?.email
            val newDisplayName = account?.displayName
            if (idToken != null && newEmail != null) {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    successMessage = null
                    try {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
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
                        currentUser?.reload()?.await()

                        val uid = currentUser?.uid ?: ""
                        if (uid.isNotBlank()) {
                            val updates = mutableMapOf<String, Any>(
                                "correo" to newEmail
                            )
                            if (!newDisplayName.isNullOrBlank()) {
                                val nameParts = newDisplayName.trim().split(Regex("\\s+"))
                                updates["nombre"] = nameParts.getOrNull(0) ?: ""
                                if (nameParts.size >= 3) {
                                    updates["nombre_s"] = nameParts.getOrNull(1) ?: ""
                                    updates["apellido_pa"] = nameParts.getOrNull(2) ?: ""
                                } else if (nameParts.size == 2) {
                                    updates["apellido_pa"] = nameParts[1]
                                }
                            }
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

                        viewModel.refreshFromFirestore()
                        refreshProviders()
                        successMessage = if (isChangingGoogle) "Cuenta de Google cambiada con éxito" else "Cuenta de Google vinculada con éxito"
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Error al actualizar cuenta de Google"
                    } finally {
                        isLoading = false
                        isChangingGoogle = false
                    }
                }
            } else {
                errorMessage = "No se pudo obtener la información de la cuenta de Google"
                isLoading = false
                isChangingGoogle = false
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501) {
                errorMessage = "Error en Google Sign-In: ${e.message}"
            }
            isLoading = false
            isChangingGoogle = false
        }
    }

    fun startNativeGoogleAuth(changing: Boolean) {
        if (changing && userProviders.size <= 1) {
            errorMessage = "Debes tener al menos 2 métodos de autenticación vinculados para poder cambiar este método de acceso."
            return
        }
        isChangingGoogle = changing
        isLoading = true
        errorMessage = null
        successMessage = null

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("397845476517-4uhbn38sqf9lhu5b8r075r3ns3glfful.apps.googleusercontent.com")
            .requestEmail()
            .requestProfile()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleAuthLauncher.launch(signInIntent)
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return "Fecha no disponible"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun linkProvider(providerId: String) {
        if (providerId == "google.com") {
            startNativeGoogleAuth(false)
            return
        }
        if (providerId == "password") {
            val intent = Intent(context, AddEmailPasswordActivity::class.java)
            context.startActivity(intent)
            return
        }
        if (activity == null) {
            errorMessage = "No se pudo iniciar la acción"
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null
            try {
                val provider = OAuthProvider.newBuilder(providerId)
                currentUser?.startActivityForLinkWithProvider(activity, provider.build())?.await()
                currentUser?.reload()?.await()
                viewModel.refreshFromFirestore()
                refreshProviders()
                successMessage = "Cuenta vinculada exitosamente"
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error al vincular cuenta. Verifica tu conexión o configuración."
            } finally {
                isLoading = false
            }
        }
    }

    fun unlinkProvider(providerId: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null
            try {
                if (userProviders.size <= 1) {
                    throw Exception("No puedes desvincular el único método de acceso. Tu cuenta quedaría inaccesible.")
                }
                currentUser?.unlink(providerId)?.await()
                currentUser?.reload()?.await()
                viewModel.refreshFromFirestore()
                refreshProviders()
                successMessage = "Cuenta desvinculada exitosamente"
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error al desvincular cuenta"
            } finally {
                isLoading = false
            }
        }
    }

    fun changeProvider(providerId: String) {
        if (providerId == "google.com") {
            startNativeGoogleAuth(true)
            return
        }
        if (providerId == "password") {
            val intent = Intent(context, AddEmailPasswordActivity::class.java)
            context.startActivity(intent)
            return
        }
        if (activity == null) {
            errorMessage = "No se pudo iniciar la acción"
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null
            try {
                if (userProviders.size <= 1) {
                    throw Exception("Debes tener al menos 2 métodos de autenticación vinculados para poder cambiar este método de acceso.")
                }
                val provider = OAuthProvider.newBuilder(providerId)
                currentUser?.startActivityForLinkWithProvider(activity, provider.build())?.await()
                currentUser?.reload()?.await()
                viewModel.refreshFromFirestore()
                refreshProviders()
                successMessage = "Cuenta actualizada con éxito"
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error al cambiar de cuenta"
            } finally {
                isLoading = false
            }
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
                    if (userData.correoAlternativo.isNotBlank()) {
                        AccountDetailItem("Correo Alternativo", userData.correoAlternativo)
                    }
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

                    val creationTime = currentUser?.metadata?.creationTimestamp ?: 0L
                    val sortedProviders = userProviders.sortedBy { it.providerId }

                    sortedProviders.forEach { userInfo ->
                        val providerName = when (userInfo.providerId) {
                            "google.com" -> "Google"
                            "microsoft.com" -> "Microsoft"
                            "password" -> "Correo y Contraseña"
                            else -> userInfo.providerId
                        }
                        val accountName = userInfo.email ?: userInfo.displayName ?: "N/A"

                        val isProviderVerified = when (userInfo.providerId) {
                            "google.com", "microsoft.com" -> true
                            else -> userData.verificado
                        }

                        LinkedAccountCard(
                            providerName = providerName,
                            accountName = accountName,
                            dateStr = formatTimestamp(creationTime),
                            canUnlink = userProviders.size > 1,
                            canChange = userProviders.size > 1 || userInfo.providerId == "password",
                            isVerified = isProviderVerified,
                            onUnlink = { unlinkProvider(userInfo.providerId) },
                            onChangeAccount = { changeProvider(userInfo.providerId) },
                            onVerify = {
                                val intent = Intent(context, EmailVerificationActivity::class.java).apply {
                                    putExtra("email", accountName)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    if (userData.correoAlternativo.isNotBlank() && !userProviders.any { it.providerId == "password" }) {
                        LinkedAccountCard(
                            providerName = "Correo y Contraseña",
                            accountName = userData.correoAlternativo,
                            dateStr = formatTimestamp(creationTime),
                            canUnlink = userProviders.size > 1,
                            canChange = true,
                            isVerified = userData.verificado,
                            onUnlink = { },
                            onChangeAccount = { changeProvider("password") },
                            onVerify = {
                                val intent = Intent(context, EmailVerificationActivity::class.java).apply {
                                    putExtra("email", userData.correoAlternativo)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Agregar nuevos proveedores:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val isGoogleLinked = userProviders.any { it.providerId == "google.com" }
                    val isMicrosoftLinked = userProviders.any { it.providerId == "microsoft.com" }
                    val isPasswordLinked = userProviders.any { it.providerId == "password" } || userData.correoAlternativo.isNotBlank()

                    if (!isGoogleLinked) {
                        Button(
                            onClick = { linkProvider("google.com") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("Vincular cuenta de Google", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isMicrosoftLinked) {
                        Button(
                            onClick = { linkProvider("microsoft.com") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F2F2F))
                        ) {
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
                            Text("Agregar Correo y Contraseña", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
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
                Text(providerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
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
