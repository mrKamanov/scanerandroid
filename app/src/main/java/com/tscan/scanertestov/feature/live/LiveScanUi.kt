package com.tscan.scanertestov.feature.live

/**
 * Описание: оформление живой проверки в стиле приложения («Скрапбук»): рисунок на бланке
 * (угловые уголки, пометки-формы, оценка красной ручкой), бумажная записка с итогом и кнопки.
 *
 * Пометки различаются ФОРМОЙ, а не только цветом:
 *  - круг с галочкой — отмечено верно;
 *  - круг с крестиком — отмечено неверно;
 *  - пунктирный круг со стрелкой — здесь правильный ответ (не отмечен);
 *  - квадрат с «И» — исправление (решает учитель).
 */
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.GridGeometry
import com.tscan.scanertestov.ui.components.ScrapTape
import com.tscan.scanertestov.ui.components.stickerShadow
import com.tscan.scanertestov.ui.theme.Scrapbook
import kotlin.math.hypot
import kotlin.math.max

internal object LiveColors {
    val Ok = Color(0xFF1E8E4E)
    val Bad = Color(0xFFC62828)
    val Fix = Color(0xFFE09A00)
    val RedPen = Color(0xFFB5452F)
    val Searching = Color.White
    val Checking = Color(0xFFFFC94D)
    val Refining = Color(0xFFB8F0CB)
    val Confirmed = Color(0xFF5BD68A)
    /** Полупрозрачность пометок: под значком видна отметка ребёнка. */
    const val MARK_ALPHA = 0.82f
}

/** Что рисовать в одном кружке. */
internal enum class LiveMark { Ok, Bad, Need, Fix }

/** Пометки листа: для каждого кружка — что рисовать. */
internal fun LiveResult.markList(geo: GridGeometry): List<Pair<com.tscan.scanertestov.feature.batch.engine.GridBubble, LiveMark>> {
    val omr = omr ?: return emptyList()
    val klass = omr.predictions.associate { (it.questionIndex to it.choiceIndex) to it.klass }
    val out = ArrayList<Pair<com.tscan.scanertestov.feature.batch.engine.GridBubble, LiveMark>>()
    for (b in geo.bubbles) {
        if (b.placeholder || b.question !in 0 until total) continue
        val k = klass[b.question to b.choice]
        val inKey = b.choice in answerKey.getOrNull(b.question).orEmpty()
        when {
            k == BatchCellClass.Fixed -> out += b to LiveMark.Fix
            k == BatchCellClass.Yes && inKey -> out += b to LiveMark.Ok
            k == BatchCellClass.Yes -> out += b to LiveMark.Bad
            inKey -> out += b to LiveMark.Need
        }
    }
    return out
}

/** Одна пометка: [c] — центр, [r] — радиус кружка на бланке (в пикселях экрана). */
internal fun DrawScope.drawLiveMark(mark: LiveMark, c: Offset, r: Float) {
    // Чуть больше самого кружка — не громоздко, но отметку ребёнка видно по краям.
    val R = r * 1.2f
    val a = LiveColors.MARK_ALPHA
    val w = max(2f, R * 0.22f)
    when (mark) {
        LiveMark.Ok -> {
            drawCircle(LiveColors.Ok.copy(alpha = a), R, c)
            val p = Path().apply {
                moveTo(c.x - R * 0.47f, c.y + R * 0.02f)
                lineTo(c.x - R * 0.12f, c.y + R * 0.38f)
                lineTo(c.x + R * 0.5f, c.y - R * 0.36f)
            }
            drawPath(p, Color.White, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        LiveMark.Bad -> {
            drawCircle(LiveColors.Bad.copy(alpha = a), R, c)
            val d = R * 0.4f
            drawLine(Color.White, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), w, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), w, StrokeCap.Round)
        }
        LiveMark.Need -> {
            // «Здесь правильный ответ» — кружок заштрихован карандашом (тёмные косые линии) и обведён тонкой
            // каймой; белая подложка — чтобы было видно и на сером листе. Не цветной: зелёный — это «верно».
            val rr = r * 1.05f
            drawCircle(Color.White.copy(alpha = 0.55f), rr, c)
            drawCircle(Color.White, rr, c, style = Stroke(max(2f, rr * 0.3f)))
            val pencil = Scrapbook.Ink.copy(alpha = 0.85f)
            val step = max(2.5f, rr * 0.42f)
            val lw = max(1.2f, rr * 0.14f)
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, rr)) }) {
                var k = -2 * rr
                while (k <= 2 * rr) {
                    drawLine(pencil, Offset(c.x + k - rr, c.y + rr), Offset(c.x + k + rr, c.y - rr), lw, StrokeCap.Round)
                    k += step
                }
            }
            drawCircle(pencil, rr, c, style = Stroke(max(1.2f, rr * 0.12f)))
        }
        LiveMark.Fix -> {
            drawRoundRect(
                LiveColors.Fix.copy(alpha = a),
                topLeft = Offset(c.x - R, c.y - R),
                size = Size(2 * R, 2 * R),
                cornerRadius = CornerRadius(R * 0.45f),
            )
            drawContext.canvas.nativeCanvas.drawText("И", c.x, c.y + R * 0.42f, letterPaint(R * 1.2f))
        }
    }
}

private val letterPaintCache = Paint().apply {
    color = android.graphics.Color.WHITE
    textAlign = Paint.Align.CENTER
    isAntiAlias = true
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

private fun letterPaint(size: Float): Paint = letterPaintCache.apply { textSize = size }

/** Все пометки листа через отображение «логические единицы сетки → экран». */
internal fun DrawScope.drawLiveMarks(r: LiveResult, geo: GridGeometry, map: (Double, Double) -> Offset?) {
    val half = geo.bubbleSize / 2.0
    for ((b, m) in r.markList(geo)) {
        val c = map(b.cx.toDouble(), b.cy.toDouble()) ?: continue
        val e = map(b.cx + half, b.cy.toDouble()) ?: continue
        drawLiveMark(m, c, hypot(e.x - c.x, e.y - c.y).coerceIn(3f, 60f))
    }
}

/**
 * Граница листа — толстые чернильные уголки с цветной серединой (цвет — состояние).
 * Прямоугольник — вокруг угловых меток: x −45…482, y −132…(высота сетки + 22).
 */
internal fun DrawScope.drawSheetCorners(map: (Double, Double) -> Offset?, gridHeight: Double, color: Color, gridWidth: Double) {
    val rx = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.rightX(gridWidth) + 35.0
    val pts = listOf(-45.0 to -132.0, rx to -132.0, rx to gridHeight + 22, -45.0 to gridHeight + 22)
        .map { (x, y) -> map(x, y) ?: return }
    val ink = 7.dp.toPx()
    val mid = 3.5.dp.toPx()
    for (i in 0..3) {
        val p = pts[i]
        val prev = pts[(i + 3) % 4]
        val next = pts[(i + 1) % 4]
        val arm = 0.13f
        val path = Path().apply {
            moveTo(p.x + (prev.x - p.x) * arm, p.y + (prev.y - p.y) * arm)
            lineTo(p.x, p.y)
            lineTo(p.x + (next.x - p.x) * arm, p.y + (next.y - p.y) * arm)
        }
        drawPath(path, Scrapbook.Ink, style = Stroke(ink, cap = StrokeCap.Square, join = StrokeJoin.Miter))
        drawPath(path, color, style = Stroke(mid, cap = StrokeCap.Square, join = StrokeJoin.Miter))
    }
}

/**
 * Вариант синей ручкой (не красной — чтобы не путать с оценкой), у левого края листа над левой верхней
 * меткой: «вариант» крупно, номер ещё крупнее и жирнее. Не прочитан — «вариант ?».
 */
internal fun DrawScope.drawPenVariant(map: (Double, Double) -> Offset?, variant: Int?, hand: Typeface?) {
    val c = map(-44.0, -146.0) ?: return
    val e = map(6.0, -146.0) ?: return
    val unit = hypot(e.x - c.x, e.y - c.y)
    val size = (unit * 0.85f).coerceIn(22.dp.toPx(), 60.dp.toPx())
    val blue = android.graphics.Color.rgb(0x15, 0x55, 0xB8)
    fun pen(sz: Float, bold: Boolean) = Paint().apply {
        color = blue
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        textSize = sz
        typeface = hand ?: Typeface.DEFAULT_BOLD
        if (bold) { style = Paint.Style.FILL_AND_STROKE; strokeWidth = sz * 0.045f; strokeJoin = Paint.Join.ROUND }
    }
    fun halo(p: Paint) = Paint(p).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = p.textSize * 0.16f
        strokeJoin = Paint.Join.ROUND
    }
    val word = pen(size, bold = false)
    val num = pen(size * 1.6f, bold = true)
    val label = "вариант "
    val n = "${variant ?: "?"}"
    val y = c.y + size * 0.35f
    rotate(-4f, c) {
        val cv = drawContext.canvas.nativeCanvas
        cv.drawText(label, c.x, y, halo(word))
        cv.drawText(label, c.x, y, word)
        val x2 = c.x + word.measureText(label)
        cv.drawText(n, x2, y + size * 0.12f, halo(num))
        cv.drawText(n, x2, y + size * 0.12f, num)
    }
}

/** Оценка красной ручкой у правого верхнего угла листа (наклонная цифра с подчёркиванием). */
internal fun DrawScope.drawPenGrade(map: (Double, Double) -> Offset?, grade: Int, hand: Typeface?, gridWidth: Double) {
    val dx = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.rightX(gridWidth) - 447.0
    val c = map(420.0 + dx, -58.0) ?: return
    val e = map(470.0 + dx, -58.0) ?: return
    val unit = hypot(e.x - c.x, e.y - c.y) // ≈ 50 логических единиц на экране
    val size = (unit * 1.5f).coerceIn(28.dp.toPx(), 90.dp.toPx())
    val paint = Paint().apply {
        color = android.graphics.Color.rgb(0xB5, 0x45, 0x2F)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        textSize = size
        typeface = hand ?: Typeface.DEFAULT_BOLD
    }
    rotate(-10f, c) {
        // Белая подложка-ореол, чтобы цифра читалась и на тёмном, и на светлом.
        val halo = Paint(paint).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.12f
            strokeJoin = Paint.Join.ROUND
        }
        drawContext.canvas.nativeCanvas.drawText("$grade", c.x, c.y + size * 0.35f, halo)
        drawContext.canvas.nativeCanvas.drawText("$grade", c.x, c.y + size * 0.35f, paint)
        val u = Path().apply {
            moveTo(c.x - size * 0.42f, c.y + size * 0.52f)
            quadraticBezierTo(c.x, c.y + size * 0.68f, c.x + size * 0.45f, c.y + size * 0.42f)
        }
        drawPath(u, Color.White, style = Stroke(size * 0.12f, cap = StrokeCap.Round))
        drawPath(u, LiveColors.RedPen, style = Stroke(size * 0.06f, cap = StrokeCap.Round))
    }
}

// ——— Бумажные элементы интерфейса.

/** Бумажная кнопка: заливка, чёрная обводка, «жёсткая» тень. */
@Composable
internal fun PaperButton(
    onClick: () -> Unit,
    fill: Color,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    /** Скругление тени — как у самой кнопки (у круглой — половина размера), иначе под кругом виден прямоугольник. */
    shadowCorner: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .stickerShadow(offset = 3.dp, cornerRadius = shadowCorner)
            .clip(shape)
            .background(fill, shape)
            .border(2.dp, Scrapbook.Ink, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun PaperIconButton(icon: ImageVector, description: String, fill: Color, size: Dp, onClick: () -> Unit) {
    PaperButton(onClick, fill, Modifier.size(size), CircleShape, shadowCorner = size / 2) {
        Icon(icon, contentDescription = description, tint = Scrapbook.Ink, modifier = Modifier.size(size * 0.48f))
    }
}

/** «На листе / Крупно» — две половинки в одной бумажной рамке. */
@Composable
internal fun PaperToggle(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .stickerShadow(offset = 3.dp, cornerRadius = 12.dp)
            .clip(shape)
            .background(Scrapbook.Paper, shape)
            .border(2.dp, Scrapbook.Ink, shape),
    ) {
        options.forEachIndexed { i, label ->
            if (i > 0) Box(Modifier.size(2.dp, 40.dp).background(Scrapbook.Ink))
            Text(
                label,
                fontFamily = Scrapbook.BodyFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Scrapbook.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .background(if (i == selected) Scrapbook.Mint else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
            )
        }
    }
}

/** Легенда: те же четыре формы, что на бланке. */
@Composable
internal fun MarksLegend(modifier: Modifier = Modifier) {
    val items = listOf(
        LiveMark.Ok to "верно",
        LiveMark.Bad to "ошибка",
        LiveMark.Need to "правильный ответ",
        LiveMark.Fix to "исправление",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (m, label) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(22.dp)) { drawLiveMark(m, Offset(size.width / 2, size.height / 2), size.minDimension * 0.32f) }
                        Text(
                            label,
                            fontFamily = Scrapbook.BodyFont,
                            fontSize = 12.sp,
                            color = Scrapbook.Ink,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Бумажная записка снизу: итог, легенда и кнопки. */
@Composable
internal fun LiveBottomNote(
    result: LiveResult?,
    status: String?,
    paused: Boolean,
    viewIndex: Int,
    onView: (Int) -> Unit,
    onPause: () -> Unit,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .stickerShadow(offset = 4.dp, cornerRadius = 8.dp)
                .background(Scrapbook.Paper, shape)
                .border(2.dp, Scrapbook.Ink, shape)
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val r = result?.takeIf { it.error == null }
            if (r != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(58.dp).rotate(-6f).border(3.dp, LiveColors.RedPen, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${r.grade}", fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = LiveColors.RedPen)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${r.correct} из ${r.total} · ${r.percent.toInt()}%",
                            fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Scrapbook.Ink,
                        )
                        Text(
                            buildString {
                                append(r.variant?.let { "Вариант $it" } ?: if (r.variantGuessed) "Вариант не прочитан — по ключу варианта 1" else "Без вариантов")
                                when {
                                    r.student != null -> append(" · ").append(r.student)
                                    r.code != null -> append(" · ученик №${r.code}")
                                }
                            },
                            fontFamily = Scrapbook.BodyFont, fontSize = 12.sp, color = Scrapbook.InkMuted,
                        )
                        Text(
                            when {
                                paused -> "⏸ на паузе"
                                r.confirmed -> "✓ подтверждено · ${"%.1f".format(r.ms / 1000.0)} с"
                                else -> "уточняю…"
                            },
                            fontFamily = Scrapbook.BodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                            color = if (r.confirmed && !paused) LiveColors.Ok else Scrapbook.InkMuted,
                        )
                    }
                }
                MarksLegend(Modifier.fillMaxWidth())
            } else if (status != null) {
                Text(status, fontFamily = Scrapbook.BodyFont, fontSize = 15.sp, color = Scrapbook.Ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperToggle(listOf("На листе", "Крупно"), viewIndex, Modifier.weight(1f), onView)
                PaperIconButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (paused) "Продолжить проверку" else "Поставить проверку на паузу",
                    if (paused) Scrapbook.Mint else Scrapbook.Rose,
                    58.dp,
                    onPause,
                )
                PaperButton(onRecheck, Scrapbook.Paper, Modifier.size(48.dp, 44.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Проверить ещё раз", tint = Scrapbook.Ink, modifier = Modifier.size(22.dp))
                }
            }
        }
        ScrapTape(Scrapbook.TapeYellow, Modifier.align(Alignment.TopStart).offset(x = 22.dp, y = (-9).dp), rotation = -4f)
        ScrapTape(Scrapbook.TapeYellow, Modifier.align(Alignment.TopEnd).offset(x = (-22).dp, y = (-9).dp), rotation = 4f)
    }
}
