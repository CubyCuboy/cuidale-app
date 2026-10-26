package com.example.cuidaleapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@Composable
fun CuidaleDrawerContent(
    nombre: String, 
    apellido: String, 
    fotoUrl: String, 
    rol: String = "", 
    isDark: Boolean, 
    onToggleTheme: () -> Unit, 
    onProfileClick: () -> Unit, 
    onFichaClick: () -> Unit, 
    onMapClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onSocialClick: () -> Unit,
    onLogoutClick: () -> Unit, 
    onCloseClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme; val customColors = CuidaleThemeExtras.customColors
    val accentColor = if (rol == "cuidador") colors.primary else customColors.successStrong
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(customColors.panelBackground)
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseClick) { 
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = colors.onSurface) 
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode, 
                    contentDescription = null, 
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = isDark, 
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primaryContainer
                    )
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp), color = colors.outlineVariant.copy(alpha = 0.3f))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(customColors.avatarBackground)
                    .border(3.dp, accentColor.copy(alpha = 0.4f), CircleShape), 
                contentAlignment = Alignment.Center
            ) {
                if (fotoUrl.isNotEmpty() && !fotoUrl.startsWith("res:")) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(fotoUrl)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person, 
                        contentDescription = null, 
                        modifier = Modifier.size(56.dp), 
                        tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .border(2.dp, customColors.panelBackground, CircleShape)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val fullName = listOf(nombre, apellido).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Usuario Cuidale" }
            
            Text(
                text = fullName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (rol.isNotEmpty()) {
                Surface(
                    color = accentColor.copy(alpha = 0.15f), 
                    shape = RoundedCornerShape(8.dp), 
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = rol.uppercase(), 
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), 
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ), 
                        color = accentColor
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Mi Cuenta",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        
        NavigationDrawerItem(
            label = { Text("Mi Perfil", fontWeight = FontWeight.Bold) }, 
            selected = false, 
            onClick = onProfileClick, 
            icon = { Icon(Icons.Default.Person, contentDescription = null) }, 
            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
        )
        NavigationDrawerItem(
            label = { Text("Ficha Técnica", fontWeight = FontWeight.Bold) }, 
            selected = false, 
            onClick = onFichaClick, 
            icon = { Icon(Icons.Default.Badge, contentDescription = null) }, 
            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Gestión",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("Red de Contactos", fontWeight = FontWeight.Bold) }, 
            selected = false, 
            onClick = onSocialClick, 
            icon = { Icon(Icons.Default.Group, contentDescription = null) }, 
            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
        )
        if (rol != "cuidador") {
            NavigationDrawerItem(
                label = { Text("Mapa de Calor", fontWeight = FontWeight.Bold) }, 
                selected = false, 
                onClick = onMapClick, 
                icon = { Icon(Icons.Default.Map, contentDescription = null) }, 
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
            )
        }
        NavigationDrawerItem(
            label = { Text("Configuración", fontWeight = FontWeight.Bold) }, 
            selected = false, 
            onClick = onSettingsClick, 
            icon = { Icon(Icons.Default.Settings, contentDescription = null) }, 
            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        HardShadowButton(
            text = "CERRAR SESIÓN", 
            containerColor = colors.error, 
            contentColor = Color.White, 
            shadowColor = colors.error.copy(alpha = 0.2f), 
            isDark = customColors.isDark, 
            onClick = onLogoutClick, 
            icon = Icons.AutoMirrored.Filled.Logout
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "v${stringResource(R.string.app_version)} • Cuidale App",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun CuidaleSearchContent(
    allRegions: List<String>,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit,
    allCommunes: List<String>,
    selectedCommunes: List<String>,
    onCommuneToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onClose: () -> Unit,
    useProfilePreferences: Boolean = false,
    onTogglePreferences: (Boolean) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val customColors = CuidaleThemeExtras.customColors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.search_button), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                if (selectedRegion.isNotEmpty() || selectedCommunes.isNotEmpty()) { TextButton(onClick = onClearFilters, modifier = Modifier.padding(start = 8.dp)) { Text(text = "Limpiar Filtros", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = colors.error) } }
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(customColors.panelBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Usar preferencias de mi ficha", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text("Aplica automáticamente tu región y comunas", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Switch(checked = useProfilePreferences, onCheckedChange = onTogglePreferences)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        CuidaleExposedDropdown(label = "Filtrar por región", options = allRegions, selectedOption = selectedRegion, onOptionSelected = onRegionSelected, placeholder = "Seleccionar región...")
        Spacer(modifier = Modifier.height(16.dp)); CuidaleMultiSelectDropdown(label = stringResource(R.string.search_filter_commune), options = allCommunes, selectedOptions = selectedCommunes, onOptionToggled = onCommuneToggle, maxSelection = 3, placeholder = if (selectedRegion.isEmpty()) "Selecciona una región primero" else stringResource(R.string.search_filter_commune), enabled = selectedRegion.isNotEmpty())
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CuidaleBottomBar(
    selectedTab: String, 
    unreadCount: Int = 0, 
    pendingNotifs: Int = 0, 
    pendingRequests: Int = 0, 
    fotoUrl: String = "",
    onTabSelected: (String) -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    Surface(
        color = customColors.panelBackground, 
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 15f
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                clip = true
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(80.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(icon = Icons.Default.Notifications, isSelected = selectedTab == "notifications", badgeCount = pendingNotifs, onClick = { onTabSelected("notifications") })
            BottomNavItem(icon = Icons.AutoMirrored.Filled.Assignment, isSelected = selectedTab == "requests", badgeCount = pendingRequests, onClick = { onTabSelected("requests") })
            BottomNavItem(icon = Icons.Default.Home, isSelected = selectedTab == "home", onClick = { onTabSelected("home") })
            BottomNavItem(icon = Icons.AutoMirrored.Filled.Chat, isSelected = selectedTab == "chats", badgeCount = unreadCount, onClick = { onTabSelected("chats") })
            BottomNavItem(icon = Icons.Default.Person, isSelected = selectedTab == "profile", onClick = { onTabSelected("profile") })
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector, 
    isSelected: Boolean, 
    onClick: () -> Unit, 
    modifier: Modifier = Modifier,
    isCenter: Boolean = false,
    badgeCount: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isCenter && isSelected -> colors.primary
            isCenter -> colors.primary.copy(alpha = 0.9f)
            isSelected -> colors.primary.copy(alpha = 0.25f)
            else -> Color.Transparent
        },
        animationSpec = tween(400),
        label = "tabBg"
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            isCenter -> Color.White
            isSelected -> colors.primary
            else -> colors.onSurface.copy(alpha = 0.4f)
        },
        animationSpec = tween(400),
        label = "tabIcon"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isSelected -> 1.25f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), 
        label = "tabScale"
    )

    Box(
        modifier = modifier
            .size(if (isCenter) 60.dp else 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(if (isCenter) 22.dp else 18.dp))
            .background(backgroundColor)
            .then(
                if (isSelected && !isCenter) {
                    Modifier.border(2.dp, colors.primary, RoundedCornerShape(18.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ), 
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(
            badge = { 
                if (badgeCount > 0) {
                    Badge(
                        containerColor = Color.Red, 
                        contentColor = Color.White,
                        modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                    ) { 
                        Text(if (badgeCount > 9) "+" else badgeCount.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = iconColor, 
                modifier = Modifier.size(if (isCenter) 30.dp else 24.dp)
            )
        }
    }
}
