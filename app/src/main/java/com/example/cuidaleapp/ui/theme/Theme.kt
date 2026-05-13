package com.example.cuidaleapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
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
    onSecondary = CuidaleOnSurfaceDark,
    background = CuidaleSurfaceDark,
    onBackground = CuidaleOnSurfaceDark,
    surface = CuidaleSurfaceDark,
    onSurface = CuidaleOnSurfaceDark,
    surfaceVariant = CuidaleSurfaceSoftDark,
    onSurfaceVariant = CuidaleOnSurfaceSoftDark,
    outline = CuidaleBorderDark,
    error = CuidaleErrorDark,
    errorContainer = CuidaleErrorContainerDark,
    onErrorContainer = Color.White
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
    val onGradient: Color,
    val panelBackground: Color,
    val inputBackground: Color,
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
        onGradient = Color.White,
        panelBackground = Color.White.copy(alpha = 0.95f),
        inputBackground = Color.White,
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

    val panelBackground = colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.98f else 0.95f)

    val customColors = if (darkTheme) {
        CuidaleCustomColors(
            warning = CuidaleWarningDark,
            warningContainer = CuidaleWarningContainerDark,
            success = CuidaleSuccessDark,
            onGradient = Color.White,
            panelBackground = panelBackground,
            inputBackground = colorScheme.surfaceVariant.copy(alpha = 0.3f),
            isDark = true
        )
    } else {
        CuidaleCustomColors(
            warning = CuidaleWarningLight,
            warningContainer = CuidaleWarningContainerLight,
            success = CuidaleSuccessLight,
            onGradient = Color.White,
            panelBackground = panelBackground,
            inputBackground = Color.White,
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