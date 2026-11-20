package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: рендер бланка и экспорт в PNG, PDF, DOCX и системный «Поделиться».
 */
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.FileProvider
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val SHEET_WIDTH_PX = 595
private const val SHEET_HEIGHT_PX = 842
private const val EMU_PER_PX = 9525L

/**
 * Во сколько раз картинка (PNG/DOCX) детальнее логического листа 595×842.
 * Было 1 (≈72 dpi: на печати кольца и текст «мыльные»), стало 4 (≈290 dpi).
 * Геометрия бланка не меняется — только чёткость. PDF рисуется векторно и от этого не зависит.
 */
internal const val EXPORT_RASTER_SCALE = 4f

/** Запас вокруг напечатанного при обрезке PNG «в документ», в логических единицах листа. */
private const val CROP_MARGIN_LOGICAL = 12f

data class BlankSheetExportSnapshot(
    val layoutConfig: BlankEditorLayoutConfig,
    val renderParams: BlankBubbleRenderParams,
    val hiddenBubbleIds: Set<String>,
    val blankPanLogical: Offset,
    val blankScale: Float,
    val textBlocks: List<BlankTextBlock>,
    val showVariantMarker: Boolean,
    val showStudentNameMarker: Boolean,
    val studentNamePrintedMode: Boolean,
    val studentSurnameText: String,
    val studentNameText: String,
    val variantPrintedMode: Boolean,
    val variantPrintedNumber: String,
    /** Бланк v2 (угловые метки, кружки варианта, код, поле имени) или null — старый формат (совместимость). */
    val v2: BlankV2Options? = null,
)

/**
 * Картинка листа. [scale] — детальность относительно логического листа 595×842.
 * Для пакета по классу картинки делаются и сохраняются по одной (см. save*Streaming), чтобы не держать
 * в памяти десятки больших листов.
 */
fun renderBlankSheetBitmap(
    snapshot: BlankSheetExportSnapshot,
    withBackground: Boolean,
    scale: Float = EXPORT_RASTER_SCALE,
): Bitmap {
    val w = (SHEET_WIDTH_PX * scale).toInt()
    val h = (SHEET_HEIGHT_PX * scale).toInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(if (withBackground) Color.WHITE else Color.TRANSPARENT)
    canvas.scale(scale, scale)
    drawBlankSheet(canvas, snapshot, withBackground)
    return bitmap
}

/** Рисует весь лист в логических координатах 595×842 на любой холст (картинка или страница PDF). */
internal fun drawBlankSheet(
    canvas: Canvas,
    snapshot: BlankSheetExportSnapshot,
    withBackground: Boolean,
) {
    val layout = buildBlankBubbleLayout(snapshot.layoutConfig, snapshot.renderParams)
    val frameFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (withBackground) Color.WHITE else Color.TRANSPARENT
    }
    val frameStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
        strokeWidth = layout.renderParams.borderWidthPx
        color = Color.parseColor("#2E3440")
    }
    val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (withBackground) Color.WHITE else Color.TRANSPARENT
    }
    val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BlankBubbleDefaults.BUBBLE_STROKE_PX
        color = Color.parseColor("#2E3440")
    }
    val bubbleEmptyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (withBackground) Color.parseColor("#F0F0F0") else Color.TRANSPARENT
    }
    val bubbleEmptyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BlankBubbleDefaults.BUBBLE_STROKE_PX
        color = Color.parseColor("#CCCCCC")
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#2E3440")
        textSize = layout.renderParams.fontSizePx * BlankBubbleDefaults.BUBBLE_FONT_SCALE
        isFakeBoldText = true
    }
    val blockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        color = Color.parseColor("#2E3440")
    }

    canvas.save()
    canvas.translate(snapshot.blankPanLogical.x, snapshot.blankPanLogical.y)
    val center = blankLayoutCenter(layout)
    canvas.translate(center.x, center.y)
    canvas.scale(clampBlankScale(snapshot.blankScale), clampBlankScale(snapshot.blankScale))
    canvas.translate(-center.x, -center.y)

    for (frame in layout.frames) {
        val rect = RectF(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height)
        canvas.drawRect(rect, frameFill)
        canvas.drawRect(rect, frameStroke)
    }
    val cornerMarkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.SQUARE
        strokeWidth = maxOf(layout.renderParams.borderWidthPx * 1.45f, 3f)
        color = Color.BLACK
    }
    // Старые уголки у рамки — только у старого формата: у бланка v2 их место заняли метки ArUco.
    if (snapshot.v2 == null) drawBlankBubbleLayoutCornerMarksOnCanvas(canvas, layout, cornerMarkStroke)

    val bubbleRadius = layout.renderParams.bubbleSizePx / 2f
    for (bubble in layout.bubbles) {
        if (bubble.id in snapshot.hiddenBubbleIds) continue
        val fillPaint = if (bubble.isEmptyPlaceholder) bubbleEmptyFill else bubbleFill
        val strokePaint = if (bubble.isEmptyPlaceholder) bubbleEmptyStroke else bubbleStroke
        canvas.drawCircle(bubble.centerX, bubble.centerY, bubbleRadius, fillPaint)
        canvas.drawCircle(bubble.centerX, bubble.centerY, bubbleRadius, strokePaint)
        val label = bubble.label
        if (label != null) {
            val fm = textPaint.fontMetrics
            val baseline = bubble.centerY - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, bubble.centerX, baseline, textPaint)
        }
    }
    snapshot.v2?.let { v2 ->
        val b = blankBubbleLayoutBoundingRect(layout)
        BlankV2Print.draw(canvas, b.left, b.top, b.right - b.left, b.bottom - b.top, v2, withBackground)
    }
    canvas.restore()

    if (snapshot.v2 == null && snapshot.showVariantMarker) {
        val marker = computeBlankVariantMarkerRect(
            layout = layout,
            blankPanLogical = snapshot.blankPanLogical,
            blankScale = snapshot.blankScale,
        )
        val markerCenterX = marker.left + marker.size / 2f
        val markerCenterY = marker.top + marker.size / 2f
        val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (withBackground) Color.WHITE else Color.TRANSPARENT
        }
        val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#2E3440")
        }
        val hexPath = Path().apply {
            val points = buildVariantHexagonPoints(marker.left, marker.top, marker.size)
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
            close()
        }
        canvas.drawPath(hexPath, markerFill)
        canvas.drawPath(hexPath, markerStroke)

        if (snapshot.variantPrintedMode) {
            val markerText = snapshot.variantPrintedNumber.ifBlank { "1" }
            val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = Color.parseColor("#2E3440")
                textSize = marker.size * 0.52f
                isFakeBoldText = true
            }
            val fm = markerTextPaint.fontMetrics
            val baseline = markerCenterY - (fm.ascent + fm.descent) / 2f
            canvas.drawText(markerText, markerCenterX, baseline, markerTextPaint)
        }
    }

    if (snapshot.v2 == null && snapshot.showStudentNameMarker) {
        val marker = computeBlankStudentNameMarkerRect(
            layout = layout,
            blankPanLogical = snapshot.blankPanLogical,
            blankScale = snapshot.blankScale,
        )
        val markerRect = RectF(
            marker.left,
            marker.top,
            marker.left + marker.width,
            marker.top + marker.height,
        )
        val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#2E3440")
        }
        val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = Color.parseColor("#2E3440")
            textSize = marker.height * 0.34f
            isFakeBoldText = false
        }
        canvas.drawRect(markerRect, markerStroke)
        val midX = markerRect.centerX()
        canvas.drawLine(
            midX,
            markerRect.top,
            midX,
            markerRect.bottom,
            markerStroke,
        )
        if (snapshot.studentNamePrintedMode) {
            markerTextPaint.textAlign = Paint.Align.LEFT
            val fm = markerTextPaint.fontMetrics
            val baseline = markerRect.centerY() - (fm.ascent + fm.descent) / 2f
            val pad = markerRect.height() * 0.10f
            val leftTextX = markerRect.left + pad
            val rightTextX = markerRect.left + markerRect.width() * 0.5f + pad
            canvas.drawText(snapshot.studentSurnameText, leftTextX, baseline, markerTextPaint)
            canvas.drawText(snapshot.studentNameText, rightTextX, baseline, markerTextPaint)
        }
    }

    // Текстовые элементы рисуются в координатах листа и не должны сдвигаться вместе с бланком.
    snapshot.textBlocks.filter { it.isVisible }.forEach { block ->
        canvas.save()
        canvas.translate(block.position.x, block.position.y)
        canvas.scale(clampTextScale(block.scale), clampTextScale(block.scale))
        blockTextPaint.textSize = clampTextFontSizePx(block.fontSizePx)
        val fm = blockTextPaint.fontMetrics
        val baseline = -fm.ascent
        canvas.drawText(block.text, 0f, baseline, blockTextPaint)
        canvas.restore()
    }
}

/**
 * Обрезает пустое поле листа вокруг всего напечатанного (рамка, метки, поле имени, надписи) с запасом.
 * Ищет непрозрачные пиксели, поэтому годится для картинки без фона. Если ничего не найдено — возвращает исходную.
 */
internal fun cropToContent(bitmap: Bitmap, scale: Float = EXPORT_RASTER_SCALE): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val row = IntArray(w)
    var top = -1
    var bottom = -1
    var left = w
    var right = -1
    for (y in 0 until h) {
        bitmap.getPixels(row, 0, w, 0, y, w, 1)
        var rowLeft = -1
        var rowRight = -1
        for (x in 0 until w) {
            if ((row[x] ushr 24) > 8) {
                if (rowLeft < 0) rowLeft = x
                rowRight = x
            }
        }
        if (rowLeft >= 0) {
            if (top < 0) top = y
            bottom = y
            if (rowLeft < left) left = rowLeft
            if (rowRight > right) right = rowRight
        }
    }
    if (top < 0 || right < 0) return bitmap
    val m = (CROP_MARGIN_LOGICAL * scale).toInt()
    val x0 = (left - m).coerceAtLeast(0)
    val y0 = (top - m).coerceAtLeast(0)
    val x1 = (right + m).coerceAtMost(w - 1)
    val y1 = (bottom + m).coerceAtMost(h - 1)
    return Bitmap.createBitmap(bitmap, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
}

fun saveBlankSheetPng(
    context: Context,
    bitmap: Bitmap,
    withBackground: Boolean,
): File {
    val suffix = if (withBackground) "with-bg" else "no-bg"
    val output = buildExportFile(context, "blank-$suffix", "png")
    // Без фона — это «вставить в свой документ»: убираем пустое поле листа, бланк и метки целы.
    val toSave = if (withBackground) bitmap else cropToContent(bitmap)
    try {
        FileOutputStream(output).use { out ->
            toSave.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    } finally {
        if (toSave !== bitmap) toSave.recycle()
    }
    return output
}

/** PDF рисуется линиями (вектор): идеальная чёткость на любом принтере и маленький файл. */
fun saveBlankSheetPdf(
    context: Context,
    snapshot: BlankSheetExportSnapshot,
): File {
    return saveBlankSheetsPdfVector(context, listOf(snapshot), filePrefix = "blank")
}

fun saveBlankSheetsPdfVector(
    context: Context,
    snapshots: List<BlankSheetExportSnapshot>,
    filePrefix: String = "blank-batch",
): File {
    require(snapshots.isNotEmpty())
    val output = buildExportFile(context, filePrefix, "pdf")
    val pdf = android.graphics.pdf.PdfDocument()
    try {
        snapshots.forEachIndexed { index, snap ->
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo
                .Builder(SHEET_WIDTH_PX, SHEET_HEIGHT_PX, index + 1)
                .create()
            val page = pdf.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            drawBlankSheet(page.canvas, snap, withBackground = true)
            pdf.finishPage(page)
        }
        FileOutputStream(output).use { out ->
            pdf.writeTo(out)
            out.flush()
        }
    } finally {
        pdf.close()
    }
    return output
}

fun saveBlankSheetPdfMultiPage(
    context: Context,
    pages: List<Bitmap>,
    filePrefix: String = "blank-batch",
): File {
    require(pages.isNotEmpty())
    val output = buildExportFile(context, filePrefix, "pdf")
    val pdf = android.graphics.pdf.PdfDocument()
    try {
        pages.forEachIndexed { index, bitmap ->
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo
                .Builder(bitmap.width, bitmap.height, index + 1)
                .create()
            val page = pdf.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdf.finishPage(page)
        }
        FileOutputStream(output).use { out ->
            pdf.writeTo(out)
            out.flush()
        }
    } finally {
        pdf.close()
    }
    return output
}

fun saveBlankSheetDocx(
    context: Context,
    bitmap: Bitmap,
): File {
    val output = buildExportFile(context, "blank", "docx")
    val pngBytes = ByteArrayOutputStream().use { baos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        baos.toByteArray()
    }
    val cx = SHEET_WIDTH_PX.toLong() * EMU_PER_PX
    val cy = SHEET_HEIGHT_PX.toLong() * EMU_PER_PX
    ZipOutputStream(FileOutputStream(output)).use { zip ->
        putTextEntry(
            zip,
            "[Content_Types].xml",
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Default Extension="png" ContentType="image/png"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
            """.trimIndent(),
        )
        putTextEntry(
            zip,
            "_rels/.rels",
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
            """.trimIndent(),
        )
        putTextEntry(
            zip,
            "word/_rels/document.xml.rels",
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
            </Relationships>
            """.trimIndent(),
        )
        putTextEntry(
            zip,
            "word/document.xml",
            buildDocxSingleDocumentXml(cx, cy),
        )
        putBinaryEntry(zip, "word/media/image1.png", pngBytes)
    }
    return output
}

fun saveBlankSheetDocxMultiPage(
    context: Context,
    pages: List<Bitmap>,
    filePrefix: String = "blank-batch",
): File {
    require(pages.isNotEmpty())
    val output = buildExportFile(context, filePrefix, "docx")
    val pngBytesList = pages.map { bmp ->
        ByteArrayOutputStream().use { baos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
    }
    val cx = SHEET_WIDTH_PX.toLong() * EMU_PER_PX
    val cy = SHEET_HEIGHT_PX.toLong() * EMU_PER_PX
    val n = pages.size
    ZipOutputStream(FileOutputStream(output)).use { zip ->
        putTextEntry(
            zip,
            "[Content_Types].xml",
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Default Extension="png" ContentType="image/png"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
            """.trimIndent(),
        )
        putTextEntry(
            zip,
            "_rels/.rels",
            """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
            """.trimIndent(),
        )
        putTextEntry(
            zip,
            "word/_rels/document.xml.rels",
            buildDocxDocumentRelsXml(n),
        )
        putTextEntry(
            zip,
            "word/document.xml",
            buildDocxMultiPageDocumentXml(cx, cy, n),
        )
        for (i in pngBytesList.indices) {
            putBinaryEntry(zip, "word/media/image${i + 1}.png", pngBytesList[i])
        }
    }
    return output
}

/** Пакет DOCX по классу: каждый лист рисуется, сохраняется и сразу освобождается. */
fun saveBlankSheetsDocxSeparate(
    context: Context,
    entries: List<Pair<String, BlankSheetExportSnapshot>>,
    filePrefix: String = "blank-docx",
): List<File> {
    require(entries.isNotEmpty())
    return entries.mapIndexed { index, (baseName, snap) ->
        val safe = sanitizeExportEntryBaseName(baseName).ifBlank { "blank-${index + 1}" }
        val out = buildExportFile(context, "$filePrefix-$safe", "docx")
        val bitmap = renderBlankSheetBitmap(snap, withBackground = true)
        val pngBytes = try {
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        val cx = SHEET_WIDTH_PX.toLong() * EMU_PER_PX
        val cy = SHEET_HEIGHT_PX.toLong() * EMU_PER_PX
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            putTextEntry(
                zip,
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Default Extension="png" ContentType="image/png"/>
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """.trimIndent(),
            )
            putTextEntry(
                zip,
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """.trimIndent(),
            )
            putTextEntry(
                zip,
                "word/_rels/document.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
                </Relationships>
                """.trimIndent(),
            )
            putTextEntry(
                zip,
                "word/document.xml",
                buildDocxSingleDocumentXml(cx, cy),
            )
            putBinaryEntry(zip, "word/media/image1.png", pngBytes)
        }
        out
    }
}

/** Рисует лист для PNG-пакета; без фона — сразу обрезает по содержимому. Вызывающий освобождает результат. */
private fun renderPngForExport(snap: BlankSheetExportSnapshot, withBackground: Boolean): Bitmap {
    val full = renderBlankSheetBitmap(snap, withBackground)
    if (withBackground) return full
    val cropped = cropToContent(full)
    if (cropped !== full) full.recycle()
    return cropped
}

fun saveBlankSheetsPngZip(
    context: Context,
    entries: List<Pair<String, BlankSheetExportSnapshot>>,
    withBackground: Boolean,
    filePrefix: String = "blank-png-batch",
): File {
    require(entries.isNotEmpty())
    val suffix = if (withBackground) "bg" else "nobg"
    val output = buildExportFile(context, "$filePrefix-$suffix", "zip")
    ZipOutputStream(FileOutputStream(output)).use { zip ->
        for ((baseName, snap) in entries) {
            val safe = sanitizeExportEntryBaseName(baseName).ifBlank { "blank" }
            zip.putNextEntry(ZipEntry("$safe.png"))
            val bitmap = renderPngForExport(snap, withBackground)
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
            } finally {
                bitmap.recycle()
            }
            zip.closeEntry()
        }
    }
    return output
}

fun saveBlankSheetsPngSeparate(
    context: Context,
    entries: List<Pair<String, BlankSheetExportSnapshot>>,
    withBackground: Boolean,
    filePrefix: String = "blank-png",
): List<File> {
    require(entries.isNotEmpty())
    val suffix = if (withBackground) "bg" else "nobg"
    return entries.mapIndexed { index, (baseName, snap) ->
        val safe = sanitizeExportEntryBaseName(baseName).ifBlank { "blank-${index + 1}" }
        val out = buildExportFile(context, "$filePrefix-$suffix-$safe", "png")
        val bitmap = renderPngForExport(snap, withBackground)
        try {
            FileOutputStream(out).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
        } finally {
            bitmap.recycle()
        }
        out
    }
}

fun shareExportedFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val mimeType = when (file.extension.lowercase(Locale.US)) {
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Отправить файл").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun shareExportedFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    if (files.size == 1) {
        shareExportedFile(context, files.first())
        return
    }
    val uris = ArrayList<android.net.Uri>(files.size)
    files.forEach { file ->
        uris += FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/png"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Отправить файлы").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
