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
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.activities.EditProfileActivity
import com.example.cuidaleapp.activities.FichaActivity
import com.example.cuidaleapp.activities.PhoneVerificationActivity
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

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
    solicitudViewModel: com.example.cuidaleapp.viewmodel.SolicitudViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    resaltarCalificaciones: Boolean = false
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
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.88f)
                                    )
                                )
                            )
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
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                                .padding(4.dp),
                            shape = CircleShape,
                            color = Color.White,
                            tonalElevation = 8.dp,
                            border = BorderStroke(
                                3.dp,
                                Brush.linearGradient(
                                    listOf(accentColor, Color(0xFF48CAE4), accentColor)
                                )
                            )
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

                        if (userData.nombre.isBlank()) {
                            ProfileCardSkeleton()
                        } else {
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
                                        text = stringResource(R.string.profile_full_name, userData.nombre, userData.apellido_pa),
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

                                Spacer(modifier = Modifier.height(20.dp))

                                val profileProgress = calculateProfileProgress(
                                    userData = userData,
                                    hasFicha = fichaState is FichaState.View
                                )
                                val recommendations = profileRecommendations(
                                    userData = userData,
                                    hasFicha = fichaState is FichaState.View
                                )
                                AnimatedVisibility(
                                    visible = profileProgress < 100,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.profile_completion_title),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = secondaryTextColor,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "$profileProgress%",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = accentColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { profileProgress / 100f },
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                                            color = accentColor,
                                            trackColor = accentColor.copy(alpha = 0.12f)
                                        )

                                        if (recommendations.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                recommendations.forEach { rec ->
                                                    Surface(
                                                        color = if (rec.critical) {
                                                            customColors.warningContainer.copy(alpha = 0.55f)
                                                        } else {
                                                            secondaryTextColor.copy(alpha = 0.06f)
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = if (rec.critical) Icons.Default.WarningAmber else Icons.Default.Info,
                                                                contentDescription = null,
                                                                tint = if (rec.critical) customColors.warning else accentColor,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Text(
                                                                text = rec.text,
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    fontWeight = if (rec.critical) FontWeight.SemiBold else FontWeight.Normal
                                                                ),
                                                                color = if (rec.critical) customColors.warning else textColor.copy(alpha = 0.75f)
                                                            )
                                                        }
                                                    }
                                                }
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

                                // ---- Identidad y contacto ----
                                SectionLabel(stringResource(R.string.profile_section_identity), accentColor)
                                InfoItem(
                                    Icons.Default.Badge,
                                    stringResource(R.string.info_rut),
                                    userData.rut.ifEmpty { stringResource(R.string.common_not_specified) },
                                    textColor,
                                    secondaryTextColor
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InfoItem(
                                        Icons.Default.Phone,
                                        stringResource(R.string.info_phone),
                                        userData.telefono.ifEmpty { stringResource(R.string.common_not_specified) },
                                        textColor,
                                        secondaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    when {
                                        userData.telefono.isEmpty() -> {}
                                        userData.telefonoVerificado -> {
                                            Icon(
                                                Icons.Default.Verified,
                                                contentDescription = stringResource(R.string.profile_verified_cd),
                                                tint = customColors.successStrong,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        else -> {
                                            Text(
                                                stringResource(R.string.profile_not_verified),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                                color = customColors.warning,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }

                                InfoItem(
                                    Icons.Default.Place,
                                    stringResource(R.string.info_location),
                                    stringResource(R.string.profile_location_format, userData.comuna, userData.region),
                                    textColor,
                                    secondaryTextColor
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = textColor.copy(alpha = 0.06f))
                                Spacer(modifier = Modifier.height(16.dp))

                                // ---- Profesión ----
                                SectionLabel(stringResource(R.string.profile_section_profession), accentColor)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InfoItem(
                                        Icons.Default.Work,
                                        stringResource(R.string.info_role),
                                        userData.rol.replaceFirstChar { it.uppercase() },
                                        textColor,
                                        secondaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (userData.rol == "cuidador") {
                                        Surface(
                                            color = accentColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = if (userData.valorHoraReferencial > 0) {
                                                    stringResource(R.string.mapa_valor_hora, formatMiles(userData.valorHoraReferencial))
                                                } else {
                                                    stringResource(R.string.mapa_valor_a_convenir)
                                                },
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = accentColor,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                if (userData.rol == "cuidador" &&
                                    (userData.formacionAcademica.isNotEmpty() ||
                                        userData.experienciasLaborales.isNotEmpty() ||
                                        userData.certificados.isNotEmpty())
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = textColor.copy(alpha = 0.06f))
                                    Spacer(modifier = Modifier.height(16.dp))

                                    SectionLabel(stringResource(R.string.profile_section_ficha), accentColor)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FichaStatChip(
                                            icon = Icons.Default.School,
                                            count = userData.formacionAcademica.size.toString(),
                                            label = stringResource(R.string.profile_stat_formacion),
                                            accent = accentColor,
                                            primary = textColor,
                                            secondary = secondaryTextColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        FichaStatChip(
                                            icon = Icons.Default.Work,
                                            count = userData.experienciasLaborales.size.toString(),
                                            label = stringResource(R.string.profile_stat_experiencia),
                                            accent = accentColor,
                                            primary = textColor,
                                            secondary = secondaryTextColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        FichaStatChip(
                                            icon = Icons.Default.VerifiedUser,
                                            count = userData.certificados.size.toString(),
                                            label = stringResource(R.string.profile_stat_certificados),
                                            accent = accentColor,
                                            primary = textColor,
                                            secondary = secondaryTextColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.profile_ficha_tap_to_expand),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }

                                if (userData.descripcion.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SectionLabel(stringResource(R.string.profile_section_about), accentColor)
                                    Surface(
                                        color = secondaryTextColor.copy(alpha = 0.06f),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.FormatQuote,
                                                contentDescription = null,
                                                tint = accentColor.copy(alpha = 0.4f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = userData.descripcion,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    lineHeight = 20.sp
                                                ),
                                                color = textColor.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
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

                            Crossfade(
                                targetState = fichaState,
                                animationSpec = tween(350),
                                label = "fichaCrossfade"
                            ) { state ->
                                when (state) {
                                    is FichaState.Loading -> {
                                        FichaSkeleton()
                                    }
                                    is FichaState.View -> {
                                        ProfileDetailView(
                                            role = fichaRole,
                                            data = state.data,
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

                            if (userData.rol == "cuidador") {
                                Spacer(modifier = Modifier.height(24.dp))
                                var misResenas by remember { mutableStateOf<List<com.example.cuidaleapp.models.CalificacionPublica>>(emptyList()) }
                                var califOffset by remember { mutableStateOf(0) }

                                DisposableEffect(userData.uid) {
                                    val uid = userData.uid
                                    if (uid.isBlank()) {
                                        onDispose {}
                                    } else {
                                        val registration = FirebaseFirestore.getInstance()
                                            .collection("fichas").document(uid)
                                            .collection("resenas")
                                            .addSnapshotListener { snapshot, _ ->
                                                misResenas = snapshot?.documents?.mapNotNull { doc ->
                                                    com.example.cuidaleapp.models.CalificacionPublica(
                                                        autorNombre = doc.getString("autorNombre") ?: "",
                                                        calificacion = doc.getDouble("calificacion") ?: 0.0,
                                                        observacion = doc.getString("comentario") ?: "",
                                                        fecha = doc.getTimestamp("fecha")
                                                    )
                                                } ?: emptyList()
                                            }
                                        onDispose { registration.remove() }
                                    }
                                }

                                LaunchedEffect(resaltarCalificaciones) {
                                    if (resaltarCalificaciones) {
                                        delay(400)
                                        if (califOffset > 0) {
                                            scrollState.animateScrollTo((califOffset - 60).coerceAtLeast(0))
                                        } else {
                                            scrollState.animateScrollTo(scrollState.maxValue)
                                        }
                                    }
                                }

                                com.example.cuidaleapp.ui.components.CalificacionesSection(
                                    misResenas,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { califOffset = it.positionInRoot().y.toInt() },
                                    resaltada = resaltarCalificaciones
                                )
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

@Composable
private fun SectionLabel(text: String, accent: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        ),
        color = accent,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun FichaStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String,
    label: String,
    accent: Color,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = secondary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(count, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = secondary)
        }
    }
}

private fun formatMiles(valor: Int): String {
    val s = valor.toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}

private fun calculateProfileProgress(userData: UserData, hasFicha: Boolean): Int {
    var score = 0
    if (userData.fotoUrl.isNotEmpty()) score += 15
    if (userData.correo.isNotEmpty()) score += 10
    if (userData.rut.isNotEmpty()) score += 10
    if (userData.telefono.isNotEmpty() && userData.telefonoVerificado) score += 15
    if (userData.comuna.isNotEmpty() && userData.region.isNotEmpty()) score += 10
    if (userData.descripcion.isNotEmpty()) score += 20
    if (hasFicha) score += 20
    return score.coerceIn(0, 100)
}

private data class ProfileRecommendation(
    val text: String,
    val critical: Boolean = false
)

@Composable
private fun profileRecommendations(userData: UserData, hasFicha: Boolean): List<ProfileRecommendation> {
    val recommendations = mutableListOf<ProfileRecommendation>()
    val isCuidador = userData.rol.equals("cuidador", ignoreCase = true)

    if (isCuidador && userData.fotoUrl.isBlank()) {
        recommendations += ProfileRecommendation(
            text = stringResource(R.string.profile_rec_foto_cuidador),
            critical = true
        )
    }
    if (userData.telefono.isEmpty() || !userData.telefonoVerificado) {
        recommendations += ProfileRecommendation(stringResource(R.string.profile_rec_phone))
    }
    if (userData.descripcion.isBlank()) {
        recommendations += ProfileRecommendation(stringResource(R.string.profile_rec_description))
    }
    if (userData.comuna.isBlank() || userData.region.isBlank()) {
        recommendations += ProfileRecommendation(stringResource(R.string.profile_rec_location))
    }
    if (userData.rut.isBlank()) {
        recommendations += ProfileRecommendation(stringResource(R.string.profile_rec_rut))
    }
    if (!hasFicha) {
        recommendations += ProfileRecommendation(
            text = if (isCuidador) stringResource(R.string.profile_rec_ficha_cuidador)
            else stringResource(R.string.profile_rec_ficha_familiar)
        )
    }
    return recommendations
}

@Composable
private fun ShimmerSkeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val skeletonAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush())
            .alpha(skeletonAlpha)
    )
}

@Composable
private fun ProfileCardSkeleton() {
    HardShadowPanel(
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowColor = CuidaleThemeExtras.shadows.hardShadowPrimary.copy(alpha = 0.2f),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShimmerSkeleton(Modifier.size(72.dp), CircleShape)
            Spacer(Modifier.height(12.dp))
            ShimmerSkeleton(Modifier.fillMaxWidth(0.6f).height(20.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerSkeleton(Modifier.fillMaxWidth(0.4f).height(14.dp))
            Spacer(Modifier.height(20.dp))
            ShimmerSkeleton(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            ShimmerSkeleton(Modifier.fillMaxWidth().height(16.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerSkeleton(Modifier.fillMaxWidth().height(16.dp))
        }
    }
}

@Composable
private fun FichaSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerSkeleton(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(24.dp))
        ShimmerSkeleton(Modifier.fillMaxWidth(0.5f).height(18.dp))
        ShimmerSkeleton(Modifier.fillMaxWidth().height(14.dp))
        ShimmerSkeleton(Modifier.fillMaxWidth(0.8f).height(14.dp))
        ShimmerSkeleton(Modifier.fillMaxWidth(0.6f).height(14.dp))
    }
}
