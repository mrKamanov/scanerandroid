package com.tscan.scanertestov.ui.theme

/**
 * Описание: палитра и шрифты стиля «Скрапбук» (бумажки, скотч, рукописные заголовки).
 * Шрифты: Amatic SC и Nunito, лицензия SIL OFL 1.1 (тексты лицензий — assets/licenses).
 */
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tscan.scanertestov.R

object Scrapbook {
    val HandFont: FontFamily = FontFamily(Font(R.font.amatic_sc_bold, FontWeight.Bold))
    val BodyFont: FontFamily = FontFamily(
        // Nunito: округлый, но с обычной кириллицей (у Comfortaa «т»/«д» читались как «m»/«g»).
        Font(R.font.nunito_medium, FontWeight.Normal),
        Font(R.font.nunito_medium, FontWeight.Medium),
        Font(R.font.nunito_bold, FontWeight.SemiBold),
        Font(R.font.nunito_bold, FontWeight.Bold),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    )

    val Board = Color(0xFFE9E2D4)
    val BoardDot = Color(0x0F000000)
    val Ink = Color(0xFF2B2B2B)
    val InkMuted = Color(0xFF5B5346)
    val AccentInk = Color(0xFFB5452F)
    val Paper = Color(0xFFFFFFFF)
    val Mint = Color(0xFFA8E6CF)
    val Peach = Color(0xFFFFD3B6)
    val Lemon = Color(0xFFFFF3A0)
    val Lavender = Color(0xFFC7CEFF)
    val Rose = Color(0xFFFFB7C5)
    val Sky = Color(0xFFD9F2FF)
    val Coral = Color(0xFFFF6F61)

    val TapeYellow = Color(0xBFFFD65A)
    val TapeWhite = Color(0xB3FFFFFF)
    val TapeBlue = Color(0x9978C8FF)
    val TapePink = Color(0x99FF8CA0)
    val TapeMint = Color(0xCCA8E6CF)
}
