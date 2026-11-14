package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.Lang

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldDark,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = Color(0xFF0C4A6E),
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
    surfaceVariant = Color(0xFFE6EEF6),
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
)

private val DarkColors = darkColorScheme(
    primary = CyberNeonEmerald,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = CyberCyan,
    onSecondary = Color(0xFF042636),
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberLight,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CrimsonLight,
    onError = Color(0xFF450A0A),
    errorContainer = CrimsonContainerDark,
    onErrorContainer = Color(0xFFFECACA),
    background = CyberVoidDark,
    onBackground = CyberTextPrimaryDark,
    surface = CyberSurfaceDark,
    onSurface = CyberTextPrimaryDark,
    surfaceVariant = CyberSurfaceElevatedDark,
    onSurfaceVariant = CyberTextSecondaryDark,
    outline = CyberBorderDark,
)

val AdwaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AdwaCyberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    lang: Lang = Lang.EN,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val typography = if (lang == Lang.AM) AdwaEthiopicTypography else AdwaTypography
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = AdwaShapes,
        content = content,
    )
}
