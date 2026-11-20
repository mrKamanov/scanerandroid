package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: раскладка нескольких бланков на один лист А4 для печати (PDF, вектор).
 * Сколько бланков помещается, считается по реальному размеру напечатанного (1 или 2 колонки, шапка, поле имени),
 * а варианты, где кружки стали бы мельче допустимого, не предлагаются.
 */
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

private const val PAGE_W = 595f
private const val PAGE_H = 842f

/** Поля листа (принтер не печатает у самого края), логические единицы ≈ 1/72 дюйма. */
private const val PAGE_MARGIN = 18f

/** Промежуток между бланками на листе — место для линии отреза. */
private const val CELL_GAP = 14f

/** Запас вокруг напечатанного внутри ячейки, чтобы рамка не прижималась к линии отреза. */
private const val CONTENT_PAD = 6f

/**
 * Самый мелкий кружок, который допускаем на печати: столько же, сколько позволяет сам конструктор
 * (стандартный кружок 30 × минимальный масштаб 0,65). Мельче не делаем, чтобы не терять надёжность проверки.
 */
private const val MIN_PRINTED_BUBBLE = BlankBubbleDefaults.BUBBLE_SIZE_PX * 0.65f

data class BlankTileOption(
    val perSheet: Int,
    val cols: Int,
    val rows: Int,
    /** Во сколько раз уменьшается бланк (1 — как в конструкторе). */
    val scale: Float,
    val fits: Boolean,
)

/** Где на листе находится всё напечатанное (рамка, метки, поле имени, надписи), в логических единицах. */
internal fun blankContentBounds(snapshot: BlankSheetExportSnapshot): RectF {
    val bmp = renderBlankSheetBitmap(snapshot, withBackground = false, scale = 1f)
    try {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        var top = -1
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 8) {
                    if (top < 0) top = y
                    bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        if (top < 0) return RectF(0f, 0f, PAGE_W, PAGE_H)
        return RectF(
            (left - CONTENT_PAD).coerceAtLeast(0f),
            (top - CONTENT_PAD).coerceAtLeast(0f),
            (right + 1 + CONTENT_PAD).coerceAtMost(PAGE_W),
            (bottom + 1 + CONTENT_PAD).coerceAtMost(PAGE_H),
        )
    } finally {
        bmp.recycle()
    }
}

private fun printedBubbleSize(snapshot: BlankSheetExportSnapshot): Float =
    snapshot.renderParams.bubbleSizePx * clampBlankScale(snapshot.blankScale)

/** Варианты 1, 2 и 4 бланка на лист; для каждого — лучшая сетка и помещается ли без потери надёжности. */
internal fun blankTileOptions(snapshot: BlankSheetExportSnapshot): List<BlankTileOption> =
    blankTileOptions(blankContentBounds(snapshot), printedBubbleSize(snapshot))

internal fun blankTileOptions(content: RectF, bubbleSize: Float): List<BlankTileOption> {
    val grids = mapOf(
        1 to listOf(1 to 1),
        2 to listOf(1 to 2, 2 to 1),
        4 to listOf(2 to 2, 1 to 4, 4 to 1),
    )
    return grids.map { (n, candidates) ->
        val best = candidates.map { (cols, rows) ->
            val cellW = (PAGE_W - 2 * PAGE_MARGIN - (cols - 1) * CELL_GAP) / cols
            val cellH = (PAGE_H - 2 * PAGE_MARGIN - (rows - 1) * CELL_GAP) / rows
            val k = if (n == 1) 1f else minOf(1f, cellW / content.width(), cellH / content.height())
            Triple(cols, rows, k)
        }.maxBy { it.third }
        BlankTileOption(
            perSheet = n,
            cols = best.first,
            rows = best.second,
            scale = best.third,
            fits = n == 1 || bubbleSize * best.third >= MIN_PRINTED_BUBBLE,
        )
    }
}

/**
 * PDF, где на каждом листе [option.perSheet] бланков. [snapshots] — по одному на бланк
 * (для пакета по классу — свой у каждого ученика); листы заполняются по порядку.
 */
fun saveBlankSheetsPdfTiled(
    context: Context,
    snapshots: List<BlankSheetExportSnapshot>,
    option: BlankTileOption,
    filePrefix: String = "blank-tiled",
): File {
    require(snapshots.isNotEmpty())
    if (option.perSheet <= 1) return saveBlankSheetsPdfVector(context, snapshots, filePrefix)
    val content = blankContentBounds(snapshots.first())
    val output = buildExportFile(context, filePrefix, "pdf")
    val pdf = android.graphics.pdf.PdfDocument()
    val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = Color.parseColor("#B8B8B8")
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }
    try {
        snapshots.chunked(option.perSheet).forEachIndexed { pageIndex, pageSnaps ->
            val info = android.graphics.pdf.PdfDocument.PageInfo
                .Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageIndex + 1)
                .create()
            val page = pdf.startPage(info)
            val c = page.canvas
            c.drawColor(Color.WHITE)
            drawTiledPage(c, pageSnaps, content, option)
            drawCutLines(c, option, cutPaint)
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

private fun cellRect(option: BlankTileOption, index: Int): RectF {
    val cellW = (PAGE_W - 2 * PAGE_MARGIN - (option.cols - 1) * CELL_GAP) / option.cols
    val cellH = (PAGE_H - 2 * PAGE_MARGIN - (option.rows - 1) * CELL_GAP) / option.rows
    val col = index % option.cols
    val row = index / option.cols
    val left = PAGE_MARGIN + col * (cellW + CELL_GAP)
    val top = PAGE_MARGIN + row * (cellH + CELL_GAP)
    return RectF(left, top, left + cellW, top + cellH)
}

private fun drawTiledPage(
    canvas: Canvas,
    snaps: List<BlankSheetExportSnapshot>,
    content: RectF,
    option: BlankTileOption,
) {
    val k = option.scale
    snaps.forEachIndexed { i, snap ->
        val cell = cellRect(option, i)
        val drawnW = content.width() * k
        val drawnH = content.height() * k
        canvas.save()
        canvas.translate(cell.left + (cell.width() - drawnW) / 2f, cell.top + (cell.height() - drawnH) / 2f)
        canvas.scale(k, k)
        canvas.translate(-content.left, -content.top)
        canvas.clipRect(content)
        drawBlankSheet(canvas, snap, withBackground = true)
        canvas.restore()
    }
}

/** Бледные пунктирные линии между бланками — по ним удобно резать лист. Они вне рамок и не мешают проверке. */
private fun drawCutLines(canvas: Canvas, option: BlankTileOption, paint: Paint) {
    for (col in 1 until option.cols) {
        val x = cellRect(option, col).left - CELL_GAP / 2f
        canvas.drawLine(x, PAGE_MARGIN / 2f, x, PAGE_H - PAGE_MARGIN / 2f, paint)
    }
    for (row in 1 until option.rows) {
        val y = cellRect(option, row * option.cols).top - CELL_GAP / 2f
        canvas.drawLine(PAGE_MARGIN / 2f, y, PAGE_W - PAGE_MARGIN / 2f, y, paint)
    }
}

/** Для превью в диалоге: маленькая картинка листа с раскладкой. */
internal fun renderTiledPreview(snapshot: BlankSheetExportSnapshot, option: BlankTileOption, scale: Float = 0.4f): Bitmap {
    val bmp = Bitmap.createBitmap((PAGE_W * scale).toInt(), (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.WHITE)
    c.scale(scale, scale)
    if (option.perSheet <= 1) {
        drawBlankSheet(c, snapshot, withBackground = true)
    } else {
        val content = blankContentBounds(snapshot)
        drawTiledPage(c, List(option.perSheet) { snapshot }, content, option)
    }
    return bmp
}
