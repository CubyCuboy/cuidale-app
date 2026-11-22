package com.example.cuidaleapp.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.activities.EditProfileActivity
import com.example.cuidaleapp.activities.FichaActivity
import com.example.cuidaleapp.activities.PhoneVerificationActivity
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel

import com.example.cuidaleapp.utils.ConnectivityObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    fichaViewModel: FichaViewModel,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onSocialClick: () -> Unit,
    onTabSelected: (String) -> Unit = {},
    unreadCount: Int = 0,
    pendingNotifs: Int = 0,
    solicitudViewModel: com.example.cuidaleapp.viewmodel.SolicitudViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val userData by viewModel.userData.collectAsState()
    val fichaState by fichaViewModel.state.collectAsState()
    val fichaRole by fichaViewModel.role.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var isFichaExpanded by remember { mutableStateOf(false) }

    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val accentColor = if (userData.rol == "cuidador") MaterialTheme.colorScheme.primary else customColors.successStrong
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val panelBg = customColors.panelBackground

    Column(
        modifier = Modifier.fillMaxSize()
    ) {



        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = { 
                if (!isFichaExpanded) {
                    viewModel.refreshFromFirestore()
                    fichaViewModel.checkProfile()
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(accentColor)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.profile_my_profile_upper),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(Icons.Default.Settings, stringResource(R.string.cd_settings), tint = Color.White)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .offset(y = (-40).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(4.dp),
                            shape = CircleShape,
                            color = Color.White,
                            tonalElevation = 8.dp
                        ) {
                            if (userData.fotoUrl.isNotEmpty()) {
                                coil3.compose.SubcomposeAsyncImage(
                                    model = userData.fotoUrl,
                                    contentDescription = stringResource(R.string.cd_profile),
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(Modifier.fillMaxSize().background(shimmerBrush()), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = accentColor)
                                        }
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = secondaryTextColor)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        HardShadowPanel(
                            backgroundColor = panelBg,
                            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                            cornerRadius = 24.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${userData.nombre} ${userData.apellido_pa}",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = textColor
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Text(
                                        text = userData.correo,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = secondaryTextColor,
                                        textAlign = TextAlign.Center
                                    )

                                    if (userData.rol == "cuidador") {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        com.example.cuidaleapp.ui.components.VerificationBadge(userData.verificacionOficial)
                                    }

                                    if (userData.telefono.isNotEmpty() && !userData.telefonoVerificado) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(
                                            color = customColors.warningContainer,
                                            shape = RoundedCornerShape(12.dp),
                                            onClick = {
                                                context.startActivity(Intent(context, PhoneVerificationActivity::class.java))
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(14.dp), tint = customColors.warning)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    stringResource(R.string.profile_verify_now),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = customColors.warning
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                
                                HardShadowButton(
                                    text = stringResource(R.string.profile_edit_button_upper),
                                    onClick = { context.startActivity(Intent(context, EditProfileActivity::class.java)) },
                                    containerColor = accentColor,
                                    contentColor = Color.White,
                                    shadowColor = shadowPalette.hardShadowPrimary,
                                    isDark = customColors.isDark,
                                    icon = Icons.Default.Edit,
                                    cornerRadius = 16.dp
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                                Spacer(modifier = Modifier.height(20.dp))

                                InfoItem(Icons.Default.Badge, stringResource(R.string.info_rut), userData.rut, textColor, secondaryTextColor)
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InfoItem(
                                        Icons.Default.Phone, 
                                        stringResource(R.string.info_phone), 
                                        userData.telefono.ifEmpty { stringResource(R.string.common_not_specified) }, 
                                        textColor, 
                                        secondaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (userData.telefono.isNotEmpty() && !userData.telefonoVerificado) {
                                        Text(
                                            stringResource(R.string.profile_not_verified),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                            color = customColors.warning,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                                
                                InfoItem(Icons.Default.Place, stringResource(R.string.info_location), "${userData.comuna}, ${userData.region}", textColor, secondaryTextColor)
                                InfoItem(Icons.Default.Work, stringResource(R.string.info_role), userData.rol.replaceFirstChar { it.uppercase() }, textColor, secondaryTextColor)

                                if (userData.descripcion.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.FormatQuote,
                                            contentDescription = null,
                                            tint = accentColor.copy(alpha = 0.4f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = userData.descripcion,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                lineHeight = 20.sp
                                            ),
                                            color = textColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = if (userData.rol == "cuidador") stringResource(R.string.profile_my_ficha_technical) else stringResource(R.string.profile_familiar_ficha),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                            )

                            when (fichaState) {
                                is FichaState.Loading -> {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = accentColor, modifier = Modifier.padding(24.dp))
                                    }
                                }
                                is FichaState.View -> {
                                    ProfileDetailView(
                                        role = fichaRole,
                                        data = (fichaState as FichaState.View).data,
                                        isSummary = !isFichaExpanded,
                                        onExpand = { isFichaExpanded = !isFichaExpanded },
                                        onEdit = { 
                                            val intent = Intent(context, FichaActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                                is FichaState.NotFound -> {
                                    FichaPlaceholder(
                                        role = userData.rol,
                                        accentColor = accentColor,
                                        panelBg = panelBg,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor,
                                        onCreateClick = {
                                            context.startActivity(Intent(context, FichaActivity::class.java))
                                        }
                                    )
                                }
                                else -> { }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FichaPlaceholder(
    role: String,
    accentColor: Color,
    panelBg: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onCreateClick: () -> Unit
) {
    val shadowPalette = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    
    HardShadowPanel(
        backgroundColor = panelBg,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.15f),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (role == "cuidador") Icons.Default.Description else Icons.Default.FamilyRestroom,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = accentColor.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (role == "cuidador") 
                    stringResource(R.string.profile_ficha_placeholder_title_caregiver) 
                else 
                    stringResource(R.string.profile_ficha_placeholder_title_rep),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (role == "cuidador")
                    stringResource(R.string.profile_ficha_placeholder_desc_caregiver)
                else
                    stringResource(R.string.profile_ficha_placeholder_desc_rep),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.height(24.dp))
            HardShadowButton(
                text = stringResource(R.string.profile_create_ficha),
                onClick = onCreateClick,
                containerColor = accentColor,
                contentColor = Color.White,
                shadowColor = shadowPalette.hardShadowPrimary,
                isDark = customColors.isDark,
                icon = Icons.Default.Add
            )
        }
    }
}

@Composable
fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    value: String, 
    primary: Color, 
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = secondary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = secondary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = secondary, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = primary)
        }
    }
}
