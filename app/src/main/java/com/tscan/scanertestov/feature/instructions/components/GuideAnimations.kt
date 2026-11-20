package com.tscan.scanertestov.feature.instructions.components

/**
 * Описание: зацикленные анимации-подсказки для инструкции (в дополнение к «как закрашивать»), рисуются кодом:
 *  - PhotoFraming: телефон выравнивается над листом, угловые метки загораются, вспышка — снимок готов;
 *  - LiveCheck: «Один бланк» — пометки ложатся на кружки построчно, затем оценка красной ручкой;
 *  - NamedCode: на именном бланке закрашиваются квадраты кода → карточка «Онегин Евгений · 7 А»;
 *  - VariantSheets: бланки вариантов 1 и 2 — с бланка варианта 2 лишние кружки исчезают;
 *  - ExcelImport: строки таблицы Excel по одной «улетают» в журнал по классам.
 * Координаты — в условных единицах шириной [GW]; масштаб под ширину экрана делает [FillingAnimation].
 */
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal const val GW = 310f

private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF2E2A25)
private val Muted = Color(0xFF8A8378)
private val Green = Color(0xFF2F9E6B)
private val Red = Color(0xFFC0392B)
private val Blue = Color(0xFF1555B8)
private val Pen = Color(0xFF1D3A8A)
private val Mint = Color(0xFFA8E6CF)
private val Lemon = Color(0xFFFFF0A0)
private val SheetGreen = Color(0xFFDDF3E9)

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun ease(f: Float) = f * f * (3 - 2 * f)

/** Текст в условных координатах: текст меряется в пикселях экрана, поэтому масштаб временно снимаем. */
private fun DrawScope.label(
    m: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    sp: Float = 11f,
    color: Color = Ink,
    bold: Boolean = false,
    center: Boolean = false,
    alpha: Float = 1f,
) {
    if (alpha <= 0f) return
    val inv = GW / size.width
    scale(inv, inv, pivot = Offset.Zero) {
        val k = 1f / inv
        val l = m.measure(text, TextStyle(color = color.copy(alpha = alpha), fontSize = sp.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium))
        val left = if (center) x * k - l.size.width / 2f else x * k
        drawText(l, topLeft = Offset(left, y * k - l.size.height / 2f))
    }
}

/** Угловая метка (квадрат с узором), [lit] — зелёный кружок «найдена». */
private fun DrawScope.marker(c: Offset, s: Float, lit: Float) {
    drawRect(Ink, Offset(c.x - s / 2, c.y - s / 2), Size(s, s))
    drawRect(Paper, Offset(c.x - s / 4, c.y - s / 4), Size(s / 3, s / 3))
    drawRect(Paper, Offset(c.x + s / 12, c.y + s / 12), Size(s / 4, s / 4))
    if (lit > 0f) {
        drawCircle(Green, radius = s * (0.9f + 0.5f * (1f - lit)), center = c, style = Stroke(2.5f), alpha = lit)
    }
}

/** Мини-лист бланка: шапка, метки по углам, сетка кружков rows×cols. */
private fun DrawScope.miniSheet(
    topLeft: Offset,
    w: Float,
    h: Float,
    rows: Int,
    cols: Int,
    lit: Float = 0f,
    filled: Map<Int, Int> = emptyMap(),
    fillAlpha: Float = 1f,
): List<List<Offset>> {
    drawRoundRect(Color(0x22000000), Offset(topLeft.x + 3f, topLeft.y + 3f), Size(w, h), CornerRadius(2f))
    drawRect(Paper, topLeft, Size(w, h))
    val ms = w * 0.10f
    val m = ms * 0.9f
    listOf(
        Offset(topLeft.x + m, topLeft.y + h * 0.28f), Offset(topLeft.x + w - m, topLeft.y + h * 0.28f),
        Offset(topLeft.x + w - m, topLeft.y + h - m), Offset(topLeft.x + m, topLeft.y + h - m),
    ).forEachIndexed { i, c -> marker(c, ms, seg(lit, i * 0.25f, i * 0.25f + 0.25f)) }
    // шапка
    drawLine(Muted, Offset(topLeft.x + w * 0.12f, topLeft.y + h * 0.08f), Offset(topLeft.x + w * 0.55f, topLeft.y + h * 0.08f), 2f)
    drawLine(Muted, Offset(topLeft.x + w * 0.12f, topLeft.y + h * 0.16f), Offset(topLeft.x + w * 0.70f, topLeft.y + h * 0.16f), 1.5f)
    val gx0 = topLeft.x + w * 0.20f
    val gx1 = topLeft.x + w * 0.80f
    val gy0 = topLeft.y + h * 0.40f
    val gy1 = topLeft.y + h - m * 1.6f
    drawRect(Ink, Offset(gx0 - 6f, gy0 - 7f), Size(gx1 - gx0 + 12f, gy1 - gy0 + 14f), style = Stroke(2.2f))
    val r = ((gx1 - gx0) / cols / 2f * 0.62f).coerceAtMost((gy1 - gy0) / rows / 2f * 0.7f)
    return (0 until rows).map { row ->
        val y = if (rows == 1) (gy0 + gy1) / 2 else gy0 + (gy1 - gy0) * row / (rows - 1)
        (0 until cols).map { col ->
            val x = gx0 + (gx1 - gx0) * (col + 0.5f) / cols
            val c = Offset(x, y)
            drawCircle(Ink, r, c, style = Stroke(1.4f))
            if (filled[row] == col) drawCircle(Pen, r * 0.8f, c, alpha = fillAlpha)
            c
        }
    }
}

/** Телефон-рамка с белым контуром-видоискателем. */
private fun DrawScope.phone(center: Offset, w: Float, h: Float, angle: Float, alpha: Float) {
    if (alpha <= 0f) return
    rotate(angle, pivot = center) {
        val tl = Offset(center.x - w / 2, center.y - h / 2)
        drawRoundRect(Ink, tl, Size(w, h), CornerRadius(14f), style = Stroke(7f), alpha = alpha)
        drawCircle(Ink, 3f, Offset(center.x, tl.y + 7f), alpha = alpha)
        val g = 12f
        val i = 16f
        listOf(
            Offset(tl.x + i, tl.y + i) to Offset(1f, 1f), Offset(tl.x + w - i, tl.y + i) to Offset(-1f, 1f),
            Offset(tl.x + w - i, tl.y + h - i) to Offset(-1f, -1f), Offset(tl.x + i, tl.y + h - i) to Offset(1f, -1f),
        ).forEach { (p, d) ->
            drawLine(Color.White, p, Offset(p.x + g * d.x, p.y), 3f, StrokeCap.Round, alpha = alpha)
            drawLine(Color.White, p, Offset(p.x, p.y + g * d.y), 3f, StrokeCap.Round, alpha = alpha)
        }
    }
}

// ---------- Как снимать ----------

internal const val PHOTO_H = 196f

/** Стол под листом — чтобы белый лист был виден на белой карточке. */
private fun DrawScope.desk(h: Float) {
    drawRoundRect(Color(0xFFE8DFCF), Offset(0f, 0f), Size(GW, h), CornerRadius(8f))
}

internal fun DrawScope.drawPhotoFraming(t: Float, m: TextMeasurer) {
    desk(PHOTO_H)
    val sheetTl = Offset(105f, 30f)
    val sw = 100f
    val sh = 130f
    val lit = seg(t, 0.40f, 0.62f)
    miniSheet(sheetTl, sw, sh, rows = 5, cols = 4, lit = lit, filled = mapOf(0 to 1, 1 to 3, 2 to 0, 3 to 2, 4 to 1))
    val c = Offset(sheetTl.x + sw / 2, sheetTl.y + sh / 2)
    val align = ease(seg(t, 0.06f, 0.36f))
    val pc = Offset(lerpF(c.x + 70f, c.x, align), lerpF(c.y - 18f, c.y, align))
    val angle = lerpF(16f, 0f, align)
    val reset = 1f - seg(t, 0.93f, 0.99f)
    phone(pc, 132f, 156f, angle, (seg(t, 0f, 0.06f)) * reset)
    // подсказки
    label(m, if (t < 0.36f) "держите параллельно листу" else "все четыре метки в кадре", GW / 2, 186f, 10.5f, Ink, bold = true, center = true, alpha = reset)
    // вспышка и итог
    val flash = seg(t, 0.64f, 0.67f) * (1f - seg(t, 0.67f, 0.74f))
    if (flash > 0f) drawRect(Color.White, Offset.Zero, Size(GW, PHOTO_H), alpha = flash * 0.9f)
    val done = seg(t, 0.72f, 0.78f) * reset
    if (done > 0f) {
        drawRoundRect(Mint, Offset(GW / 2 - 70f, 6f), Size(140f, 20f), CornerRadius(10f), alpha = done)
        drawRoundRect(Ink, Offset(GW / 2 - 70f, 6f), Size(140f, 20f), CornerRadius(10f), style = Stroke(1.5f), alpha = done)
        label(m, "✓ Снимок 1 сохранён", GW / 2, 16f, 10.5f, Ink, bold = true, center = true, alpha = done)
    }
}

// ---------- Один бланк в камере ----------

internal const val LIVE_H = 196f

internal fun DrawScope.drawLiveCheck(t: Float, m: TextMeasurer) {
    desk(LIVE_H)
    val sheetTl = Offset(95f, 22f)
    val sw = 120f
    val sh = 150f
    val answers = mapOf(0 to 1, 1 to 3, 2 to 0, 3 to 2, 4 to 1)
    val key = mapOf(0 to 1, 1 to 2, 2 to 0, 3 to 2, 4 to 1)
    val reset = 1f - seg(t, 0.93f, 0.99f)
    val grid = miniSheet(sheetTl, sw, sh, rows = 5, cols = 4, lit = seg(t, 0.05f, 0.18f), filled = answers)
    phone(Offset(sheetTl.x + sw / 2, sheetTl.y + sh / 2), sw + 30f, sh + 12f, 0f, reset)
    val r = (grid[0][1].x - grid[0][0].x) * 0.42f
    for (row in 0 until 5) {
        val a = seg(t, 0.22f + row * 0.07f, 0.28f + row * 0.07f) * reset
        if (a <= 0f) continue
        val chosen = grid[row][answers.getValue(row)]
        if (answers[row] == key[row]) {
            drawCircle(Green, r * 1.25f, chosen, style = Stroke(3f), alpha = a)
        } else {
            drawCircle(Red, r * 1.25f, chosen, style = Stroke(3f), alpha = a)
            drawLine(Red, Offset(chosen.x - r, chosen.y - r), Offset(chosen.x + r, chosen.y + r), 2.5f, StrokeCap.Round, alpha = a)
            // где был правильный — штриховка
            val right = grid[row][key.getValue(row)]
            for (k in -2..2) {
                drawLine(Ink, Offset(right.x - r + k * r * 0.4f, right.y + r * 0.7f), Offset(right.x + r * 0.6f + k * r * 0.4f, right.y - r * 0.9f), 1.2f, alpha = a * 0.8f)
            }
        }
    }
    // Оценка красной ручкой и вариант синим.
    val g = seg(t, 0.62f, 0.72f) * reset
    if (g > 0f) {
        val gc = Offset(sheetTl.x + sw + 34f, sheetTl.y + 26f)
        drawArc(Red, -90f, 360f * g, false, Offset(gc.x - 18f, gc.y - 18f), Size(36f, 36f), style = Stroke(3f, cap = StrokeCap.Round))
        label(m, "4", gc.x, gc.y, 22f, Red, bold = true, center = true, alpha = g)
        label(m, "4 из 5", gc.x, gc.y + 30f, 10f, Red, bold = true, center = true, alpha = g)
    }
    label(m, "вариант 1", 42f, sheetTl.y + 20f, 10f, Blue, bold = true, center = true, alpha = seg(t, 0.18f, 0.24f) * reset)
    val leg = seg(t, 0.74f, 0.80f) * reset
    label(m, "зелёный — верно · красный — ошибка · штриховка — правильный", GW / 2, 188f, 9f, Ink, center = true, alpha = leg)
}

// ---------- Именной бланк: код → ученик ----------

internal const val CODE_H = 150f

internal fun DrawScope.drawNamedCode(t: Float, m: TextMeasurer) {
    val reset = 1f - seg(t, 0.93f, 0.99f)
    // Шапка именного бланка.
    val tl = Offset(10f, 20f)
    drawRoundRect(Color(0x22000000), Offset(tl.x + 3f, tl.y + 3f), Size(160f, 110f), CornerRadius(2f))
    drawRect(Paper, tl, Size(160f, 110f))
    label(m, "Фамилия", tl.x + 12f, tl.y + 12f, 7f, Muted)
    label(m, "Имя", tl.x + 80f, tl.y + 12f, 7f, Muted)
    drawRect(Ink, Offset(tl.x + 10f, tl.y + 18f), Size(140f, 22f), style = Stroke(1.5f))
    drawLine(Ink, Offset(tl.x + 78f, tl.y + 18f), Offset(tl.x + 78f, tl.y + 40f), 1.5f)
    label(m, "Онегин", tl.x + 16f, tl.y + 29f, 10f, Ink, bold = true)
    label(m, "Евгений", tl.x + 84f, tl.y + 29f, 10f, Ink, bold = true)
    // 8 квадратов кода: №2 → биты 00001001 (номер + проверка).
    val bits = listOf(0, 0, 0, 0, 1, 0, 0, 1)
    val sq = 13f
    val x0 = tl.x + 16f
    val y0 = tl.y + 56f
    label(m, "код ученика", tl.x + 80f, y0 - 6f, 7.5f, Muted, center = true)
    bits.forEachIndexed { i, b ->
        val p = Offset(x0 + i * (sq + 4f), y0)
        drawRect(Ink, p, Size(sq, sq), style = Stroke(1.5f))
        if (b == 1) {
            val f = seg(t, 0.10f + i * 0.025f, 0.16f + i * 0.025f) * reset
            if (f > 0f) drawRect(Ink, p, Size(sq * f, sq))
        }
    }
    // строка варианта
    label(m, "Вариант", tl.x + 14f, tl.y + 92f, 7.5f, Muted)
    drawCircle(Ink, 6f, Offset(tl.x + 66f, tl.y + 92f), style = Stroke(1.4f))
    drawCircle(Ink, 6f, Offset(tl.x + 84f, tl.y + 92f), style = Stroke(1.4f))
    drawCircle(Ink, 4.5f, Offset(tl.x + 84f, tl.y + 92f), alpha = seg(t, 0.30f, 0.34f) * reset)
    // Стрелка-«проверка».
    val ar = ease(seg(t, 0.40f, 0.55f)) * reset
    if (ar > 0f) {
        val a0 = Offset(178f, 75f)
        val a1 = Offset(lerpF(178f, 205f, ar), 75f)
        drawLine(Ink, a0, a1, 3f, StrokeCap.Round)
        if (ar > 0.95f) {
            drawLine(Ink, a1, Offset(a1.x - 7f, a1.y - 6f), 3f, StrokeCap.Round)
            drawLine(Ink, a1, Offset(a1.x - 7f, a1.y + 6f), 3f, StrokeCap.Round)
        }
        label(m, "№2", 191f, 62f, 9f, Muted, bold = true, center = true, alpha = ar)
    }
    // Карточка результата.
    val card = ease(seg(t, 0.55f, 0.68f)) * reset
    if (card > 0f) {
        translate(top = (1f - card) * 12f) {
            val c = Offset(212f, 40f)
            drawRoundRect(Color(0x22000000), Offset(c.x + 3f, c.y + 3f), Size(92f, 72f), CornerRadius(6f), alpha = card)
            drawRoundRect(Paper, c, Size(92f, 72f), CornerRadius(6f), alpha = card)
            drawRoundRect(Ink, c, Size(92f, 72f), CornerRadius(6f), style = Stroke(1.5f), alpha = card)
            label(m, "Онегин", c.x + 8f, c.y + 13f, 10f, Ink, bold = true, alpha = card)
            label(m, "Евгений", c.x + 8f, c.y + 26f, 10f, Ink, bold = true, alpha = card)
            drawRoundRect(SheetGreen, Offset(c.x + 6f, c.y + 36f), Size(80f, 14f), CornerRadius(4f), alpha = card)
            label(m, "7 А · по коду №2", c.x + 46f, c.y + 43f, 7.5f, Color(0xFF085041), bold = true, center = true, alpha = card)
            label(m, "вариант 2 · «4»", c.x + 8f, c.y + 60f, 8.5f, Ink, alpha = card)
        }
    }
    label(m, "фамилия подставится сама", 258f, 132f, 8.5f, Muted, center = true, alpha = seg(t, 0.70f, 0.76f) * reset)
}

// ---------- Разные бланки вариантов ----------

internal const val VAR_H = 160f

internal fun DrawScope.drawVariantSheets(t: Float, m: TextMeasurer) {
    val reset = 1f - seg(t, 0.93f, 0.99f)
    fun sheet(x: Float, v: Int, removedAlpha: Float, strike: Float) {
        val tl = Offset(x, 22f)
        val w = 118f
        val h = 118f
        drawRoundRect(Color(0x22000000), Offset(tl.x + 3f, tl.y + 3f), Size(w, h), CornerRadius(2f))
        drawRect(Paper, tl, Size(w, h))
        label(m, "Вариант", tl.x + 10f, tl.y + 14f, 7.5f, Muted)
        listOf(1, 2).forEach { k ->
            val c = Offset(tl.x + 52f + (k - 1) * 18f, tl.y + 14f)
            drawCircle(Ink, 6f, c, style = Stroke(1.4f))
            if (k == v) drawCircle(Ink, 4.5f, c)
        }
        val rowsN = 4
        val hide = if (v == 2) setOf(0 to 3, 1 to 2, 1 to 3) else emptySet()
        for (row in 0 until rowsN) {
            label(m, "${row + 1}.", tl.x + 12f, tl.y + 36f + row * 22f, 8f, Ink, bold = true)
            for (col in 0 until 4) {
                val c = Offset(tl.x + 34f + col * 22f, tl.y + 36f + row * 22f)
                val hidden = (row to col) in hide
                val a = if (hidden) removedAlpha else 1f
                drawCircle(Ink, 7.5f, c, style = Stroke(1.4f), alpha = a)
                if (hidden && strike > 0f) {
                    drawLine(Blue, Offset(c.x - 8f, c.y + 8f), Offset(c.x - 8f + 16f * strike, c.y + 8f - 16f * strike), 2.5f, StrokeCap.Round, alpha = a)
                }
            }
        }
        drawRoundRect(if (v == 2) Lemon else Mint, Offset(tl.x + w / 2 - 34f, tl.y + h + 6f), Size(68f, 16f), CornerRadius(8f))
        label(m, "бланк вар. $v", tl.x + w / 2, tl.y + h + 14f, 8.5f, Ink, bold = true, center = true)
    }
    val strike = seg(t, 0.20f, 0.42f)
    val removed = 1f - seg(t, 0.48f, 0.62f) * reset
    sheet(26f, 1, 1f, 0f)
    sheet(166f, 2, removed, strike * reset)
    label(m, "на бланке варианта 2 лишних кружков нет", GW / 2, 9f, 9.5f, Ink, bold = true, center = true, alpha = seg(t, 0.62f, 0.70f) * reset)
}

// ---------- Загрузка из Excel ----------

internal const val XLS_H = 150f

internal fun DrawScope.drawExcelImport(t: Float, m: TextMeasurer) {
    val reset = 1f - seg(t, 0.93f, 0.99f)
    val rows = listOf(
        listOf("7А", "Иванов", "Иван"),
        listOf("", "Петрова", "Варвара"),
        listOf("7Б", "Сидоров Пётр", ""),
    )
    val names = listOf("Иванов Иван" to "7 А", "Петрова Варвара" to "7 А", "Сидоров Пётр" to "7 Б")
    // Таблица.
    val tl = Offset(8f, 28f)
    val colW = floatArrayOf(30f, 70f, 50f)
    val rh = 20f
    label(m, "Excel · лист «Ученики»", tl.x, 14f, 8.5f, Muted, bold = true)
    drawRect(SheetGreen.copy(alpha = 1f), tl, Size(colW.sum(), rh))
    listOf("Класс", "Фамилия", "Имя").forEachIndexed { i, h ->
        label(m, h, tl.x + colW.take(i).sum() + 4f, tl.y + rh / 2, 8f, Ink, bold = true)
    }
    rows.forEachIndexed { r, cells ->
        val y = tl.y + rh * (r + 1)
        val fly = seg(t, 0.15f + r * 0.18f, 0.30f + r * 0.18f)
        val highlight = if (fly in 0.01f..0.99f) 1f else 0f
        if (highlight > 0f) drawRect(Lemon, Offset(tl.x, y), Size(colW.sum(), rh))
        cells.forEachIndexed { i, c -> label(m, c, tl.x + colW.take(i).sum() + 4f, y + rh / 2, 8.5f, Ink) }
        // «Летящая» строка.
        if (fly > 0f && reset > 0f) {
            val from = Offset(tl.x + colW.sum() / 2, y + rh / 2)
            val to = Offset(232f, 54f + r * 26f)
            val p = Offset(lerpF(from.x, to.x, ease(fly)), lerpF(from.y, to.y, ease(fly)) - 18f * kotlin.math.sin(fly * Math.PI.toFloat()))
            if (fly < 1f) {
                drawRoundRect(Lemon, Offset(p.x - 30f, p.y - 8f), Size(60f, 16f), CornerRadius(8f))
                label(m, names[r].first.substringBefore(' '), p.x, p.y, 7.5f, Ink, bold = true, center = true)
            }
        }
    }
    for (i in 0..3) drawLine(Muted, Offset(tl.x, tl.y + rh * i), Offset(tl.x + colW.sum(), tl.y + rh * i), 0.8f)
    var cx = tl.x
    for (w in colW + 0f) { drawLine(Muted, Offset(cx, tl.y), Offset(cx, tl.y + rh * 4), 0.8f); cx += w }
    label(m, "пусто = класс выше", tl.x + 15f, tl.y + rh * 4 + 10f, 7.5f, Muted)
    label(m, "ФИО в одной ячейке — разделится", tl.x + 15f, tl.y + rh * 4 + 22f, 7.5f, Muted)
    // Журнал.
    val j = Offset(176f, 28f)
    drawRoundRect(Paper, j, Size(128f, 100f), CornerRadius(6f))
    drawRoundRect(Ink, j, Size(128f, 100f), CornerRadius(6f), style = Stroke(1.5f))
    label(m, "Журналы", j.x + 8f, j.y + 11f, 9f, Ink, bold = true)
    names.forEachIndexed { r, (n, cls) ->
        val a = seg(t, 0.28f + r * 0.18f, 0.32f + r * 0.18f) * reset
        val y = j.y + 26f + r * 26f
        drawRoundRect(if (cls == "7 А") Mint else Color(0xFFCFE3FF), Offset(j.x + 6f, y - 8f), Size(28f, 16f), CornerRadius(6f), alpha = a)
        label(m, cls, j.x + 20f, y, 7.5f, Ink, bold = true, center = true, alpha = a)
        label(m, n, j.x + 40f, y, 8.5f, Ink, alpha = a)
    }
    val done = seg(t, 0.82f, 0.88f) * reset
    label(m, "Импорт: добавлено 3", j.x + 64f, j.y + 112f, 8.5f, Green, bold = true, center = true, alpha = done)
}
