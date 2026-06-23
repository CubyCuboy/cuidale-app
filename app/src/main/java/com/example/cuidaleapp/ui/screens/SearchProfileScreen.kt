package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.CuidaleTextField
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.models.UserData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.SearchOff
import com.example.cuidaleapp.utils.ConnectivityObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchProfileScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val customColors = CuidaleThemeExtras.customColors
    
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(searchQuery, isOnline) {
        if (isOnline && searchQuery.length >= 3) {
            isLoading = true
            try {
                val q = searchQuery.lowercase().trim()
                val query = db.collection("usuarios")
                    .whereGreaterThanOrEqualTo("nombre", q)
                    .whereLessThanOrEqualTo("nombre", q + "\uf8ff")
                    .limit(10)
                    .get()
                    .await()
                
                searchResults = query.toObjects(UserData::class.java)
            } catch (e: Exception) {
                // Error handled
            } finally {
                isLoading = false
            }
        } else if (!isOnline) {
            searchResults = emptyList()
        } else if (searchQuery.length < 3) {
            searchResults = emptyList()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Text("Buscar Usuarios", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            CuidaleTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = if (isOnline) "Buscar por nombre..." else "Conéctate para buscar...",
                enabled = isOnline,
                containerColor = if (isOnline) customColors.inputBackground else Color.Gray.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!isOnline) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No hay conexión a internet", color = Color.White.copy(alpha = 0.6f))
                        Text("La búsqueda no está disponible offline", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(searchResults) { user ->
                        UserSearchItem(user) { onUserClick(user.uid) }
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchItem(user: UserData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CuidaleThemeExtras.customColors.panelBackground)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = if (user.fotoUrl.startsWith("res:")) null else user.fotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${user.nombre} ${user.apellido_pa}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.rol.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
