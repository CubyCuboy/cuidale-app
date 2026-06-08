package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.ui.components.CuidaleBottomBar
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.viewmodel.FavoritosViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun ChatsScreen(
    onTabSelected: (String) -> Unit,
    onUserClick: (String) -> Unit,
    favoritosViewModel: FavoritosViewModel = viewModel()
) {
    val gradient = CuidaleThemeExtras.gradient
    val managedUsers by favoritosViewModel.managedUsers.collectAsState()
    val favoritos by favoritosViewModel.favoritos.collectAsState()
    val noDeseados by favoritosViewModel.noDeseados.collectAsState()
    val customColors = CuidaleThemeExtras.customColors

    val savedUsers = managedUsers.filter { favoritos.contains(it.uid) }
    val unwantedUsers = managedUsers.filter { noDeseados.contains(it.uid) }
    
    Scaffold(
        bottomBar = {
            CuidaleBottomBar(selectedTab = "chats", onTabSelected = onTabSelected)
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Text(
                    text = "Mensajes",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )

                if (savedUsers.isNotEmpty()) {
                    Text(
                        text = "USUARIOS GUARDADOS",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(savedUsers) { user ->
                            HardShadowPanel(
                                backgroundColor = customColors.panelBackground,
                                modifier = Modifier.clickable { onUserClick(user.uid) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp).clip(CircleShape).background(customColors.avatarBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.fotoUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = user.fotoUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${user.nombre} ${user.apellido_pa}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Toca para ver perfil y solicitar chat",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = customColors.linkColor)
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.White.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No tienes usuarios guardados",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Guarda perfiles que te interesen para contactarlos aquí.",
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // No deseados section
                if (unwantedUsers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "NO DESEADOS",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                        color = customColors.warning.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(unwantedUsers) { user ->
                            HardShadowPanel(
                                backgroundColor = customColors.warningContainer.copy(alpha = 0.3f),
                                shadowColor = customColors.warning.copy(alpha = 0.1f),
                                modifier = Modifier.clickable { onUserClick(user.uid) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(customColors.avatarBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.fotoUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = user.fotoUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${user.nombre} ${user.apellido_pa}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = customColors.warning
                                        )
                                        Text(
                                            "Usuario no deseado",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = customColors.warning, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
