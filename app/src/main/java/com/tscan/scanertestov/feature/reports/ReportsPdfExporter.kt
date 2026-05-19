package com.tscan.scanertestov.feature.reports

/**
 * Описание: экспорт аналитических отчётов в PDF (A4, через [android.graphics.pdf.PdfDocument]).
 */
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ReportsPdfExporter(private val context: Context) {

    fun exportToPdf(state: ReportsState): File? {
        val summary = state.summary ?: return null
        val pdf = PdfDocument()
        return try {
            val session = PdfSession(pdf)
            val sectionPaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = SECTION_TEXT_SIZE
                    isFakeBoldText = true
                }
            val bodyPaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = BODY_TEXT_SIZE
                }
            val smallPaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = SMALL_TEXT_SIZE
                }

            session.startPortraitPage()
            session.drawStaticLayout(
                "OMR — аналитический отчёт\nСформирован: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}",
                bodyPaint,
                session.contentWidthPortrait(),
            )
            session.advance(SECTION_GAP)

            val gradeDistributionMap = state.gradeDistribution.associate { it.grade to it.count }
            val g2 = gradeDistributionMap[2] ?: 0
            val g3 = gradeDistributionMap[3] ?: 0
            val g4 = gradeDistributionMap[4] ?: 0
            val g5 = gradeDistributionMap[5] ?: 0
            val totalW = summary.totalWorks
            val sou =
                if (totalW > 0) {
                    (g5 * 1.0 + g4 * 0.64 + g3 * 0.36 + g2 * 0.16) / totalW * 100
                } else {
                    0.0
                }

            session.drawStaticLayout("Общая статистика", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            val generalLines =
                listOf(
                    "Общее количество работ: ${summary.totalWorks}",
                    "Средний балл: ${String.format(Locale.US, "%.1f", summary.averageGrade)}",
                    "Процент успешности: ${String.format(Locale.US, "%.0f%%", summary.successRatePercent)}",
                    "Качество знаний (КЗ): ${String.format(Locale.US, "%.0f%%", summary.qualityKnowledgePercent)}",
                    "Степень обученности (СОУ): ${String.format(Locale.US, "%.0f%%", sou)}",
                    "С именем (ФИО): ${state.namedWorksCount}",
                    "Без имени: ${state.unnamedWorksCount}",
                )
            generalLines.forEach { line ->
                session.drawStaticLayout(line, bodyPaint, session.contentWidthPortrait())
                session.advance(LINE_GAP)
            }
            session.advance(SECTION_GAP)

            session.drawStaticLayout("Распределение оценок", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            session.drawStaticLayout("Оценка | Кол-во | %", bodyPaint, session.contentWidthPortrait())
            session.advance(LINE_GAP)
            state.gradeDistribution.sortedBy { it.grade }.forEach { item ->
                session.drawStaticLayout(
                    "${item.grade} | ${item.count} | ${String.format(Locale.US, "%.1f%%", item.percent)}",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP)
            }
            session.advance(SECTION_GAP)

            session.drawStaticLayout("Топ сложных вопросов", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            session.drawStaticLayout("Вопрос | Ошибок | % от проверенных", bodyPaint, session.contentWidthPortrait())
            session.advance(LINE_GAP)
            state.hardestQuestions.forEach { item ->
                session.drawStaticLayout(
                    "${item.questionNumber} | ${item.wrongCount} | ${String.format(Locale.US, "%.1f%%", item.wrongPercent)}",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP)
            }
            session.advance(SECTION_GAP)

            session.drawStaticLayout("Тепловая карта (доля ошибок)", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            val totalWorksForHeat = summary.totalWorks.coerceAtLeast(1)
            state.heatmap.forEach { item ->
                val seen =
                    when {
                        item.wrongPercent <= 0 -> totalWorksForHeat
                        else -> (item.wrongCount * 100.0 / item.wrongPercent).roundToInt().coerceAtLeast(1)
                    }
                val status =
                    when {
                        item.wrongPercent >= 60 -> "Очень сложно"
                        item.wrongPercent >= 40 -> "Сложно"
                        item.wrongPercent >= 25 -> "Средне"
                        item.wrongPercent >= 10 -> "Легко"
                        else -> "Очень легко"
                    }
                session.drawStaticLayout(
                    "В${item.questionNumber}: ошибок ${item.wrongCount}, проверено $seen, " +
                        "${String.format(Locale.US, "%.1f%%", item.wrongPercent)} — $status",
                    smallPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP_SMALL)
            }
            session.advance(SECTION_GAP)

            session.drawStaticLayout("Связанные ошибки (пары вопросов)", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            state.relatedErrors.forEach { item ->
                val pct = if (totalW > 0) item.togetherCount * 100.0 / totalW else 0.0
                session.drawStaticLayout(
                    "В${item.firstQuestion} + В${item.secondQuestion}: вместе ${item.togetherCount} " +
                        "(${String.format(Locale.US, "%.1f%%", pct)} работ)",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP)
            }
            session.advance(SECTION_GAP)

            session.drawStaticLayout("Идентичные ответы", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            state.identicalWorks.forEach { item ->
                session.drawStaticLayout(item.signature, bodyPaint, session.contentWidthPortrait())
                session.advance(LINE_GAP)
                session.drawStaticLayout(item.workTitles.joinToString(", "), smallPaint, session.contentWidthPortrait())
                session.advance(LINE_GAP_SMALL)
            }
            session.advance(SECTION_GAP)

            state.variantComparison?.let { cmp ->
                session.drawStaticLayout("Сравнение вариантов", sectionPaint, session.contentWidthPortrait())
                session.advance(PARA_GAP)
                session.drawStaticLayout(
                    "Лучший: В${cmp.bestVariant} (${String.format(Locale.US, "%.1f%%", cmp.bestPercent)})",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP)
                session.drawStaticLayout(
                    "Худший: В${cmp.worstVariant} (${String.format(Locale.US, "%.1f%%", cmp.worstPercent)})",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP)
                session.drawStaticLayout(
                    "Разница: ${String.format(Locale.US, "%.1f", cmp.gapPercent)} п.п.",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(SECTION_GAP)
            }

            session.drawStaticLayout("По вариантам эталона", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            state.variantStats.forEach { item ->
                session.drawStaticLayout(
                    "В${item.variant}: работ ${item.worksCount}, средний % ${String.format(Locale.US, "%.1f", item.averagePercent)}",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP)
            }
            session.advance(SECTION_GAP)

            session.drawStaticLayout("По классам", sectionPaint, session.contentWidthPortrait())
            session.advance(PARA_GAP)
            state.classStats.forEach { item ->
                session.drawStaticLayout(
                    "${item.className}: работ ${item.worksCount}, средний % ${String.format(Locale.US, "%.1f", item.averagePercent)}, " +
                        "успеваемость ${String.format(Locale.US, "%.1f%%", item.successRatePercent)}",
                    bodyPaint,
                    session.contentWidthPortrait(),
                )
                session.advance(LINE_GAP)
            }
            session.advance(SECTION_GAP)

            session.startLandscapePage()
            session.drawStaticLayout("Детальные результаты", sectionPaint, session.contentWidthLandscape())
            session.advance(PARA_GAP)
            drawDetailedTableHeader(session, smallPaint)
            state.works.forEachIndexed { index, work ->
                session.ensureLandscapeSpace(DETAIL_ROW_HEIGHT)
                drawDetailedRow(session, smallPaint, index + 1, work)
                session.advance(DETAIL_ROW_HEIGHT)
            }

            session.finishPage()

            val fileName =
                "OMR_Отчет_${SimpleDateFormat("dd_MM_yyyy_HH_mm", Locale.getDefault()).format(Date())}.pdf"
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val exportDir = File(dir, "reports-export").apply { mkdirs() }
            val file = File(exportDir, fileName)
            FileOutputStream(file).use { out ->
                pdf.writeTo(out)
                out.flush()
            }
            Log.d(TAG, "PDF создан: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка экспорта PDF", e)
            null
        } finally {
            pdf.close()
        }
    }

    private fun drawDetailedTableHeader(session: PdfSession, paint: TextPaint) {
        session.ensureLandscapeSpace(DETAIL_ROW_HEIGHT)
        val c = session.canvas()
        val y = session.yBaseline(paint)
        val labels =
            arrayOf("№", "Название", "ФИО", "Класс", "В", "Счёт", "%", "Оц", "Статус")
        for (i in labels.indices) {
            c.drawText(labels[i], DETAIL_COL_X[i], y, paint)
        }
        session.advance(DETAIL_ROW_HEIGHT)
    }

    private fun drawDetailedRow(session: PdfSession, paint: TextPaint, num: Int, work: ReportsWorkListItem) {
        val c = session.canvas()
        val y = session.yBaseline(paint)
        val cols =
            arrayOf(
                num.toString(),
                ellipsize(paint, work.title, DETAIL_COL_W[1]),
                ellipsize(paint, work.studentDisplayName.orEmpty(), DETAIL_COL_W[2]),
                ellipsize(paint, work.className.orEmpty(), DETAIL_COL_W[3]),
                work.variant?.toString().orEmpty(),
                work.scoreText,
                work.percent?.let { String.format(Locale.US, "%.1f%%", it.toDouble()) }.orEmpty(),
                work.grade?.toString().orEmpty(),
                ellipsize(paint, work.status, DETAIL_COL_W[8]),
            )
        for (i in cols.indices) {
            c.drawText(cols[i], DETAIL_COL_X[i], y, paint)
        }
    }

    private fun ellipsize(paint: TextPaint, text: String, maxWidth: Float): String {
        if (text.isEmpty()) return ""
        if (paint.measureText(text) <= maxWidth) return text
        return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    private class PdfSession(private val pdf: PdfDocument) {
        private var pageIndex = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private var landscape = false

        fun canvas(): Canvas = canvas!!

        fun contentWidthPortrait(): Int = (PAGE_W_PORTRAIT - 2 * MARGIN).toInt()

        fun contentWidthLandscape(): Int = (PAGE_W_LANDSCAPE - 2 * MARGIN).toInt()

        fun yBaseline(paint: TextPaint): Float {
            val fm = paint.fontMetrics
            return y - fm.ascent
        }

        fun startPortraitPage() {
            finishPage()
            landscape = false
            newPage(PAGE_W_PORTRAIT, PAGE_H_PORTRAIT)
        }

        fun startLandscapePage() {
            finishPage()
            landscape = true
            newPage(PAGE_W_LANDSCAPE, PAGE_H_LANDSCAPE)
        }

        private fun newPage(w: Int, h: Int) {
            val info = PdfDocument.PageInfo.Builder(w, h, ++pageIndex).create()
            page = pdf.startPage(info)
            canvas = page!!.canvas
            canvas!!.drawColor(Color.WHITE)
            y = MARGIN
        }

        fun finishPage() {
            page?.let { pdf.finishPage(it) }
            page = null
            canvas = null
        }

        fun pageBottom(): Float =
            if (landscape) {
                PAGE_H_LANDSCAPE - MARGIN
            } else {
                PAGE_H_PORTRAIT - MARGIN
            }

        fun ensurePortraitSpace(needed: Float) {
            if (canvas == null || !landscape) {
                if (canvas == null) startPortraitPage()
            } else {
                startPortraitPage()
            }
            if (y + needed > pageBottom()) {
                startPortraitPage()
            }
        }

        fun ensureLandscapeSpace(needed: Float) {
            if (canvas == null || !landscape) {
                startLandscapePage()
            }
            if (y + needed > pageBottom()) {
                startLandscapePage()
            }
        }

        fun drawStaticLayout(text: String, paint: TextPaint, widthPx: Int) {
            val need =
                StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
                    .height
                    .toFloat()
            if (!landscape) {
                ensurePortraitSpace(need + PARA_GAP)
            } else {
                ensureLandscapeSpace(need + PARA_GAP)
            }
            val layout =
                StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
            canvas!!.save()
            canvas!!.translate(MARGIN, y)
            layout.draw(canvas!!)
            canvas!!.restore()
            y += need
        }

        fun advance(delta: Float) {
            y += delta
        }
    }

    companion object {
        private const val TAG = "ReportsPdfExporter"

        private const val PAGE_W_PORTRAIT = 595
        private const val PAGE_H_PORTRAIT = 842
        private const val PAGE_W_LANDSCAPE = 842
        private const val PAGE_H_LANDSCAPE = 595

        private const val MARGIN = 40f
        private const val SECTION_TEXT_SIZE = 12f
        private const val BODY_TEXT_SIZE = 10f
        private const val SMALL_TEXT_SIZE = 9f

        private const val LINE_GAP = 4f
        private const val LINE_GAP_SMALL = 3f
        private const val PARA_GAP = 6f
        private const val SECTION_GAP = 12f

        private const val DETAIL_ROW_HEIGHT = 11f

        private val DETAIL_COL_X =
            floatArrayOf(36f, 52f, 210f, 330f, 376f, 398f, 448f, 488f, 512f)

        private val DETAIL_COL_W =
            floatArrayOf(
                14f,
                155f,
                115f,
                42f,
                20f,
                48f,
                38f,
                22f,
                320f,
            )
    }
}
