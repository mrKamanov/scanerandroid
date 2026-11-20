package com.tscan.scanertestov.feature.live

/**
 * Описание: живая проверка бланка v2 в видеопотоке.
 *
 * Два слоя, чтобы кадры не мешали друг другу:
 *  1. [LiveSheetTracker.track] — в КАЖДОМ кадре, быстро: угловые метки ArUco → где лежит сетка.
 *     По этому результат рисуется прямо на листе и «прилипает» к нему, даже если рука движется.
 *  2. [LiveRecognizer.recognize] — ОДИН раз на лист, когда он замер: полный кадр, сетка по меткам,
 *     кружки — чистым OpenCV или нейросетью (выбирается на экране), вариант и код ученика по меткам.
 *     Одновременно идёт не больше одной проверки — отсюда нет наложений и путаницы.
 */
import android.content.Context
import android.graphics.Bitmap
import com.tscan.scanertestov.data.AppSettingsStore
import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.data.PrintedBlankParamsStore
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.batch.engine.BatchGridLocator
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import com.tscan.scanertestov.feature.batch.engine.BlankV2Markers
import com.tscan.scanertestov.feature.batch.engine.GridGeometry
import com.tscan.scanertestov.feature.tests.blankDesign
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class LiveMethod(val title: String) { OpenCv("OpenCV"), Model("Нейросеть") }

/** Размеры кружков и промежутков — из оформления бланка теста (как его напечатали), а не стандартные. */
internal fun BatchCriteriaPreset.liveRenderParams(): com.tscan.scanertestov.feature.blankeditor.BlankBubbleRenderParams =
    blankDesign().render

/** Убранные с бланка кружки теста. */
internal fun BatchCriteriaPreset.liveHidden(variant: Int? = null): Set<String> = blankDesign().hiddenFor(variant)

private val geometryCache = java.util.concurrent.ConcurrentHashMap<String, GridGeometry>()

/** Геометрия сетки бланка теста (кэш по тесту и его оформлению). */
internal fun BatchCriteriaPreset.liveGeometry(): GridGeometry =
    geometryCache.getOrPut("$id|$questionsCount|$choicesCount|$columnCount|${blankDesignJson.hashCode()}") {
        BatchGridLocator.geometryFor(questionsCount, choicesCount, columnCount, liveRenderParams(), liveHidden())
    }

/** Где сетка в кадре: логические единицы сетки → доли кадра [0..1] (кадр уже повёрнут «как на экране»). */
internal class LiveTrack(
    val gridToNorm: DoubleArray,
    val frameW: Int,
    val frameH: Int,
    /** Номера тёмных точек из списка проб (кружки ответов, варианта, квадраты кода) — «отпечаток» листа. */
    val dark: Set<Int> = emptySet(),
) {
    fun map(x: Double, y: Double): Pair<Double, Double>? {
        val h = gridToNorm
        val w = h[6] * x + h[7] * y + h[8]
        if (w <= 1e-9) return null
        return ((h[0] * x + h[1] * y + h[2]) / w) to ((h[3] * x + h[4] * y + h[5]) / w)
    }
}

internal object LiveSheetTracker {
    /** Высота сетки, при которой сошлись метки в последнем кадре (может отличаться от стандартной). */
    @Volatile var lastGridHeight: Double = 0.0

    /**
     * Яркость в окне 5×5 вокруг каждой пробы (сетка → пиксели кадра по [m], ещё до деления на размер).
     * Тёмная — заметно темнее медианы (большинство кружков пустые, медиана — бумага с кольцом).
     */
    internal fun darkProbes(small: Mat, m: DoubleArray, probes: List<Pair<Double, Double>>): Set<Int> {
        if (probes.isEmpty()) return emptySet()
        val w = small.cols()
        val h = small.rows()
        val px = ByteArray(w * h)
        small.get(0, 0, px)
        val v = DoubleArray(probes.size) { -1.0 }
        probes.forEachIndexed { i, (x, y) ->
            val ww = m[6] * x + m[7] * y + m[8]
            if (ww <= 1e-9) return@forEachIndexed
            val cx = ((m[0] * x + m[1] * y + m[2]) / ww).roundToInt()
            val cy = ((m[3] * x + m[4] * y + m[5]) / ww).roundToInt()
            if (cx < 2 || cy < 2 || cx >= w - 2 || cy >= h - 2) return@forEachIndexed
            var sum = 0
            for (yy in cy - 2..cy + 2) for (xx in cx - 2..cx + 2) sum += px[yy * w + xx].toInt() and 0xFF
            v[i] = sum / 25.0
        }
        val known = v.filter { it >= 0 }.sorted()
        if (known.size < 4) return emptySet()
        val median = known[known.size / 2]
        return v.indices.filter { v[it] >= 0 && v[it] < median * 0.65 }.toSet()
    }

    /** Верхние метки не зависят от высоты сетки: по ним — где на листе нижняя метка, отсюда высота. */
    internal fun estimateGridHeight(found: Map<Int, List<List<org.opencv.core.Point>>>, gridWidth: Double = BlankV2Markers.PILOT_GRID_WIDTH): Double? {
        val m0 = found[0]?.firstOrNull() ?: return null
        val m1 = found[1]?.firstOrNull() ?: return null
        val bottomId = listOf(3, 2).firstOrNull { found[it] != null } ?: return null
        val src = org.opencv.core.MatOfPoint2f(*(m0 + m1).toTypedArray())
        val dst = org.opencv.core.MatOfPoint2f(*(BlankV2Markers.markerCornersLogical(0, 0.0, gridWidth) + BlankV2Markers.markerCornersLogical(1, 0.0, gridWidth)).toTypedArray())
        val hTop = org.opencv.calib3d.Calib3d.findHomography(src, dst)
        src.release(); dst.release()
        if (hTop.empty()) return null
        val pt = org.opencv.core.MatOfPoint2f(found.getValue(bottomId).first()[0])
        val out = org.opencv.core.MatOfPoint2f()
        Core.perspectiveTransform(pt, out, hTop)
        val y = out.toArray()[0].y
        pt.release(); out.release(); hTop.release()
        val gh = y - BlankV2Markers.markerCornersLogical(bottomId, 0.0)[0].y
        return gh.takeIf { it in 100.0..2000.0 }
    }

    /** Длинная сторона кадра для поиска меток: быстрее, а метки 10 мм на листе вблизи видны хорошо. */
    private const val TRACK_SIDE = 960.0

    /**
     * [gray] — яркость кадра в ориентации датчика; [rotation] — на сколько повернуть, чтобы было «как на экране».
     * [gridHeight] — высота сетки в логических единицах (от неё зависит место нижних меток).
     */
    fun track(gray: Mat, rotation: Int, gridHeight: Double, probes: List<Pair<Double, Double>> = emptyList()): LiveTrack? {
        val small = Mat()
        try {
            val k = min(1.0, TRACK_SIDE / max(gray.cols(), gray.rows()))
            if (k < 1.0) Imgproc.resize(gray, small, Size(), k, k, Imgproc.INTER_AREA) else gray.copyTo(small)
            when ((rotation % 360 + 360) % 360) {
                90 -> Core.rotate(small, small, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(small, small, Core.ROTATE_180)
                270 -> Core.rotate(small, small, Core.ROTATE_90_COUNTERCLOCKWISE)
            }
            val found = BlankV2Markers.find(small, subpix = true)
            if (found.size < 3) return null
            var gh = gridHeight
            var h = BlankV2Markers.bestHomography(found, gh, 1.0) // кадр → сетка
            if (h == null) {
                // Сетка другой высоты (свои размеры кружков в тесте): высоту берём по самим меткам.
                gh = estimateGridHeight(found) ?: return null
                h = BlankV2Markers.bestHomography(found, gh, 1.0) ?: return null
            }
            lastGridHeight = gh
            val inv = Mat()
            try {
                Core.invert(h, inv) // сетка → кадр (пиксели уменьшенного кадра)
                val sw = small.cols().toDouble()
                val sh = small.rows().toDouble()
                val m = DoubleArray(9)
                inv.get(0, 0, m)
                val dark = darkProbes(small, m, probes)
                // Делим строки x и y на размер кадра — получаем доли кадра.
                for (i in 0..2) { m[i] /= sw; m[3 + i] /= sh }
                val upright = (rotation % 180) != 0
                val fw = if (upright) gray.rows() else gray.cols()
                val fh = if (upright) gray.cols() else gray.rows()
                return LiveTrack(m, fw, fh, dark)
            } finally {
                h.release(); inv.release()
            }
        } finally {
            small.release()
        }
    }
}

internal data class LiveResult(
    val method: LiveMethod,
    val omr: BatchOmrResult?,
    val geometry: GridGeometry?,
    val variant: Int?,
    val code: Int?,
    val answerKey: List<Set<Int>>,
    val correct: Int,
    val total: Int,
    val percent: Float,
    val grade: Int,
    val ms: Long,
    /** Для теста с вариантами вариант не прочитан — проверено по ключу варианта 1. */
    val variantGuessed: Boolean = false,
    val error: String? = null,
    val config: BatchOmrConfig? = null,
    val criteria: GradingCriteriaConfig? = null,
    /** Сколько проходов сведено в этот результат и совпали ли два последних. */
    val passes: Int = 1,
    val confirmed: Boolean = false,
    /** Время по шагам — для журнала. */
    val stages: String = "",
    /** Поправка «метки → сетка», найденная на этом кадре (см. [com.tscan.scanertestov.feature.batch.engine.GridLocation.markerCorrection]). */
    val markerCorrection: DoubleArray? = null,
    /** Ученик по коду с именного бланка: «Онегин Е., 7 А» (null — код не выдавался или не прочитан). */
    val student: String? = null,
)

/** Отмеченные кружки — для сравнения проходов. */
internal fun LiveResult.marks(): Set<Pair<Int, Int>> =
    omr?.predictions?.filter { it.klass != com.tscan.scanertestov.feature.batch.engine.BatchCellClass.No }
        ?.map { it.questionIndex to it.choiceIndex }?.toSet().orEmpty()

internal object LiveRecognizer {

    fun recognize(context: Context, frame: Bitmap, preset: BatchCriteriaPreset, method: LiveMethod, correction: DoubleArray? = null): LiveResult {
        val t0 = System.nanoTime()
        val src = Mat()
        val bgr = Mat()
        fun fail(msg: String) = LiveResult(
            method, null, null, null, null, emptyList(), 0, preset.questionsCount, 0f, 0,
            (System.nanoTime() - t0) / 1_000_000, error = msg,
        )
        var tLap = t0
        val st = StringBuilder()
        fun lap(name: String) {
            val now = System.nanoTime()
            st.append(name).append('=').append((now - tLap) / 1_000_000).append(' ')
            tLap = now
        }
        val gray = Mat()
        try {
            Utils.bitmapToMat(frame, src)
            if (src.channels() == 4) Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR) else src.copyTo(bgr)
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            lap("кадр")
            // Метки на полном кадре ищем один раз: и для сетки, и для варианта с кодом.
            val markers = BlankV2Markers.find(gray)
            lap("метки")
            val q = preset.questionsCount
            val c = preset.choicesCount
            val cols = preset.columnCount
            val alternatives = (listOf(preset.liveRenderParams()) + PrintedBlankParamsStore.load(context)).distinct()
            // Только по угловым меткам. Медленный общий поиск в живом режиме не берём: на смазанном кадре
            // он может «найти» сетку соседнего листа. Не вышло — просто следующий кадр.
            val loc = BatchGridLocator.locateByMarkers(bgr, q, c, cols, alternatives, markers, correction, preset.liveHidden())
                ?: return fail(
                    if (markers.size < 3) "Не видно угловых меток (кадр смазан или бланк не целиком) — проверю следующий кадр."
                    else "Сетка не совпала с тестом «${preset.name}». Проверьте, что выбран тест этого бланка.",
                )
            lap("сетка")
            try {
                val (variant, code) = readHeader(context, bgr, markers, loc.geometry.height.toDouble(), loc.geometry.width.toDouble())
                lap("вариант+код")
                val keys = preset.variantAnswerKeys
                val rawKey = when {
                    keys.isEmpty() -> preset.correctAnswers
                    variant != null -> keys.firstOrNull { it.variant == variant }?.correctAnswers ?: keys.first().correctAnswers
                    else -> keys.first().correctAnswers
                }
                val key = List(q) { i -> rawKey.getOrNull(i).orEmpty().filter { it in 0 until c }.toSet() }
                val config = BatchOmrConfig(
                    questionsCount = q,
                    choicesCount = c,
                    columnCount = cols,
                    answerKey = key,
                    strictScoring = true,
                    hiddenBubbleIds = preset.liveHidden(variant),
                )
                val omr = BatchOmrEngine.classifyLocated(context, bgr, config, loc, useModel = method == LiveMethod.Model)
                lap("кружки")
                val scores = omr.questionScores
                val points = scores.sumOf { it.score.toDouble() }
                val percent = if (scores.isEmpty()) 0f else (points / scores.size * 100).toFloat()
                val criteria = preset.gradingCriteriaJson?.let { GradingCriteriaConfig.fromPrefsString(it) }
                    ?: AppSettingsStore.getGradingCriteria(context)
                return LiveResult(
                    method = method,
                    omr = omr,
                    geometry = loc.geometry,
                    variant = variant,
                    code = code,
                    student = com.tscan.scanertestov.feature.tests.StudentCodeStore.lookup(context, preset.id, code)?.let { s ->
                        listOf(s.surname, s.name.take(1).let { if (it.isEmpty()) "" else "$it." }).filter { it.isNotBlank() }
                            .joinToString(" ") + ", " + s.className
                    },
                    answerKey = key,
                    correct = scores.count { it.correct },
                    total = q,
                    percent = percent,
                    grade = criteria.resolveGrade(percent, points),
                    ms = (System.nanoTime() - t0) / 1_000_000,
                    variantGuessed = keys.size > 1 && variant == null,
                    config = config,
                    criteria = criteria,
                    stages = st.toString().trim(),
                    markerCorrection = loc.markerCorrection,
                )
            } finally {
                loc.homography.release()
            }
        } catch (e: Throwable) {
            return fail("Не удалось проверить: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            src.release(); bgr.release(); gray.release()
        }
    }

    /**
     * Несколько проходов одного листа → по каждому кружку большинство голосов (при равенстве — последний).
     * Случайный смазанный кадр не портит итог. Берутся проходы с тем же вариантом, что и последний.
     */
    fun merge(history: List<LiveResult>): LiveResult {
        val last = history.last()
        val same = history.filter { it.error == null && it.omr != null && it.variant == last.variant }.takeLast(3)
        val config = last.config
        val criteria = last.criteria
        val omr = last.omr
        if (same.size < 2 || config == null || criteria == null || omr == null) return last.copy(passes = same.size.coerceAtLeast(1))
        val votes = HashMap<Pair<Int, Int>, MutableList<com.tscan.scanertestov.feature.batch.engine.BatchCellClass>>()
        same.forEach { r -> r.omr!!.predictions.forEach { votes.getOrPut(it.questionIndex to it.choiceIndex) { mutableListOf() } += it.klass } }
        val predictions = omr.predictions.map { p ->
            val v = votes[p.questionIndex to p.choiceIndex].orEmpty()
            val best = v.groupingBy { it }.eachCount().let { counts ->
                val top = counts.maxOf { it.value }
                if (counts.getValue(p.klass) == top) p.klass else counts.entries.first { it.value == top }.key
            }
            if (best == p.klass) p else p.copy(klass = best)
        }
        val scores = com.tscan.scanertestov.feature.batch.engine.BatchScoringEngine.score(predictions, config)
        val points = scores.sumOf { it.score.toDouble() }
        val percent = if (scores.isEmpty()) 0f else (points / scores.size * 100).toFloat()
        val prev = history.filter { it.error == null && it.omr != null }.dropLast(1).lastOrNull()
        return last.copy(
            omr = omr.copy(predictions = predictions, questionScores = scores, fixedCells = predictions.filter { it.klass == com.tscan.scanertestov.feature.batch.engine.BatchCellClass.Fixed }),
            correct = scores.count { it.correct },
            percent = percent,
            grade = criteria.resolveGrade(percent, points),
            passes = same.size,
            confirmed = prev != null && prev.marks() == last.marks(),
        )
    }

    /**
     * Вариант и код ученика — тем же способом, что в пакетной проверке ([readV2Header]: вариант нейросетью
     * по 4 кружкам, код — порогом между бумагой и самым тёмным квадратом), вырезки — по угловым меткам.
     */
    private fun readHeader(context: Context, bgr: Mat, markers: Map<Int, List<List<org.opencv.core.Point>>>, gridHeight: Double, gridWidth: Double): Pair<Int?, Int?> {
        try {
            val hm = BlankV2Markers.bestHomography(markers, gridHeight, 1.0, gridWidth) ?: return null to null
            try {
                val h = com.tscan.scanertestov.feature.batch.readV2Header(context) { l, t, r, b, sc ->
                    val tm = Mat.eye(3, 3, CvType.CV_64F)
                    tm.put(0, 0, sc); tm.put(1, 1, sc); tm.put(0, 2, -l * sc); tm.put(1, 2, -t * sc)
                    val m = Mat()
                    Core.gemm(tm, hm, 1.0, Mat(), 0.0, m)
                    tm.release()
                    val out = Mat()
                    Imgproc.warpPerspective(
                        bgr, out, m, Size(((r - l) * sc).roundToInt().toDouble(), ((b - t) * sc).roundToInt().toDouble()),
                        Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0, 255.0, 255.0),
                    )
                    m.release()
                    out
                }
                return h.variant to h.code
            } finally {
                hm.release()
            }
        } catch (e: Throwable) {
            return null to null
        }
    }
}
