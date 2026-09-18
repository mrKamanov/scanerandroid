package com.tscan.scanertestov.feature.batch

/**
 * Описание: вспомогательные функции импорта изображений (камера через FileProvider, подписи по URI, превью).
 */
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

private const val CAMERA_SUBDIR = "batch_capture"

fun createBatchCameraImageUri(context: Context): Pair<File, Uri> {
    val dir = File(context.filesDir, CAMERA_SUBDIR).apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
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
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@runCatching null
        val degrees = exifRotationDegrees(context, uri, bytes)
        applyRotationDegrees(decoded, degrees)
    }.getOrNull()
}

private fun exifRotationDegrees(context: Context, uri: Uri, cachedBytes: ByteArray?): Int {
    return runCatching {
        when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return@runCatching 0
                exifToDegrees(ExifInterface(path))
            }
            "content" -> {
                context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }?.let { exifToDegrees(it) }
                    ?: cachedBytes?.let { exifToDegrees(ExifInterface(it.inputStream())) }
                    ?: 0
            }
            else -> cachedBytes?.let { exifToDegrees(ExifInterface(it.inputStream())) } ?: 0
        }
    }.getOrDefault(0)
}

private fun exifToDegrees(exif: ExifInterface): Int = when (
    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    ExifInterface.ORIENTATION_TRANSPOSE -> 90
    ExifInterface.ORIENTATION_TRANSVERSE -> 270
    else -> 0
}

private fun applyRotationDegrees(source: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return source
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (rotated !== source) {
        source.recycle()
    }
    return rotated
}
