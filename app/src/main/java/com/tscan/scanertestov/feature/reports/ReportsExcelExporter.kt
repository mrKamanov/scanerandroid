package com.tscan.scanertestov.feature.reports

/**
 * Описание: выгрузка отчёта по тесту в .xlsx. Листы: «Сводка», «Ученики» (по строке на ученика,
 * ответ на каждый вопрос цветом), «Классы», «Варианты», «Вопросы». Ответы — как на бланке («14.2»).
 * Оформление ячейками (заливка, жирный, рамки): открывается одинаково в Excel, LibreOffice и Google Таблицах.
 */
import android.content.Context
import android.os.Environment
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsExcelExporter(private val context: Context) {

    private class Styles(private val wb: XSSFWorkbook) {
        private val cache = HashMap<String, XSSFCellStyle>()
        fun get(fill: Int? = null, font: Int = 0x1E1E1E, bold: Boolean = false, center: Boolean = false, border: Boolean = true, size: Short = 11, wrap: Boolean = false): XSSFCellStyle =
            cache.getOrPut("$fill|$font|$bold|$center|$border|$size|$wrap") {
                wb.createCellStyle().apply {
                    if (fill != null) {
                        setFillForegroundColor(XSSFColor(rgb(fill), null))
                        fillPattern = FillPatternType.SOLID_FOREGROUND
                    }
                    setFont(wb.createFont().apply {
                        this.bold = bold
                        fontHeightInPoints = size
                        setColor(XSSFColor(rgb(font), null))
                    })
                    alignment = if (center) HorizontalAlignment.CENTER else HorizontalAlignment.LEFT
                    verticalAlignment = VerticalAlignment.CENTER
                    wrapText = wrap
                    if (border) {
                        borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
                        borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
                    }
                }
            }
        private fun rgb(c: Int) = byteArrayOf((c shr 16 and 0xFF).toByte(), (c shr 8 and 0xFF).toByte(), (c and 0xFF).toByte())
    }

    private val head = 0x1F5A8C
    private val keyFill = 0xEAF1FB
    private val ok = 0xC6EFCE; private val okFont = 0x006100
    private val bad = 0xFFC7CE; private val badFont = 0x9C0006
    private val warn = 0xFFEB9C; private val warnFont = 0x9C5700
    private val gradeFill = mapOf(5 to 0xC6EFCE, 4 to 0xDDEBF7, 3 to 0xFFEB9C, 2 to 0xFFC7CE)

    private fun heat(p: Double): Pair<Int, Int> = when {
        p >= 80 -> ok to okFont
        p >= 60 -> warn to warnFont
        else -> bad to badFont
    }

    fun export(r: TestReport, o: ReportExportOptions): File {
        val wb = XSSFWorkbook()
        val st = Styles(wb)
        if (o.summary) summarySheet(wb, st, r)
        if (o.students) studentsSheet(wb, st, r, o.answers)
        if (o.classes && r.classRows.size > 1) classesSheet(wb, st, r)
        if (o.variants && r.variants.size > 1) variantsSheet(wb, st, r)
        if (o.questions) questionsSheet(wb, st, r)
        if (wb.numberOfSheets == 0) summarySheet(wb, st, r)
        val file = outFile(r, "xlsx")
        FileOutputStream(file).use { wb.write(it) }
        wb.close()
        return file
    }

    private fun outFile(r: TestReport, ext: String): File {
        val safe = r.title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(60).ifBlank { "Отчёт" }
        val cls = r.classFilter?.let { "_" + it.replace(Regex("[^\\p{L}\\p{N}]+"), "") }.orEmpty()
        val date = SimpleDateFormat("dd_MM_yyyy", Locale.getDefault()).format(Date())
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(File(dir, "reports-export").apply { mkdirs() }, "${safe}${cls}_$date.$ext")
    }

    private fun XSSFSheet.put(row: Int, col: Int, v: Any?, style: XSSFCellStyle) {
        val r = getRow(row) ?: createRow(row)
        val c = r.createCell(col)
        when (v) {
            is Number -> c.setCellValue(v.toDouble())
            null -> c.setCellValue("")
            else -> c.setCellValue(v.toString())
        }
        c.cellStyle = style
    }

    private fun titleRows(sh: XSSFSheet, st: Styles, r: TestReport, cols: Int): Int {
        sh.put(0, 0, r.title, st.get(bold = true, size = 14, border = false))
        sh.put(1, 0, buildString {
            append(r.questionCount).append(" вопросов")
            if (r.variants.size > 1) append(" · ").append(r.variants.size).append(
                when {
                    r.variants.size % 10 == 1 && r.variants.size % 100 != 11 -> " вариант"
                    r.variants.size % 10 in 2..4 && r.variants.size % 100 !in 12..14 -> " варианта"
                    else -> " вариантов"
                },
            )
            append(" · ").append(r.classFilter ?: r.classes.joinToString(", "))
            append(" · ").append(TestReportBuilder.worksWord(r.totalWorks))
            if (r.dateRange.isNotBlank()) append(" · ").append(r.dateRange)
        }, st.get(font = 0x555555, border = false))
        if (cols > 1) {
            sh.addMergedRegion(CellRangeAddress(0, 0, 0, cols - 1))
            sh.addMergedRegion(CellRangeAddress(1, 1, 0, cols - 1))
        }
        return 3
    }

    private fun summarySheet(wb: XSSFWorkbook, st: Styles, r: TestReport) {
        val sh = wb.createSheet("Сводка")
        var row = titleRows(sh, st, r, 6)
        val k = r.kpi
        sh.put(row, 0, "Показатель", st.get(head, 0xFFFFFF, bold = true)); sh.put(row, 1, "Значение", st.get(head, 0xFFFFFF, bold = true, center = true)); row++
        listOf(
            "Работ в отчёте" to k.works,
            "Средняя оценка" to String.format(Locale("ru"), "%.1f", k.avgGrade),
            "Средний процент" to "${f0(k.avgPercent)}%",
            "Успеваемость («3» и выше)" to "${f0(k.successPct)}%",
            "Качество («4» и «5»)" to "${f0(k.qualityPct)}%",
            "СОУ (степень обученности)" to "${f0(k.souPct)}%",
            "Повторы (не учтены)" to r.duplicateCount,
            "Без имени" to r.unnamedCount,
        ).forEach { (a, b) -> sh.put(row, 0, a, st.get()); sh.put(row, 1, b, st.get(center = true)); row++ }
        row++
        sh.put(row, 0, "Оценки", st.get(bold = true, border = false, size = 12)); row++
        listOf("Оценка", "Работ", "Доля", "").forEachIndexed { i, h -> sh.put(row, i, h, st.get(head, 0xFFFFFF, bold = true, center = true)) }; row++
        listOf(5, 4, 3, 2).forEach { g ->
            val n = k.grades[g] ?: 0
            val share = if (k.works == 0) 0.0 else n * 100.0 / k.works
            sh.put(row, 0, "«$g»", st.get(gradeFill[g], bold = true, center = true))
            sh.put(row, 1, n, st.get(center = true))
            sh.put(row, 2, "${f0(share)}%", st.get(center = true))
            sh.put(row, 3, "█".repeat((share / 4).toInt().coerceAtLeast(if (n > 0) 1 else 0)), st.get(font = 0x1F5A8C, border = false))
            row++
        }
        if (r.classRows.size > 1 && r.classFilter == null) {
            row++
            sh.put(row, 0, "Оценки по классам", st.get(bold = true, border = false, size = 12)); row++
            listOf("Класс", "«5»", "«4»", "«3»", "«2»", "Средняя").forEachIndexed { i, h -> sh.put(row, i, h, st.get(head, 0xFFFFFF, bold = true, center = true)) }; row++
            r.classRows.forEach { c ->
                sh.put(row, 0, c.className, st.get(bold = true))
                listOf(5, 4, 3, 2).forEachIndexed { i, g -> sh.put(row, i + 1, c.kpi.grades[g] ?: 0, st.get(gradeFill[g], center = true)) }
                sh.put(row, 5, String.format(Locale("ru"), "%.1f", c.kpi.avgGrade), st.get(center = true, bold = true))
                row++
            }
        }
        sh.setColumnWidth(0, 30 * 256); sh.setColumnWidth(1, 12 * 256); sh.setColumnWidth(2, 10 * 256); sh.setColumnWidth(3, 30 * 256)
        sh.setColumnWidth(4, 8 * 256); sh.setColumnWidth(5, 10 * 256)
    }

    private fun studentsSheet(wb: XSSFWorkbook, st: Styles, r: TestReport, answers: Boolean) {
        val sh = wb.createSheet("Ученики")
        val qn = if (answers) r.questionCount else 0
        val tail = listOf("Верно", "%", "Оценка", "Примечание")
        val headers = listOf("№", "Класс", "Фамилия Имя", "Вар.") + (1..qn).map { "$it" } + tail
        var row = titleRows(sh, st, r, headers.size.coerceAtMost(12))
        val headerRow = row
        headers.forEachIndexed { i, h -> sh.put(row, i, h, st.get(head, 0xFFFFFF, bold = true, center = i != 2)) }
        row++
        // Верные ответы по вариантам — строки над учениками.
        if (answers) {
            val variantsKeys = r.students.map { it.work }.groupBy { it.variant ?: 0 }.toSortedMap()
            variantsKeys.forEach { (v, ws) ->
                val key = ws.first().answerKey
                sh.put(row, 0, "", st.get(keyFill)); sh.put(row, 1, "", st.get(keyFill))
                sh.put(row, 2, if (v == 0) "Верные ответы" else "Верные ответы, вар. $v", st.get(keyFill, bold = true))
                sh.put(row, 3, if (v == 0) "" else v, st.get(keyFill, bold = true, center = true))
                for (q in 0 until qn) sh.put(row, 4 + q, key.getOrNull(q)?.sorted()?.joinToString(",") { "${it + 1}" }.orEmpty(), st.get(keyFill, bold = true, center = true))
                tail.indices.forEach { sh.put(row, 4 + qn + it, "", st.get(keyFill)) }
                row++
            }
        }
        r.students.forEachIndexed { i, s ->
            val w = s.work
            sh.put(row, 0, i + 1, st.get(center = true))
            sh.put(row, 1, s.className, st.get(center = true))
            sh.put(row, 2, s.displayName, st.get())
            sh.put(row, 3, w.variant ?: "", st.get(center = true))
            for (q in 0 until qn) {
                val sc = w.questionScores.firstOrNull { it.questionIndex == q }
                val (text, style) = when {
                    sc == null -> "" to st.get(center = true)
                    sc.selectedChoices.isEmpty() -> "—" to st.get(warn, warnFont, center = true)
                    sc.correct -> sc.selectedChoices.sorted().joinToString(",") { "${it + 1}" } to st.get(ok, okFont, center = true)
                    else -> (sc.selectedChoices.sorted().joinToString(",") { "${it + 1}" } + if (sc.hasFixed) " И" else "") to st.get(bad, badFont, center = true)
                }
                sh.put(row, 4 + q, text, style)
            }
            sh.put(row, 4 + qn, "${w.scoreCorrect} из ${w.scoreTotal}", st.get(center = true))
            sh.put(row, 5 + qn, "${f0(w.percent.toDouble())}%", st.get(center = true))
            sh.put(row, 6 + qn, w.grade, st.get(gradeFill[w.grade], bold = true, center = true))
            sh.put(row, 7 + qn, when {
                s.duplicateOf != null -> "повтор — не учтён"
                w.fullName == null -> "имя не указано"
                else -> ""
            }, st.get(font = 0x9C5700))
            row++
        }
        if (answers && qn > 0) {
            row++
            sh.put(row, 2, "В клетке — номер отмеченного кружка (4 → кружок 14.4). Зелёный — верно, красный — неверно, «—» — не ответил, «И» — исправление.", st.get(font = 0x555555, border = false))
        }
        sh.setAutoFilter(CellRangeAddress(headerRow, headerRow + r.students.size + if (answers) r.students.map { it.work.variant ?: 0 }.distinct().size else 0, 0, headers.size - 1))
        sh.createFreezePane(3, headerRow + 1)
        sh.setColumnWidth(0, 5 * 256); sh.setColumnWidth(1, 9 * 256); sh.setColumnWidth(2, 28 * 256); sh.setColumnWidth(3, 6 * 256)
        for (q in 0 until qn) sh.setColumnWidth(4 + q, 5 * 256)
        sh.setColumnWidth(4 + qn, 10 * 256); sh.setColumnWidth(5 + qn, 7 * 256); sh.setColumnWidth(6 + qn, 8 * 256); sh.setColumnWidth(7 + qn, 22 * 256)
    }

    private fun classesSheet(wb: XSSFWorkbook, st: Styles, r: TestReport) {
        val sh = wb.createSheet("Классы")
        var row = titleRows(sh, st, r, 11)
        val h = listOf("Класс", "Работ", "Оценка", "Процент", "Успеваемость", "Качество", "СОУ", "«5»", "«4»", "«3»", "«2»")
        h.forEachIndexed { i, t -> sh.put(row, i, t, st.get(head, 0xFFFFFF, bold = true, center = true)) }; row++
        (r.classRows.map { it.className to it.kpi } + listOf("Всего" to r.kpi)).forEach { (name, k) ->
            val b = name == "Всего"
            sh.put(row, 0, name, st.get(bold = true))
            sh.put(row, 1, k.works, st.get(center = true, bold = b))
            sh.put(row, 2, String.format(Locale("ru"), "%.1f", k.avgGrade), st.get(center = true, bold = b))
            val (f, c) = heat(k.avgPercent)
            sh.put(row, 3, "${f0(k.avgPercent)}%", st.get(f, c, center = true, bold = b))
            sh.put(row, 4, "${f0(k.successPct)}%", st.get(center = true, bold = b))
            sh.put(row, 5, "${f0(k.qualityPct)}%", st.get(center = true, bold = b))
            sh.put(row, 6, "${f0(k.souPct)}%", st.get(center = true, bold = b))
            listOf(5, 4, 3, 2).forEachIndexed { i, g -> sh.put(row, 7 + i, k.grades[g] ?: 0, st.get(gradeFill[g], center = true, bold = b)) }
            row++
        }
        row++
        sh.put(row, 0, "СОУ — степень обученности учащихся: (5·1 + 4·0,64 + 3·0,36 + 2·0,16) / число работ.", st.get(font = 0x555555, border = false))
        sh.setColumnWidth(0, 14 * 256); (1..10).forEach { sh.setColumnWidth(it, 12 * 256) }
    }

    private fun variantsSheet(wb: XSSFWorkbook, st: Styles, r: TestReport) {
        val sh = wb.createSheet("Варианты")
        var row = titleRows(sh, st, r, r.variants.size + 2)
        (listOf("Класс") + r.variants.map { "Вариант $it" } + listOf("Разница")).forEachIndexed { i, t ->
            sh.put(row, i, t, st.get(head, 0xFFFFFF, bold = true, center = true))
        }
        row++
        val rows = r.classVariantRows.map { it.className to it.cells } +
            listOf("Все" to r.variantCells.associateBy { it.variant })
        rows.forEach { (name, cells) ->
            sh.put(row, 0, name, st.get(bold = true))
            val present = r.variants.mapNotNull { cells[it]?.takeIf { c -> c.works > 0 } }
            r.variants.forEachIndexed { i, v ->
                val c = cells[v]?.takeIf { it.works > 0 }
                if (c == null) sh.put(row, 1 + i, "—", st.get(center = true))
                else { val (f, fc) = heat(c.avgPercent); sh.put(row, 1 + i, "${f0(c.avgPercent)}% (${c.works})", st.get(f, fc, center = true)) }
            }
            val gap = if (present.size >= 2) present.maxOf { it.avgPercent } - present.minOf { it.avgPercent } else null
            sh.put(row, 1 + r.variants.size, gap?.let { "${f0(it)}%" } ?: "—", st.get(if (gap != null && gap >= 10) 0x333333 else null, if (gap != null && gap >= 10) 0xFFFFFF else 0x1E1E1E, bold = true, center = true))
            row++
        }
        row++
        sh.put(row, 0, "Средний процент выполнения, в скобках — число работ. Разница 10% и больше — варианты неравноценны.", st.get(font = 0x555555, border = false))
        sh.setColumnWidth(0, 14 * 256); (1..r.variants.size + 1).forEach { sh.setColumnWidth(it, 14 * 256) }
    }

    private fun questionsSheet(wb: XSSFWorkbook, st: Styles, r: TestReport) {
        val sh = wb.createSheet("Вопросы")
        val classes = if (r.classFilter == null && r.classes.size > 1) r.classes else emptyList()
        val h = listOf("№", "Верный ответ", "Верно (все)") + classes + listOf("Частый неверный", "Не ответили", "Несколько отметок", "С исправлением")
        var row = titleRows(sh, st, r, h.size)
        h.forEachIndexed { i, t -> sh.put(row, i, t, st.get(head, 0xFFFFFF, bold = true, center = true, wrap = true)) }; row++
        r.questions.forEach { q ->
            sh.put(row, 0, q.number, st.get(bold = true, center = true))
            sh.put(row, 1, q.keyLabels.entries.sortedBy { it.key }.joinToString(" / ") { it.value }, st.get(center = true))
            val (f, c) = heat(q.correctPct)
            sh.put(row, 2, if (q.answered == 0) "—" else "${f0(q.correctPct)}%", st.get(if (q.answered == 0) null else f, if (q.answered == 0) 0x1E1E1E else c, center = true, bold = true))
            classes.forEachIndexed { i, cl ->
                val p = q.correctByClass[cl]
                if (p == null) sh.put(row, 3 + i, "—", st.get(center = true))
                else { val (ff, fc) = heat(p); sh.put(row, 3 + i, "${f0(p)}%", st.get(ff, fc, center = true)) }
            }
            val base = 3 + classes.size
            sh.put(row, base, q.topWrong?.let { "${it.label} — ${it.count} чел." } ?: "", st.get())
            sh.put(row, base + 1, q.noAnswer, st.get(center = true))
            sh.put(row, base + 2, q.multiMarked, st.get(center = true))
            sh.put(row, base + 3, q.withFixed, st.get(center = true))
            row++
        }
        if (r.pairs.isNotEmpty()) {
            row++
            sh.put(row, 0, "Ошибаются вместе: " + r.pairs.joinToString("; ") { "№ ${it.a} и № ${it.b} — ${it.together} уч." }, st.get(font = 0x555555, border = false))
        }
        sh.createFreezePane(1, 4)
        sh.setColumnWidth(0, 5 * 256); sh.setColumnWidth(1, 16 * 256); sh.setColumnWidth(2, 11 * 256)
        classes.indices.forEach { sh.setColumnWidth(3 + it, 10 * 256) }
        val base = 3 + classes.size
        sh.setColumnWidth(base, 22 * 256); sh.setColumnWidth(base + 1, 12 * 256); sh.setColumnWidth(base + 2, 14 * 256); sh.setColumnWidth(base + 3, 14 * 256)
    }
}
