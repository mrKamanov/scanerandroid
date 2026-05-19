package com.tscan.scanertestov.feature.mainmenu.components

/**
 * Описание: анимированный декор OMR-кружков на фоне главного меню.
 */
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val OutlineColor = Color.White.copy(alpha = 0.22f)
private val PaperFill = Color(0xFFE8DFD0).copy(alpha = 0.38f)
private val StripeInk = Color(0xFFF2F6FA)

private val MarkCorrectColor = Color(0xFF8BC34A)
private val MarkWrongColor = Color(0xFFE57373)

private const val BubbleLayerAlpha = 0.34f
private const val OutcomeIconWindowStart = 0.72f
private const val OutcomeIconFadeInFrac = 0.10f
private const val OutcomeIconFadeOutFrac = 0.12f

private data class SparseBubble(
    val nx: Float,
    val ny: Float,
    val radiusMul: Float = 1f,
    val phaseShift: Float = 0f,
    val durationMs: Int = 4200,
    val reverse: Boolean = false,
    val outcomeCorrect: Boolean = true,
)

private val SparseBubbles: List<SparseBubble> = listOf(
    SparseBubble(0.14f, 0.12f, radiusMul = 1.05f, phaseShift = 0.02f, durationMs = 2600, reverse = false, outcomeCorrect = true),
    SparseBubble(0.86f, 0.16f, radiusMul = 0.92f, phaseShift = 0.31f, durationMs = 5400, reverse = true, outcomeCorrect = false),
    SparseBubble(0.10f, 0.46f, radiusMul = 0.88f, phaseShift = 0.55f, durationMs = 3800, reverse = false, outcomeCorrect = false),
    SparseBubble(0.90f, 0.42f, radiusMul = 1.0f, phaseShift = 0.18f, durationMs = 4700, reverse = true, outcomeCorrect = true),
    SparseBubble(0.26f, 0.72f, radiusMul = 0.95f, phaseShift = 0.72f, durationMs = 3200, reverse = false, outcomeCorrect = true),
    SparseBubble(0.74f, 0.78f, radiusMul = 1.08f, phaseShift = 0.44f, durationMs = 6100, reverse = true, outcomeCorrect = false),
)

private val TitleGlyphBubbleSpec = SparseBubble(
    nx = 0.5f,
    ny = 0.5f,
    radiusMul = 1f,
    phaseShift = 0.19f,
    durationMs = 3800,
    reverse = false,
    outcomeCorrect = true,
)

private fun fract01(x: Float): Float {
    var v = x % 1f
    if (v < 0f) v += 1f
    return v
}

private data class BubbleAnim(
    val phase: Float,
    val cycleLinear: Float,
)

@Composable
private fun rememberBubbleAnim(spec: SparseBubble, index: Int): BubbleAnim {
    val transition = rememberInfiniteTransition(label = "omrBubble$index")
    val raw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(spec.durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val cycleLinear = fract01(raw + spec.phaseShift)
    val phase = if (spec.reverse) 1f - cycleLinear else cycleLinear
    return BubbleAnim(phase = phase, cycleLinear = cycleLinear)
}

private fun outcomeIconAlpha(cycleLinear: Float): Float {
    if (cycleLinear <= OutcomeIconWindowStart) return 0f
    val u = ((cycleLinear - OutcomeIconWindowStart) / (1f - OutcomeIconWindowStart)).coerceIn(0f, 1f)
    val fadeInEnd = OutcomeIconFadeInFrac
    val fadeOutStart = 1f - OutcomeIconFadeOutFrac
    return when {
        u <= fadeInEnd && fadeInEnd > 0f -> (u / fadeInEnd).coerceIn(0f, 1f)
        u >= fadeOutStart && fadeOutStart < 1f -> ((1f - u) / (1f - fadeOutStart)).coerceIn(0f, 1f)
        else -> 1f
    }
}

private fun DrawScope.drawOmRBubbleLayered(
    cx: Float,
    cy: Float,
    r: Float,
    anim: BubbleAnim,
    layerAlpha: Float,
    outlineW: Float,
    drawOutcome: Boolean,
    outcomeCorrect: Boolean,
) {
    val p = anim.phase.coerceIn(0f, 1f)
    val iconFade =
        if (drawOutcome) outcomeIconAlpha(anim.cycleLinear) * layerAlpha else 0f

    val paper = PaperFill.copy(alpha = PaperFill.alpha * layerAlpha)
    val outline = OutlineColor.copy(alpha = OutlineColor.alpha * layerAlpha)

    drawCircle(color = paper, radius = r * 0.98f, center = Offset(cx, cy))

    val oval = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    clipPath(oval) {
        val reveal = revealFraction(p)
        val bottom = cy - r + reveal * 2f * r
        clipRect(
            left = cx - r - 4f,
            top = cy - r,
            right = cx + r + 4f,
            bottom = bottom,
        ) {
            drawDenseStripes135(
                cx = cx,
                cy = cy,
                r = r,
                opacityMul = opacityKeyframe(p) * layerAlpha,
            )
        }
    }

    drawCircle(
        color = outline,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = outlineW),
    )

    if (drawOutcome && iconFade > 0.02f) {
        val markColor = if (outcomeCorrect) MarkCorrectColor else MarkWrongColor
        val markStroke = max(2.dp.toPx(), r * 0.13f)
        drawOutcomeMark(
            center = Offset(cx, cy),
            radius = r,
            correct = outcomeCorrect,
            color = markColor.copy(alpha = markColor.alpha * iconFade),
            strokeW = markStroke,
        )
    }
}

@Composable
fun MainMenuBubbleSheetDecor(modifier: Modifier = Modifier) {
    val a0 = rememberBubbleAnim(SparseBubbles[0], 0)
    val a1 = rememberBubbleAnim(SparseBubbles[1], 1)
    val a2 = rememberBubbleAnim(SparseBubbles[2], 2)
    val a3 = rememberBubbleAnim(SparseBubbles[3], 3)
    val a4 = rememberBubbleAnim(SparseBubbles[4], 4)
    val a5 = rememberBubbleAnim(SparseBubbles[5], 5)
    val anims = arrayOf(a0, a1, a2, a3, a4, a5)

    Canvas(modifier = modifier) {
        val baseR = min(size.width, size.height) * 0.056f
        val outlineW = 1.2.dp.toPx()

        SparseBubbles.forEachIndexed { idx, spec ->
            val cx = size.width * spec.nx
            val cy = size.height * spec.ny
            val r = baseR * spec.radiusMul
            val anim = anims[idx]
            drawOmRBubbleLayered(
                cx = cx,
                cy = cy,
                r = r,
                anim = anim,
                layerAlpha = BubbleLayerAlpha,
                outlineW = outlineW,
                drawOutcome = true,
                outcomeCorrect = spec.outcomeCorrect,
            )
        }
    }
}

@Composable
internal fun MainMenuTitleOmRBubbleGlyph(modifier: Modifier = Modifier) {
    val anim = rememberBubbleAnim(TitleGlyphBubbleSpec, index = 20)
    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val r = side * 0.42f
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val outlineW = max(1.dp.toPx(), side * 0.075f)
        drawOmRBubbleLayered(
            cx = cx,
            cy = cy,
            r = r,
            anim = anim,
            layerAlpha = 0.92f,
            outlineW = outlineW,
            drawOutcome = false,
            outcomeCorrect = true,
        )
    }
}

private fun revealFraction(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    return when {
        t <= 0.5f -> (t / 0.5f) * 0.55f
        t <= 0.75f -> 0.55f + ((t - 0.5f) / 0.25f) * (0.85f - 0.55f)
        else -> 0.85f + ((t - 0.75f) / 0.25f) * (1f - 0.85f)
    }
}

private fun opacityKeyframe(p: Float): Float {
    val t = p.coerceIn(0f, 1f)
    val curve = when {
        t <= 0.2f -> (t / 0.2f) * 0.3f
        t <= 0.5f -> 0.3f + ((t - 0.2f) / 0.3f) * (0.6f - 0.3f)
        t <= 0.75f -> 0.6f + ((t - 0.5f) / 0.25f) * (0.85f - 0.6f)
        else -> 0.85f + ((t - 0.75f) / 0.25f) * (1f - 0.85f)
    }
    return 0.25f + 0.75f * curve
}

private fun DrawScope.drawOutcomeMark(
    center: Offset,
    radius: Float,
    correct: Boolean,
    color: Color,
    strokeW: Float,
) {
    val h = radius * 0.42f
    if (correct) {
        val path = Path().apply {
            moveTo(center.x - h * 0.42f, center.y + h * 0.08f)
            lineTo(center.x - h * 0.05f, center.y + h * 0.42f)
            lineTo(center.x + h * 0.52f, center.y - h * 0.38f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    } else {
        val d = h * 0.48f
        drawLine(
            color = color,
            start = Offset(center.x - d, center.y - d),
            end = Offset(center.x + d, center.y + d),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x - d, center.y + d),
            end = Offset(center.x + d, center.y - d),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawDenseStripes135(cx: Float, cy: Float, r: Float, opacityMul: Float) {
    if (opacityMul <= 0.02f) return

    val stripeAlong = (45f * PI / 180.0).toFloat()
    val ux = cos(stripeAlong)
    val uy = sin(stripeAlong)
    val nx = -uy
    val ny = ux

    val spacing = 6.dp.toPx()
    val strokeW = max(2.dp.toPx(), 2.6f)
    val ink = StripeInk.copy(alpha = (0.42f * opacityMul).coerceIn(0.12f, 0.85f))

    val maxK = ceil((r * 2.8f) / spacing).toInt().coerceAtLeast(5)
    for (k in -maxK..maxK) {
        val d = k * spacing
        if (abs(d) > r * 1.25f) continue
        val disc = r * r - d * d
        if (disc < 0f) continue
        val halfChord = sqrt(disc)
        val px = cx + nx * d
        val py = cy + ny * d
        val ax = px - ux * halfChord * 1.2f
        val ay = py - uy * halfChord * 1.2f
        val bx = px + ux * halfChord * 1.2f
        val by = py + uy * halfChord * 1.2f
        drawLine(
            color = ink,
            start = Offset(ax, ay),
            end = Offset(bx, by),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}
