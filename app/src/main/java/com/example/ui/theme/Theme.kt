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

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = OnNavyPrimaryContainer,
    secondary = BurnishedGold,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = SlateTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SlateTertiaryContainer,
    onTertiaryContainer = OnSlateTertiaryContainer,
    background = PaperBackground,
    onBackground = InkOnSurface,
    surface = PaperSurface,
    onSurface = InkOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = HairlineOutline,
    outlineVariant = HairlineOutlineVariant,
    error = StatusUnpaidText,
    onError = Color.White,
    errorContainer = StatusUnpaidBg,
    onErrorContainer = StatusUnpaidText
)

private val DarkColorScheme = darkColorScheme(
    primary = BurnishedGoldBright,
    onPrimary = NavyPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnSurface,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = Color(0xFF3A290E),
    onSecondaryContainer = GoldContainer,
    tertiary = Color(0xFF7CB7FF),
    onTertiary = DarkOnPrimary,
    tertiaryContainer = Color(0xFF1A3459),
    onTertiaryContainer = SlateTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF21314A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val HmGrupShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HmGrupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = HmGrupShapes,
        content = content
    )
}
