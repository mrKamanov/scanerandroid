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
)

fun renderBlankSheetBitmap(
    snapshot: BlankSheetExportSnapshot,
    withBackground: Boolean,
): Bitmap {
    val bitmap = Bitmap.createBitmap(SHEET_WIDTH_PX, SHEET_HEIGHT_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (withBackground) {
        canvas.drawColor(Color.WHITE)
    } else {
        canvas.drawColor(Color.TRANSPARENT)
    }

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
    drawBlankBubbleLayoutCornerMarksOnCanvas(canvas, layout, cornerMarkStroke)

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
    canvas.restore()

    if (snapshot.showVariantMarker) {
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

    if (snapshot.showStudentNameMarker) {
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
    return bitmap
}

fun saveBlankSheetPng(
    context: Context,
    bitmap: Bitmap,
    withBackground: Boolean,
): File {
    val suffix = if (withBackground) "with-bg" else "no-bg"
    val output = buildExportFile(context, "blank-$suffix", "png")
    FileOutputStream(output).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
    }
    return output
}

fun saveBlankSheetPdf(
    context: Context,
    bitmap: Bitmap,
): File {
    return saveBlankSheetPdfMultiPage(context, listOf(bitmap), filePrefix = "blank")
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
    val cx = bitmap.width.toLong() * EMU_PER_PX
    val cy = bitmap.height.toLong() * EMU_PER_PX
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
    val w = pages.first().width
    val h = pages.first().height
    val cx = w.toLong() * EMU_PER_PX
    val cy = h.toLong() * EMU_PER_PX
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

fun saveBlankSheetsDocxSeparate(
    context: Context,
    entries: List<Pair<String, Bitmap>>,
    filePrefix: String = "blank-docx",
): List<File> {
    require(entries.isNotEmpty())
    return entries.mapIndexed { index, (baseName, bitmap) ->
        val safe = sanitizeExportEntryBaseName(baseName).ifBlank { "blank-${index + 1}" }
        val out = buildExportFile(context, "$filePrefix-$safe", "docx")
        val pngBytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
        val cx = bitmap.width.toLong() * EMU_PER_PX
        val cy = bitmap.height.toLong() * EMU_PER_PX
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

fun saveBlankSheetsPngZip(
    context: Context,
    entries: List<Pair<String, Bitmap>>,
    withBackground: Boolean,
    filePrefix: String = "blank-png-batch",
): File {
    require(entries.isNotEmpty())
    val suffix = if (withBackground) "bg" else "nobg"
    val output = buildExportFile(context, "$filePrefix-$suffix", "zip")
    ZipOutputStream(FileOutputStream(output)).use { zip ->
        for ((baseName, bitmap) in entries) {
            val safe = sanitizeExportEntryBaseName(baseName).ifBlank { "blank" }
            zip.putNextEntry(ZipEntry("$safe.png"))
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                zip.write(baos.toByteArray())
            }
            zip.closeEntry()
        }
    }
    return output
}

fun saveBlankSheetsPngSeparate(
    context: Context,
    entries: List<Pair<String, Bitmap>>,
    withBackground: Boolean,
    filePrefix: String = "blank-png",
): List<File> {
    require(entries.isNotEmpty())
    val suffix = if (withBackground) "bg" else "nobg"
    return entries.mapIndexed { index, (baseName, bitmap) ->
        val safe = sanitizeExportEntryBaseName(baseName).ifBlank { "blank-${index + 1}" }
        val out = buildExportFile(context, "$filePrefix-$suffix-$safe", "png")
        FileOutputStream(out).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
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
