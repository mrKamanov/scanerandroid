package com.tscan.scanertestov.feature.batch

/**
 * Описание: чтение бланка v2 (пробные листы с угловыми метками): вариант — по закрашенному кружку той же
 * нейросетью, что и ответы; код ученика — по 8 квадратам (закрашен = 1); поле «Фамилия, имя» — вырезка для OCR.
 * Если меток на фото нет — возвращает null, и работает прежний путь (шестиугольник + OCR).
 */
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchCellClassifier
import com.tscan.scanertestov.feature.batch.engine.BatchGridLocator
import com.tscan.scanertestov.feature.batch.engine.BlankV2Markers
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

internal data class BlankV2Reading(
    val markers: Int,
    /** 1–4 или null, если не закрашен ни один / закрашено несколько. */
    val variant: Int?,
    val variantNote: String,
    /** Номер ученика из кода (0–63) или null: кода нет, не читается или не сошлась проверка. */
    val code: Int?,
    /** Вырезка поля «Фамилия, имя» для OCR; вызывающий освобождает. */
    val nameCrop: Mat,
)

private const val TAG = "BlankV2"

internal fun readBlankV2(
    context: Context,
    sourceBitmap: Bitmap,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
    hidden: Set<String> = emptySet(),
): BlankV2Reading? {
    if (questionCount <= 0 || optionCount <= 0) return null
    if (!OpenCVLoader.initLocal()) return null
    val src = Mat()
    val bgr = Mat()
    try {
        Utils.bitmapToMat(sourceBitmap, src)
        if (src.channels() == 4) Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR) else src.copyTo(bgr)
        val alternatives = com.tscan.scanertestov.data.PrintedBlankParamsStore.load(context)
        val loc = BatchGridLocator.locateAny(bgr, questionCount, optionCount, columnCount, alternatives, hidden) ?: return null
        try {
            if (loc.markers == 0 || loc.score < BatchGridLocator.MIN_SCORE) return null
            val s = BatchGridLocator.SCALE
            // Всё над сеткой режем по угловым меткам: они рядом с кодом и вариантом. По самой сетке на узком
            // бланке (1 колонка) ошибка положения на расстоянии кода сдвигала квадраты и ломала проверку кода.
            val gray0 = Mat()
            Imgproc.cvtColor(bgr, gray0, Imgproc.COLOR_BGR2GRAY)
            val hMarks = BlankV2Markers.bestHomography(BlankV2Markers.find(gray0), loc.geometry.height.toDouble(), 1.0, loc.geometry.width.toDouble())
            gray0.release()
            fun region(l: Double, t: Double, r: Double, b: Double, sc: Double = s): Mat {
                val hm = hMarks ?: return BatchGridLocator.warpRegion(bgr, loc, l, t, r, b, scale = sc)
                val tm = Mat.eye(3, 3, org.opencv.core.CvType.CV_64F)
                tm.put(0, 0, sc); tm.put(1, 1, sc); tm.put(0, 2, -l * sc); tm.put(1, 2, -t * sc)
                val h = Mat()
                Core.gemm(tm, hm, 1.0, Mat(), 0.0, h)
                tm.release()
                val out = Mat()
                Imgproc.warpPerspective(
                    bgr, out, h,
                    org.opencv.core.Size(((r - l) * sc).roundToInt().toDouble(), ((b - t) * sc).roundToInt().toDouble()),
                    Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, org.opencv.core.Scalar(255.0, 255.0, 255.0),
                )
                h.release()
                return out
            }

            val header = readV2Header(context) { l, t, r, b, sc -> region(l, t, r, b, sc) }
            val variant = header.variant
            val note = header.note
            val code = header.code
            val white = header.white
            val inner = header.inner

            // ——— Поле имени: как в старом бланке (210×34, «Фамилия | Имя»), чтобы сработало распознавание по ячейкам.
            val nb = BlankV2Markers.NAME_BOX
            val nameCrop = region(
                nb[0] - GRID_NAME_MARGIN, nb[1] - GRID_NAME_MARGIN, nb[2] + GRID_NAME_MARGIN, nb[3] + GRID_NAME_MARGIN,
                GRID_NAME_SCALE,
            )
            hMarks?.release()
            Log.i(TAG, "markers=${loc.markers} variant=$variant ($note) code=$code white=${white.roundToInt()} inner=${inner.map { it.roundToInt() }}")
            return BlankV2Reading(loc.markers, variant, note, code, nameCrop)
        } finally {
            loc.homography.release()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Чтение бланка v2 не удалось: ${e.message}")
        return null
    } finally {
        src.release()
        bgr.release()
    }
}

internal class V2Header(val variant: Int?, val note: String, val code: Int?, val white: Double, val inner: List<Double>)

/**
 * Вариант (кружки 1–4, нейросетью, как ответы) и код ученика (8 квадратов, порог между бумагой и самым тёмным).
 * [region] вырезает прямоугольник в логических единицах сетки с масштабом sc — по угловым меткам.
 * Общая часть пакетной и живой проверки.
 */
internal fun readV2Header(context: Context, region: (Double, Double, Double, Double, Double) -> Mat): V2Header {
    val s = BatchGridLocator.SCALE
    // ——— Вариант: строка из 4 кружков. Выравниваем яркость по всей строке, режем по шагу 40, как ответы.
    val vc = BlankV2Markers.VARIANT_CENTERS
    val rowL = vc.first().first - 20.0
    val rowT = vc.first().second - 20.0
    val row = region(rowL, rowT, vc.last().first + 20.0, rowT + 40.0, s)
    BatchGridLocator.normalizeContrast(row)
    val cells = vc.mapIndexed { i, (cx, _) ->
        val x = ((cx - 20.0 - rowL) * s).roundToInt().coerceIn(0, row.cols() - 1)
        val w = (40.0 * s).roundToInt().coerceAtMost(row.cols() - x)
        Triple(0, i, Mat(row, Rect(x, 0, w, row.rows())).clone())
    }
    row.release()
    val preds = try { BatchCellClassifier.classifyCells(cells, context) } finally { cells.forEach { it.third.release() } }
    val yes = preds.filter { it.klass == BatchCellClass.Yes }.map { it.choiceIndex + 1 }
    val variant = yes.singleOrNull()
    val note = when {
        yes.isEmpty() -> "вариант не отмечен"
        yes.size > 1 -> "отмечено несколько вариантов: ${yes.joinToString()}"
        else -> "вариант $variant по кружку"
    }

    // ——— Код ученика: 8 квадратов. Белый — бумага вокруг квадратов, чёрный — самый тёмный квадрат.
    val first = BlankV2Markers.codeSquare(0)
    val last = BlankV2Markers.codeSquare(BlankV2Markers.CODE_BITS - 1)
    val cs = 4.0
    val pad = 6.0
    val strip = region(first[0] - pad, first[1] - pad, last[2] + pad, last[3] + pad, cs)
    val gray = Mat()
    Imgproc.cvtColor(strip, gray, Imgproc.COLOR_BGR2GRAY)
    strip.release()
    val inner = ArrayList<Double>()
    val sz = BlankV2Markers.CODE_SIZE
    for (k in 0 until BlankV2Markers.CODE_BITS) {
        val q = BlankV2Markers.codeSquare(k)
        // середина квадрата (60 %), без контура
        val x0 = ((q[0] - (first[0] - pad) + sz * 0.2) * cs).roundToInt().coerceIn(0, gray.cols() - 2)
        val y0 = ((q[1] - (first[1] - pad) + sz * 0.2) * cs).roundToInt().coerceIn(0, gray.rows() - 2)
        val side = (sz * 0.6 * cs).roundToInt()
        val rr = Rect(x0, y0, side.coerceAtMost(gray.cols() - x0), side.coerceAtMost(gray.rows() - y0))
        val sub = gray.submat(rr)
        inner += Core.mean(sub).`val`[0]
        sub.release()
    }
    // Бумага: полоса над квадратами (отступ pad), её медиана.
    val paperRow = gray.submat(Rect(0, 0, gray.cols(), (pad * cs * 0.6).roundToInt().coerceAtLeast(1)))
    val white = Core.mean(paperRow).`val`[0]
    paperRow.release()
    gray.release()
    val black = inner.min()
    val code = if (white - black > 45) {
        val thr = (white + black) / 2
        val bits = inner.fold(0) { acc, v -> (acc shl 1) or (if (v < thr) 1 else 0) }
        BlankV2Markers.decodeCode(bits)
    } else {
        null // все квадраты светлые — кода нет (обычный бланк)
    }
    return V2Header(variant, note, code, white, inner)
}
