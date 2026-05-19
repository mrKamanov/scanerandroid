package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: файловые helper-функции экспорта бланков (имена, пути, ZIP-записи, sanitize).
 */
import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun buildExportFile(context: Context, prefix: String, extension: String): File {
    val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    val dir = File(root, "blank-exports")
    if (!dir.exists()) dir.mkdirs()
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return File(dir, "$prefix-$stamp.$extension")
}

internal fun putTextEntry(zip: ZipOutputStream, path: String, content: String) {
    zip.putNextEntry(ZipEntry(path))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
}

internal fun putBinaryEntry(zip: ZipOutputStream, path: String, content: ByteArray) {
    zip.putNextEntry(ZipEntry(path))
    zip.write(content)
    zip.closeEntry()
}

internal fun sanitizeExportEntryBaseName(raw: String): String {
    if (raw.isBlank()) return "blank"
    return raw
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
}
