package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: виджет листа A4 с бланком, направляющими и текстовыми блоками.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun A4SheetPage(
    layoutConfig: BlankEditorLayoutConfig,
    renderParams: BlankBubbleRenderParams,
    hiddenBubbleIds: Set<String>,
    blankPanLogical: Offset,
    blankScale: Float,
    textBlocks: List<BlankTextBlock>,
    showVariantMarker: Boolean,
    showStudentNameMarker: Boolean,
    studentNamePrintedMode: Boolean,
    studentSurnameText: String,
    studentNameText: String,
    variantPrintedMode: Boolean,
    variantPrintedNumber: String,
    showTextElements: Boolean,
    alignmentGuides: List<AlignmentGuide>,
    selection: BlankEditorSelection,
    onBlankPanDragStart: () -> Unit,
    onBlankPanDragAccumulatedLogical: (Offset) -> Unit,
    onBlankPanDragRevertForTap: () -> Unit,
    onTextBlockTap: (String) -> Unit,
    onTextBlockDragStart: (String) -> Unit,
    onTextBlockDragDeltaLogical: (String, Offset) -> Unit,
    onTextBlockDragEnd: (String) -> Unit,
    onBlankTap: () -> Unit,
    onTapOutsideElements: () -> Unit,
    onBubbleToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 8.dp,
) {
    val bubbleLayout = remember(layoutConfig, renderParams) {
        buildBlankBubbleLayout(layoutConfig, renderParams)
    }
    val outline = Color(0xFF2E3440)
    val paper = Color(0xFFFEFEFE)
    val shadowElev = 6.dp

    val getPanUpdated = rememberUpdatedState(blankPanLogical)
    val getScaleUpdated = rememberUpdatedState(blankScale)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RectangleShape),
    ) {
        val innerW = maxWidth - horizontalPadding * 2
        val innerH = maxHeight - verticalPadding * 2
        val r = BlankSheetSpec.aspectRatio

        val (sheetW, sheetH) =
            if (innerW / innerH > r) {
                val h = innerH
                val w = h * r
                w to h
            } else {
                val w = innerW
                val h = w / r
                w to h
            }
        val sheetToScreen = with(density) { sheetW.toPx() / BlankSheetSpec.LOGICAL_WIDTH_PX }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(sheetW, sheetH)
                .blankSheetPanAndTap(
                    layout = bubbleLayout,
                    getBlankPanLogical = { getPanUpdated.value },
                    getBlankScale = { getScaleUpdated.value },
                    onBlankPanDragStart = onBlankPanDragStart,
                    onBlankPanDragAccumulatedLogical = onBlankPanDragAccumulatedLogical,
                    onBlankPanDragRevertForTap = onBlankPanDragRevertForTap,
                    onBubbleIdTap = onBubbleToggle,
                    onBlankTap = onBlankTap,
                    onTapOutsideElements = onTapOutsideElements,
                )
                .shadow(shadowElev, RoundedCornerShape(2.dp), ambientColor = Color.Black.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(2.dp))
                .background(paper)
                .border(width = 2.dp, color = outline, shape = RoundedCornerShape(2.dp)),
        ) {
            BlankBubbleCanvas(
                layout = bubbleLayout,
                hiddenBubbleIds = hiddenBubbleIds,
                panLogical = blankPanLogical,
                blankScale = blankScale,
                isBlankSelected = selection == BlankEditorSelection.Blank,
                modifier = Modifier.fillMaxSize(),
            )

            if (showVariantMarker) {
                val marker = computeBlankVariantMarkerRect(
                    layout = bubbleLayout,
                    blankPanLogical = blankPanLogical,
                    blankScale = blankScale,
                )
                val markerSizeDp = with(density) { (marker.size * sheetToScreen).toDp() }
                val markerX = marker.left * sheetToScreen
                val markerY = marker.top * sheetToScreen
                Box(
                    modifier = Modifier
                        .offset { IntOffset(markerX.roundToInt(), markerY.roundToInt()) }
                        .size(markerSizeDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            val points = buildVariantHexagonPoints(size.width)
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                            close()
                        }
                        drawPath(path = path, color = Color.White)
                        drawPath(path = path, color = Color(0xFF2E3440), style = Stroke(width = 2f))
                    }
                    if (variantPrintedMode) {
                        Text(
                            text = variantPrintedNumber.ifBlank { "1" },
                            style = TextStyle(
                                color = Color(0xFF2E3440),
                                fontSize = with(density) { (16f * sheetToScreen).toSp() },
                            ),
                        )
                    }
                }
            }

            if (showStudentNameMarker) {
                val marker = computeBlankStudentNameMarkerRect(
                    layout = bubbleLayout,
                    blankPanLogical = blankPanLogical,
                    blankScale = blankScale,
                )
                val markerWidthDp = with(density) { (marker.width * sheetToScreen).toDp() }
                val markerHeightDp = with(density) { (marker.height * sheetToScreen).toDp() }
                val markerX = marker.left * sheetToScreen
                val markerY = marker.top * sheetToScreen
                Box(
                    modifier = Modifier
                        .offset { IntOffset(markerX.roundToInt(), markerY.roundToInt()) }
                        .size(markerWidthDp, markerHeightDp)
                        .border(2.dp, Color(0xFF2E3440), RectangleShape),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val midX = size.width / 2f
                        drawLine(
                            color = Color(0xFF2E3440),
                            start = Offset(midX, 0f),
                            end = Offset(midX, size.height),
                            strokeWidth = 2f,
                        )
                    }
                    if (studentNamePrintedMode) {
                        Text(
                            text = studentSurnameText,
                            style = TextStyle(
                                color = Color(0xFF2E3440),
                                fontSize = with(density) { (10f * sheetToScreen).toSp() },
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp),
                        )
                        Text(
                            text = studentNameText,
                            style = TextStyle(
                                color = Color(0xFF2E3440),
                                fontSize = with(density) { (10f * sheetToScreen).toSp() },
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = markerWidthDp / 2f + 8.dp),
                        )
                    }
                }
            }

            if (alignmentGuides.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    alignmentGuides.forEach { guide ->
                        when (guide.axis) {
                            AlignmentGuideAxis.Vertical -> {
                                val x = guide.logicalPos * sheetToScreen
                                drawLine(
                                    color = Color(0xFF88C0D0),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 2f,
                                )
                            }
                            AlignmentGuideAxis.Horizontal -> {
                                val y = guide.logicalPos * sheetToScreen
                                drawLine(
                                    color = Color(0xFF88C0D0),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 2f,
                                )
                            }
                        }
                    }
                }
            }

            val selectionBorder = Color(0xFF5E81AC)
            textBlocks
                .filter { showTextElements && it.isVisible }
                .forEach { block ->
                val isSelected = selection == BlankEditorSelection.Text(block.id)
                val blockScreenX = block.position.x * sheetToScreen
                val blockScreenY = block.position.y * sheetToScreen
                val fontSizeSp = with(density) {
                    (clampTextFontSizePx(block.fontSizePx) * sheetToScreen).toSp()
                }
                Text(
                    text = block.text,
                    style = TextStyle(
                        fontSize = fontSizeSp,
                        color = Color(0xFF2E3440),
                    ),
                    modifier = Modifier
                        .offset { IntOffset(blockScreenX.roundToInt(), blockScreenY.roundToInt()) }
                        .graphicsLayer(
                            scaleX = block.scale,
                            scaleY = block.scale,
                        )
                        .then(
                            if (isSelected) Modifier.border(1.dp, selectionBorder, RoundedCornerShape(4.dp))
                            else Modifier
                        )
                        .pointerInput(block.id, sheetToScreen) {
                            detectDragGestures(
                                onDragStart = {
                                    onTextBlockDragStart(block.id)
                                    onTextBlockTap(block.id)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val logicalDelta = Offset(
                                        x = dragAmount.x / sheetToScreen,
                                        y = dragAmount.y / sheetToScreen,
                                    )
                                    onTextBlockDragDeltaLogical(block.id, logicalDelta)
                                },
                                onDragCancel = {},
                                onDragEnd = {
                                    onTextBlockDragEnd(block.id)
                                },
                            )
                        }
                        .pointerInput(block.id) {
                            detectTapGestures(
                                onTap = {
                                    onTextBlockTap(block.id)
                                }
                            )
                        }
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                )
            }
        }
    }
}
