package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object BrandIcons {

    val GoogleG: ImageVector by lazy {
        ImageVector.Builder(
            name = "GoogleG",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF4285F4))) {
                moveTo(23.49f, 12.27f)
                curveToRelative(0f, -0.79f, -0.07f, -1.54f, -0.19f, -2.27f)
                horizontalLineTo(12f)
                verticalLineToRelative(4.51f)
                horizontalLineToRelative(6.47f)
                curveToRelative(-0.29f, 1.48f, -1.14f, 2.73f, -2.4f, 3.58f)
                verticalLineToRelative(2.98f)
                horizontalLineToRelative(3.87f)
                curveToRelative(2.26f, -2.09f, 3.55f, -5.17f, 3.55f, -8.8f)
                close()
            }
            path(fill = SolidColor(Color(0xFF34A853))) {
                moveTo(12f, 24f)
                curveToRelative(3.24f, 0f, 5.95f, -1.08f, 7.93f, -2.91f)
                lineToRelative(-3.87f, -2.98f)
                curveToRelative(-1.08f, 0.72f, -2.45f, 1.16f, -4.06f, 1.16f)
                curveToRelative(-3.13f, 0f, -5.78f, -2.11f, -6.73f, -4.96f)
                horizontalLineTo(1.29f)
                verticalLineToRelative(3.07f)
                curveTo(3.26f, 21.3f, 7.31f, 24f, 12f, 24f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFBBC05))) {
                moveTo(5.27f, 14.31f)
                curveToRelative(-0.25f, -0.72f, -0.38f, -1.49f, -0.38f, -2.31f)
                reflectiveCurveToRelative(0.14f, -1.59f, 0.38f, -2.31f)
                verticalLineTo(6.62f)
                horizontalLineTo(1.29f)
                curveTo(0.47f, 8.24f, 0f, 10.06f, 0f, 12f)
                reflectiveCurveToRelative(0.47f, 3.76f, 1.29f, 5.38f)
                lineToRelative(3.98f, -3.07f)
                close()
            }
            path(fill = SolidColor(Color(0xFFEA4335))) {
                moveTo(12f, 4.73f)
                curveToRelative(1.76f, 0f, 3.34f, 0.6f, 4.58f, 1.79f)
                lineToRelative(3.44f, -3.44f)
                curveTo(17.94f, 1.19f, 15.24f, 0f, 12f, 0f)
                curveTo(7.31f, 0f, 3.26f, 2.7f, 1.29f, 6.62f)
                lineToRelative(3.98f, 3.07f)
                curveTo(6.22f, 6.84f, 8.87f, 4.73f, 12f, 4.73f)
                close()
            }
        }.build()
    }

    val MicrosoftLogo: ImageVector by lazy {
        ImageVector.Builder(
            name = "MicrosoftLogo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFF25022))) {
                moveTo(0f, 0f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(11f)
                horizontalLineTo(0f)
                close()
            }
            path(fill = SolidColor(Color(0xFF7FBA00))) {
                moveTo(13f, 0f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(11f)
                horizontalLineTo(13f)
                close()
            }
            path(fill = SolidColor(Color(0xFF00A4EF))) {
                moveTo(0f, 13f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(11f)
                horizontalLineTo(0f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFFB900))) {
                moveTo(13f, 13f)
                horizontalLineToRelative(11f)
                verticalLineToRelative(11f)
                horizontalLineTo(13f)
                close()
            }
        }.build()
    }
}

@Composable
fun ProviderBrandIcon(
    providerId: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val vector = when (providerId.trim().lowercase()) {
        "google.com", "google" -> BrandIcons.GoogleG
        "microsoft.com", "microsoft" -> BrandIcons.MicrosoftLogo
        else -> Icons.Default.Email
    }
    Box(modifier = modifier) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
