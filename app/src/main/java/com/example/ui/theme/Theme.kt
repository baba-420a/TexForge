package com.example.ui.theme

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
    primary = TexForgePrimary,
    onPrimary = TexForgeOnPrimary,
    primaryContainer = TexForgePrimaryContainer,
    onPrimaryContainer = TexForgeOnPrimaryContainer,
    secondary = TexForgeSecondary,
    onSecondary = TexForgeOnSecondary,
    secondaryContainer = TexForgeSecondaryContainer,
    onSecondaryContainer = TexForgeOnSecondaryContainer,
    tertiary = TexForgeTertiary,
    onTertiary = TexForgeOnTertiary,
    background = TexForgeBackground,
    onBackground = TexForgeOnSurface,
    surface = TexForgeSurface,
    onSurface = TexForgeOnSurface,
    surfaceVariant = TexForgeSurfaceVariant,
    onSurfaceVariant = TexForgeOnSurfaceVariant,
    surfaceContainerLowest = TexForgeSurfaceContainerLowest,
    surfaceContainerLow = TexForgeSurfaceContainerLow,
    surfaceContainer = TexForgeSurfaceContainer,
    surfaceContainerHigh = TexForgeSurfaceContainerHigh,
    surfaceContainerHighest = TexForgeSurfaceContainerHighest,
    outline = TexForgeOutline,
    outlineVariant = TexForgeOutlineVariant,
    error = TexForgeError,
    onError = TexForgeOnError
)

private val LightColorScheme = darkColorScheme(
    primary = TexForgePrimary,
    onPrimary = TexForgeOnPrimary,
    background = TexForgeBackground,
    surface = TexForgeSurface,
    onSurface = TexForgeOnSurface
)


@Composable
fun TexForgeTheme(
    darkTheme: Boolean = true, // Dark theme by default
    dynamicColor: Boolean = false, // Keep distinctive TexForge forge styling
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
