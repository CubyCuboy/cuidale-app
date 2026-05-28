package com.example.cuidaleapp.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import com.example.cuidaleapp.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    isDark: Boolean,
    onEditProfile: () -> Unit,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val remoteState by viewModel.remoteState.collectAsState()
    val userData = remoteState.data
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val galleryState by viewModel.galleryState.collectAsState()

    val gradient = CuidaleThemeExtras.gradient
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.refreshProfile()
    }

    LaunchedEffect(userData) {
        // La actualización de isRefreshing ahora se maneja vía Flow en el ViewModel
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            viewModel.refreshProfile()
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
                        style = MaterialTheme.typography.headlineMedium,
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(customColors.panelBackground)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            RoundedCornerShape(32.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable {
                                    userData?.profilePhoto?.let { photo ->
                                        viewModel.openGallery(0, listOf(photo) + userData.galleryPhotos)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val profilePhoto = userData?.profilePhoto
                            val context = androidx.compose.ui.platform.LocalContext.current

                            val imageSource: Any = when {
                                profilePhoto == null -> R.drawable.profile_default
                                profilePhoto.sourceType == com.example.cuidaleapp.models.ImageSourceType.DEFAULT_AVATAR -> {
                                    val iconName = profilePhoto.cacheKey.substringAfter("res:")
                                    context.resources.getIdentifier(
                                        iconName,
                                        "drawable",
                                        context.packageName
                                    ).let {
                                        if (it != 0) it else R.drawable.profile_default
                                    }
                                }
                                else -> profilePhoto.remoteUrl ?: R.drawable.profile_default
                            }

                            AsyncImage(
                                model = imageSource,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "${userData?.nombre ?: ""} ${userData?.apellido_pa ?: ""}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = userData?.rol?.uppercase() ?: "",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (userData != null) {
                            if (!userData.descripcion.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = userData.descripcion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            ProfileInfoRow(
                                icon = Icons.Default.Email,
                                label = stringResource(R.string.profile_label_email),
                                value = userData.correo,
                                isPublic = userData.mostrarCorreo
                            )
                            ProfileInfoRow(
                                icon = Icons.Default.Badge,
                                label = stringResource(R.string.profile_label_rut),
                                value = userData.rut,
                                isPublic = userData.mostrarRut
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ProfileInfoRow(
                                        icon = Icons.Default.Phone,
                                        label = stringResource(R.string.profile_label_phone),
                                        value = if (userData.telefono.isNotBlank()) userData.telefono else "No registrado",
                                        isPublic = userData.mostrarTelefono
                                    )
                                }
                                if (userData.telefono.isNotBlank()) {
                                    val verificado = userData.telefonoVerificado
                                    Surface(
                                        color = if (verificado) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (verificado) "Verificado" else "No verificado",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (verificado) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            if (userData.galleryPhotos.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Fotos adicionales",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(userData.galleryPhotos) { index, photo ->
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                .clickable {
                                                    val allPhotos = listOfNotNull(userData.profilePhoto) + userData.galleryPhotos
                                                    viewModel.openGallery(index + 1, allPhotos)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = photo.remoteUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (photo.isPrivate) {
                                                Icon(
                                                    Icons.Default.VisibilityOff,
                                                    contentDescription = "Privada",
                                                    tint = Color.White,
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = "Pública",
                                                    tint = Color.White,
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (userData.socialLinks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Redes Sociales",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Column(modifier = Modifier.fillMaxWidth()) {
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
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        HardShadowButton(
                            text = stringResource(R.string.profile_edit_button),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shadowColor = shadows.hardShadowPrimary,
                            icon = Icons.Default.Edit,
                            isDark = isDark,
                            onClick = onEditProfile
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
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

@Composable
fun PhotoGalleryDialog(
    photos: List<com.example.cuidaleapp.models.ProfileImage>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = photos[page]
                val context = androidx.compose.ui.platform.LocalContext.current
                val imageSource: Any = if (photo.sourceType == com.example.cuidaleapp.models.ImageSourceType.DEFAULT_AVATAR) {
                    val iconName = photo.cacheKey.substringAfter("res:")
                    context.resources.getIdentifier(iconName, "drawable", context.packageName).let {
                        if (it != 0) it else R.drawable.profile_default
                    }
                } else photo.remoteUrl ?: R.drawable.profile_default

                AsyncImage(
                    model = imageSource,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Controls
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }

            // Indicator
            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(Color.Black.copy(0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun ProfileInfoRow(icon: ImageVector, label: String, value: String, isPublic: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = if (isPublic) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            contentDescription = if (isPublic) "Público" else "Privado",
            tint = if (isPublic) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}
