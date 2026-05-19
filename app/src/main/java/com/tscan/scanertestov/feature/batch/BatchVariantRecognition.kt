package com.tscan.scanertestov.feature.batch

/**
 * Описание: распознавание номера варианта по изображению работы (OCR + извлечение числа).
 */
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tscan.scanertestov.ml.ocr.CyrillicPpOcrV3RecEngine
import com.tscan.scanertestov.ml.ocr.EnNumberMobileV2PpOcrRecEngine
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class BatchVariantRecognitionResult(
    val variant: Int?,
    val confidence: Float,
    val rawText: String,
    val error: String? = null,
)

private const val MIN_VARIANT_CONFIDENCE = 0.02f
private const val MIN_VARIANT_CONFIDENCE_WEAK = 0.005f

fun recognizeVariantFromWorkImage(
    context: Context,
    contentUri: Uri,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
    maxVariantHint: Int?,
): BatchVariantRecognitionResult {
    val bitmap = decodeBitmapFromContentUri(context, contentUri)
        ?: return BatchVariantRecognitionResult(
            variant = null,
            confidence = 0f,
            rawText = "",
            error = "Не удалось открыть изображение",
        )
    return try {
        recognizeVariantFromBitmap(
            context = context,
            sourceBitmap = bitmap,
            questionCount = questionCount,
            optionCount = optionCount,
            columnCount = columnCount,
            maxVariantHint = maxVariantHint,
        )
    } finally {
        bitmap.recycle()
    }
}

fun recognizeVariantFromBitmap(
    context: Context,
    sourceBitmap: Bitmap,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
    maxVariantHint: Int?,
): BatchVariantRecognitionResult {
    var rawTextForDebug = ""
    return try {
        val variantCrop = extractVariantCropFromPhoto(
            sourceBitmap = sourceBitmap,
            questionCount = questionCount,
            optionCount = optionCount,
            columnCount = columnCount,
        )
        try {
            recognizeVariantFromVariantCropMat(context, variantCrop, maxVariantHint)
        } finally {
            variantCrop.release()
        }
    } catch (e: Exception) {
        BatchVariantRecognitionResult(
            variant = null,
            confidence = 0f,
            rawText = rawTextForDebug,
            error = e.message ?: "Ошибка распознавания",
        )
    }
}

fun recognizeVariantFromVariantCropMat(
    context: Context,
    variantCrop: Mat,
    maxVariantHint: Int?,
): BatchVariantRecognitionResult {
    val enNumberLoadErr = EnNumberMobileV2PpOcrRecEngine.ensureLoaded(context)
    val cyrillicLoadErr = CyrillicPpOcrV3RecEngine.ensureLoaded(context)
    if (cyrillicLoadErr != null) {
        return BatchVariantRecognitionResult(
            variant = null,
            confidence = 0f,
            rawText = "",
            error = cyrillicLoadErr,
        )
    }
    val useEnNumberOcr = (enNumberLoadErr == null)
    var rawTextForDebug = ""
    return try {
        val (text, score, parsed) = recognizeVariantByBestAttempt(variantCrop, maxVariantHint, useEnNumberOcr)
        rawTextForDebug = text
        val variant = parsed ?: extractVariantNumber(
            rawText = text,
            confidence = score,
            maxVariantHint = maxVariantHint,
        )
        BatchVariantRecognitionResult(
            variant = variant,
            confidence = score,
            rawText = text,
            error = null,
        )
    } catch (e: Exception) {
        BatchVariantRecognitionResult(
            variant = null,
            confidence = 0f,
            rawText = rawTextForDebug,
            error = e.message ?: "Ошибка распознавания",
        )
    }
}

private data class OcrAttempt(val text: String, val score: Float)

private fun recognizeVariantByBestAttempt(
    crop: Mat,
    maxVariantHint: Int?,
    useEnNumberOcr: Boolean,
): Triple<String, Float, Int?> {
    val attempts = mutableListOf<OcrAttempt>()
    val gray = Mat()
    val clahe = Mat()
    val binary = Mat()
    val adaptive = Mat()
    val work = Mat()
    val enlarged = Mat()
    try {
        Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.createCLAHE(2.8, Size(8.0, 8.0)).apply(gray, clahe)

        val center = centerCrop(clahe, cropFraction = 0.88f)
        try {
            attempts.addAll(recognizeGrayAttempts(center, scale = 3.0, useEnNumberOcr))
            attempts.addAll(recognizeGrayAttempts(center, scale = 4.0, useEnNumberOcr))

            Imgproc.threshold(center, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            attempts.addAll(
                recognizeBinaryAttempts(binary, invertIfNeeded = true, scale = 4.0, dst = enlarged, work = work, useEnNumberOcr),
            )

            Imgproc.adaptiveThreshold(
                center,
                adaptive,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                7.0,
            )
            attempts.addAll(
                recognizeBinaryAttempts(adaptive, invertIfNeeded = true, scale = 4.0, dst = enlarged, work = work, useEnNumberOcr),
            )
        } finally {
            center.release()
        }
    } finally {
        gray.release()
        clahe.release()
        binary.release()
        adaptive.release()
        work.release()
        enlarged.release()
    }

    val scored = attempts.map { attempt ->
        val parsed = extractVariantNumber(
            rawText = attempt.text,
            confidence = attempt.score,
            maxVariantHint = maxVariantHint,
        )
        Triple(attempt.text, attempt.score, parsed)
    }

    val withDigit = scored.filter { it.third != null }
    if (withDigit.isNotEmpty()) {
        val best = withDigit.maxBy { it.second }
        return Triple(best.first, best.second, best.third)
    }

    val best = scored.maxByOrNull { it.second }
    return if (best != null) {
        Triple(best.first, best.second, null)
    } else {
        Triple("", 0f, null)
    }
}

private fun recognizeGrayAttempts(gray: Mat, scale: Double, useEnNumberOcr: Boolean): List<OcrAttempt> {
    val enlarged = Mat()
    return try {
        Imgproc.resize(gray, enlarged, Size(), scale, scale, Imgproc.INTER_CUBIC)
        Imgproc.cvtColor(enlarged, enlarged, Imgproc.COLOR_GRAY2BGR)
        buildList {
            if (useEnNumberOcr) {
                val (t, s) = EnNumberMobileV2PpOcrRecEngine.recognizeLine(enlarged)
                add(OcrAttempt(t, s))
            }
            val (tc, sc) = CyrillicPpOcrV3RecEngine.recognizeLine(enlarged)
            add(OcrAttempt(tc, sc))
        }
    } finally {
        enlarged.release()
    }
}

private fun recognizeBinaryAttempts(
    binary: Mat,
    invertIfNeeded: Boolean,
    scale: Double,
    dst: Mat,
    work: Mat,
    useEnNumberOcr: Boolean,
): List<OcrAttempt> {
    binary.copyTo(work)
    if (invertIfNeeded) {
        val mean = Core.mean(work).`val`[0]
        if (mean < 127.0) Core.bitwise_not(work, work)
    }
    Imgproc.resize(work, dst, Size(), scale, scale, Imgproc.INTER_CUBIC)
    Imgproc.cvtColor(dst, dst, Imgproc.COLOR_GRAY2BGR)
    return buildList {
        if (useEnNumberOcr) {
            val (t, s) = EnNumberMobileV2PpOcrRecEngine.recognizeLine(dst)
            add(OcrAttempt(t, s))
        }
        val (tc, sc) = CyrillicPpOcrV3RecEngine.recognizeLine(dst)
        add(OcrAttempt(tc, sc))
    }
}

private fun centerCrop(gray: Mat, cropFraction: Float): Mat {
    val w = gray.cols()
    val h = gray.rows()
    val nw = (w * cropFraction).toInt().coerceIn(4, w)
    val nh = (h * cropFraction).toInt().coerceIn(4, h)
    val x = ((w - nw) / 2).coerceAtLeast(0)
    val y = ((h - nh) / 2).coerceAtLeast(0)
    return Mat(gray, Rect(x, y, nw, nh)).clone()
}

private fun extractVariantNumber(
    rawText: String,
    confidence: Float,
    maxVariantHint: Int?,
): Int? {
    val normalized = rawText
        .replace("О", "0")
        .replace("о", "0")
        .replace("O", "0")
        .replace("I", "1")
        .replace("l", "1")
        .replace("|", "1")
        .replace("!", "1")
        .replace("]", "1")
        .replace("S", "5")
        .replace("s", "5")
        .trim()
    val matches = Regex("\\d{1,2}")
        .findAll(normalized)
        .mapNotNull { it.value.toIntOrNull() }
        .filter { it > 0 }
        .toList()
    if (matches.isEmpty()) return null

    val inRange = if (maxVariantHint != null && maxVariantHint > 0) {
        matches.filter { it in 1..maxVariantHint }
    } else {
        matches
    }
    if (inRange.isEmpty()) return null

    val chosen = inRange.minByOrNull { it.toString().length } ?: inRange.first()

    if (confidence >= MIN_VARIANT_CONFIDENCE) return chosen

    if (confidence >= MIN_VARIANT_CONFIDENCE_WEAK && inRange.distinct().size == 1) {
        return chosen
    }
    return null
}

