package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: текстовые блоки на листе и правила их позиционирования.
 */
import androidx.compose.ui.geometry.Offset

data class BlankTextBlock(
    val id: String,
    val label: String,
    val text: String,
    val fontSizePx: Float,
    val position: Offset,
    val isVisible: Boolean = true,
    val scale: Float = 1f,
)

sealed interface BlankEditorSelection {
    data object None : BlankEditorSelection
    data object Blank : BlankEditorSelection
    data class Text(val id: String) : BlankEditorSelection
}

fun defaultBlankTextBlocks(): List<BlankTextBlock> = listOf(
    BlankTextBlock(
        id = "title",
        label = "Название",
        text = "Тест",
        fontSizePx = 26f,
        position = Offset(70f, 70f),
    ),
    BlankTextBlock(
        id = "name",
        label = "Имя",
        text = "ФИО_____________________________",
        fontSizePx = 18f,
        position = Offset(70f, 150f),
    ),
    BlankTextBlock(
        id = "date",
        label = "Дата",
        text = "Дата_____________________________",
        fontSizePx = 18f,
        position = Offset(70f, 210f),
    ),
    BlankTextBlock(
        id = "instruction",
        label = "Инструкция",
        text = "Инструкция: закрась правильные ответы",
        fontSizePx = 18f,
        position = Offset(70f, 270f),
    ),
)

fun clampTextPositionOnSheet(position: Offset): Offset = Offset(
    x = position.x.coerceIn(6f, BlankSheetSpec.LOGICAL_WIDTH_PX - 20f),
    y = position.y.coerceIn(6f, BlankSheetSpec.LOGICAL_HEIGHT_PX - 20f),
)

fun clampTextScale(scale: Float): Float = scale.coerceIn(0.7f, 2.2f)

/** Примерный прямоугольник надписи на листе (логические единицы): ширина по числу знаков, высота по шрифту. */
private fun textBlockRect(b: BlankTextBlock, at: Offset = b.position): androidx.compose.ui.geometry.Rect {
    val size = clampTextFontSizePx(b.fontSizePx) * clampTextScale(b.scale)
    val w = (b.text.length.coerceAtLeast(4) * size * 0.56f).coerceAtMost(BlankSheetSpec.LOGICAL_WIDTH_PX - 12f)
    val h = size * 1.3f
    return androidx.compose.ui.geometry.Rect(at.x, at.y, at.x + w, at.y + h)
}

/**
 * Включили надпись — ставим её туда, где её видно: если её место занято сеткой кружков (вместе с метками
 * и шапкой бланка v2) или другой надписью, или она за краем листа — ищем ближайшее свободное место (сначала
 * рядом по вертикали, затем по всему листу). Раньше надпись появлялась на старом месте — под сдвинутым
 * бланком, — и её приходилось вытаскивать.
 */
fun placeTextBlockInFreeSpace(
    block: BlankTextBlock,
    blocks: List<BlankTextBlock>,
    layout: BlankBubbleLayout,
    pan: Offset,
    scale: Float,
): BlankTextBlock {
    val g = blankLayoutBoundingRectTransformed(layout, pan, scale)
    val o = BlankV2Print.outset(layout)
    val co = cornerMarkOutset(layout)
    val busy = ArrayList<androidx.compose.ui.geometry.Rect>()
    busy += androidx.compose.ui.geometry.Rect(
        g.left - maxOf(o[0], co) * scale, g.top - maxOf(o[1], co) * scale,
        g.right + maxOf(o[2], co) * scale, g.bottom + maxOf(o[3], co) * scale,
    )
    blocks.filter { it.isVisible && it.id != block.id }.forEach { busy += textBlockRect(it).inflate(4f) }
    fun free(at: Offset): Boolean {
        val r = textBlockRect(block, at)
        if (r.left < 6f || r.top < 6f || r.right > BlankSheetSpec.LOGICAL_WIDTH_PX - 6f || r.bottom > BlankSheetSpec.LOGICAL_HEIGHT_PX - 6f) return false
        return busy.none { it.overlaps(r) }
    }
    val start = clampTextPositionOnSheet(block.position)
    if (free(start)) return block.copy(position = start)
    // Ближе к прежнему месту по вертикали, затем — с левого поля, затем — по всему листу.
    val ys = (0..((BlankSheetSpec.LOGICAL_HEIGHT_PX - 20f) / 8f).toInt()).map { it * 8f + 10f }.sortedBy { kotlin.math.abs(it - start.y) }
    for (x in listOf(start.x, 40f, 20f)) for (y in ys) {
        val at = Offset(x, y)
        if (free(at)) return block.copy(position = at)
    }
    return block.copy(position = start)
}
fun clampTextFontSizePx(size: Float): Float = size.coerceIn(10f, 72f)

enum class AlignmentGuideAxis { Vertical, Horizontal }

data class AlignmentGuide(
    val axis: AlignmentGuideAxis,
    val logicalPos: Float,
)

fun computeAlignmentGuides(
    movingId: String,
    movingPos: Offset,
    blocks: List<BlankTextBlock>,
    thresholdLogical: Float = 6f,
): List<AlignmentGuide> {
    val guides = mutableSetOf<AlignmentGuide>()

    val centerX = BlankSheetSpec.LOGICAL_WIDTH_PX / 2f
    val centerY = BlankSheetSpec.LOGICAL_HEIGHT_PX / 2f
    if (kotlin.math.abs(movingPos.x - centerX) <= thresholdLogical) {
        guides += AlignmentGuide(AlignmentGuideAxis.Vertical, centerX)
    }
    if (kotlin.math.abs(movingPos.y - centerY) <= thresholdLogical) {
        guides += AlignmentGuide(AlignmentGuideAxis.Horizontal, centerY)
    }

    blocks
        .filter { it.id != movingId && it.isVisible }
        .forEach { other ->
            if (kotlin.math.abs(movingPos.x - other.position.x) <= thresholdLogical) {
                guides += AlignmentGuide(AlignmentGuideAxis.Vertical, other.position.x)
            }
            if (kotlin.math.abs(movingPos.y - other.position.y) <= thresholdLogical) {
                guides += AlignmentGuide(AlignmentGuideAxis.Horizontal, other.position.y)
            }
        }

    return guides.toList()
}
