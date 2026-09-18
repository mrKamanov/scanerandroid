package com.tscan.scanertestov.feature.batch

/**
 * Описание: обрезка кадра камеры до области белого контура-ориентира (пропорции A4).
 */
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

const val BATCH_CAMERA_SHEET_W_TO_H = 595f / 842f

/**
 * Центрированный прямоугольник контура в координатах вью (в px), такой же,
 * как рисует BatchDocumentCameraFrameOverlay. [fLeft, fTop, fRight, fBottom].
 */
fun a4GuideBox(viewW: Float, viewH: Float, marginPx: Float): FloatArray {
    val maxW = (viewW - marginPx * 2f).coerceAtLeast(40f)
    val maxH = (viewH - marginPx * 2f).coerceAtLeast(40f)
    var boxW = maxW
    var boxH = boxW / BATCH_CAMERA_SHEET_W_TO_H
    if (boxH > maxH) {
        boxH = maxH
        boxW = boxH * BATCH_CAMERA_SHEET_W_TO_H
    }
    val left = (viewW - boxW) / 2f
    val top = (viewH - boxH) / 2f
    return floatArrayOf(left, top, left + boxW, top + boxH)
}

/**
 * Обрезает снимок до области контура с учётом того, что PreviewView показывает
 * сенсор с центральным кропом под пропорции экрана (FILL_CENTER).
 * viewW/viewH/marginPx — геометрия оверлея в px.
 */
fun cropCameraShotByGuide(bitmap: Bitmap, viewW: Int, viewH: Int, marginPx: Int): Bitmap {
    val needW = bitmap.width
    val needH = bitmap.height
    if (needW <= 0 || needH <= 0) return bitmap
    val viewAspect = viewW.toFloat() / max(viewH, 1)
    val shotAspect = needW.toFloat() / max(needH, 1)

    var visW = needW.toFloat()
    var visH = needH.toFloat()
    var offX = 0f
    var offY = 0f
    if (shotAspect > viewAspect) {
        visW = needH * viewAspect
        offX = (needW - visW) / 2f
    } else if (shotAspect < viewAspect) {
        visH = needW / viewAspect
        offY = (needH - visH) / 2f
    }

    val box = a4GuideBox(viewW.toFloat(), viewH.toFloat(), marginPx.toFloat())
    fun mapX(vx: Float): Float = offX + (vx / max(viewW, 1)) * visW
    fun mapY(vy: Float): Float = offY + (vy / max(viewH, 1)) * visH

    var x0 = mapX(box[0]).toInt().coerceIn(0, needW - 1)
    var y0 = mapY(box[1]).toInt().coerceIn(0, needH - 1)
    var x1 = mapX(box[2]).toInt().coerceIn(x0 + 1, needW)
    var y1 = mapY(box[3]).toInt().coerceIn(y0 + 1, needH)
    if (x1 - x0 < 4 || y1 - y0 < 4) return bitmap
    return Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0)
}

/**
 * Декодирует JPEG с камеры, приводя к естественной ориентации (учитывает EXIF),
 * не более maxDim по большей стороне.
 */
fun decodeUprightSampled(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null

    val deg = runCatching {
        when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }.getOrDefault(0)

    return when {
        deg == 0 -> bmp
        deg == 180 -> rotateBitmap(bmp, 180)
        deg == 90 || deg == 270 -> {
            // Если BitmapFactory уже применил ориентацию, кадр вертикальный — не крутим.
            if (bmp.width > bmp.height) rotateBitmap(bmp, deg) else bmp
        }
        else -> bmp
    }
}

private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (out !== source) {
        source.recycle()
    }
    return out
}

/**
 * Перезаписывает файл JPEG без EXIF-поворота (битмап уже в естественной ориентации).
 */
fun writeJpegOverwrite(file: File, bitmap: Bitmap, quality: Int = 95): Boolean {
    return runCatching {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        true
    }.getOrDefault(false)
}