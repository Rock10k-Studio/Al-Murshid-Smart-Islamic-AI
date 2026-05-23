package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ShimmeringGold,
    secondary = ClearEmerald,
    tertiary = ShimmeringGoldSoft,
    background = DarkEmeraldBg,
    surface = SoftCardDark,
    onPrimary = DarkEmeraldBg,
    onSecondary = TextLightPrimary,
    onBackground = TextLightPrimary,
    onSurface = TextLightPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = DivineGreen,
    secondary = ClearEmerald,
    tertiary = ShimmeringGold,
    background = CreamSandBg,
    surface = SoftCardLight,
    onPrimary = CreamSandBg,
    onSecondary = TextDarkPrimary,
    onBackground = TextDarkPrimary,
    onSurface = TextDarkPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
