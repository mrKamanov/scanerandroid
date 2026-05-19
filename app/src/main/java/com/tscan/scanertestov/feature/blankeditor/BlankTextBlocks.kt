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
