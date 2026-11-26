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
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = NavySecondary,
    onSecondary = Color.White,
    secondaryContainer = NavyContainer,
    onSecondaryContainer = OnNavyContainer,
    tertiary = SavingsAmber,
    onTertiary = Color.White,
    tertiaryContainer = SavingsAmberBg,
    onTertiaryContainer = Color(0xFF78350F),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = Color(0xFF022C22),
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = Color(0xFFECFDF5),
    secondary = Color(0xFFCBD5E1),
    onSecondary = NavySecondary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF451A03),
    background = DarkBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEF2F2)
)

val CoupleCashShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CoupleCashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CoupleCashTypography,
        shapes = CoupleCashShapes,
        content = content
    )
}
