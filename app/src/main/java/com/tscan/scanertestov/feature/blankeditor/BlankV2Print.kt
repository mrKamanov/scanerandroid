package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: печать бланка v2 — то, по чему его читает проверка (`feature/batch/engine/BlankV2Markers.kt`):
 *  - 4 угловые метки ArUco (словарь 4×4_50, номера 0–3) по углам вокруг сетки;
 *  - строка «Вариант» с кружками 1…N (вариант при печати можно закрасить заранее);
 *  - 8 квадратов кода ученика (печатаются закрашенными только при выгрузке по журналу);
 *  - поле «Фамилия | Имя».
 *
 * Все координаты — логические единицы относительно левого верхнего угла сетки и берутся ИЗ [BlankV2Markers]:
 * что печатаем, то и читаем. Рисуется на любом android.graphics.Canvas — картинка, страница PDF или экран
 * конструктора (через nativeCanvas), поэтому предпросмотр совпадает с бумагой.
 */
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.tscan.scanertestov.feature.batch.engine.BlankV2Markers

/** Что печатать в шапке бланка v2. */
data class BlankV2Options(
    /** Сколько кружков варианта (2–4); 0–1 — строки варианта нет. */
    val variantCount: Int,
    /** Закрасить этот вариант при печати (1…4) или null — ученик отметит сам. */
    val printedVariant: Int? = null,
    /** Номер ученика 1–63 — закрасить квадраты кода; null — квадраты не печатаем. */
    val studentCode: Int? = null,
    /** Поле «Фамилия | Имя». */
    val nameField: Boolean = true,
    /** Напечатать фамилию и имя в поле (выгрузка по журналу). */
    val printedSurname: String? = null,
    val printedName: String? = null,
)

internal object BlankV2Print {
    /** Внутренние 4×4 клетки меток 0–3 словаря DICT_4X4_50 (1 — белая клетка), из OpenCV 5.0 `generateImageMarker`. */
    private val ARUCO = arrayOf(
        "1011010100110010",
        "0000111110011010",
        "0011001100101101",
        "1001100101000110",
    )
    private const val INK = 0xFF2E3440.toInt()

    /**
     * [gridLeft], [gridTop] — левый верхний угол сетки в координатах холста; [gridWidth], [gridHeight] — её размер
     * (логические единицы, без масштаба бланка: масштаб и сдвиг уже применены к холсту).
     */
    fun draw(
        canvas: Canvas,
        gridLeft: Float,
        gridTop: Float,
        gridWidth: Float,
        gridHeight: Float,
        o: BlankV2Options,
        withBackground: Boolean,
    ) {
        canvas.save()
        canvas.translate(gridLeft, gridTop)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = INK; strokeWidth = 1.6f }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 10f; isFakeBoldText = true }
        val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B7280.toInt(); textSize = 6.5f }

        // ——— Угловые метки: чёрная рамка в 1 клетку + 4×4 клетки; вокруг — белое поле (тихая зона).
        val m = BlankV2Markers.MARKER.toFloat()
        val cell = m / 6f
        for (id in 0..3) {
            val p = BlankV2Markers.markerOrigin(id, gridHeight.toDouble(), gridWidth.toDouble())
            val x = p.x.toFloat()
            val y = p.y.toFloat()
            if (withBackground) canvas.drawRect(x - cell, y - cell, x + m + cell, y + m + cell, white)
            canvas.drawRect(x, y, x + m, y + m, ink)
            val bits = ARUCO[id]
            for (r in 0 until 4) for (c in 0 until 4) {
                if (bits[r * 4 + c] == '1') {
                    val cx = x + (c + 1) * cell
                    val cy = y + (r + 1) * cell
                    // Чуть внахлёст, чтобы на картинке не было тонких щелей между соседними белыми клетками.
                    canvas.drawRect(cx - 0.05f, cy - 0.05f, cx + cell + 0.05f, cy + cell + 0.05f, white)
                }
            }
        }

        // ——— Поле «Фамилия | Имя».
        if (o.nameField) {
            val b = BlankV2Markers.NAME_BOX
            val rect = RectF(b[0].toFloat(), b[1].toFloat(), b[2].toFloat(), b[3].toFloat())
            if (withBackground) canvas.drawRect(rect, white)
            canvas.drawRect(rect, line)
            val mid = rect.centerX()
            canvas.drawLine(mid, rect.top, mid, rect.bottom, line)
            canvas.drawText("Фамилия", rect.left + 1f, rect.top - 3f, label)
            canvas.drawText("Имя", mid + 1f, rect.top - 3f, label)
            val printed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 13f }
            val base = rect.centerY() + 4.5f
            o.printedSurname?.takeIf { it.isNotBlank() }?.let { canvas.drawText(fit(it, printed, rect.width() / 2 - 8f), rect.left + 4f, base, printed) }
            o.printedName?.takeIf { it.isNotBlank() }?.let { canvas.drawText(fit(it, printed, rect.width() / 2 - 8f), mid + 4f, base, printed) }
        }

        // ——— Квадраты кода ученика (только при выгрузке по журналу).
        o.studentCode?.takeIf { it in 1..63 }?.let { code ->
            val bits = BlankV2Markers.encodeCode(code)
            val sq = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = INK; strokeWidth = 1.3f }
            for (k in 0 until BlankV2Markers.CODE_BITS) {
                val s = BlankV2Markers.codeSquare(k)
                val r = RectF(s[0].toFloat(), s[1].toFloat(), s[2].toFloat(), s[3].toFloat())
                val one = (bits shr (BlankV2Markers.CODE_BITS - 1 - k)) and 1 == 1
                if (one) canvas.drawRect(r, ink) else {
                    if (withBackground) canvas.drawRect(r, white)
                    canvas.drawRect(r, sq)
                }
            }
            val first = BlankV2Markers.codeSquare(0)
            canvas.drawText("код ученика (не закрашивать)", first[0].toFloat(), first[3].toFloat() + 8f, hint)
        }

        // ——— Строка «Вариант»: кружки 1…N на местах, где их ищет проверка.
        if (o.variantCount >= 2) {
            val centers = BlankV2Markers.VARIANT_CENTERS.take(o.variantCount.coerceAtMost(4))
            val cy = centers.first().second.toFloat()
            canvas.drawText("Вариант", 2f, cy + 3.5f, label)
            val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9CA3AF.toInt(); textSize = 10f; textAlign = Paint.Align.CENTER }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = INK; strokeWidth = 1.6f }
            centers.forEachIndexed { i, (x, y) ->
                val cx = x.toFloat()
                val yy = y.toFloat()
                if (o.printedVariant == i + 1) {
                    canvas.drawCircle(cx, yy, 15f, ink)
                } else {
                    if (withBackground) canvas.drawCircle(cx, yy, 15f, white)
                    canvas.drawCircle(cx, yy, 15f, ring)
                    canvas.drawText("${i + 1}", cx, yy + 3.5f, num)
                }
            }
            if (o.printedVariant != null) {
                val last = centers.last().first.toFloat()
                canvas.drawText("отмечен при печати", last + 24f, cy + 2.5f, hint)
            }
        }
        canvas.restore()
    }

    /**
     * Насколько шапка v2 выступает за сетку (логические единицы, без масштаба): слева, сверху, справа, снизу —
     * с белым полем вокруг меток. Чтобы конструктор не задвинул метки за край листа.
     */
    fun outset(layout: BlankBubbleLayout): FloatArray {
        val b = blankBubbleLayoutBoundingRect(layout)
        val gw = (b.right - b.left).toDouble()
        val m = BlankV2Markers.MARKER
        val cell = m / 6
        val left = -BlankV2Markers.LEFT_MARKER_X + cell
        val top = -BlankV2Markers.TOP_MARKER_Y + cell
        val right = BlankV2Markers.rightX(gw) + m + cell - gw
        val bottom = BlankV2Markers.BOTTOM_MARKER_OFFSET + m + cell
        return floatArrayOf(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    /** Обрезает строку многоточием под ширину. */
    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }
}
