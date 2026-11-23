package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R

/**
 * Diálogo rediseñado para elegir el origen de una foto (cámara / galería)
 * o eliminarla. Reutilizable para el perfil y la ficha del familiar.
 */
@Composable
fun PhotoSourcePickerDialog(
    title: String,
    hasCurrentPhoto: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhotoSourceOption(
                    icon = Icons.Default.CameraAlt,
                    label = stringResource(R.string.source_camera),
                    subtitle = stringResource(R.string.photo_source_camera_sub),
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = enabled,
                    onClick = { if (enabled) onCamera() }
                )
                PhotoSourceOption(
                    icon = Icons.Default.Collections,
                    label = stringResource(R.string.source_gallery),
                    subtitle = stringResource(R.string.photo_source_gallery_sub),
                    tint = MaterialTheme.colorScheme.tertiary,
                    enabled = enabled,
                    onClick = { if (enabled) onGallery() }
                )
                if (hasCurrentPhoto) {
                    PhotoSourceOption(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.edit_profile_remove_photo),
                        subtitle = stringResource(R.string.photo_source_remove_sub),
                        tint = MaterialTheme.colorScheme.error,
                        enabled = enabled,
                        destructive = true,
                        onClick = { if (enabled) onRemove() }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun PhotoSourceOption(
    icon: ImageVector,
    label: String,
    subtitle: String,
    tint: Color,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(
            1.dp,
            if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (destructive) tint else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = tint.copy(alpha = 0.6f)
            )
        }
    }
}
