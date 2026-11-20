package com.tscan.scanertestov.feature.journals

/**
 * Описание: загрузка учеников из Excel (.xlsx, .xls) и CSV и шаблон Excel с листом «Как заполнять».
 *
 * Чтение — через Apache POI (то же, что выгрузка отчётов): понимает файлы из Excel, LibreOffice и Google Таблиц
 * (строки внутри ячеек, оформленный текст, числа-классы «7»). Лист с учениками ищется по названию «Ученики»
 * или «Журнал», иначе — первый лист, кроме «Как заполнять».
 *
 * Строки разбираются терпимо:
 *  - строка заголовков («Класс», «Фамилия»…) и пустые строки пропускаются;
 *  - пустой класс = класс из строки выше (учителя часто пишут класс один раз);
 *  - всё ФИО в одном столбце «Фамилия» («Иванов Иван Иванович») делится само;
 *  - строки без класса или без имени не теряются молча — их номера показываются учителю.
 */
import android.content.Context
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

/** Итог разбора файла: ученики и номера строк, которые не удалось прочитать (с причиной). */
data class StudentsImport(
    val students: List<JournalStudent>,
    val skipped: List<Pair<Int, String>> = emptyList(),
)

internal const val JOURNAL_SHEET_NAME = "Ученики"
private const val HELP_SHEET_NAME = "Как заполнять"
private val ru = Locale("ru")

internal fun parseStudentsImportInternal(bytes: ByteArray): StudentsImport {
    val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    val isOldXls = bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
        bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
    return if (isZip || isOldXls) parseStudentsWorkbook(bytes) else parseStudentsCsvRows(decodeCsvText(bytes))
}

internal fun parseStudentsFromBytesInternal(bytes: ByteArray): List<JournalStudent> =
    parseStudentsImportInternal(bytes).students

private fun parseStudentsWorkbook(bytes: ByteArray): StudentsImport {
    WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
        val sheets = (0 until wb.numberOfSheets).map { wb.getSheetAt(it) }
        val sheet = sheets.firstOrNull { it.sheetName.trim().equals(JOURNAL_SHEET_NAME, true) }
            ?: sheets.firstOrNull { it.sheetName.trim().equals("Журнал", true) }
            ?: sheets.firstOrNull { !it.sheetName.trim().equals(HELP_SHEET_NAME, true) }
            ?: return StudentsImport(emptyList())
        val fmt = DataFormatter(ru)
        val rows = ArrayList<Pair<Int, List<String>>>()
        for (r in sheet) {
            val cells = (0..3).map { c -> r.getCell(c)?.let { fmt.formatCellValue(it) }?.trim().orEmpty() }
            rows += (r.rowNum + 1) to cells
        }
        return rowsToStudents(rows)
    }
}

private fun parseStudentsCsvRows(csvRaw: String): StudentsImport {
    val rows = csvRaw.replace("\r\n", "\n").split('\n').mapIndexed { i, line ->
        val delimiter = when {
            line.contains(';') -> ';'
            line.contains('\t') -> '\t'
            else -> ','
        }
        (i + 1) to line.split(delimiter).map { it.trim().trim('"').trim() }
    }
    return rowsToStudents(rows)
}

/** Общие правила для Excel и CSV: [rows] — (номер строки в файле, ячейки A–D). */
private fun rowsToStudents(rows: List<Pair<Int, List<String>>>): StudentsImport {
    val out = ArrayList<JournalStudent>()
    val skipped = ArrayList<Pair<Int, String>>()
    var lastClass = ""
    for ((n, cells) in rows) {
        val a = cells.getOrNull(0).orEmpty()
        var b = cells.getOrNull(1).orEmpty()
        var c = cells.getOrNull(2).orEmpty()
        var d = cells.getOrNull(3).orEmpty()
        if (listOf(a, b, c, d).all { it.isBlank() }) continue
        val lower = listOf(a, b, c).map { it.lowercase(ru) }
        if (lower.any { it == "фамилия" || it == "класс" || it == "имя" || it == "фио" }) continue
        if (a.isNotBlank()) lastClass = a
        val cls = a.ifBlank { lastClass }
        // ФИО одной строкой в столбце «Фамилия».
        if (c.isBlank() && b.contains(' ')) {
            val parts = b.split(Regex("\\s+")).filter { it.isNotBlank() }
            b = parts[0]; c = parts.getOrElse(1) { "" }
            if (d.isBlank()) d = parts.drop(2).joinToString(" ")
        }
        when {
            cls.isBlank() -> skipped += n to "не указан класс"
            b.isBlank() -> skipped += n to "нет фамилии"
            c.isBlank() -> skipped += n to "нет имени"
            !looksLikeClass(cls) -> skipped += n to "«${cls.take(12)}» не похоже на класс"
            !looksLikeName(b) || !looksLikeName(c) || (d.isNotBlank() && !looksLikeName(d)) ->
                skipped += n to "не похоже на фамилию и имя"
            else -> out += JournalStudent(cls, b, c, d.ifBlank { null }).normalized()
        }
    }
    return StudentsImport(out, skipped)
}

/** Класс: номер 1–11 и буква/номер группы — «7А», «7 «Б»», «10-1», «5». Защита от чужих файлов. */
private val ClassLike = Regex("""^\d{1,2}\s*[-.]?\s*[«"']?\s*[\p{L}\d]{0,3}\s*[»"']?$""")
private fun looksLikeClass(s: String): Boolean = s.trim().length <= 12 && ClassLike.matches(s.trim())

/** Фамилия, имя, отчество: буквы, дефис, апостроф, пробел; есть хотя бы одна буква. */
private val NameLike = Regex("""^[\p{L}][\p{L}\s'’`-]{0,39}$""")
private fun looksLikeName(s: String): Boolean = NameLike.matches(s.trim())

/**
 * Excel на русской Windows сохраняет CSV в Windows-1251, а не в UTF-8 — без этой проверки
 * фамилии превращались в «кракозябры». Порядок: BOM UTF-8 → строгий UTF-8 → Windows-1251.
 */
internal fun decodeCsvText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    val strictUtf8 = runCatching {
        java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
    return strictUtf8 ?: String(bytes, charset("windows-1251"))
}

/** Шаблон: лист «Ученики» (только заголовки — сюда список) и лист «Как заполнять» (правила и пример). */
internal fun buildJournalTemplateXlsxInternal(context: Context): File {
    val file = File(context.cacheDir, "journal-template.xlsx")
    XSSFWorkbook().use { wb ->
        val header = wb.createCellStyle().apply {
            setFont(wb.createFont().apply { bold = true; fontHeightInPoints = 12 })
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.MEDIUM
        }
        val note = wb.createCellStyle().apply {
            setFont(wb.createFont().apply { italic = true; color = IndexedColors.GREY_50_PERCENT.index })
        }

        val students = wb.createSheet(JOURNAL_SHEET_NAME)
        val h = students.createRow(0)
        listOf("Класс", "Фамилия", "Имя", "Отчество").forEachIndexed { i, t ->
            h.createCell(i).apply { setCellValue(t); cellStyle = header }
        }
        h.createCell(5).apply {
            setCellValue("← со 2-й строки: один ученик — одна строка. Правила — на листе «$HELP_SHEET_NAME».")
            cellStyle = note
        }
        students.setColumnWidth(0, 10 * 256)
        students.setColumnWidth(1, 22 * 256)
        students.setColumnWidth(2, 18 * 256)
        students.setColumnWidth(3, 22 * 256)
        students.createFreezePane(0, 1)

        val help = wb.createSheet(HELP_SHEET_NAME)
        val title = wb.createCellStyle().apply { setFont(wb.createFont().apply { bold = true; fontHeightInPoints = 14 }) }
        val wrap = wb.createCellStyle().apply { wrapText = true }
        val bold = wb.createCellStyle().apply { setFont(wb.createFont().apply { bold = true }) }
        help.setColumnWidth(0, 100 * 256)
        var r = 0
        fun line(text: String, style: org.apache.poi.xssf.usermodel.XSSFCellStyle? = wrap) {
            help.createRow(r++).createCell(0).apply { setCellValue(text); style?.let { cellStyle = it } }
        }
        line("Как заполнить список учеников для «Сканера тестов»", title)
        line("")
        line("1. Ученики пишутся на листе «$JOURNAL_SHEET_NAME»: со 2-й строки, один ученик — одна строка. Строку заголовков не удаляйте.")
        line("2. Столбцы: A — Класс, B — Фамилия, C — Имя, D — Отчество (можно не заполнять).")
        line("3. Класс пишите как в школе: 7А, 7 «Б», 10-1. Если класс у нескольких учеников подряд один — можно написать его только у первого, у остальных оставить пусто.")
        line("4. Можно вписать всё ФИО в столбец B («Иванов Иван Иванович») — приложение само разделит фамилию, имя и отчество.")
        line("5. Несколько классов — в одном листе подряд. Ученик, который уже есть в журнале, второй раз не добавится.")
        line("6. Не объединяйте ячейки, не вставляйте пустые столбцы перед «Класс». Всё правее столбца D не читается — там можно писать пометки.")
        line("7. Сохраните файл как Excel (.xlsx) — или CSV, разделитель «;». На телефоне: «Журналы» → «Импорт из Excel/CSV» → выберите файл.")
        line("8. После загрузки приложение покажет, сколько учеников добавлено, и номера строк, которые не удалось прочитать (нет класса или имени).")
        line("")
        line("Пример (этот лист не загружается — пишите на листе «$JOURNAL_SHEET_NAME»):", bold)
        val example = listOf(
            listOf("Класс", "Фамилия", "Имя", "Отчество"),
            listOf("7А", "Иванов", "Иван", "Иванович"),
            listOf("", "Петрова", "Варвара", ""),
            listOf("7Б", "Сидоров Пётр Ильич", "", ""),
        )
        help.setColumnWidth(1, 22 * 256)
        help.setColumnWidth(2, 18 * 256)
        help.setColumnWidth(3, 22 * 256)
        // Пример — со 2-го столбца, чтобы не мешать тексту в широком A.
        example.forEachIndexed { i, cells ->
            val row = help.createRow(r++)
            cells.forEachIndexed { j, t -> row.createCell(j + 1).apply { setCellValue(t); if (i == 0) cellStyle = header } }
        }
        line("Петрова — тоже 7А (класс взят из строки выше); у Сидорова ФИО в одном столбце.", note)

        // Учитель открывает файл — сначала видит правила.
        wb.setActiveSheet(1)
        wb.setSelectedTab(1)
        file.outputStream().use { wb.write(it) }
    }
    return file
}
