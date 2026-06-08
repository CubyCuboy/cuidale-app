package com.example.cuidaleapp.ui.screens

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import coil3.compose.AsyncImage
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FavoritosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialManagementScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: FavoritosViewModel = viewModel()
) {
    val managedUsers by viewModel.managedUsers.collectAsState()
    val favoritos by viewModel.favoritos.collectAsState()
    val noDeseados by viewModel.noDeseados.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Gestión Social", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("GUARDADOS (${favoritos.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("NO DESEADOS (${noDeseados.size})") }
                    )
                }
            }
        }
    ) { paddingValues ->
        val filteredUsers = remember(managedUsers, selectedTab, favoritos, noDeseados) {
            if (selectedTab == 0) {
                managedUsers.filter { favoritos.contains(it.uid) }
            } else {
                managedUsers.filter { noDeseados.contains(it.uid) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredUsers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedTab == 0) "No tienes usuarios guardados." else "No hay usuarios en tu lista negra.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredUsers) { user ->
                        SocialUserItem(
                            user = user,
                            isFavorito = favoritos.contains(user.uid),
                            isNoDeseado = noDeseados.contains(user.uid),
                            onUserClick = { onUserClick(user.uid) },
                            onToggleFav = { viewModel.toggleFavorito(user.uid) },
                            onToggleHidden = { viewModel.toggleNoDeseado(user.uid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialUserItem(
    user: UserData,
    isFavorito: Boolean,
    isNoDeseado: Boolean,
    onUserClick: () -> Unit,
    onToggleFav: () -> Unit,
    onToggleHidden: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    
    val itemInteractionSource = remember { MutableInteractionSource() }
    val isItemPressed by itemInteractionSource.collectIsPressedAsState()
    val itemScale by animateFloatAsState(
        targetValue = if (isItemPressed) 0.98f else 1f,
        animationSpec = tween(100), label = "itemScale"
    )

    HardShadowPanel(
        modifier = Modifier
            .scale(itemScale)
            .clickable(
                interactionSource = itemInteractionSource,
                indication = null,
                onClick = onUserClick
            ),
        backgroundColor = customColors.panelBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(customColors.avatarBackground)
            ) {
                if (user.fotoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = user.fotoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${user.nombre} ${user.apellido_pa}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.rol.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Actions
            Row {
                val favInteractionSource = remember { MutableInteractionSource() }
                val isFavPressed by favInteractionSource.collectIsPressedAsState()
                val favScale by animateFloatAsState(
                    targetValue = if (isFavPressed) 0.85f else 1f,
                    animationSpec = tween(100), label = "favScale"
                )

                IconButton(
                    onClick = onToggleFav,
                    interactionSource = favInteractionSource,
                    modifier = Modifier.scale(favScale)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Favorito",
                        tint = if (isFavorito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }

                val unwantedInteractionSource = remember { MutableInteractionSource() }
                val isUnwantedPressed by unwantedInteractionSource.collectIsPressedAsState()
                val unwantedScale by animateFloatAsState(
                    targetValue = if (isUnwantedPressed) 0.85f else 1f,
                    animationSpec = tween(100), label = "unwantedScale"
                )

                IconButton(
                    onClick = onToggleHidden,
                    interactionSource = unwantedInteractionSource,
                    modifier = Modifier.scale(unwantedScale)
                ) {
                    Icon(
                        imageVector = if (isNoDeseado) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "No deseado",
                        tint = if (isNoDeseado) customColors.warning else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
