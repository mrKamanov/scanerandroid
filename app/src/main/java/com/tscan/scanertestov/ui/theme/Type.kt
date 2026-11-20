package com.tscan.scanertestov.ui.theme

/**
 * Описание: типографика «Скрапбук»: крупные заголовки — рукописный Amatic SC,
 * всё остальное — округлый Nunito (хорошо читается мелким кеглем).
 */
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

private fun TextStyle.body(weight: FontWeight = FontWeight.Medium) =
    copy(fontFamily = Scrapbook.BodyFont, fontWeight = weight)

private fun hand(size: Int, line: Int) = TextStyle(
    fontFamily = Scrapbook.HandFont,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val AppTypography = Typography(
    displayLarge = hand(57, 60),
    displayMedium = hand(45, 48),
    displaySmall = hand(40, 42),
    headlineLarge = hand(40, 42),
    headlineMedium = hand(36, 38),
    headlineSmall = hand(32, 34),
    titleLarge = hand(30, 32),
    titleMedium = base.titleMedium.body(FontWeight.Bold),
    titleSmall = base.titleSmall.body(FontWeight.Bold),
    bodyLarge = base.bodyLarge.body(),
    bodyMedium = base.bodyMedium.body(),
    bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body(FontWeight.Bold),
    labelMedium = base.labelMedium.body(FontWeight.Bold),
    labelSmall = base.labelSmall.body(FontWeight.Bold),
)
