package com.niyammitra.payfixationcalculator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NiyamPrimary,
    onPrimary = NiyamOnPrimary,
    primaryContainer = NiyamPrimaryContainer,
    onPrimaryContainer = NiyamOnPrimaryContainer,
    secondary = NiyamSecondary,
    onSecondary = NiyamOnSecondary,
    secondaryContainer = NiyamSecondaryContainer,
    onSecondaryContainer = NiyamOnSecondaryContainer,
    tertiary = NiyamTertiary,
    onTertiary = NiyamOnTertiary,
    tertiaryContainer = NiyamTertiaryContainer,
    onTertiaryContainer = NiyamOnTertiaryContainer,
    background = NiyamOnSurfaceVariant,
    onBackground = NiyamSurface,
    surface = NiyamOnSurfaceVariant,
    onSurface = NiyamSurface,
    surfaceVariant = NiyamOnSurface,
    onSurfaceVariant = NiyamSurfaceVariant,
    outline = NiyamOutline
)

private val LightColorScheme = lightColorScheme(
    primary = NiyamPrimary,
    onPrimary = NiyamOnPrimary,
    primaryContainer = NiyamPrimaryContainer,
    onPrimaryContainer = NiyamOnPrimaryContainer,
    secondary = NiyamSecondary,
    onSecondary = NiyamOnSecondary,
    secondaryContainer = NiyamSecondaryContainer,
    onSecondaryContainer = NiyamOnSecondaryContainer,
    tertiary = NiyamTertiary,
    onTertiary = NiyamOnTertiary,
    tertiaryContainer = NiyamTertiaryContainer,
    onTertiaryContainer = NiyamOnTertiaryContainer,
    background = NiyamBackground,
    onBackground = NiyamOnBackground,
    surface = NiyamSurface,
    onSurface = NiyamOnSurface,
    surfaceVariant = NiyamSurfaceVariant,
    onSurfaceVariant = NiyamOnSurfaceVariant,
    outline = NiyamOutline
)

@Composable
fun PayFixationCalculatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}