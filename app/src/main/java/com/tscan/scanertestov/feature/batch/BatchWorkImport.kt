package com.tscan.scanertestov.feature.batch

/**
 * Описание: вспомогательные функции импорта изображений (камера через FileProvider, подписи по URI, превью).
 */
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

private const val CAMERA_SUBDIR = "batch_capture"

fun createBatchCameraImageUri(context: Context): Pair<File, Uri> {
    val dir = File(context.filesDir, CAMERA_SUBDIR).apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    return file to uri
}

fun resolveBatchImageDisplayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                cursor.getString(idx)
            } else {
                null
            }
        }?.takeIf { !it.isNullOrBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: "изображение"
    } catch (_: Throwable) {
        uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "изображение"
    }
}

fun scaleBitmapForPreview(source: Bitmap, maxSide: Int): Bitmap {
    val w = source.width
    val h = source.height
    val longest = maxOf(w, h)
    if (longest <= maxSide) return source
    val scale = maxSide.toFloat() / longest
    val nw = (w * scale).toInt().coerceAtLeast(1)
    val nh = (h * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, nw, nh, true)
    if (scaled != source) {
        source.recycle()
    }
    return scaled
}

fun decodeBitmapFromContentUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}
