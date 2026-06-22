package com.example.cuidaleapp.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@Composable
fun CuidaleImagePicker(label: String, imageUri: Uri?, onImageClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
        Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(CuidaleThemeExtras.customColors.inputBackground).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)).clickable { onImageClick() }, contentAlignment = Alignment.Center) {
            if (imageUri != null) AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            else Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
fun CuidaleMultiImagePicker(label: String, imageUris: List<Uri>, onAddImage: () -> Unit, onRemoveImage: (Uri) -> Unit, maxImages: Int = 3) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            imageUris.forEach { uri ->
                Box(modifier = Modifier.size(80.dp).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(CuidaleThemeExtras.customColors.inputBackground)) {
                    AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    IconButton(onClick = { onRemoveImage(uri) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) { Icon(imageVector = Icons.Default.Close, contentDescription = "Eliminar", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            if (imageUris.size < maxImages) {
                Box(modifier = Modifier.size(80.dp).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(CuidaleThemeExtras.customColors.inputBackground).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)).clickable { onAddImage() }, contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = "Agregar foto", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp)) }
            }
        }
    }
}
