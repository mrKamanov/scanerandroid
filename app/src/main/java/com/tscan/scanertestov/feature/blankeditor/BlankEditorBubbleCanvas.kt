package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: отрисовка сетки бланка на Canvas с учётом панорамы и масштаба.
 */
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.toColorInt

@Composable
fun BlankBubbleCanvas(
    layout: BlankBubbleLayout,
    hiddenBubbleIds: Set<String>,
    panLogical: Offset,
    blankScale: Float,
    isBlankSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val frameFill = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = "#FFFFFFFF".toColorInt()
        }
    }
    val frameStroke = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.MITER
            color = "#FF2E3440".toColorInt()
        }
    }
    val bubbleFill = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = "#FFFFFFFF".toColorInt()
        }
    }
    val bubbleStroke = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#FF2E3440".toColorInt()
        }
    }
    val bubbleEmptyFill = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = "#FFF0F0F0".toColorInt()
        }
    }
    val bubbleEmptyStroke = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#FFCCCCCC".toColorInt()
        }
    }
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = "#FF2E3440".toColorInt()
        }
    }
    val bubbleHiddenFill = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = "#FFF7FAFC".toColorInt()
        }
    }
    val bubbleHiddenStroke = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#FFCBD5E0".toColorInt()
        }
    }
    val textHiddenPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = "#FFA0AEC0".toColorInt()
        }
    }
    val bubbleEmptyHiddenFill = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = "#FFE8E8E8".toColorInt()
        }
    }
    val bubbleEmptyHiddenStroke = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#FFBFBFBF".toColorInt()
        }
    }
    val selectedOutlineStroke = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#FF5E81AC".toColorInt()
        }
    }
    val cornerMarkStroke = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.MITER
            strokeCap = Paint.Cap.SQUARE
            color = "#FF000000".toColorInt()
        }
    }

    Canvas(modifier = modifier) {
        val scale = size.width / BlankSheetSpec.LOGICAL_WIDTH_PX
        val renderParams = layout.renderParams
        val bubbleR = renderParams.bubbleSizePx / 2f
        frameStroke.strokeWidth = renderParams.borderWidthPx
        bubbleStroke.strokeWidth = BlankBubbleDefaults.BUBBLE_STROKE_PX
        bubbleEmptyStroke.strokeWidth = BlankBubbleDefaults.BUBBLE_STROKE_PX
        bubbleHiddenStroke.strokeWidth = BlankBubbleDefaults.BUBBLE_STROKE_PX
        bubbleEmptyHiddenStroke.strokeWidth = BlankBubbleDefaults.BUBBLE_STROKE_PX
        selectedOutlineStroke.strokeWidth = 3f
        val textSize = renderParams.fontSizePx * BlankBubbleDefaults.BUBBLE_FONT_SCALE
        textPaint.textSize = textSize
        textHiddenPaint.textSize = textSize

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.scale(scale, scale)
            nc.translate(panLogical.x, panLogical.y)
            val c = blankLayoutCenter(layout)
            nc.translate(c.x, c.y)
            nc.scale(blankScale, blankScale)
            nc.translate(-c.x, -c.y)

            for (f in layout.frames) {
                val rect = RectF(f.left, f.top, f.left + f.width, f.top + f.height)
                nc.drawRect(rect, frameFill)
                nc.drawRect(rect, frameStroke)
            }
            cornerMarkStroke.strokeWidth = maxOf(renderParams.borderWidthPx * 1.45f, 3f)
            drawBlankBubbleLayoutCornerMarksOnCanvas(nc, layout, cornerMarkStroke)

            for (b in layout.bubbles) {
                val hidden = b.id in hiddenBubbleIds
                val fill: Paint
                val stroke: Paint
                when {
                    hidden && b.isEmptyPlaceholder -> {
                        fill = bubbleEmptyHiddenFill
                        stroke = bubbleEmptyHiddenStroke
                    }
                    hidden -> {
                        fill = bubbleHiddenFill
                        stroke = bubbleHiddenStroke
                    }
                    b.isEmptyPlaceholder -> {
                        fill = bubbleEmptyFill
                        stroke = bubbleEmptyStroke
                    }
                    else -> {
                        fill = bubbleFill
                        stroke = bubbleStroke
                    }
                }
                nc.drawCircle(b.centerX, b.centerY, bubbleR, fill)
                nc.drawCircle(b.centerX, b.centerY, bubbleR, stroke)
                val label = b.label
                if (label != null) {
                    val tp = if (hidden) textHiddenPaint else textPaint
                    val fm = tp.fontMetrics
                    val baseline = b.centerY - (fm.ascent + fm.descent) / 2f
                    nc.drawText(label, b.centerX, baseline, tp)
                }
            }
            if (isBlankSelected) {
                val r = blankBubbleLayoutBoundingRect(layout)
                val rect = RectF(r.left - 4f, r.top - 4f, r.right + 4f, r.bottom + 4f)
                nc.drawRect(rect, selectedOutlineStroke)
            }

            nc.restore()
        }
    }
}
