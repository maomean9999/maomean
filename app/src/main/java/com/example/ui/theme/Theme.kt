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

private val DarkColorScheme =
  darkColorScheme(
    primary = CelestialBlue,
    secondary = WarmAmber,
    tertiary = GlowingCyan,
    background = SpaceBlack,
    surface = DarkSlate,
    surfaceVariant = SlateCard,
    onPrimary = PolarWhite,
    onSecondary = SpaceBlack,
    onBackground = PolarWhite,
    onSurface = PolarWhite,
    outline = BorderSlate
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CelestialBlue,
    secondary = WarmAmber,
    tertiary = GlowingCyan,
    background = SpaceBlack,
    surface = DarkSlate,
    surfaceVariant = SlateCard,
    onPrimary = PolarWhite,
    onSecondary = SpaceBlack,
    onBackground = PolarWhite,
    onSurface = PolarWhite,
    outline = BorderSlate
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme by default for elite audio-video workspace look
  dynamicColor: Boolean = false, // Preserve carefully-chosen branding palette
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
