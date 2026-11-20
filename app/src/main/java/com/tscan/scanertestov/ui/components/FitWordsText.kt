package com.tscan.scanertestov.ui.components

/**
 * Описание: текст, который не рвёт слова посередине. Если самое длинное слово не помещается в ширину
 * (узкий экран, крупный шрифт системы), шрифт уменьшается ровно настолько, чтобы слово встало целиком
 * (не мельче [minScale] от исходного). Обычные переносы между словами остаются. Для подписей кнопок и
 * коротких заголовков карточек.
 *
 * Без SubcomposeLayout (BoxWithConstraints): такие кнопки стоят в строках, которые спрашивают «естественную»
 * ширину детей, а SubcomposeLayout её не отдаёт. Ширину узнаём при измерении и пересчитываем шрифт.
 */
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

@Composable
fun FitWordsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    minScale: Float = 0.7f,
) {
    val base = style.merge(
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            textAlign = textAlign ?: TextAlign.Unspecified,
        ),
    )
    val measurer = rememberTextMeasurer()
    var scale by remember(text, base) { mutableFloatStateOf(1f) }
    // Ширина самого длинного слова при исходном шрифте — считаем один раз на текст и стиль.
    val widest = remember(text, base) {
        if (!base.fontSize.isSpecified) 0 else text.split(' ', '\n').filter { it.isNotBlank() }
            .maxOfOrNull { w -> measurer.measure(w, base, softWrap = false, maxLines = 1).size.width } ?: 0
    }
    val st = if (scale >= 1f) base else base.copy(
        fontSize = base.fontSize * scale,
        lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else base.lineHeight,
    )
    Text(
        text = text,
        style = st,
        modifier = modifier.layout { measurable, constraints ->
            if (constraints.hasBoundedWidth && widest > 0) {
                val need = if (widest <= constraints.maxWidth) 1f
                else (constraints.maxWidth.toFloat() / widest * 0.98f).coerceAtLeast(minScale)
                if (need != scale) scale = need
            }
            val p = measurable.measure(constraints)
            layout(p.width, p.height) { p.place(0, 0) }
        },
    )
}
