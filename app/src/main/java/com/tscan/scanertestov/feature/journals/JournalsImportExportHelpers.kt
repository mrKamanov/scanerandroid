package com.tscan.scanertestov.feature.journals

/**
 * Описание: вспомогательные функции импорта/экспорта журнала (CSV/XLSX парсинг и генерация шаблона XLSX).
 */
import android.content.Context
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringReader
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

internal fun parseStudentsCsvInternal(csvRaw: String): List<JournalStudent> {
    val rows = csvRaw
        .replace("\r\n", "\n")
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (rows.isEmpty()) return emptyList()

    val dataRows = if (rows.first().lowercase(Locale("ru")).contains("фамилия")) rows.drop(1) else rows
    return dataRows.mapNotNull { line ->
        val delimiter = if (line.contains(';')) ';' else ','
        val parts = line.split(delimiter).map { it.trim().trim('"') }
        val cls = parts.getOrNull(0).orEmpty()
        val sn = parts.getOrNull(1).orEmpty()
        val nm = parts.getOrNull(2).orEmpty()
        val mn = parts.getOrNull(3).orEmpty()
        if (cls.isBlank() || sn.isBlank() || nm.isBlank()) {
            null
        } else {
            JournalStudent(
                className = cls,
                surname = sn,
                name = nm,
                middleName = mn.ifBlank { null },
            ).normalized()
        }
    }
}

internal fun parseStudentsFromBytesInternal(bytes: ByteArray): List<JournalStudent> {
    return if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
        parseStudentsXlsxInternal(bytes)
    } else {
        parseStudentsCsvInternal(bytes.toString(Charsets.UTF_8))
    }
}

internal fun parseStudentsXlsxInternal(bytes: ByteArray): List<JournalStudent> {
    val entries = mutableMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    val sheetXml = entries["xl/worksheets/sheet1.xml"].orEmpty()
    if (sheetXml.isBlank()) return emptyList()
    val sharedStrings = parseSharedStringsInternal(entries["xl/sharedStrings.xml"].orEmpty())
    return parseStudentsFromSheetXmlInternal(sheetXml, sharedStrings)
}

internal fun parseSharedStringsInternal(xml: String): List<String> {
    if (xml.isBlank()) return emptyList()
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))
    val nodes = doc.getElementsByTagName("si")
    return buildList {
        for (i in 0 until nodes.length) {
            val item = nodes.item(i)
            val texts = (item as? org.w3c.dom.Element)?.getElementsByTagName("t")
            if (texts != null && texts.length > 0) {
                add(texts.item(0).textContent.orEmpty())
            } else {
                add("")
            }
        }
    }
}

internal fun parseStudentsFromSheetXmlInternal(
    sheetXml: String,
    sharedStrings: List<String>,
): List<JournalStudent> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(InputSource(StringReader(sheetXml)))
    val rows = doc.getElementsByTagName("row")
    val result = mutableListOf<JournalStudent>()
    for (i in 0 until rows.length) {
        val row = rows.item(i) as? org.w3c.dom.Element ?: continue
        val cells = row.getElementsByTagName("c")
        val valuesByCol = mutableMapOf<String, String>()
        for (j in 0 until cells.length) {
            val cell = cells.item(j) as? org.w3c.dom.Element ?: continue
            val ref = cell.getAttribute("r")
            val col = ref.takeWhile { it.isLetter() }
            if (col.isBlank()) continue
            val type = cell.getAttribute("t")
            val valueNode = cell.getElementsByTagName("v")
            val rawValue = if (valueNode.length > 0) valueNode.item(0).textContent.orEmpty() else ""
            val value = if (type == "s") {
                rawValue.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx).orEmpty() }.orEmpty()
            } else {
                rawValue
            }
            valuesByCol[col] = value.trim()
        }
        val cls = valuesByCol["A"].orEmpty()
        val sn = valuesByCol["B"].orEmpty()
        val nm = valuesByCol["C"].orEmpty()
        val mn = valuesByCol["D"].orEmpty()
        if (sn.equals("Фамилия", ignoreCase = true)) continue
        if (cls.isBlank() || sn.isBlank() || nm.isBlank()) continue
        result += JournalStudent(
            className = cls,
            surname = sn,
            name = nm,
            middleName = mn.ifBlank { null },
        ).normalized()
    }
    return result
}

internal fun buildJournalTemplateXlsxInternal(context: Context): File {
    val file = File(context.cacheDir, "journal-template.xlsx")
    val sharedStrings = listOf(
        "Класс", "Фамилия", "Имя", "Отчество",
        "7А", "Иванов", "Иван", "Иванович",
        "7А", "Петрова", "Варвара", ""
    )
    ZipOutputStream(file.outputStream()).use { zip ->
        putZipTextEntryInternal(zip, "[Content_Types].xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
            </Types>
        """.trimIndent())
        putZipTextEntryInternal(zip, "_rels/.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent())
        putZipTextEntryInternal(zip, "xl/workbook.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Журнал" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent())
        putZipTextEntryInternal(zip, "xl/_rels/workbook.xml.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
            </Relationships>
        """.trimIndent())
        putZipTextEntryInternal(zip, "xl/sharedStrings.xml", buildSharedStringsXmlInternal(sharedStrings))
        putZipTextEntryInternal(zip, "xl/worksheets/sheet1.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>
                <row r="2"><c r="A2" t="s"><v>4</v></c><c r="B2" t="s"><v>5</v></c><c r="C2" t="s"><v>6</v></c><c r="D2" t="s"><v>7</v></c></row>
                <row r="3"><c r="A3" t="s"><v>8</v></c><c r="B3" t="s"><v>9</v></c><c r="C3" t="s"><v>10</v></c><c r="D3" t="s"><v>11</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent())
    }
    return file
}

internal fun buildSharedStringsXmlInternal(values: List<String>): String {
    val count = values.size
    val items = values.joinToString("") { v ->
        "<si><t>${xmlEscapeInternal(v)}</t></si>"
    }
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="$count" uniqueCount="$count">
          $items
        </sst>
    """.trimIndent()
}

internal fun xmlEscapeInternal(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

internal fun putZipTextEntryInternal(zip: ZipOutputStream, path: String, content: String) {
    zip.putNextEntry(ZipEntry(path))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
}
