package com.tscan.scanertestov.feature.reports

/**
 * Описание: выгрузка отчёта по тесту в PDF (A4, читается и в чёрно-белой печати).
 * Стр. 1 — сводка (главные цифры, оценки по классам, классы, варианты, выводы);
 * затем вопросы (доля верных по классам, частый неверный ответ); затем по странице на класс —
 * список учеников с оценкой и строкой ответов (● верно, ○ неверно, – не ответил, И — исправление).
 */
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsPdfExporter(private val context: Context) {

    private val W = 595f
    private val H = 842f
    private val M = 40f
    private val CW = W - 2 * M

    private fun paint(size: Float, bold: Boolean = false, color: Int = 0xFF1E1E1E.toInt()) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; isFakeBoldText = bold; this.color = color }

    private val pTitle = paint(15f, true)
    private val pSub = paint(9.5f, color = 0xFF555555.toInt())
    private val pH2 = paint(12f, true)
    private val pBody = paint(9.5f)
    private val pBodyB = paint(9.5f, true)
    private val pSmall = paint(8.5f, color = 0xFF555555.toInt())
    private val pBig = paint(18f, true)

    private inner class Doc(val r: TestReport) {
        val pdf = PdfDocument()
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = 0f
        var n = 0

        var contentTop = 0f

        fun newPage() {
            if (page != null && y <= contentTop + 0.5f) return // страница ещё пустая
            page?.let { footer(); pdf.finishPage(it) }
            n++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n).create())
            c = page!!.canvas
            c.drawColor(Color.WHITE)
            y = M - 6
            // Шапка на каждой странице.
            c.drawText(ellipsize(r.title, pTitle, CW - 120), M, y + 14, pTitle)
            val sub = buildString {
                append(r.classFilter ?: r.classes.joinToString(", "))
                append(" · ").append(TestReportBuilder.worksWord(r.totalWorks))
                if (r.dateRange.isNotBlank()) append(" · ").append(r.dateRange)
            }
            c.drawText(ellipsize(sub, pSub, CW - 120), M, y + 28, pSub)
            val rp = paint(8.5f, color = 0xFF555555.toInt()).apply { textAlign = Paint.Align.RIGHT }
            c.drawText("Отчёт о проверке", W - M, y + 12, rp)
            c.drawText("Сканер тестов", W - M, y + 24, rp)
            val line = Paint().apply { color = 0xFF1E1E1E.toInt(); strokeWidth = 1.6f }
            c.drawLine(M, y + 36, W - M, y + 36, line)
            y += 50
            contentTop = y
        }

        fun footer() {
            val fp = paint(8f, color = 0xFF777777.toInt())
            val line = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.8f }
            c.drawLine(M, H - 26, W - M, H - 26, line)
            val left = buildString {
                if (r.duplicateCount > 0) append("Повторные снимки не учтены: ${r.duplicateCount}. ")
                if (r.unnamedCount > 0) append("Без имени: ${r.unnamedCount}. ")
                append("Сформирован ").append(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()))
            }
            c.drawText(left, M, H - 14, fp)
            fp.textAlign = Paint.Align.RIGHT
            c.drawText("стр. $n", W - M, H - 14, fp)
        }

        fun ensure(h: Float) {
            if (page == null) newPage()
            else if (y + h > H - 36) { contentTop = -1f; newPage() }
        }

        fun text(t: String, p: TextPaint, width: Float = CW, x: Float = M, gapAfter: Float = 4f) {
            val l = StaticLayout.Builder.obtain(t, 0, t.length, p, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            ensure(l.height.toFloat())
            c.save(); c.translate(x, y); l.draw(c); c.restore()
            y += l.height + gapAfter
        }

        fun h2(t: String) { ensure(40f); y += 8; text(t, pH2, gapAfter = 6f) }

        fun finish(): PdfDocument { page?.let { footer(); pdf.finishPage(it) }; page = null; return pdf }
    }

    private class TCell(val text: String, val fill: Int? = null, val bold: Boolean = false, val white: Boolean = false, val mono: Boolean = false)

    /** Таблица: доли ширины столбцов, первая строка — заголовок. Переносится на новую страницу с повтором шапки. */
    private fun Doc.table(weights: List<Float>, header: List<String>, rows: List<List<TCell>>, rowH: Float = 16f, firstLeft: Int = 1) {
        val sum = weights.sum()
        val xs = weights.runningFold(M) { acc, w -> acc + CW * w / sum }
        val border = Paint().apply { style = Paint.Style.STROKE; color = 0xFF999999.toInt(); strokeWidth = 0.6f }
        val fill = Paint().apply { style = Paint.Style.FILL }
        fun drawRow(cells: List<TCell>, isHeader: Boolean) {
            ensure(rowH)
            cells.forEachIndexed { i, cell ->
                val rect = RectF(xs[i], y, xs[i + 1], y + rowH)
                val f = if (isHeader) 0xFFE6E6E6.toInt() else cell.fill
                if (f != null) { fill.color = f; c.drawRect(rect, fill) }
                c.drawRect(rect, border)
                val p = paint(if (cell.mono) 9f else 9f, isHeader || cell.bold, if (cell.white) Color.WHITE else 0xFF1E1E1E.toInt())
                if (cell.mono) p.letterSpacing = 0.08f
                val w = rect.width() - 6
                val t = ellipsize(cell.text, p, w)
                val left = i < firstLeft
                p.textAlign = if (left) Paint.Align.LEFT else Paint.Align.CENTER
                val tx = if (left) rect.left + 3 else rect.centerX()
                c.drawText(t, tx, rect.centerY() + p.textSize * 0.35f, p)
            }
            y += rowH
        }
        val headerCells = header.map { TCell(it) }
        ensure(rowH * 2)
        drawRow(headerCells, true)
        rows.forEach { r ->
            if (y + rowH > H - 36) { newPage(); drawRow(headerCells, true) }
            drawRow(r, false)
        }
        y += 6
    }

    private fun shade(p: Double): Pair<Int?, Boolean> = when {
        p < 40 -> 0xFF3A3A3A.toInt() to true
        p < 60 -> 0xFF9A9A9A.toInt() to true
        p < 80 -> 0xFFE3E3E3.toInt() to false
        else -> null to false
    }

    private fun pctCell(p: Double?, suffix: String = ""): TCell =
        if (p == null) TCell("—") else shade(p).let { (f, w) -> TCell("${f0(p)}%$suffix", f, white = w) }

    fun export(r: TestReport, o: ReportExportOptions): File {
        val d = Doc(r)
        d.newPage()
        if (o.summary) summary(d, r)
        if (o.classes && r.classRows.size > 1) classesBlock(d, r)
        if (o.variants && r.variants.size > 1) variantsBlock(d, r)
        if (o.summary) conclusions(d, r)
        if (o.questions) { d.newPage(); questions(d, r) }
        if (o.students) students(d, r, o.answers)
        val pdf = d.finish()
        val file = outFile(r)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun outFile(r: TestReport): File {
        val safe = r.title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(60).ifBlank { "Отчёт" }
        val cls = r.classFilter?.let { "_" + it.replace(Regex("[^\\p{L}\\p{N}]+"), "") }.orEmpty()
        val date = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault()).format(Date())
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(File(dir, "reports-export").apply { mkdirs() }, "${safe}${cls}_$date.pdf")
    }

    private fun summary(d: Doc, r: TestReport) {
        val k = r.kpi
        val boxes = listOf(f1(k.avgGrade) to "средняя оценка", "${f0(k.avgPercent)}%" to "средний процент", "${f0(k.successPct)}%" to "успеваемость", "${f0(k.qualityPct)}%" to "качество", "${f0(k.souPct)}%" to "СОУ")
        d.ensure(56f)
        val bw = (CW - 4 * 6) / 5
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; color = 0xFF1E1E1E.toInt() }
        boxes.forEachIndexed { i, (v, l) ->
            val x = M + i * (bw + 6)
            d.c.drawRoundRect(RectF(x, d.y, x + bw, d.y + 48), 6f, 6f, stroke)
            val pb = paint(18f, true).apply { textAlign = Paint.Align.CENTER }
            d.c.drawText(v, x + bw / 2, d.y + 24, pb)
            val ps = paint(8f, color = 0xFF555555.toInt()).apply { textAlign = Paint.Align.CENTER }
            d.c.drawText(l, x + bw / 2, d.y + 40, ps)
        }
        d.y += 58
        d.h2(if (r.classRows.size > 1 && r.classFilter == null) "Оценки по классам" else "Оценки")
        val bars = (if (r.classRows.size > 1 && r.classFilter == null) r.classRows.map { it.className to it.kpi } else emptyList()) + listOf((if (r.classRows.size > 1 && r.classFilter == null) "Все" else "") to k)
        val shades = mapOf(5 to 0xFF1E1E1E.toInt(), 4 to 0xFF555555.toInt(), 3 to 0xFF9A9A9A.toInt(), 2 to 0xFFD6D6D6.toInt())
        val labelW = if (bars.size > 1) 56f else 0f
        bars.forEach { (name, kk) ->
            d.ensure(20f)
            if (labelW > 0) d.c.drawText(ellipsize(name, pBodyB, labelW - 4), M, d.y + 12, pBodyB)
            val barX = M + labelW
            val barW = CW - labelW - 70
            var x = barX
            val total = kk.works.coerceAtLeast(1)
            listOf(5, 4, 3, 2).forEach { g ->
                val n = kk.grades[g] ?: 0
                if (n == 0) return@forEach
                val w = barW * n / total
                val f = Paint().apply { color = shades[g]!! }
                d.c.drawRect(x, d.y, x + w, d.y + 16, f)
                if (w > 12) {
                    val tp = paint(8.5f, true, if (g >= 4) Color.WHITE else 0xFF1E1E1E.toInt()).apply { textAlign = Paint.Align.CENTER }
                    d.c.drawText("$n", x + w / 2, d.y + 11.5f, tp)
                }
                x += w
            }
            d.c.drawRect(barX, d.y, barX + barW, d.y + 16, Paint().apply { style = Paint.Style.STROKE; color = 0xFF1E1E1E.toInt(); strokeWidth = 0.8f })
            d.c.drawText("ср. ${f1(kk.avgGrade)}", barX + barW + 8, d.y + 12, pBody)
            d.y += 22
        }
        d.text("Заливка: «5» — чёрная, «4» — тёмно-серая, «3» — серая, «2» — светлая.", pSmall)
    }

    private fun classesBlock(d: Doc, r: TestReport) {
        d.h2("Сравнение классов")
        val rows = (r.classRows.map { it.className to it.kpi } + listOf("Все" to r.kpi)).map { (n, k) ->
            val b = n == "Все"
            listOf(TCell(n, bold = true), TCell("${k.works}", bold = b), TCell(f1(k.avgGrade), bold = b), pctCell(k.avgPercent), TCell("${f0(k.successPct)}%", bold = b), TCell("${f0(k.qualityPct)}%", bold = b), TCell("${f0(k.souPct)}%", bold = b))
        }
        d.table(listOf(1.3f, 0.8f, 0.9f, 1f, 1.2f, 1f, 0.8f), listOf("Класс", "Работ", "Оценка", "Процент", "Успеваемость", "Качество", "СОУ"), rows)
    }

    private fun variantsBlock(d: Doc, r: TestReport) {
        d.h2("Сравнение вариантов")
        val src = r.classVariantRows.map { it.className to it.cells } + listOf("Все" to r.variantCells.associateBy { it.variant })
        val rows = src.map { (n, cells) ->
            val present = r.variants.mapNotNull { cells[it]?.takeIf { c -> c.works > 0 } }
            val gap = if (present.size >= 2) present.maxOf { it.avgPercent } - present.minOf { it.avgPercent } else null
            listOf(TCell(n, bold = true)) + r.variants.map { v -> cells[v]?.takeIf { it.works > 0 }?.let { pctCell(it.avgPercent, " (${it.works})") } ?: TCell("—") } +
                listOf(TCell(gap?.let { "${f0(it)}%" + if (it >= 10) " ⚑" else "" } ?: "—", bold = gap != null && gap >= 10))
        }
        d.table(listOf(1.2f) + r.variants.map { 1.2f } + listOf(0.9f), listOf("Класс") + r.variants.map { "Вариант $it" } + listOf("Разница"), rows)
        d.text("Средний процент, в скобках — число работ. ⚑ — разница 10% и больше, варианты неравноценны.", pSmall)
    }

    private fun conclusions(d: Doc, r: TestReport) {
        val lines = buildList {
            if (r.classRows.size > 1 && r.classFilter == null) {
                val best = r.classRows.maxBy { it.kpi.avgPercent }
                val worst = r.classRows.minBy { it.kpi.avgPercent }
                val gap = best.kpi.avgPercent - worst.kpi.avgPercent
                if (gap >= 5) add("${worst.className} отстаёт от ${best.className} на ${f0(gap)}%.")
            }
            TestReportBuilder.variantVerdict(r.variantCells)?.let { verdict ->
                add(verdict)
            }
            val hard = r.hardest.take(3)
            if (hard.isNotEmpty()) add("Самые трудные вопросы — " + hard.joinToString(", ") { "№ ${it.number} (${f0(it.correctPct)}%)" } + ".")
        }
        if (lines.isEmpty()) return
        d.h2("Выводы")
        lines.forEach { d.text("• $it", pBody, gapAfter = 2f) }
    }

    private fun questions(d: Doc, r: TestReport) {
        d.h2("Вопросы: доля верных ответов")
        val classes = if (r.classFilter == null && r.classes.size > 1) r.classes else emptyList()
        val header = listOf("№", "Верный", "Все") + classes + listOf("Частый неверный (чел.)")
        val rows = r.questions.map { q ->
            listOf(TCell("${q.number}", bold = true), TCell(q.keyLabels.entries.sortedBy { it.key }.joinToString(" / ") { it.value }), if (q.answered == 0) TCell("—") else pctCell(q.correctPct)) +
                classes.map { pctCell(q.correctByClass[it]) } +
                listOf(TCell(q.topWrong?.let { "${it.label} — ${it.count}" } ?: ""))
        }
        d.table(listOf(0.5f, 1.3f, 0.8f) + classes.map { 0.8f } + listOf(1.8f), header, rows, rowH = 15f)
        d.text("Ниже 40% — тёмная заливка, 40–59% — серая, 60–79% — светлая, 80% и выше — без заливки.", pSmall)
        if (r.pairs.isNotEmpty()) {
            d.h2("Ошибаются вместе")
            d.text(r.pairs.joinToString(" · ") { "№ ${it.a} и № ${it.b} — ${it.together} уч." }, pBody)
        }
    }

    private fun students(d: Doc, r: TestReport, answers: Boolean) {
        r.students.groupBy { it.className }.forEach { (cls, rows) ->
            d.newPage()
            val counted = rows.filter { it.duplicateOf == null }.map { it.work }
            val k = TestReportBuilder.kpi(counted)
            d.h2("$cls · ${TestReportBuilder.worksWord(counted.size)}")
            d.text("ср. оценка ${f1(k.avgGrade)} · ${f0(k.avgPercent)}% · успеваемость ${f0(k.successPct)}% · качество ${f0(k.qualityPct)}%", pBody)
            val header = listOf("№", "Фамилия Имя", "Вар.") + (if (answers) listOf("Ответы на вопросы 1–${r.questionCount}") else emptyList()) + listOf("Верно", "%", "Оц.")
            val table = rows.mapIndexed { i, s ->
                val w = s.work
                val strip = (0 until r.questionCount).joinToString("") { qi ->
                    val sc = w.questionScores.firstOrNull { it.questionIndex == qi }
                    when {
                        sc == null -> " "
                        sc.hasFixed && !sc.correct -> "И"
                        sc.selectedChoices.isEmpty() -> "–"
                        sc.correct -> "●"
                        else -> "○"
                    }
                }
                listOf(TCell("${i + 1}"), TCell(s.displayName + if (s.duplicateOf != null) " (повтор)" else ""), TCell(w.variant?.toString() ?: "")) +
                    (if (answers) listOf(TCell(strip, mono = true)) else emptyList()) +
                    listOf(TCell("${w.scoreCorrect}"), TCell("${f0(w.percent.toDouble())}%"), TCell("${w.grade}", bold = true))
            }
            val weights = listOf(0.4f, 2.4f, 0.5f) + (if (answers) listOf((r.questionCount * 0.13f).coerceIn(1.6f, 3.6f)) else emptyList()) + listOf(0.6f, 0.6f, 0.5f)
            d.table(weights, header, table, rowH = 16f, firstLeft = 2)
            if (answers) d.text("● верно · ○ неверно · – не ответил · И — исправление. Каждый класс начинается с новой страницы.", pSmall)
        }
    }

    private fun ellipsize(t: String, p: TextPaint, w: Float): String =
        if (p.measureText(t) <= w) t else TextUtils.ellipsize(t, p, w, TextUtils.TruncateAt.END).toString()
}
