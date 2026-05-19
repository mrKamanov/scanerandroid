package com.tscan.scanertestov.feature.mainmenu.components

/**
 * Описание: декоративный анимированный фон главного меню, перенесенный из прошлой версии приложения.
 */
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.sin

enum class LegacyDecorativeBackgroundEdge {
    Top,
    Bottom
}

@Composable
fun LegacyDecorativeBackground(
    modifier: Modifier = Modifier,
    edge: LegacyDecorativeBackgroundEdge
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LegacyDecorativeView(context, edge)
        },
        update = { view ->
            view.edge = edge
        }
    )
}

private class LegacyDecorativeView @JvmOverloads constructor(
    context: Context,
    var edge: LegacyDecorativeBackgroundEdge,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f
    private var attachedAtMs: Long = 0L

    private val swapStartDelayMs = 24_000L
    private val swapMoveDurationMs = 6_000L
    private val swapSequenceDurationMs = 40_000L

    private val letters1 = listOf("✓", "А", "Б", "В", "Г", "Д", "✕")
    private val letters2 = listOf("✓", "1", "2", "3", "✕")

    private val animator = object : Runnable {
        override fun run() {
            time += 0.04f
            invalidate()
            postDelayed(this, 16L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedAtMs = System.currentTimeMillis()
        post(animator)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animator)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (edge == LegacyDecorativeBackgroundEdge.Top) {
            drawTopEdge(canvas, w, h)
        } else {
            drawBottomEdge(canvas, w, h)
        }
    }

    private fun drawTopEdge(canvas: Canvas, w: Float, h: Float) {
        val pairProgress = pairSwapProgress(edge = LegacyDecorativeBackgroundEdge.Top)
        val baseY = h * 0.62f + 10f * sin(time)
        val baseX = w * 0.06f
        paint.textSize = 34f
        letters2.forEachIndexed { i, symbol ->
            paint.color = topSymbolColor(symbol, i)
            paint.alpha = 215
            canvas.drawText(symbol, baseX + i * 36f, baseY + 7f * sin(time + i), paint)
        }

        val leftX = lerp(0.16f, 0.84f, pairProgress)
        val leftY = lerp(0.34f, 0.38f, pairProgress)
        val rightX = lerp(0.84f, 0.16f, pairProgress)
        val rightY = lerp(0.38f, 0.34f, pairProgress)

        drawGrid(canvas, w, h, leftX, leftY, 0.54f, 7f)
        drawGrid(canvas, w, h, rightX, rightY, 0.62f, -6f)
    }

    private fun drawBottomEdge(canvas: Canvas, w: Float, h: Float) {
        val pairProgress = pairSwapProgress(edge = LegacyDecorativeBackgroundEdge.Bottom)
        val baseY = h * 0.93f + 8f * sin(time)
        val baseX = w * 0.58f
        paint.textSize = 36f
        letters1.forEachIndexed { i, symbol ->
            paint.color = bottomSymbolColor(symbol, i)
            paint.alpha = 220
            canvas.drawText(symbol, baseX + i * 38f, baseY + 8f * sin(time + i), paint)
        }

        val leftX = lerp(0.18f, 0.82f, pairProgress)
        val leftY = lerp(0.66f, 0.62f, pairProgress)
        val rightX = lerp(0.82f, 0.18f, pairProgress)
        val rightY = lerp(0.62f, 0.66f, pairProgress)

        drawGrid(canvas, w, h, leftX, leftY, 0.66f, -8f)
        drawGrid(canvas, w, h, rightX, rightY, 0.52f, 9f)
    }

    private fun drawGrid(
        canvas: Canvas,
        w: Float,
        h: Float,
        centerXPercent: Float,
        centerYPercent: Float,
        scale: Float,
        baseRotation: Float
    ) {
        canvas.save()

        val centerX = w * centerXPercent
        val centerY = h * centerYPercent
        val rotationAngle = baseRotation + 2f * sin(time * (0.5f + scale * 0.3f))
        val floatOffset = 3f * sin(time * (0.3f + scale * 0.2f))

        canvas.translate(centerX, centerY)
        canvas.rotate(rotationAngle)
        canvas.translate(-centerX, -centerY + floatOffset)

        val gridSize = 300f * scale
        val cellSize = gridSize / 4f
        val startX = centerX - gridSize / 2f
        val startY = centerY - gridSize / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2E3440")
        paint.alpha = 156
        val padding = 28f * scale
        val backgroundRect = RectF(startX - padding, startY - padding, startX + gridSize + padding, startY + gridSize + padding)
        canvas.drawRoundRect(backgroundRect, 12f * scale, 12f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.1f * scale
        paint.color = Color.parseColor("#81A1C1")
        paint.alpha = 210

        for (i in 0..4) {
            canvas.drawLine(startX + i * cellSize, startY, startX + i * cellSize, startY + gridSize, paint)
            canvas.drawLine(startX, startY + i * cellSize, startX + gridSize, startY + i * cellSize, paint)
        }

        for (row in 0..3) {
            for (col in 0..3) {
                val circleX = startX + col * cellSize + cellSize / 2f
                val circleY = startY + row * cellSize + cellSize / 2f
                val radius = 21f * scale

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.9f * scale
                paint.color = when (row + 1) {
                    1 -> Color.parseColor("#A3BE8C")
                    2 -> Color.parseColor("#81A1C1")
                    3 -> Color.parseColor("#EBCB8B")
                    4 -> Color.parseColor("#B48EAD")
                    else -> Color.parseColor("#81A1C1")
                }
                paint.alpha = 220
                canvas.drawCircle(circleX, circleY, radius, paint)

                paint.style = Paint.Style.FILL
                paint.textSize = 11f * scale
        paint.color = Color.parseColor("#D8DEE9")
        paint.alpha = 220
                val text = "${row + 1}.${col + 1}"
                val textBounds = android.graphics.Rect()
                paint.getTextBounds(text, 0, text.length, textBounds)
                canvas.drawText(text, circleX - textBounds.width() / 2f, circleY + textBounds.height() / 2f, paint)

                val selectedCells = setOf(Pair(0, 1), Pair(1, 3), Pair(2, 0), Pair(3, 3))
                if (selectedCells.contains(Pair(row, col))) {
                    val checkPhase = ((time * 0.35f + (row * 2 + col) * 0.6f) % 2f) / 2f
                    paint.style = Paint.Style.FILL
                    paint.textSize = 22f * scale
                    paint.color = Color.parseColor("#A3BE8C")
                    paint.alpha = (150 + 90 * checkPhase).toInt()
                    canvas.drawText("✓", circleX - 8f * scale, circleY + 7f * scale, paint)
                }
            }
        }

        canvas.restore()
    }

    private fun topSymbolColor(symbol: String, index: Int): Int {
        return when (symbol) {
            "✓" -> Color.parseColor("#A3BE8C")
            "✕" -> Color.parseColor("#BF616A")
            else -> Color.parseColor("#81A1C1")
        }
    }

    private fun bottomSymbolColor(symbol: String, index: Int): Int {
        return when (symbol) {
            "✓" -> Color.parseColor("#A3BE8C")
            "✕" -> Color.parseColor("#BF616A")
            else -> Color.parseColor("#81A1C1")
        }
    }

    private fun pairSwapProgress(edge: LegacyDecorativeBackgroundEdge): Float {
        val elapsed = System.currentTimeMillis() - attachedAtMs
        if (elapsed < swapStartDelayMs) return 0f

        val afterDelay = (elapsed - swapStartDelayMs) % swapSequenceDurationMs

        return if (edge == LegacyDecorativeBackgroundEdge.Top) {
            when {
                afterDelay < swapMoveDurationMs -> easeInOut(afterDelay / swapMoveDurationMs.toFloat())
                afterDelay < 20_000L -> 1f
                afterDelay < 20_000L + swapMoveDurationMs -> {
                    val backPhase = (afterDelay - 20_000L) / swapMoveDurationMs.toFloat()
                    1f - easeInOut(backPhase)
                }
                else -> 0f
            }
        } else {
            when {
                afterDelay < 10_000L -> 0f
                afterDelay < 10_000L + swapMoveDurationMs -> {
                    val movePhase = (afterDelay - 10_000L) / swapMoveDurationMs.toFloat()
                    easeInOut(movePhase)
                }
                afterDelay < 30_000L -> 1f
                afterDelay < 30_000L + swapMoveDurationMs -> {
                    val backPhase = (afterDelay - 30_000L) / swapMoveDurationMs.toFloat()
                    1f - easeInOut(backPhase)
                }
                else -> 0f
            }
        }
    }

    private fun easeInOut(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }
}
