package com.tscan.scanertestov.feature.batch

/**
 * Описание: распознавание фамилии и имени ученика по изображению работы.
 */
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tscan.scanertestov.feature.journals.JournalStudent
import com.tscan.scanertestov.ml.ocr.EslavPpOcrV5RecEngine
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Locale

data class BatchStudentNameRecognitionResult(
    val surname: String? = null,
    val name: String? = null,
    val journalClassName: String? = null,
    val confidence: Float = 0f,
    val rawText: String = "",
    val error: String? = null,
)

private const val MIN_NAME_CONFIDENCE = 0.01f

fun recognizeStudentNameFromWorkImage(
    context: Context,
    contentUri: Uri,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
    journalRoster: List<JournalStudent> = emptyList(),
): BatchStudentNameRecognitionResult {
    val bitmap = decodeBitmapFromContentUri(context, contentUri)
        ?: return BatchStudentNameRecognitionResult(error = "Не удалось открыть изображение")
    return try {
        recognizeStudentNameFromBitmap(
            context = context,
            sourceBitmap = bitmap,
            questionCount = questionCount,
            optionCount = optionCount,
            columnCount = columnCount,
            journalRoster = journalRoster,
        )
    } finally {
        bitmap.recycle()
    }
}

fun recognizeStudentNameFromBitmap(
    context: Context,
    sourceBitmap: Bitmap,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
    journalRoster: List<JournalStudent> = emptyList(),
): BatchStudentNameRecognitionResult {
    var raw = ""
    return try {
        val crop = extractStudentNameCropFromPhoto(
            sourceBitmap = sourceBitmap,
            questionCount = questionCount,
            optionCount = optionCount,
            columnCount = columnCount,
        )
        try {
            recognizeStudentNameFromNameCropMat(context, crop, journalRoster)
        } finally {
            crop.release()
        }
    } catch (e: Exception) {
        BatchStudentNameRecognitionResult(
            confidence = 0f,
            rawText = raw,
            error = e.message ?: "Ошибка распознавания имени",
        )
    }
}

fun recognizeStudentNameFromNameCropMat(
    context: Context,
    nameCrop: Mat,
    journalRoster: List<JournalStudent>,
): BatchStudentNameRecognitionResult {
    val loadErr = EslavPpOcrV5RecEngine.ensureLoaded(context)
    if (loadErr != null) {
        val err = if (loadErr.isNotBlank()) loadErr else "Ошибка загрузки OCR"
        return BatchStudentNameRecognitionResult(error = err)
    }
    var raw = ""
    return try {
        val result = recognizeStudentNameByHalves(nameCrop)
        raw = result.rawText
        val (dictSurname, dictName) = BatchNameDictionaryCorrector.correct(
            context = context,
            surname = result.surname,
            name = result.name,
            confidence = result.confidence,
        )
        val snap = JournalRosterNameMatcher.snapToRoster(
            roster = journalRoster,
            surname = dictSurname,
            name = dictName,
            confidence = result.confidence,
        )
        BatchStudentNameRecognitionResult(
            surname = snap.surname,
            name = snap.name,
            journalClassName = snap.matchedClassName,
            confidence = result.confidence,
            rawText = result.rawText,
        )
    } catch (e: Exception) {
        BatchStudentNameRecognitionResult(
            confidence = 0f,
            rawText = raw,
            error = e.message ?: "Ошибка распознавания имени",
        )
    }
}

private data class NameAttempt(val text: String, val score: Float)
private data class NameSplitResult(
    val surname: String?,
    val name: String?,
    val confidence: Float,
    val rawText: String,
)

private data class FullNameFallback(
    val surname: String?,
    val name: String?,
    val rawText: String,
    val score: Float,
)

private fun recognizeStudentNameByHalves(crop: Mat): NameSplitResult {
    val w = crop.cols().coerceAtLeast(2)
    val h = crop.rows().coerceAtLeast(2)
    val mid = (w / 2).coerceIn(1, w - 1)
    val leftMat = Mat(crop, Rect(0, 0, mid, h)).clone()
    val rightMat = Mat(crop, Rect(mid, 0, w - mid, h)).clone()
    return try {
        val (leftRaw, leftScore) = recognizeNameByBestAttempt(leftMat)
        val (rightRaw, rightScore) = recognizeNameByBestAttempt(rightMat)
        val surname = leftRaw.normalizeCyrNamePart()
        val name = rightRaw.normalizeCyrNamePart()

        val full = run {
            val (r, s) = recognizeNameByBestAttempt(crop)
            val (sn, nm) = extractSurnameName(r, s)
            FullNameFallback(sn, nm, r, s)
        }
        val bestSurname = chooseBestNamePart(surname, full.surname)
        val bestName = chooseBestNamePart(name, full.name)

        if (!bestSurname.isNullOrBlank() || !bestName.isNullOrBlank()) {
            val conf = listOf(leftScore, rightScore).average().toFloat()
            return NameSplitResult(
                surname = bestSurname,
                name = bestName,
                confidence = conf,
                rawText = "${leftRaw.trim()} | ${rightRaw.trim()}".trim(),
            )
        }

        NameSplitResult(
            surname = full.surname,
            name = full.name,
            confidence = full.score,
            rawText = full.rawText,
        )
    } finally {
        leftMat.release()
        rightMat.release()
    }
}

private fun recognizeNameByBestAttempt(crop: Mat): Pair<String, Float> {
    val gray = Mat()
    val clahe = Mat()
    val binary = Mat()
    val adaptive = Mat()
    val work = Mat()
    val enlarged = Mat()
    val attempts = mutableListOf<NameAttempt>()
    try {
        Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.createCLAHE(2.4, Size(8.0, 8.0)).apply(gray, clahe)
        attempts.addAll(recognizeNameGrayAttempts(clahe, 2.4))
        attempts.addAll(recognizeNameGrayAttempts(clahe, 3.2))

        Imgproc.threshold(clahe, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        attempts.addAll(recognizeNameBinaryAttempts(binary, true, 3.2, enlarged, work))
        Imgproc.adaptiveThreshold(
            clahe,
            adaptive,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            29,
            6.0,
        )
        attempts.addAll(recognizeNameBinaryAttempts(adaptive, true, 3.2, enlarged, work))
    } finally {
        gray.release()
        clahe.release()
        binary.release()
        adaptive.release()
        work.release()
        enlarged.release()
    }
    val best = attempts.maxByOrNull { attempt ->
        val letters = Regex("[A-Za-zА-Яа-яЁё]").findAll(attempt.text).count()
        attempt.score + letters * 0.015f
    }
    return if (best != null) best.text to best.score else "" to 0f
}

private fun recognizeNameGrayAttempts(gray: Mat, scale: Double): List<NameAttempt> {
    val enlarged = Mat()
    return try {
        Imgproc.resize(gray, enlarged, Size(), scale, scale, Imgproc.INTER_CUBIC)
        Imgproc.cvtColor(enlarged, enlarged, Imgproc.COLOR_GRAY2BGR)
        buildList {
            val (text, score) = EslavPpOcrV5RecEngine.recognizeLine(enlarged)
            add(NameAttempt(text, score))
        }
    } finally {
        enlarged.release()
    }
}

private fun recognizeNameBinaryAttempts(
    binary: Mat,
    invertIfNeeded: Boolean,
    scale: Double,
    dst: Mat,
    work: Mat,
): List<NameAttempt> {
    binary.copyTo(work)
    if (invertIfNeeded) {
        val mean = Core.mean(work).`val`[0]
        if (mean < 127.0) Core.bitwise_not(work, work)
    }
    Imgproc.resize(work, dst, Size(), scale, scale, Imgproc.INTER_CUBIC)
    Imgproc.cvtColor(dst, dst, Imgproc.COLOR_GRAY2BGR)
    return buildList {
        val (text, score) = EslavPpOcrV5RecEngine.recognizeLine(dst)
        add(NameAttempt(text, score))
    }
}

private fun extractSurnameName(rawText: String, confidence: Float): Pair<String?, String?> {
    if (confidence < MIN_NAME_CONFIDENCE) return null to null
    val normalized = rawText
        .toCyrillicLookalikes()
        .replace('|', ' ')
        .replace('/', ' ')
        .replace('\\', ' ')
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return null to null
    val words = Regex("[A-Za-zА-Яа-яЁё\\-]{2,}")
        .findAll(normalized)
        .map { it.value.normalizeNamePart() }
        .filter { it.isNotBlank() }
        .toList()
    if (words.isEmpty()) return null to null
    val surname = words.getOrNull(0)
    val name = words.getOrNull(1)
    return surname to name
}

private fun String.normalizeNamePart(): String {
    if (isBlank()) return this
    val low = toCyrillicLookalikes().lowercase(Locale("ru"))
    return low.replaceFirstChar { it.titlecase(Locale("ru")) }
}

private fun String.normalizeCyrNamePart(): String? {
    val cleaned = toCyrillicLookalikes()
        .replace('|', ' ')
        .replace('/', ' ')
        .replace('\\', ' ')
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    val tokens = Regex("[А-Яа-яЁё\\-]{1,}")
        .findAll(cleaned)
        .map { it.value }
        .toList()
    if (tokens.isEmpty()) return null
    val candidates = mutableListOf<String>()
    candidates += tokens
    val gluedAll = tokens.joinToString("")
    if (gluedAll.length >= 3) {
        candidates += gluedAll
    }
    for (i in 0 until tokens.lastIndex) {
        val a = tokens[i]
        val b = tokens[i + 1]
        if (a.length <= 4 && b.length >= 2) {
            candidates += (a + b)
        }
    }
    for (start in tokens.indices) {
        var merged = ""
        for (end in start until minOf(tokens.lastIndex, start + 3)) {
            merged += tokens[end]
            if (merged.length >= 3) candidates += merged
        }
    }
    val best = candidates
        .map { it.normalizeNamePart() }
        .filter { it.length >= 2 }
        .maxByOrNull { candidate ->
            val bonus = if (candidate.length >= 5) 2 else 0
            candidate.length + bonus
        }
    return best?.takeIf { it.isNotBlank() }
}

private fun chooseBestNamePart(primary: String?, fallback: String?): String? {
    val p = primary?.trim().orEmpty()
    val f = fallback?.trim().orEmpty()
    return when {
        p.isBlank() -> fallback
        f.isBlank() -> primary
        p.length >= f.length -> primary
        else -> fallback
    }
}

private fun String.toCyrillicLookalikes(): String {
    if (isBlank()) return this
    val map = mapOf(
        'A' to 'А', 'a' to 'а',
        'B' to 'В',
        'C' to 'С', 'c' to 'с',
        'E' to 'Е', 'e' to 'е',
        'H' to 'Н',
        'K' to 'К',
        'M' to 'М',
        'O' to 'О', 'o' to 'о',
        'P' to 'Р', 'p' to 'р',
        'T' to 'Т',
        'X' to 'Х', 'x' to 'х',
        'Y' to 'У', 'y' to 'у',
    )
    val sb = StringBuilder(length)
    for (ch in this) {
        sb.append(map[ch] ?: ch)
    }
    return sb.toString()
}

