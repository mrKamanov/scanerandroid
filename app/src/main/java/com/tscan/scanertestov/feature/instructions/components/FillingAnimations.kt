package com.tscan.scanertestov.feature.instructions.components

/**
 * Описание: зацикленные анимации «как закрашивать бланк» для инструкции — как гифка, но рисуются кодом:
 * чётко на любом экране и почти не занимают места.
 *  - FillBubble: карандаш плотно штрихует кружок → зелёная галочка;
 *  - WrongMarks: галочка рядом, точка, крестик, мазок за краем → красные «нельзя»;
 *  - EraseAndRefill: ластик стирает закрашенный кружок, карандаш закрашивает другой;
 *  - CrossOutAndRefill: ручка зачёркивает кружок крестиком и закрашивает другой.
 * Если в системе отключены анимации, показывается последний, «итоговый» кадр.
 */
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.feature.instructions.InstructionAnimation
import com.tscan.scanertestov.ui.theme.Scrapbook

private const val VW = 310f
private const val VH = 110f
private const val BUBBLE_R = 17f
private val BUBBLE_X = floatArrayOf(60f, 120f, 180f, 240f)
private const val BUBBLE_Y = 60f

private val Graphite = Color(0xFF3A3A3A)
private val InkBlue = Color(0xFF1D3A8A)
private val InkDark = Color(0xFF0F1F5C)
private val OkGreen = Color(0xFF2F6B4F)
private val NoRed = Color(0xFFB5452F)
private val PencilYellow = Color(0xFFFFD65A)
private val Wood = Color(0xFFF3D9B5)
private val Metal = Color(0xFFC9C2B4)
private val EraserPink = Color(0xFFFFB7C5)
private val EraserBlue = Color(0xFF78C8FF)

/** Штриховка внутри кружка, смещения от центра (как рука водит карандашом). */
private val ScribbleOffsets = listOf(
    -13f to -11f, 13f to -12f, -15f to -6f, 15f to -7f, -16f to -1f, 16f to -2f,
    -15f to 4f, 15f to 3f, -14f to 9f, 13f to 8f, -10f to 13f, 10f to 13f,
)

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun lerp(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

/** Позиция по ключевым кадрам (время → точка) с линейной интерполяцией. */
private fun keyframes(t: Float, frames: List<Pair<Float, Offset>>): Offset {
    if (t <= frames.first().first) return frames.first().second
    for (i in 1 until frames.size) {
        val (t1, p1) = frames[i]
        if (t <= t1) {
            val (t0, p0) = frames[i - 1]
            return lerp(p0, p1, seg(t, t0, t1))
        }
    }
    return frames.last().second
}

private fun scribblePath(cx: Float): Path = Path().apply {
    ScribbleOffsets.forEachIndexed { i, (dx, dy) ->
        if (i == 0) moveTo(cx + dx, BUBBLE_Y + dy) else lineTo(cx + dx, BUBBLE_Y + dy)
    }
}

private fun circlePath(cx: Float, r: Float = BUBBLE_R - 1f): Path = Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(Offset(cx, BUBBLE_Y), r))
}

private fun animationsDisabled(context: android.content.Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

@Composable
internal fun FillingAnimation(kind: InstructionAnimation, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember { animationsDisabled(context) }
    val durationMs = when (kind) {
        InstructionAnimation.FillBubble -> 6000
        InstructionAnimation.WrongMarks -> 8000
        InstructionAnimation.LiveCheck, InstructionAnimation.ExcelImport -> 8000
        InstructionAnimation.Tiling, InstructionAnimation.WordInsert -> 9000
        else -> 7000
    }
    val transition = rememberInfiniteTransition(label = "filling")
    val animT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "fillingT",
    )
    // Итоговый кадр для тех, у кого отключены анимации.
    val t = if (still) {
        when (kind) {
            InstructionAnimation.FillBubble -> 0.8f
            InstructionAnimation.WrongMarks -> 0.6f
            InstructionAnimation.EraseAndRefill -> 0.9f
            InstructionAnimation.CrossOutAndRefill -> 0.88f
            InstructionAnimation.PhotoFraming -> 0.85f
            InstructionAnimation.LiveCheck -> 0.85f
            InstructionAnimation.NamedCode -> 0.85f
            InstructionAnimation.VariantSheets -> 0.85f
            InstructionAnimation.ExcelImport -> 0.88f
            InstructionAnimation.Tiling -> 0.62f
            InstructionAnimation.WordInsert -> 0.6f
        }
    } else {
        animT
    }
    val measurer = rememberTextMeasurer()
    val description = when (kind) {
        InstructionAnimation.FillBubble -> "Карандаш плотно закрашивает кружок целиком"
        InstructionAnimation.WrongMarks -> "Неправильно: галочка рядом, точка, крестик вместо заливки, мазок за краем"
        InstructionAnimation.EraseAndRefill -> "Ластик стирает закрашенный кружок, затем карандаш закрашивает другой"
        InstructionAnimation.CrossOutAndRefill -> "Ручка зачёркивает кружок крестиком и закрашивает другой"
        InstructionAnimation.PhotoFraming -> "Телефон выравнивается над листом, четыре угловые метки находятся, снимок сохранён"
        InstructionAnimation.LiveCheck -> "Камера над бланком: на кружках появляются пометки верно и ошибка, затем оценка"
        InstructionAnimation.NamedCode -> "На именном бланке закрашен код ученика, проверка подставляет фамилию и класс"
        InstructionAnimation.VariantSheets -> "Бланки вариантов 1 и 2: с бланка варианта 2 лишние кружки убраны"
        InstructionAnimation.ExcelImport -> "Строки таблицы Excel по одной попадают в журнал по классам"
        InstructionAnimation.Tiling -> "На листе A4 один бланк, затем два на боку, затем четыре; пунктир показывает, где резать"
        InstructionAnimation.WordInsert -> "Картинка бланка вставляется в документ с вопросами и растягивается за угол"
    }
    val aspect = when (kind) {
        InstructionAnimation.WrongMarks -> VW / 150f
        InstructionAnimation.PhotoFraming -> GW / PHOTO_H
        InstructionAnimation.LiveCheck -> GW / LIVE_H
        InstructionAnimation.NamedCode -> GW / CODE_H
        InstructionAnimation.VariantSheets -> GW / VAR_H
        InstructionAnimation.ExcelImport -> GW / XLS_H
        InstructionAnimation.Tiling -> GW / TILE_H
        InstructionAnimation.WordInsert -> GW / WORD_H
        else -> VW / VH
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .semantics { contentDescription = description },
    ) {
        val k = size.width / VW
        scale(k, k, pivot = Offset.Zero) {
            when (kind) {
                InstructionAnimation.FillBubble -> drawFillBubble(t, measurer)
                InstructionAnimation.WrongMarks -> drawWrongMarks(t)
                InstructionAnimation.EraseAndRefill -> drawEraseAndRefill(t, measurer)
                InstructionAnimation.CrossOutAndRefill -> drawCrossOut(t, measurer)
                InstructionAnimation.PhotoFraming -> drawPhotoFraming(t, measurer)
                InstructionAnimation.LiveCheck -> drawLiveCheck(t, measurer)
                InstructionAnimation.NamedCode -> drawNamedCode(t, measurer)
                InstructionAnimation.VariantSheets -> drawVariantSheets(t, measurer)
                InstructionAnimation.ExcelImport -> drawExcelImport(t, measurer)
                InstructionAnimation.Tiling -> drawTiling(t, measurer)
                InstructionAnimation.WordInsert -> drawWordInsert(t, measurer)
            }
        }
    }
}

// ---------- общие элементы ----------

private fun DrawScope.drawRow(question: String, measurer: androidx.compose.ui.text.TextMeasurer) {
    val labelStyle = TextStyle(color = Scrapbook.InkMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    // Текст меряется в пикселях экрана, а мы рисуем в масштабированных единицах — компенсируем.
    val inv = VW / size.width
    scale(inv, inv, pivot = Offset.Zero) {
        val k = 1f / inv
        val q = measurer.measure(question, TextStyle(color = Scrapbook.Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
        drawText(q, topLeft = Offset(12f * k, BUBBLE_Y * k - q.size.height / 2f))
        BUBBLE_X.forEachIndexed { i, x ->
            val l = measurer.measure("${i + 1}", labelStyle)
            drawText(l, topLeft = Offset(x * k - l.size.width / 2f, 34f * k - l.size.height))
        }
    }
}

private fun DrawScope.drawRings() {
    BUBBLE_X.forEach { x ->
        drawCircle(Scrapbook.Ink, radius = BUBBLE_R, center = Offset(x, BUBBLE_Y), style = Stroke(3f))
    }
}

/** Частичная штриховка кружка [cx] на долю [progress]; возвращает точку, где сейчас «грифель». */
private fun DrawScope.drawScribble(cx: Float, progress: Float, color: Color, alpha: Float = 1f): Offset {
    val path = scribblePath(cx)
    val pm = PathMeasure().apply { setPath(path, false) }
    val len = pm.length * progress
    val part = Path()
    if (len > 0f) pm.getSegment(0f, len, part, true)
    clipPath(circlePath(cx)) {
        drawPath(part, color, alpha = alpha, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    return if (len > 0f) pm.getPosition(len) else Offset(cx + ScribbleOffsets[0].first, BUBBLE_Y + ScribbleOffsets[0].second)
}

private fun DrawScope.drawCheck(center: Offset, appear: Float) {
    if (appear <= 0f) return
    val s = if (appear < 0.7f) 0.4f + appear / 0.7f * 0.75f else 1.15f - (appear - 0.7f) / 0.3f * 0.15f
    scale(s, s, pivot = center) {
        drawCircle(OkGreen, radius = 12f, center = center, alpha = appear.coerceAtMost(1f))
        val p = Path().apply {
            moveTo(center.x - 6f, center.y)
            lineTo(center.x - 1.5f, center.y + 4.5f)
            lineTo(center.x + 6f, center.y - 4f)
        }
        drawPath(p, Color.White, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawNo(center: Offset, appear: Float) {
    if (appear <= 0f) return
    val s = 1.8f - 0.8f * appear
    rotate(-12f, pivot = center) {
        scale(s, s, pivot = center) {
            drawCircle(NoRed, radius = 11f, center = center, alpha = appear)
            drawLine(Color.White, Offset(center.x - 5f, center.y - 5f), Offset(center.x + 5f, center.y + 5f), 3f, StrokeCap.Round, alpha = appear)
            drawLine(Color.White, Offset(center.x + 5f, center.y - 5f), Offset(center.x - 5f, center.y + 5f), 3f, StrokeCap.Round, alpha = appear)
        }
    }
}

/** Карандаш: [tip] — кончик грифеля. */
private fun DrawScope.drawPencil(tip: Offset, alpha: Float) {
    if (alpha <= 0f) return
    val line = Stroke(1.5f, join = StrokeJoin.Round)
    rotate(-40f, pivot = tip) {
        val x = tip.x
        val y = tip.y
        val woodP = Path().apply { moveTo(x, y); lineTo(x + 15f, y - 6f); lineTo(x + 15f, y + 6f); close() }
        drawPath(woodP, Wood, alpha = alpha)
        drawPath(woodP, Scrapbook.Ink, alpha = alpha, style = line)
        val lead = Path().apply { moveTo(x, y); lineTo(x + 5f, y - 2f); lineTo(x + 5f, y + 2f); close() }
        drawPath(lead, Scrapbook.Ink, alpha = alpha)
        listOf(Triple(15f, 46f, PencilYellow), Triple(61f, 7f, Metal), Triple(68f, 9f, EraserPink)).forEach { (dx, w, c) ->
            drawRect(c, Offset(x + dx, y - 6f), Size(w, 12f), alpha = alpha)
            drawRect(Scrapbook.Ink, Offset(x + dx, y - 6f), Size(w, 12f), alpha = alpha, style = line)
        }
    }
}

private fun DrawScope.drawPen(tip: Offset, alpha: Float) {
    if (alpha <= 0f) return
    val line = Stroke(1.5f, join = StrokeJoin.Round)
    rotate(-40f, pivot = tip) {
        val x = tip.x
        val y = tip.y
        val cone = Path().apply { moveTo(x, y); lineTo(x + 13f, y - 5f); lineTo(x + 13f, y + 5f); close() }
        drawPath(cone, Metal, alpha = alpha)
        drawPath(cone, Scrapbook.Ink, alpha = alpha, style = line)
        drawCircle(InkDark, 1.6f, Offset(x + 1f, y), alpha = alpha)
        drawRect(Scrapbook.Board, Offset(x + 13f, y - 6f), Size(20f, 12f), alpha = alpha)
        drawRect(Scrapbook.Ink, Offset(x + 13f, y - 6f), Size(20f, 12f), alpha = alpha, style = line)
        drawRect(Color(0xFF2A3F8A), Offset(x + 33f, y - 6f), Size(44f, 12f), alpha = alpha)
        drawRect(Scrapbook.Ink, Offset(x + 33f, y - 6f), Size(44f, 12f), alpha = alpha, style = line)
    }
}

private fun DrawScope.drawEraser(pos: Offset, alpha: Float) {
    if (alpha <= 0f) return
    val line = Stroke(1.5f)
    rotate(-25f, pivot = pos) {
        drawRect(EraserPink, Offset(pos.x - 14f, pos.y - 10f), Size(24f, 20f), alpha = alpha)
        drawRect(EraserBlue, Offset(pos.x + 10f, pos.y - 10f), Size(16f, 20f), alpha = alpha)
        drawRect(Scrapbook.Ink, Offset(pos.x - 14f, pos.y - 10f), Size(40f, 20f), alpha = alpha, style = line)
    }
}

/** Траектория руки по штриховке кружка [cx] (кадры), начиная с [t0] и заканчивая [t1]. */
private fun scribbleFrames(cx: Float, t0: Float, t1: Float): List<Pair<Float, Offset>> {
    val n = ScribbleOffsets.size
    return ScribbleOffsets.mapIndexed { i, (dx, dy) ->
        (t0 + (t1 - t0) * i / (n - 1)) to Offset(cx + dx, BUBBLE_Y + dy)
    }
}

// ---------- 1. Закрасить целиком ----------

private fun DrawScope.drawFillBubble(t: Float, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawRow("3.", measurer)
    val fade = 1f - seg(t, 0.92f, 0.97f)
    drawScribble(BUBBLE_X[1], seg(t, 0.10f, 0.60f), Graphite, alpha = fade)
    drawRings()
    drawCheck(Offset(152f, 34f), if (t < 0.92f) seg(t, 0.64f, 0.74f) else fade)
    val away = Offset(235f, 0f)
    val frames = listOf(0f to Offset(230f, 0f)) + scribbleFrames(BUBBLE_X[1], 0.10f, 0.60f) + listOf(0.70f to away)
    val pencilAlpha = when {
        t < 0.06f -> seg(t, 0f, 0.06f)
        t > 0.70f -> 1f - seg(t, 0.70f, 0.74f)
        else -> 1f
    }
    drawPencil(keyframes(t, frames), pencilAlpha)
}

// ---------- 2. Так не надо ----------

private fun DrawScope.drawWrongMarks(t: Float) {
    // Четыре мини-примера 2×2; каждый появляется на секунду позже предыдущего.
    val cells = listOf(Offset(0f, 0f), Offset(155f, 0f), Offset(0f, 75f), Offset(155f, 75f))
    val fadeAll = 1f - seg(t, 0.80f, 0.88f)
    cells.forEachIndexed { i, o ->
        val start = i * 0.125f
        val a = seg(t, start + 0.04f, start + 0.10f) * fadeAll
        val badge = seg(t, start + 0.10f, start + 0.15f) * fadeAll
        val c = Offset(o.x + 50f, o.y + 40f)
        drawRect(Color(0xFFFBFAF6), Offset(o.x + 4f, o.y + 4f), Size(147f, 67f))
        if (i == 3) drawCircle(Graphite, 22f, Offset(c.x + 4f, c.y - 4f), alpha = 0.85f * a)
        drawCircle(Scrapbook.Ink, BUBBLE_R, c, style = Stroke(3f))
        when (i) {
            0 -> {
                val p = Path().apply { moveTo(c.x + 21f, c.y - 2f); lineTo(c.x + 29f, c.y + 8f); lineTo(c.x + 47f, c.y - 16f) }
                drawPath(p, Graphite, alpha = a, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            1 -> drawCircle(Graphite, 4f, c, alpha = a)
            2 -> {
                drawLine(Graphite, Offset(c.x - 10f, c.y - 10f), Offset(c.x + 10f, c.y + 10f), 4f, StrokeCap.Round, alpha = a)
                drawLine(Graphite, Offset(c.x + 10f, c.y - 10f), Offset(c.x - 10f, c.y + 10f), 4f, StrokeCap.Round, alpha = a)
            }
        }
        drawNo(Offset(o.x + 125f, o.y + 22f), badge)
    }
}

// ---------- 3. Карандаш: стереть ----------

private fun DrawScope.drawEraseAndRefill(t: Float, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawRow("5.", measurer)
    val reset = 1f - seg(t, 0.94f, 0.97f)
    val erased = 1f - 0.93f * seg(t, 0.20f, 0.45f)
    drawScribble(BUBBLE_X[0], 1f, Graphite, alpha = erased * if (t > 0.97f) seg(t, 0.97f, 1f) else 1f)
    drawScribble(BUBBLE_X[2], seg(t, 0.54f, 0.84f), Graphite, alpha = reset)
    drawRings()
    // Крошки от ластика.
    val crumbs = seg(t, 0.22f, 0.30f) * (1f - seg(t, 0.48f, 0.56f))
    listOf(Offset(40f, 88f), Offset(52f, 94f), Offset(76f, 90f), Offset(86f, 98f), Offset(63f, 99f)).forEach {
        drawCircle(Color(0xFFE39AAA), 2f, it, alpha = crumbs)
    }
    drawCheck(Offset(212f, 34f), if (t < 0.94f) seg(t, 0.85f, 0.92f) else reset)
    val eraserFrames = listOf(
        0f to Offset(240f, 10f), 0.16f to Offset(58f, 52f), 0.20f to Offset(70f, 62f), 0.24f to Offset(48f, 56f),
        0.28f to Offset(72f, 58f), 0.32f to Offset(50f, 64f), 0.36f to Offset(70f, 54f), 0.40f to Offset(52f, 60f),
        0.44f to Offset(66f, 62f), 0.50f to Offset(240f, 10f),
    )
    val eraserAlpha = when {
        t < 0.08f -> seg(t, 0f, 0.08f)
        t > 0.50f -> 1f - seg(t, 0.50f, 0.53f)
        else -> 1f
    }
    drawEraser(keyframes(t, eraserFrames), eraserAlpha)
    val pencilFrames = listOf(0.50f to Offset(240f, 0f)) + scribbleFrames(BUBBLE_X[2], 0.54f, 0.84f) +
        listOf(0.89f to Offset(240f, 0f))
    val pencilAlpha = when {
        t < 0.50f -> 0f
        t < 0.52f -> seg(t, 0.50f, 0.52f)
        t > 0.89f -> 1f - seg(t, 0.89f, 0.91f)
        else -> 1f
    }
    drawPencil(keyframes(t, pencilFrames), pencilAlpha)
}

// ---------- 4. Ручка: зачеркнуть ----------

private fun DrawScope.drawCrossOut(t: Float, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawRow("2.", measurer)
    val reset = 1f - seg(t, 0.94f, 0.97f)
    drawScribble(BUBBLE_X[0], 1f, InkBlue, alpha = if (t > 0.97f) seg(t, 0.97f, 1f) else reset)
    drawScribble(BUBBLE_X[2], seg(t, 0.47f, 0.77f), InkBlue, alpha = reset)
    drawRings()
    val x1 = seg(t, 0.16f, 0.27f)
    val x2 = seg(t, 0.29f, 0.40f)
    if (x1 > 0f) drawLine(InkDark, Offset(40f, 40f), lerp(Offset(40f, 40f), Offset(80f, 80f), x1), 6f, StrokeCap.Round, alpha = reset)
    if (x2 > 0f) drawLine(InkDark, Offset(80f, 40f), lerp(Offset(80f, 40f), Offset(40f, 80f), x2), 6f, StrokeCap.Round, alpha = reset)
    drawCheck(Offset(212f, 34f), if (t < 0.94f) seg(t, 0.79f, 0.86f) else reset)
    val frames = listOf(
        0f to Offset(240f, 0f), 0.16f to Offset(40f, 40f), 0.27f to Offset(80f, 80f),
        0.29f to Offset(80f, 40f), 0.40f to Offset(40f, 80f),
    ) + scribbleFrames(BUBBLE_X[2], 0.47f, 0.77f) + listOf(0.84f to Offset(240f, 0f))
    val penAlpha = when {
        t < 0.08f -> seg(t, 0f, 0.08f)
        t > 0.84f -> 1f - seg(t, 0.84f, 0.86f)
        else -> 1f
    }
    drawPen(keyframes(t, frames), penAlpha)
}
