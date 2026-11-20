package com.tscan.scanertestov.ui.theme

/**
 * Описание: светлая тема приложения в стиле «Скрапбук»: картон, бумага, тёмные «чернила».
 */
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScrapbookColors = lightColorScheme(
    primary = Scrapbook.Ink,
    onPrimary = Color.White,
    primaryContainer = Scrapbook.Mint,
    onPrimaryContainer = Scrapbook.Ink,
    secondary = Color(0xFF5B5346),
    onSecondary = Color.White,
    secondaryContainer = Scrapbook.Lemon,
    onSecondaryContainer = Scrapbook.Ink,
    tertiary = Scrapbook.Coral,
    onTertiary = Color.White,
    tertiaryContainer = Scrapbook.Rose,
    onTertiaryContainer = Scrapbook.Ink,
    background = Scrapbook.Board,
    onBackground = Scrapbook.Ink,
    surface = Scrapbook.Paper,
    onSurface = Scrapbook.Ink,
    surfaceVariant = Color(0xFFF3EEE3),
    onSurfaceVariant = Color(0xFF5B5346),
    surfaceContainer = Color(0xFFFBF8F1),
    surfaceContainerHigh = Color(0xFFF6F1E6),
    surfaceContainerHighest = Color(0xFFF0EADD),
    outline = Color(0xFF8A8172),
    outlineVariant = Color(0xFFD8CFBF),
    error = Color(0xFFC0392B),
    onError = Color.White,
)

@Composable
fun ScanerTestovTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScrapbookColors,
        typography = AppTypography,
        content = content
    )
}
