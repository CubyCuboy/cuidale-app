package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.models.*

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    fichaViewModel: FichaViewModel,
    isDark: Boolean,
    onEditProfile: () -> Unit,
    onNavigateToFicha: () -> Unit,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val remoteState by viewModel.remoteState.collectAsState()
    val userData = remoteState.data
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val galleryState by viewModel.galleryState.collectAsState()
    val fichaState by fichaViewModel.state.collectAsState()

    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.refreshProfile()
        fichaViewModel.checkProfile()
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing || fichaState is FichaState.Loading,
        onRefresh = {
            viewModel.refreshProfile()
            fichaViewModel.checkProfile()
        },
        modifier = Modifier.fillMaxSize()
    ) {
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
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = customColors.onGradient
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = customColors.onGradient,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = customColors.onGradient
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (userData != null) {
                    PersonalDataCard(
                        userData = userData,
                        onEditClick = onEditProfile,
                        onPhotoClick = {
                            userData.profilePhoto?.let { photo ->
                                viewModel.openGallery(0, listOf(photo) + userData.galleryPhotos)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (val state = fichaState) {
                    is FichaState.View -> {
                        ProfileDetailView(
                            role = userData?.rol ?: "",
                            data = state.data,
                            isEditMode = false,
                            onEdit = onNavigateToFicha,
                            defaultPhoto = userData?.profilePhoto?.remoteUrl,
                            onPhotoClick = { index, photos ->
                                viewModel.openGallery(index, photos.mapNotNull { it as? ProfileImage ?: (it as? String)?.let { url -> ProfileImage(remoteUrl = url) } })
                            }
                        )
                    }
                    is FichaState.NotFound -> {
                        HardShadowPanel(
                            backgroundColor = customColors.panelBackground,
                            shadowColor = shadows.hardShadowSecondary.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No tienes una ficha técnica creada",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                HardShadowButton(
                                    text = "Crear Ficha",
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = Color.White,
                                    shadowColor = shadows.hardShadowSecondary,
                                    isDark = isDark,
                                    onClick = onNavigateToFicha
                                )
                            }
                        }
                    }
                    else -> { }
                }

                if (userData != null && userData.galleryPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Galería Personal",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = customColors.onGradient
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(userData.galleryPhotos) { index, photo ->
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        val allPhotos = listOfNotNull(userData.profilePhoto) + userData.galleryPhotos
                                        viewModel.openGallery(index + 1, allPhotos)
                                    }
                            ) {
                                key(photo.cacheKey) {
                                    AsyncImage(
                                        model = photo.remoteUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                if (userData != null && userData.socialLinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Redes Sociales",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = customColors.onGradient
                    )
                    HardShadowPanel(
                        backgroundColor = customColors.panelBackground,
                        shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.2f)
                    ) {
                        userData.socialLinks.forEach { social ->
                            ProfileInfoRow(
                                icon = when(social.platform) {
                                    SocialPlatform.INSTAGRAM -> Icons.Default.CameraAlt
                                    SocialPlatform.FACEBOOK -> Icons.Default.Facebook
                                    SocialPlatform.X -> Icons.Default.Close
                                    else -> Icons.Default.Link
                                },
                                label = social.platform.displayName,
                                value = social.link
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }

            if (galleryState.isOpen) {
                PhotoGalleryDialog(
                    photos = galleryState.photos,
                    initialIndex = galleryState.initialIndex,
                    onDismiss = { viewModel.closeGallery() }
                )
            }
        }
    }
}


