package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: жесты панорамы бланка и тап по кружку на листе.
 */
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

@Composable
fun Modifier.blankSheetPanAndTap(
    layout: BlankBubbleLayout,
    getBlankPanLogical: () -> Offset,
    getBlankScale: () -> Float,
    onBlankPanDragStart: () -> Unit,
    onBlankPanDragAccumulatedLogical: (Offset) -> Unit,
    onBlankPanDragRevertForTap: () -> Unit,
    onBubbleIdTap: (String) -> Unit,
    onBlankTap: () -> Unit,
    onTapOutsideElements: () -> Unit,
): Modifier {
    val getPan = rememberUpdatedState(getBlankPanLogical)
    val getScale = rememberUpdatedState(getBlankScale)
    val onDragStart = rememberUpdatedState(onBlankPanDragStart)
    val onAccumulated = rememberUpdatedState(onBlankPanDragAccumulatedLogical)
    val onRevert = rememberUpdatedState(onBlankPanDragRevertForTap)
    val onBubble = rememberUpdatedState(onBubbleIdTap)
    val onBlankTapState = rememberUpdatedState(onBlankTap)
    val onTapOutsideState = rememberUpdatedState(onTapOutsideElements)
    return this.then(
        Modifier.pointerInput(layout) {
            val slop = 8f * density
            awaitEachGesture {
                val down = awaitFirstDown()
                val pointerId = down.id
                val panAtDown = getPan.value()
                onDragStart.value()

                var accLogical = Offset.Zero
                var totalScreen = Offset.Zero

                drag(pointerId) { change ->
                    val d = change.positionChange()
                    totalScreen += d
                    val scale = size.width / BlankSheetSpec.LOGICAL_WIDTH_PX
                    if (scale > 0f) {
                        accLogical += Offset(d.x / scale, d.y / scale)
                        onAccumulated.value(accLogical)
                    }
                    change.consume()
                }

                if (totalScreen.getDistance() < slop) {
                    onRevert.value()
                    val scale = size.width / BlankSheetSpec.LOGICAL_WIDTH_PX
                    if (scale > 0f) {
                        val sheetPoint = Offset(down.position.x / scale, down.position.y / scale)
                        val objectScale = clampBlankScale(getScale.value())
                        val bubbleId = hitTestBubbleIdTransformed(
                            layout = layout,
                            sheetPoint = sheetPoint,
                            pan = panAtDown,
                            scale = objectScale,
                        )
                        when {
                            bubbleId != null -> onBubble.value(bubbleId)
                            hitTestBlankObjectTransformed(
                                layout = layout,
                                sheetPoint = sheetPoint,
                                pan = panAtDown,
                                scale = objectScale,
                            ) -> onBlankTapState.value()
                            else -> onTapOutsideState.value()
                        }
                    }
                }
            }
        }
    )
}
