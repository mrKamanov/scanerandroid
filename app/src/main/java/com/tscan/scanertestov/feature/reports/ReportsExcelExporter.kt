package com.tscan.scanertestov.feature.reports

/**
 * Описание: экспорт аналитических отчётов в .xlsx (структура близка к external/TscanAndroid ExcelExporter).
 */
import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ReportsExcelExporter(private val context: Context) {

    fun exportToExcel(state: ReportsState): File? {
        val summary = state.summary ?: return null
        return try {
            val workbook = XSSFWorkbook()
            val headerStyle = createHeaderStyle(workbook)
            val dataStyle = createDataStyle(workbook)
            val titleStyle = createTitleStyle(workbook)

            createGeneralStatisticsSheet(workbook, state, summary, headerStyle, dataStyle, titleStyle)
            createDetailedResultsSheet(workbook, state, headerStyle, dataStyle, titleStyle)
            createQuestionAnalysisSheet(workbook, state, summary, headerStyle, dataStyle, titleStyle)
            createVariantsClassesSheet(workbook, state, headerStyle, dataStyle, titleStyle)
            createChartsSheet(workbook, state, summary, headerStyle, dataStyle, titleStyle)

            val fileName =
                "OMR_Отчет_${SimpleDateFormat("dd_MM_yyyy_HH_mm", Locale.getDefault()).format(Date())}.xlsx"
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val exportDir = File(dir, "reports-export").apply { mkdirs() }
            val file = File(exportDir, fileName)
            FileOutputStream(file).use { fos -> workbook.write(fos) }
            workbook.close()
            Log.d(TAG, "Excel создан: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка экспорта Excel", e)
            null
        }
    }

    private fun createGeneralStatisticsSheet(
        workbook: Workbook,
        state: ReportsState,
        summary: ReportsSummaryStats,
        headerStyle: CellStyle,
        dataStyle: CellStyle,
        titleStyle: CellStyle,
    ) {
        val sheet = workbook.createSheet("Общая статистика")
        var rowNum = 0

        val titleRow = sheet.createRow(rowNum++)
        titleRow.createCell(0).apply {
            setCellValue("ОБЩАЯ СТАТИСТИКА РАБОТ")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 3))
        rowNum++

        val gradeDistributionMap = state.gradeDistribution.associate { it.grade to it.count }
        val grade2 = gradeDistributionMap[2] ?: 0
        val grade3 = gradeDistributionMap[3] ?: 0
        val grade4 = gradeDistributionMap[4] ?: 0
        val grade5 = gradeDistributionMap[5] ?: 0
        val totalWorks = summary.totalWorks

        val sou =
            if (totalWorks > 0) {
                (grade5 * 1.0 + grade4 * 0.64 + grade3 * 0.36 + grade2 * 0.16) / totalWorks * 100
            } else {
                0.0
            }

        val metricsData =
            listOf(
                listOf("Показатель", "Значение"),
                listOf("Общее количество работ", totalWorks.toString()),
                listOf("Средний балл", String.format(Locale.US, "%.1f", summary.averageGrade)),
                listOf("Процент успешности", String.format(Locale.US, "%.0f%%", summary.successRatePercent)),
                listOf("Качество знаний (КЗ)", String.format(Locale.US, "%.0f%%", summary.qualityKnowledgePercent)),
                listOf("Степень обученности (СОУ)", String.format(Locale.US, "%.0f%%", sou)),
                listOf("С именем (ФИО)", state.namedWorksCount.toString()),
                listOf("Без имени", state.unnamedWorksCount.toString()),
            )

        metricsData.forEachIndexed { index, rowData ->
            val row = sheet.createRow(rowNum++)
            rowData.forEachIndexed { colIndex, value ->
                row.createCell(colIndex).apply {
                    setCellValue(value)
                    cellStyle = if (index == 0) headerStyle else dataStyle
                }
            }
        }

        rowNum++

        val gradeTitleRow = sheet.createRow(rowNum++)
        gradeTitleRow.createCell(0).apply {
            setCellValue("РАСПРЕДЕЛЕНИЕ ОЦЕНОК")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3))
        rowNum++

        val gradeHeaderRow = sheet.createRow(rowNum++)
        listOf("Оценка", "Количество", "Процент").forEachIndexed { index, header ->
            gradeHeaderRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        state.gradeDistribution.sortedBy { it.grade }.forEach { item ->
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue(item.grade.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.count.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(String.format(Locale.US, "%.1f%%", item.percent))
                cellStyle = dataStyle
            }
        }

        sheet.setColumnWidth(0, 6000)
        sheet.setColumnWidth(1, 3500)
        sheet.setColumnWidth(2, 2500)
        sheet.setColumnWidth(3, 2500)
    }

    private fun createDetailedResultsSheet(
        workbook: Workbook,
        state: ReportsState,
        headerStyle: CellStyle,
        dataStyle: CellStyle,
        titleStyle: CellStyle,
    ) {
        val sheet = workbook.createSheet("Детальные результаты")
        var rowNum = 0

        val titleRow = sheet.createRow(rowNum++)
        titleRow.createCell(0).apply {
            setCellValue("ДЕТАЛЬНЫЕ РЕЗУЛЬТАТЫ")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 11))
        rowNum++

        val headers =
            listOf(
                "№",
                "Название",
                "ФИО",
                "Класс",
                "Вариант",
                "Правильно",
                "Всего",
                "Процент",
                "Оценка",
                "Статус",
                "Частично выполнено",
                "Множественный выбор",
            )
        val headerRow = sheet.createRow(rowNum++)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        state.works.forEachIndexed { index, work ->
            val row = sheet.createRow(rowNum++)
            var col = 0
            row.createCell(col++).apply {
                setCellValue((index + 1).toDouble())
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                setCellValue(work.title)
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                setCellValue(work.studentDisplayName.orEmpty())
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                setCellValue(work.className.orEmpty())
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                if (work.variant != null) setCellValue(work.variant.toDouble()) else setCellValue("")
                cellStyle = dataStyle
            }
            val parts = work.scoreText.split("/")
            val correct = parts.getOrNull(0)?.toIntOrNull()
            val total = parts.getOrNull(1)?.toIntOrNull()
            row.createCell(col++).apply {
                if (correct != null) setCellValue(correct.toDouble()) else setCellValue("")
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                if (total != null) setCellValue(total.toDouble()) else setCellValue("")
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                val p = work.percent
                setCellValue(if (p != null) String.format(Locale.US, "%.1f%%", p.toDouble()) else "")
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                val g = work.grade
                if (g != null) setCellValue(g.toDouble()) else setCellValue("")
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                setCellValue(work.status)
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                if (!work.strictScoring && work.hasMultiChoiceQuestions) {
                    setCellValue(work.partialCompletedCount.toDouble())
                } else {
                    setCellValue("")
                }
                cellStyle = dataStyle
            }
            row.createCell(col++).apply {
                setCellValue(if (work.hasMultiChoiceQuestions) "да" else "нет")
                cellStyle = dataStyle
            }
        }

        sheet.setColumnWidth(0, 1200)
        sheet.setColumnWidth(1, 4500)
        sheet.setColumnWidth(2, 3500)
        sheet.setColumnWidth(3, 2500)
        sheet.setColumnWidth(4, 2000)
        sheet.setColumnWidth(5, 2200)
        sheet.setColumnWidth(6, 1800)
        sheet.setColumnWidth(7, 2200)
        sheet.setColumnWidth(8, 1800)
        sheet.setColumnWidth(9, 2200)
        sheet.setColumnWidth(10, 3500)
        sheet.setColumnWidth(11, 3500)
    }

    private fun createQuestionAnalysisSheet(
        workbook: Workbook,
        state: ReportsState,
        summary: ReportsSummaryStats,
        headerStyle: CellStyle,
        dataStyle: CellStyle,
        titleStyle: CellStyle,
    ) {
        val sheet = workbook.createSheet("Анализ вопросов")
        var rowNum = 0

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("АНАЛИЗ ВОПРОСОВ")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 5))

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("Топ сложных вопросов")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4))

        val hardestHeader = sheet.createRow(rowNum++)
        listOf("Вопрос", "Ошибок", "% от проверенных").forEachIndexed { i, h ->
            hardestHeader.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        state.hardestQuestions.forEach { item ->
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue(item.questionNumber.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.wrongCount.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(String.format(Locale.US, "%.1f%%", item.wrongPercent))
                cellStyle = dataStyle
            }
        }
        rowNum += 2

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("Тепловая карта (доля ошибок по вопросам)")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 5))

        val heatHeader = sheet.createRow(rowNum++)
        listOf("Вопрос", "Ошибок", "Проверено работ", "% ошибок", "Оценка сложности").forEachIndexed { i, h ->
            heatHeader.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        val totalW = summary.totalWorks.coerceAtLeast(1)
        state.heatmap.forEach { item ->
            val seen =
                when {
                    item.wrongPercent <= 0 -> totalW
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
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue(item.questionNumber.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.wrongCount.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(seen.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(3).apply {
                setCellValue(String.format(Locale.US, "%.1f%%", item.wrongPercent))
                cellStyle = dataStyle
            }
            row.createCell(4).apply {
                setCellValue(status)
                cellStyle = dataStyle
            }
        }

        rowNum += 2

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("Связанные ошибки")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4))

        val relatedHeader = sheet.createRow(rowNum++)
        listOf("Вопрос 1", "Вопрос 2", "Вместе ошиблись", "% работ").forEachIndexed { i, h ->
            relatedHeader.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        state.relatedErrors.forEach { item ->
            val row = sheet.createRow(rowNum++)
            val pct = if (totalW > 0) item.togetherCount * 100.0 / totalW else 0.0
            row.createCell(0).apply {
                setCellValue(item.firstQuestion.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.secondQuestion.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(item.togetherCount.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(3).apply {
                setCellValue(String.format(Locale.US, "%.1f%%", pct))
                cellStyle = dataStyle
            }
        }

        rowNum += 2

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("Идентичные ответы")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4))

        val identicalHeader = sheet.createRow(rowNum++)
        listOf("Описание", "Работ", "Названия работ").forEachIndexed { i, h ->
            identicalHeader.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        state.identicalWorks.forEach { item ->
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue(item.signature)
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.workTitles.size.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(item.workTitles.joinToString(", "))
                cellStyle = dataStyle
            }
        }

        sheet.setColumnWidth(0, 4500)
        sheet.setColumnWidth(1, 2800)
        sheet.setColumnWidth(2, 3500)
        sheet.setColumnWidth(3, 3500)
        sheet.setColumnWidth(4, 4000)
        sheet.setColumnWidth(5, 3000)
    }

    private fun createVariantsClassesSheet(
        workbook: Workbook,
        state: ReportsState,
        headerStyle: CellStyle,
        dataStyle: CellStyle,
        titleStyle: CellStyle,
    ) {
        val sheet = workbook.createSheet("Варианты и классы")
        var rowNum = 0

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("ВАРИАНТЫ И КЛАССЫ")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 4))
        rowNum++

        state.variantComparison?.let { cmp ->
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue("Сравнение вариантов")
                cellStyle = headerStyle
            }
            sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 4))
            rowNum++

            listOf(
                listOf("Лучший вариант", "В${cmp.bestVariant}", "${String.format(Locale.US, "%.1f", cmp.bestPercent)}%"),
                listOf("Худший вариант", "В${cmp.worstVariant}", "${String.format(Locale.US, "%.1f", cmp.worstPercent)}%"),
                listOf("Разница, п.п.", "", "${String.format(Locale.US, "%.1f", cmp.gapPercent)}"),
            ).forEach { cells ->
                val r = sheet.createRow(rowNum++)
                cells.forEachIndexed { i, v ->
                    r.createCell(i).apply {
                        setCellValue(v)
                        cellStyle = dataStyle
                    }
                }
            }
            rowNum += 2
        }

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("По вариантам эталона")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3))

        val variantHeader = sheet.createRow(rowNum++)
        listOf("Вариант", "Работ", "Средний %").forEachIndexed { i, h ->
            variantHeader.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        state.variantStats.forEach { item ->
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue(item.variant.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.worksCount.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(String.format(Locale.US, "%.1f%%", item.averagePercent))
                cellStyle = dataStyle
            }
        }
        rowNum += 2

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("По классам (журнал)")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3))

        val classHeader = sheet.createRow(rowNum++)
        listOf("Класс", "Работ", "Средний %", "Успеваемость %").forEachIndexed { i, h ->
            classHeader.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        state.classStats.forEach { item ->
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue(item.className)
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.worksCount.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(String.format(Locale.US, "%.1f%%", item.averagePercent))
                cellStyle = dataStyle
            }
            row.createCell(3).apply {
                setCellValue(String.format(Locale.US, "%.1f%%", item.successRatePercent))
                cellStyle = dataStyle
            }
        }

        sheet.setColumnWidth(0, 4500)
        sheet.setColumnWidth(1, 2800)
        sheet.setColumnWidth(2, 3500)
        sheet.setColumnWidth(3, 3500)
        sheet.setColumnWidth(4, 3500)
    }

    private fun createChartsSheet(
        workbook: Workbook,
        state: ReportsState,
        summary: ReportsSummaryStats,
        headerStyle: CellStyle,
        dataStyle: CellStyle,
        titleStyle: CellStyle,
    ) {
        val sheet = workbook.createSheet("Данные для графиков")
        var rowNum = 0

        sheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("ДАННЫЕ ДЛЯ ГРАФИКОВ")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 3))
        rowNum++

        val headerRow = sheet.createRow(rowNum++)
        listOf("Оценка", "Количество", "Доля, %").forEachIndexed { i, h ->
            headerRow.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        val total = summary.totalWorks.coerceAtLeast(1)
        state.gradeDistribution.sortedBy { it.grade }.forEach { item ->
            val row = sheet.createRow(rowNum++)
            row.createCell(0).apply {
                setCellValue("Оценка ${item.grade}")
                cellStyle = dataStyle
            }
            row.createCell(1).apply {
                setCellValue(item.count.toDouble())
                cellStyle = dataStyle
            }
            row.createCell(2).apply {
                setCellValue(item.count * 100.0 / total)
                cellStyle = dataStyle
            }
        }

        sheet.setColumnWidth(0, 3500)
        sheet.setColumnWidth(1, 3000)
        sheet.setColumnWidth(2, 3000)
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.color = IndexedColors.WHITE.index
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.DARK_BLUE.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        return style
    }

    private fun createDataStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        return style
    }

    private fun createTitleStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 14
        style.setFont(font)
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        return style
    }

    companion object {
        private const val TAG = "ReportsExcelExporter"
    }
}
