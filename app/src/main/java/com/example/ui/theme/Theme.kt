package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldDark,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = AmberAccent,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF78350F),
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = Color(0xFF991B1B),
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
)

private val DarkColors = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = CrimsonContainerDark,
    onErrorContainer = Color(0xFFFECACA),
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
)

val AdwaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AdwaCyberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AdwaTypography,
        shapes = AdwaShapes,
        content = content,
    )
}
