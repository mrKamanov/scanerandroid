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

/**
 * Максимальная сторона изображения, с которой работает распознавание.
 * 12 МП (4032×3024) проходит без потерь; фото 48–200 МП уменьшаются — иначе один кадр
 * занимает 150–800 МБ памяти и приложение падает с OutOfMemoryError.
 */
const val BATCH_DECODE_MAX_SIDE: Int = 4096

/**
 * Декодирует изображение по URI с учётом EXIF-поворота и ограничением размера [maxSide].
 * Блокирующий вызов: выполнять только в фоновом потоке (Dispatchers.IO / Default).
 */
fun decodeBitmapFromContentUri(
    context: Context,
    uri: Uri,
    maxSide: Int = BATCH_DECODE_MAX_SIDE,
): Bitmap? {
    return runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@runCatching null
        var sample = 1
        // Самый крупный inSampleSize, при котором сторона ещё не меньше maxSide.
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return@runCatching null
        if (maxOf(decoded.width, decoded.height) > maxSide) {
            decoded = scaleBitmapForPreview(decoded, maxSide)
        }
        val degrees = exifRotationDegrees(context, uri, bytes)
        applyRotationDegrees(decoded, degrees)
    }.getOrNull()
}

/** Удаляет файл снимка, если URI указывает на собственный каталог камеры приложения. */
fun deleteOwnBatchCaptureFile(context: Context, contentUri: String) {
    runCatching {
        val uri = Uri.parse(contentUri)
        val authority = "${context.packageName}.fileprovider"
        if (uri.authority != authority) return
        val name = uri.lastPathSegment ?: return
        val dir = File(context.filesDir, CAMERA_SUBDIR)
        val file = File(dir, File(name).name)
        if (file.parentFile?.canonicalPath == dir.canonicalPath && file.exists()) {
            file.delete()
        }
    }
}

/**
 * Удаляет снимки камеры, на которые больше не ссылается очередь.
 * Вызывается при холодном старте (очередь пуста) и после очистки очереди.
 */
fun purgeBatchCaptureFiles(context: Context, keepUris: Collection<String> = emptyList()) {
    runCatching {
        val dir = File(context.filesDir, CAMERA_SUBDIR)
        val keepNames = keepUris.mapNotNull { Uri.parse(it).lastPathSegment?.let { s -> File(s).name } }.toSet()
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name !in keepNames) f.delete()
        }
    }
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
