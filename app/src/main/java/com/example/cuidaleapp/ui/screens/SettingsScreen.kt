package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.example.cuidaleapp.activities.PhoneVerificationActivity
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.AppCacheManager
import com.example.cuidaleapp.viewmodel.ChatViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onBack: () -> Unit,
    onLogoutClick: () -> Unit,
    onResetPass: () -> Unit,
    onGestionarCuentas: () -> Unit = {},
    chatViewModel: ChatViewModel? = null
) {
    val context = LocalContext.current
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val userData by viewModel.userData.collectAsState()
    val darkTheme by viewModel.isDarkMode.collectAsState()
    var showCacheMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showDeviceMenu by remember { mutableStateOf(false) }


    val gradient = CuidaleThemeExtras.gradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(gradient.top, gradient.bottom)
                )
            )
    ) {
        if (showCacheMenu) {
            CacheSettingsScreen(
                userData = userData,
                chatViewModel = chatViewModel,
                onBack = { showCacheMenu = false }
            )
        } else if (showAccountMenu) {
            AccountSettingsScreen(
                userData = userData,
                onBack = { showAccountMenu = false },
                onResetPass = onResetPass,
                onLogout = { viewModel.logout { onLogoutClick() } }
            )
        } else if (showDeviceMenu) {
            DeviceSettingsScreen(
                isDark = darkTheme,
                cacheSummary = AppCacheManager.formatBytes(AppCacheManager.cacheSizeBytes(context)),
                onToggleTheme = { viewModel.toggleTheme() },
                onOpenCache = { showCacheMenu = true },
                onBack = { showDeviceMenu = false }
            )
        } else {
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.nav_settings),
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
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val isPasswordLinked = (currentUser?.providerData ?: emptyList()).any { it.providerId == "password" }
                    val emailVerified = userData.verificado ||
                        userData.linkedAccounts.any { it.verificado } ||
                        (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.isEmailVerified ?: false)
                    val pendingAccountActions =
                        (if (userData.telefono.isNotEmpty() && !userData.telefonoVerificado) 1 else 0) +
                        (if (userData.correo.isNotEmpty() && !emailVerified) 1 else 0)

                    // ── Cuenta y Seguridad ──
                    SettingsGroupLabel(stringResource(R.string.settings_account_security), MaterialTheme.colorScheme.primary)

                    SettingsRow(
                        icon = Icons.Default.AccountBox,
                        title = stringResource(R.string.settings_account_settings),
                        subtitle = stringResource(R.string.settings_account_settings_subtitle),
                        tint = MaterialTheme.colorScheme.primary,
                        trailing = {
                            if (pendingAccountActions > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(pendingAccountActions.toString(), color = MaterialTheme.colorScheme.onError)
                                }
                            }
                        },
                        onClick = { showAccountMenu = true }
                    )

                    SettingsRow(
                        icon = Icons.Default.AccountBox,
                        title = stringResource(R.string.settings_account_management),
                        subtitle = stringResource(R.string.settings_account_management_subtitle),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            context.startActivity(Intent(context, com.example.cuidaleapp.activities.AccountManagementActivity::class.java))
                        }
                    )

                    SettingsRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = stringResource(R.string.settings_datos_bancarios),
                        subtitle = stringResource(R.string.settings_datos_bancarios_subtitle),
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = onGestionarCuentas
                    )

                    if (isPasswordLinked) {
                        SettingsRow(
                            icon = Icons.Default.LockReset,
                            title = stringResource(R.string.profile_reset_pass_button),
                            subtitle = stringResource(R.string.settings_reset_pass_subtitle),
                            tint = MaterialTheme.colorScheme.secondary,
                            onClick = onResetPass
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    SettingsGroupLabel(stringResource(R.string.settings_device_options), MaterialTheme.colorScheme.primary)

                    SettingsRow(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.settings_device_options),
                        subtitle = stringResource(R.string.settings_device_options_subtitle),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { showDeviceMenu = true }
                    )

                    if (chatViewModel != null) {
                        val manualStatus by chatViewModel.myManualStatus.collectAsState()

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        SettingsGroupLabel(stringResource(R.string.settings_connection_status), MaterialTheme.colorScheme.primary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = manualStatus == null,
                                onClick = { chatViewModel.setManualStatus(null) },
                                label = { Text(stringResource(R.string.settings_online)) }
                            )
                            FilterChip(
                                selected = manualStatus == "desconectado",
                                onClick = { chatViewModel.setManualStatus("desconectado") },
                                label = { Text(stringResource(R.string.settings_offline)) }
                            )
                            FilterChip(
                                selected = manualStatus == "no_molestar",
                                onClick = { chatViewModel.setManualStatus("no_molestar") },
                                label = { Text(stringResource(R.string.settings_do_not_disturb)) }
                            )
                        }

                        Text(
                            text = stringResource(R.string.settings_inactivity_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // ── Sesión (al final) ──
                    SettingsGroupLabel(stringResource(R.string.settings_session), MaterialTheme.colorScheme.error)

                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = stringResource(R.string.profile_logout_button),
                        subtitle = stringResource(R.string.settings_logout_subtitle),
                        tint = MaterialTheme.colorScheme.error,
                        destructive = true,
                        onClick = {
                            viewModel.logout {
                                onLogoutClick()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_version_footer, stringResource(R.string.app_version), stringResource(R.string.app_name)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SettingsGroupLabel(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        ),
        color = color,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color,
    destructive: Boolean = false,
    titleBadge: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (destructive) tint else MaterialTheme.colorScheme.onSurface
                )
                if (titleBadge != null) {
                    Spacer(Modifier.width(6.dp))
                    titleBadge()
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = tint.copy(alpha = 0.6f))
    }
}

@Composable
private fun AccountSettingsScreen(
    userData: com.example.cuidaleapp.models.UserData,
    onBack: () -> Unit,
    onResetPass: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val gradient = CuidaleThemeExtras.gradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.settings_account_settings),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            val pendingPhone = userData.telefono.isNotEmpty() && !userData.telefonoVerificado
            val emailVerified = userData.verificado ||
        userData.linkedAccounts.any { it.verificado } ||
        (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.isEmailVerified ?: false)
            val pendingEmail = userData.correo.isNotEmpty() && !emailVerified
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val isPasswordLinked = (currentUser?.providerData ?: emptyList()).any { it.providerId == "password" }

            HardShadowPanel(
                backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsGroupLabel(stringResource(R.string.settings_account_security), MaterialTheme.colorScheme.primary)

                    SettingsRow(
                        icon = Icons.Default.Phone,
                        title = stringResource(R.string.settings_verify_phone_title),
                        subtitle = stringResource(R.string.settings_verify_phone_body, userData.telefono.ifBlank { "-" }),
                        tint = MaterialTheme.colorScheme.primary,
                        titleBadge = {
                            if (pendingPhone) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(stringResource(R.string.account_pending_badge), color = MaterialTheme.colorScheme.onError)
                                }
                            }
                        },
                        onClick = { context.startActivity(Intent(context, PhoneVerificationActivity::class.java)) }
                    )

                    val successTint = CuidaleThemeExtras.customColors.successStrong
                    SettingsRow(
                        icon = Icons.Default.Email,
                        title = stringResource(if (emailVerified) R.string.settings_verify_email_done else R.string.settings_verify_email_title),
                        subtitle = stringResource(
                            if (emailVerified) R.string.settings_verify_email_done_sub
                            else R.string.settings_verify_email_body, userData.correo.ifBlank { "-" }
                        ),
                        tint = if (emailVerified) successTint else MaterialTheme.colorScheme.primary,
                        titleBadge = {
                            if (emailVerified) {
                                Badge(containerColor = successTint.copy(alpha = 0.15f)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = successTint, modifier = Modifier.size(14.dp))
                                }
                            } else if (pendingEmail) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text(stringResource(R.string.account_pending_badge), color = MaterialTheme.colorScheme.onError)
                                }
                            }
                        },
                        onClick = {
                            if (!emailVerified) {
                                val intent = Intent(context, com.example.cuidaleapp.activities.EmailVerificationActivity::class.java).apply {
                                    putExtra("email", userData.correo)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )

                    SettingsRow(
                        icon = Icons.Default.AccountBox,
                        title = stringResource(R.string.settings_account_management),
                        subtitle = stringResource(R.string.settings_account_management_subtitle),
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            context.startActivity(Intent(context, com.example.cuidaleapp.activities.AccountManagementActivity::class.java))
                        }
                    )

                    SettingsRow(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.account_management_view_change_history),
                        subtitle = stringResource(R.string.account_history_see_subtitle),
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            context.startActivity(Intent(context, com.example.cuidaleapp.activities.AccountHistoryActivity::class.java))
                        }
                    )

                    if (isPasswordLinked) {
                        SettingsRow(
                            icon = Icons.Default.LockReset,
                            title = stringResource(R.string.profile_reset_pass_button),
                            subtitle = stringResource(R.string.settings_reset_pass_subtitle),
                            tint = MaterialTheme.colorScheme.tertiary,
                            onClick = onResetPass
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    SettingsGroupLabel(stringResource(R.string.settings_session), MaterialTheme.colorScheme.error)

                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = stringResource(R.string.profile_logout_button),
                        subtitle = stringResource(R.string.settings_logout_subtitle),
                        tint = MaterialTheme.colorScheme.error,
                        destructive = true,
                        onClick = onLogout
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_version_footer, stringResource(R.string.app_version), stringResource(R.string.app_name)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSettingsScreen(
    isDark: Boolean,
    cacheSummary: String,
    onToggleTheme: () -> Unit,
    onOpenCache: () -> Unit,
    onBack: () -> Unit
) {
    val gradient = CuidaleThemeExtras.gradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.settings_device_options),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            HardShadowPanel(
                backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
                shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsGroupLabel(stringResource(R.string.settings_device_theme), MaterialTheme.colorScheme.primary)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_device_theme), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.settings_device_theme_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isDark, onCheckedChange = { onToggleTheme() })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    SettingsGroupLabel(stringResource(R.string.settings_storage_cache), MaterialTheme.colorScheme.tertiary)

                    SettingsRow(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.settings_device_cache),
                        subtitle = cacheSummary,
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenCache
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheSettingsScreen(
    userData: com.example.cuidaleapp.models.UserData,
    chatViewModel: ChatViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows

    // Inicializa el singleton de caché con el contexto de la app.
    com.example.cuidaleapp.utils.ChatCacheStore.init(context)

    val currentUid = userData.uid.ifBlank {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }
    var mediaBytes by remember { mutableLongStateOf(AppCacheManager.mediaSizeBytes(context)) }
    var imagesBytes by remember { mutableLongStateOf(AppCacheManager.imagesSizeBytes(context)) }
    var chatBytes by remember { mutableLongStateOf(com.example.cuidaleapp.utils.ChatCacheStore.sizeForUser(currentUid)) }
    var busyAction by remember { mutableStateOf<String?>(null) }
    var retentionDays by remember { mutableIntStateOf(com.example.cuidaleapp.utils.ChatCacheStore.getRetentionDays()) }
    var chatsCached by remember { mutableStateOf(com.example.cuidaleapp.utils.ChatCacheStore.loadSummaries(currentUid)) }

    fun refreshSizes() {
        mediaBytes = AppCacheManager.mediaSizeBytes(context)
        imagesBytes = AppCacheManager.imagesSizeBytes(context)
        chatBytes = com.example.cuidaleapp.utils.ChatCacheStore.sizeForUser(currentUid)
        chatsCached = com.example.cuidaleapp.utils.ChatCacheStore.loadSummaries(currentUid)
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_cache_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.settings_cache_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsGroupLabel(stringResource(R.string.settings_used_locally), MaterialTheme.colorScheme.primary)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(R.string.settings_sizes_summary, AppCacheManager.formatBytes(mediaBytes), AppCacheManager.formatBytes(imagesBytes), AppCacheManager.formatBytes(chatBytes)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CacheActionRow(
                        icon = Icons.Default.VideoLibrary,
                        title = stringResource(R.string.settings_delete_media_title),
                        subtitle = stringResource(R.string.settings_delete_media_subtitle),
                        destructive = false,
                        buttonText = if (busyAction == "media") stringResource(R.string.settings_deleting) else stringResource(R.string.settings_delete),
                        enabled = busyAction == null,
                        onAction = {
                            busyAction = "media"
                            scope.launch {
                                AppCacheManager.clearMultimediaCaches(context)
                                refreshSizes()
                                busyAction = null
                            }
                        }
                    )

                    CacheActionRow(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.settings_delete_images_title),
                        subtitle = stringResource(R.string.settings_delete_images_subtitle),
                        destructive = false,
                        buttonText = if (busyAction == "images") stringResource(R.string.settings_deleting) else stringResource(R.string.settings_delete),
                        enabled = busyAction == null,
                        onAction = {
                            busyAction = "images"
                            scope.launch {
                                AppCacheManager.clearImageCaches(context)
                                refreshSizes()
                                busyAction = null
                            }
                        }
                    )

                    CacheActionRow(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = stringResource(R.string.settings_delete_chats_title),
                        subtitle = stringResource(R.string.settings_delete_chats_subtitle),
                        destructive = true,
                        buttonText = if (busyAction == "chats") stringResource(R.string.settings_deleting) else stringResource(R.string.settings_delete_all),
                        enabled = busyAction == null,
                        onAction = {
                            busyAction = "chats"
                            scope.launch {
                                if (currentUid.isNotBlank()) com.example.cuidaleapp.utils.ChatCacheStore.clearForUser(currentUid)
                                else com.example.cuidaleapp.utils.ChatCacheStore.clearAll()
                                chatViewModel?.clearAllCachedState()
                                refreshSizes()
                                busyAction = null
                            }
                        }
                    )
                }
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsGroupLabel(stringResource(R.string.settings_cache_per_chat), MaterialTheme.colorScheme.tertiary)
                    if (chatsCached.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_no_cached_chats),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        chatsCached.forEach { summary ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        summary.otherUser?.nombre?.ifBlank { stringResource(R.string.common_chat) } ?: stringResource(R.string.common_chat),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        summary.lastMessage?.text?.take(36) ?: stringResource(R.string.settings_no_messages),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            com.example.cuidaleapp.utils.ChatCacheStore.clearChat(currentUid, summary.chatId)
                                            refreshSizes()
                                        }
                                    },
                                    enabled = busyAction == null
                                ) {
                                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            HardShadowPanel(
                backgroundColor = customColors.panelBackground,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsGroupLabel(stringResource(R.string.settings_offline_retention), MaterialTheme.colorScheme.secondary)
                    Text(
                        stringResource(R.string.settings_retention_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 7, 30).forEach { days ->
                            FilterChip(
                                selected = retentionDays == days,
                                onClick = {
                                    retentionDays = days
                                    com.example.cuidaleapp.utils.ChatCacheStore.setRetentionDays(days)
                                },
                                label = { Text(if (days == 1) stringResource(R.string.settings_retention_1day) else stringResource(R.string.settings_retention_days, days), fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean,
    buttonText: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    null,
                    tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onAction, enabled = enabled) {
            Text(
                buttonText,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
