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
    primary = VioletPrimary,
    secondary = VioletSecondary,
    tertiary = VioletTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = OnSurfaceWhite,
    onSurface = OnSurfaceWhite,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = OnSurfaceMuted
)

private val LightColorScheme = DarkColorScheme // Forced dark theme as requested by user

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme as requested by user
  dynamicColor: Boolean = false, // Force custom violet branding instead of dynamic wallpaper color
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
