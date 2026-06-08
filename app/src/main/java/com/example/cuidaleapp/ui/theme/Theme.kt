package com.example.cuidaleapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = CuidalePrimaryLight,
    onPrimary = CuidaleOnPrimaryLight,
    secondary = CuidalePrimaryVariantLight,
    onSecondary = CuidaleOnPrimaryLight,
    background = CuidaleSurfaceLight,
    onBackground = CuidaleOnSurfaceLight,
    surface = CuidaleSurfaceLight,
    onSurface = CuidaleOnSurfaceLight,
    surfaceVariant = CuidaleSurfaceSoftLight,
    onSurfaceVariant = CuidaleOnSurfaceSoftLight,
    outline = CuidaleBorderLight,
    error = CuidaleErrorLight,
    errorContainer = CuidaleErrorContainerLight,
    onErrorContainer = CuidaleOnSurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = CuidalePrimaryDark,
    onPrimary = CuidaleOnPrimaryDark,
    secondary = CuidalePrimaryVariantDark,
    onSecondary = CuidaleOnPrimaryDark,
    background = CuidaleSurfaceDark,
    onBackground = CuidaleOnSurfaceDark,
    surface = CuidaleSurfaceDark,
    onSurface = CuidaleOnSurfaceDark,
    surfaceVariant = CuidaleSurfaceSoftDark,
    onSurfaceVariant = CuidaleOnSurfaceSoftDark,
    outline = CuidaleBorderDark,
    error = CuidaleErrorDark,
    errorContainer = CuidaleErrorContainerDark,
    onErrorContainer = CuidaleOnSurfaceDark
)

@Immutable
data class CuidaleGradient(
    val top: Color,
    val bottom: Color
)

@Immutable
data class CuidaleShadowPalette(
    val hardShadowPrimary: Color,
    val hardShadowSecondary: Color,
    val hardShadowAccent: Color
)

@Immutable
data class CuidaleCustomColors(
    val warning: Color,
    val warningContainer: Color,
    val success: Color,
    val successStrong: Color,
    val onGradient: Color,
    val panelBackground: Color,
    val panelGradient: Brush,
    val avatarBackground: Color,
    val inputBackground: Color,
    val linkColor: Color,
    val isDark: Boolean
)

private val LocalCuidaleGradient = staticCompositionLocalOf {
    CuidaleGradient(
        top = CuidaleBlueTopLight,
        bottom = CuidaleBlueBottomLight
    )
}

private val LocalCuidaleShadowPalette = staticCompositionLocalOf {
    CuidaleShadowPalette(
        hardShadowPrimary = CuidaleShadowBlueLight,
        hardShadowSecondary = CuidaleShadowMintLight,
        hardShadowAccent = CuidaleShadowLavenderLight
    )
}

private val LocalCuidaleCustomColors = staticCompositionLocalOf {
    CuidaleCustomColors(
        warning = CuidaleWarningLight,
        warningContainer = CuidaleWarningContainerLight,
        success = CuidaleSuccessLight,
        successStrong = CuidaleSuccessStrongLight,
        onGradient = Color.White,
        panelBackground = Color.White,
        panelGradient = Brush.verticalGradient(listOf(Color.White, Color.White)),
        avatarBackground = Color.White,
        inputBackground = Color.White,
        linkColor = CuidaleLinkLight,
        isDark = false
    )
}

object CuidaleThemeExtras {
    val gradient: CuidaleGradient
        @Composable get() = LocalCuidaleGradient.current

    val shadows: CuidaleShadowPalette
        @Composable get() = LocalCuidaleShadowPalette.current

    val customColors: CuidaleCustomColors
        @Composable get() = LocalCuidaleCustomColors.current
}

@Composable
fun CuidaleAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val gradient = if (darkTheme) {
        CuidaleGradient(top = CuidaleBlueTopDark, bottom = CuidaleBlueBottomDark)
    } else {
        CuidaleGradient(top = CuidaleBlueTopLight, bottom = CuidaleBlueBottomLight)
    }

    val shadowPalette = if (darkTheme) {
        CuidaleShadowPalette(
            hardShadowPrimary = CuidaleShadowBlueDark,
            hardShadowSecondary = CuidaleShadowMintDark,
            hardShadowAccent = CuidaleShadowLavenderDark
        )
    } else {
        CuidaleShadowPalette(
            hardShadowPrimary = CuidaleShadowBlueLight,
            hardShadowSecondary = CuidaleShadowMintLight,
            hardShadowAccent = CuidaleShadowLavenderLight
        )
    }

    val panelBackground = if (darkTheme) colorScheme.surfaceVariant else Color(0xFFD6EAF8)
    val panelGradient = if (darkTheme) {
        Brush.verticalGradient(listOf(colorScheme.surfaceVariant, colorScheme.surfaceVariant))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFD6EAF8), Color(0xFFC4E0F5)))
    }

    val customColors = if (darkTheme) {
        CuidaleCustomColors(
            warning = CuidaleWarningDark,
            warningContainer = CuidaleWarningContainerDark,
            success = CuidaleSuccessDark,
            successStrong = CuidaleSuccessStrongDark,
            onGradient = Color.White,
            panelBackground = panelBackground,
            panelGradient = panelGradient,
            avatarBackground = Color(0xFF2C2F33), // Grey for dark
            inputBackground = CuidaleSurfaceDark,
            linkColor = CuidaleLinkDark,
            isDark = true
        )
    } else {
        CuidaleCustomColors(
            warning = CuidaleWarningLight,
            warningContainer = CuidaleWarningContainerLight,
            success = CuidaleSuccessLight,
            successStrong = CuidaleSuccessStrongLight,
            onGradient = Color(0xFF1A233A),
            panelBackground = panelBackground,
            panelGradient = panelGradient,
            avatarBackground = Color(0xFFC4DCE8), // Soft blue for light theme avatars
            inputBackground = Color(0xFFE0F0F8),
            linkColor = CuidaleLinkLight,
            isDark = false
        )
    }

    CompositionLocalProvider(
        LocalCuidaleGradient provides gradient,
        LocalCuidaleShadowPalette provides shadowPalette,
        LocalCuidaleCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CuidaleTypography,
            shapes = CuidaleShapes,
            content = content
        )
    }
}