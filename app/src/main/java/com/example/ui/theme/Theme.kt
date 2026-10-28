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
    primary = ForgeCyanPrimary,
    onPrimary = ForgeCyanOnPrimary,
    primaryContainer = ForgeCyanContainer,
    onPrimaryContainer = ForgeCyanOnContainer,
    secondary = ForgeAmberSecondary,
    onSecondary = ForgeAmberOnSecondary,
    secondaryContainer = ForgeAmberContainer,
    onSecondaryContainer = ForgeAmberOnContainer,
    tertiary = ForgeTealTertiary,
    onTertiary = ForgeTealOnTertiary,
    tertiaryContainer = ForgeTealContainer,
    onTertiaryContainer = ForgeTealOnContainer,
    background = ForgeDarkBg,
    onBackground = ForgeTextPrimary,
    surface = ForgeDarkSurface,
    onSurface = ForgeTextPrimary,
    surfaceVariant = ForgeSurfaceVariant,
    onSurfaceVariant = ForgeTextSecondary,
    surfaceContainer = ForgeSurfaceContainer,
    surfaceContainerHigh = ForgeSurfaceContainerHigh,
    surfaceContainerHighest = ForgeSurfaceContainerHighest,
    outline = ForgeOutline,
    outlineVariant = ForgeOutlineVariant,
    error = ForgeError,
    onError = ForgeOnError
)

private val LightColorScheme = lightColorScheme(
    primary = ForgeCyanContainer,
    onPrimary = ForgeCyanOnContainer,
    secondary = ForgeAmberSecondary,
    background = ForgeDarkBg,
    surface = ForgeDarkSurface,
    onSurface = ForgeTextPrimary
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
