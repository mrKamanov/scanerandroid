package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: поиск сетки бланка на фото по её устройству из конструктора.
 *
 * 1. Кандидаты — четырёхугольники из ВСЕХ контуров адаптивно бинаризованного кадра
 *    (раньше брались только внешние, и рамка внутри листа не находилась).
 * 2. Каждый кандидат примеряется как внешний или внутренний край рамки, кадр выпрямляется
 *    в «канон» (логические единицы × [SCALE]) и проверяется по решётке кружков: у скольких
 *    кружков кольцо темнее фона. Это же — уверенность «это наш бланк» (фото тетради даёт ~0.1).
 * 3. Лучший кандидат уточняется ECC-подгонкой гомографии к отрисованному шаблону.
 * 4. Ориентация: над левым верхним углом сетки напечатаны шестиугольник варианта и поле имени.
 *    Ищем их шаблоном в обеих ориентациях — так распознаётся перевёрнутый на 180° лист.
 */
import android.util.Log
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleDefaults
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleLayout
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleRenderParams
import com.tscan.scanertestov.feature.blankeditor.BlankEditorLayoutConfig
import com.tscan.scanertestov.feature.blankeditor.blankBubbleLayoutBoundingRect
import com.tscan.scanertestov.feature.blankeditor.buildBlankBubbleLayout
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Кружок в каноне (логические единицы относительно левого верхнего угла всей сетки). */
internal data class GridBubble(val question: Int, val choice: Int, val cx: Float, val cy: Float, val placeholder: Boolean)

internal data class GridGeometry(
    val width: Float,
    val height: Float,
    val frames: List<FloatArray>, // [left, top, right, bottom] каждой рамки
    val bubbles: List<GridBubble>,
    val pitchX: Float,
    val pitchY: Float,
    val bubbleSize: Float,
    val borderWidth: Float,
)

internal data class GridLocation(
    /** Гомография: исходное изображение → канон в пикселях (× [BatchGridLocator.SCALE]). */
    val homography: Mat,
    val score: Float,
    val flipped: Boolean,
    val orientationConfidence: Float,
    val geometry: GridGeometry,
    /** Сколько угловых меток бланка v2 найдено (0 — старый бланк или метки не видны). */
    val markers: Int = 0,
    /**
     * Поправка «метки → сетка» этого бланка (3×3 по строкам): истинные логические координаты сетки →
     * координаты, как их даёт расчёт по меткам. На напечатанных листах сетка стоит относительно меток
     * чуть иначе, чем в программе; поправка постоянна для бланков одной печати. null — не вычислена.
     */
    val markerCorrection: DoubleArray? = null,
)

internal object BatchGridLocator {
    private const val TAG = "BatchGridLocator"
    private const val PERF = "BatchGridPerf"
    const val SCALE = 3.0
    private const val WORK_SIDE = 1000.0
    private const val FAST_WORK_SIDE = 640.0
    private const val SCORE_SCALE = 1.25
    private const val ECC_SCALE = 1.5
    const val MIN_SCORE = 0.6f
    private const val MARGIN = 115.0 // логических единиц вокруг сетки для поиска шапки
    private const val STROKE = BlankBubbleDefaults.BUBBLE_STROKE_PX

    fun geometryFor(
        questions: Int,
        choices: Int,
        columns: Int,
        params: BlankBubbleRenderParams = BlankBubbleRenderParams.defaults(),
        /** Убранные с бланка кружки — их на бумаге нет, в сетку не входят. */
        hidden: Set<String> = emptySet(),
    ): GridGeometry {
        val layout: BlankBubbleLayout = buildBlankBubbleLayout(
            BlankEditorLayoutConfig(questionCount = questions, optionCount = choices, columnCount = columns),
            params,
        )
        val b = blankBubbleLayoutBoundingRect(layout)
        val rp = layout.renderParams
        val frames = layout.frames.map { f ->
            floatArrayOf(f.left - b.left, f.top - b.top, f.left - b.left + f.width, f.top - b.top + f.height)
        }
        val bubbles = layout.bubbles.filter { it.id !in hidden }.map { bb ->
            val label = bb.label
            val (q, c) = if (label != null) {
                val parts = label.split('.')
                (parts[0].toInt() - 1) to (parts[1].toInt() - 1)
            } else -1 to -1
            GridBubble(q, c, bb.centerX - b.left, bb.centerY - b.top, bb.isEmptyPlaceholder)
        }
        return GridGeometry(
            width = b.right - b.left,
            height = b.bottom - b.top,
            frames = frames,
            bubbles = bubbles,
            pitchX = rp.bubbleSizePx + rp.cellGapHorizontalPx,
            pitchY = rp.bubbleSizePx + rp.cellGapVerticalPx,
            bubbleSize = rp.bubbleSizePx,
            borderWidth = rp.borderWidthPx,
        )
    }

    /** Лучший кандидат-четырёхугольник: гомография «кадр → канон × ss» и доля узнанных кружков. */
    private fun bestCandidate(gray: Mat, geo: GridGeometry, ss: Double, canon: Mat): Pair<Mat, Float>? {
        val sw = (geo.width * ss).roundToInt()
        val sh = (geo.height * ss).roundToInt()
        var best: Pair<Mat, Float>? = null
        for (q in candidateQuads(gray)) {
            for (f in geo.frames) {
                val fw = f[2] - f[0]
                val fh = f[3] - f[1]
                val e1 = dist(q[0], q[1]) + dist(q[3], q[2])
                val e2 = dist(q[0], q[3]) + dist(q[1], q[2])
                val ratio = max(e1, e2) / max(min(e1, e2), 1.0)
                val expected = max(fw, fh) / min(fw, fh)
                if (ratio < expected * 0.6 || ratio > expected * 1.6) continue
                // Длинная сторона кандидата должна лечь на длинную сторону рамки.
                val frameTall = fh >= fw
                val quadTall = e2 >= e1
                val qq = if (frameTall == quadTall) q else arrayOf(q[1], q[2], q[3], q[0])
                for (inner in booleanArrayOf(false, true)) {
                    val inset = if (inner) geo.borderWidth else 0f
                    val dst = arrayOf(
                        Point((f[0] + inset) * ss, (f[1] + inset) * ss),
                        Point((f[2] - inset) * ss, (f[1] + inset) * ss),
                        Point((f[2] - inset) * ss, (f[3] - inset) * ss),
                        Point((f[0] + inset) * ss, (f[3] - inset) * ss),
                    )
                    val h = perspective(qq, dst)
                    Imgproc.warpPerspective(gray, canon, h, Size(sw.toDouble(), sh.toDouble()))
                    val sc = latticeScore(canon, geo, ss)
                    val cur = best
                    if (cur == null || sc > cur.second) {
                        cur?.first?.release()
                        best = h to sc
                    } else {
                        h.release()
                    }
                }
            }
        }
        return best
    }

    /**
     * Быстрый поиск для живого превью камеры: без точной подгонки и ориентации.
     * Возвращает 4 угла сетки в координатах кадра (по часовой от левого верхнего) и уверенность.
     */
    fun locateFast(
        image: Mat,
        questions: Int,
        choices: Int,
        columns: Int,
        params: BlankBubbleRenderParams = BlankBubbleRenderParams.defaults(),
        hidden: Set<String> = emptySet(),
    ): Pair<Array<Point>, Float>? {
        val geo = geometryFor(questions, choices, columns, params, hidden)
        val grayFull = Mat()
        val gray = Mat()
        val canon = Mat()
        try {
            toGray(image, grayFull)
            val k = min(1.0, FAST_WORK_SIDE / max(grayFull.cols(), grayFull.rows()))
            if (k < 1.0) Imgproc.resize(grayFull, gray, Size(), k, k, Imgproc.INTER_AREA) else grayFull.copyTo(gray)
            val ss = 1.0
            val best = bestCandidate(gray, geo, ss, canon) ?: return null
            try {
                val inv = Mat()
                Core.invert(best.first, inv)
                val src = MatOfPoint2f(
                    Point(0.0, 0.0), Point(geo.width * ss, 0.0),
                    Point(geo.width * ss, geo.height * ss), Point(0.0, geo.height * ss),
                )
                val dst = MatOfPoint2f()
                Core.perspectiveTransform(src, dst, inv)
                val pts = dst.toArray().map { Point(it.x / k, it.y / k) }.toTypedArray()
                inv.release(); src.release(); dst.release()
                return pts to best.second
            } finally {
                best.first.release()
            }
        } finally {
            grayFull.release(); gray.release(); canon.release()
        }
    }

    private fun toGray(image: Mat, out: Mat) {
        when (image.channels()) {
            4 -> Imgproc.cvtColor(image, out, Imgproc.COLOR_RGBA2GRAY)
            3 -> Imgproc.cvtColor(image, out, Imgproc.COLOR_BGR2GRAY)
            else -> image.copyTo(out)
        }
    }

    /**
     * Сначала стандартная сетка, затем параметры недавно напечатанных из конструктора бланков.
     * Возвращает первую уверенно найденную сетку или лучшую из неуверенных.
     */
    fun locateAny(
        bgr: Mat,
        questions: Int,
        choices: Int,
        columns: Int,
        alternatives: List<BlankBubbleRenderParams>,
        hidden: Set<String> = emptySet(),
    ): GridLocation? {
        var best: GridLocation? = null
        for (params in listOf(BlankBubbleRenderParams.defaults()) + alternatives) {
            val loc = locate(bgr, questions, choices, columns, params, hidden) ?: continue
            if (loc.score >= MIN_SCORE) {
                best?.homography?.release()
                return loc
            }
            if (best == null || loc.score > best.score) {
                best?.homography?.release()
                best = loc
            } else {
                loc.homography.release()
            }
        }
        return best
    }

    fun locate(
        bgr: Mat,
        questions: Int,
        choices: Int,
        columns: Int,
        params: BlankBubbleRenderParams = BlankBubbleRenderParams.defaults(),
        hidden: Set<String> = emptySet(),
    ): GridLocation? {
        val geo = geometryFor(questions, choices, columns, params, hidden)
        val grayFull = Mat()
        val gray = Mat()
        try {
            toGray(bgr, grayFull)
            val k = min(1.0, WORK_SIDE / max(grayFull.cols(), grayFull.rows()))
            if (k < 1.0) Imgproc.resize(grayFull, gray, Size(), k, k, Imgproc.INTER_AREA) else grayFull.copyTo(gray)

            // Кандидатов много — проверяем их на маленьком варпе; точную подгонку делаем на среднем.
            val ss = SCORE_SCALE
            val sw = (geo.width * ss).roundToInt()
            val sh = (geo.height * ss).roundToInt()
            val canon = Mat()
            try {
                val t0 = System.nanoTime()
                // Бланк v2: угловые метки дают положение и ориентацию сразу, даже при закрытой рамке.
                val markers = BlankV2Markers.find(grayFull)
                var best = bestCandidate(gray, geo, ss, canon)
                var byMarkers = false
                // Если в кадре метки соседнего бланка, перебираем сочетания: верное то, где совпали кружки.
                for (combo in BlankV2Markers.combinations(markers)) {
                    val hm = BlankV2Markers.homography(combo, geo.height.toDouble(), ss, geo.width.toDouble()) ?: continue
                    val kInv = scaleMat(1.0 / k)
                    val hWork = mul(hm, kInv)
                    kInv.release(); hm.release()
                    Imgproc.warpPerspective(gray, canon, hWork, Size(sw.toDouble(), sh.toDouble()))
                    val sc = latticeScore(canon, geo, ss)
                    val cur = best
                    if (cur == null || sc >= cur.second - (if (byMarkers) 0f else 0.02f)) {
                        cur?.first?.release()
                        best = hWork to sc
                        byMarkers = true
                    } else {
                        hWork.release()
                    }
                }
                val tCand = System.nanoTime()
                val found = best ?: return null
                if (found.second < 0.1f) { // грубая оценка до подгонки бывает низкой (0.2–0.3) на снимках под углом
                    found.first.release()
                    return null
                }
                // ECC на среднем масштабе.
                val es = ECC_SCALE
                val ew = (geo.width * es).roundToInt()
                val eh = (geo.height * es).roundToInt()
                val toEcc = scaleMat(es / ss)
                val hEcc0 = mul(toEcc, found.first)
                toEcc.release(); found.first.release()
                val template = renderTemplate(geo, ew, eh, es)
                val hEcc = try { refineEcc(gray, hEcc0, template, ew, eh) } finally { template.release() }
                // Итоговая оценка — снова на маленьком варпе.
                val toScore = scaleMat(ss / es)
                val hScore = mul(toScore, hEcc)
                Imgproc.warpPerspective(gray, canon, hScore, Size(sw.toDouble(), sh.toDouble()))
                val score = latticeScore(canon, geo, ss)
                toScore.release(); hScore.release()
                // В координаты исходного кадра и масштаб канона SCALE.
                val toCanon = scaleMat(SCALE / es)
                val kM = scaleMat(k)
                val tmp = mul(toCanon, hEcc)
                val hFull = mul(tmp, kM)
                tmp.release(); toCanon.release(); kM.release(); hEcc.release()
                val tEcc = System.nanoTime()
                val (flipped, conf) = when {
                    byMarkers -> false to 1f
                    else -> BlankV2Markers.flippedByMarkers(markers, hFull, geo.width.toDouble(), geo.height.toDouble(), SCALE)
                        ?.let { it to 1f }
                        ?: decideOrientation(grayFull, hFull, geo)
                }
                Log.i(PERF, "locate cand=${(tCand - t0) / 1_000_000} ecc=${(tEcc - tCand) / 1_000_000} orient=${(System.nanoTime() - tEcc) / 1_000_000} ms")
                Log.i(TAG, "grid score=${"%.2f".format(score)} first=${"%.2f".format(found.second)} flipped=$flipped conf=${"%.2f".format(conf)} markers=${markers.size} byMarkers=$byMarkers")
                return GridLocation(hFull, score, flipped, conf, geo, markers.size)
            } finally {
                canon.release()
            }
        } finally {
            grayFull.release()
            gray.release()
        }
    }

    /**
     * Быстрый путь для живой проверки (бланк v2): сетка — сразу по угловым меткам, без перебора
     * четырёхугольников и ECC. Десятки миллисекунд вместо 0,5–0,8 с. Небольшую неточность снимает
     * [refineBubbleCenters] при классификации. null — меток меньше 3 или кружки не совпали
     * (тогда вызывающий берёт обычный [locateAny]).
     */
    fun locateByMarkers(
        bgr: Mat,
        questions: Int,
        choices: Int,
        columns: Int,
        alternatives: List<BlankBubbleRenderParams>,
        /** Уже найденные на этом кадре метки (чтобы не искать второй раз); null — найти здесь. */
        knownMarkers: Map<Int, List<List<Point>>>? = null,
        /** Известная поправка «метки → сетка» ([GridLocation.markerCorrection]) — тогда подгонка обычно не нужна. */
        correction: DoubleArray? = null,
        hidden: Set<String> = emptySet(),
    ): GridLocation? {
        if (correction != null) {
            locateByMarkersOnce(bgr, questions, choices, columns, alternatives, knownMarkers, correction, hidden)?.let { return it }
            // Поправка не подошла (сохранилась неудачная или бланк другой печати) — без неё, с подгонкой.
            Log.i(PERF, "locateByMarkers: с поправкой не сошлось — без поправки")
        }
        return locateByMarkersOnce(bgr, questions, choices, columns, alternatives, knownMarkers, null, hidden)
    }

    private fun locateByMarkersOnce(
        bgr: Mat,
        questions: Int,
        choices: Int,
        columns: Int,
        alternatives: List<BlankBubbleRenderParams>,
        knownMarkers: Map<Int, List<List<Point>>>?,
        correction: DoubleArray?,
        hidden: Set<String>,
    ): GridLocation? {
        val grayFull = Mat()
        val gray = Mat()
        val canon = Mat()
        class Cand(val hWork: Mat, val hMarks: Mat, val score: Float, val geo: GridGeometry)
        var best: Cand? = null
        try {
            val t0 = System.nanoTime()
            toGray(bgr, grayFull)
            val markers = knownMarkers ?: BlankV2Markers.find(grayFull)
            if (markers.size < 3) return null
            // Как в [locate]: оценка — на аккуратно уменьшенном кадре (INTER_AREA), иначе кольца кружков
            // «рассыпаются» при варпе с полного кадра и оценка занижается.
            val k = min(1.0, WORK_SIDE / max(grayFull.cols(), grayFull.rows()))
            if (k < 1.0) Imgproc.resize(grayFull, gray, Size(), k, k, Imgproc.INTER_AREA) else grayFull.copyTo(gray)
            val ss = SCORE_SCALE
            val dInv = correction?.let { c ->
                Mat(3, 3, CvType.CV_64F).also { it.put(0, 0, *c) }.let { d -> Mat().also { Core.invert(d, it); d.release() } }
            }
            // Сначала переданные параметры (бланк теста, недавно напечатанные), потом стандартные.
            for (params in (alternatives + BlankBubbleRenderParams.defaults()).distinct()) {
                val geo = geometryFor(questions, choices, columns, params, hidden)
                val hm1 = BlankV2Markers.bestHomography(markers, geo.height.toDouble(), 1.0, geo.width.toDouble()) ?: continue // кадр → сетка по меткам
                val hTrue = if (dInv != null) mul(dInv, hm1) else hm1.clone()
                val sS = scaleMat(ss)
                val kInv = scaleMat(1.0 / k)
                val tmp = mul(sS, hTrue)
                val hWork = mul(tmp, kInv)
                sS.release(); kInv.release(); tmp.release(); hTrue.release()
                Imgproc.warpPerspective(gray, canon, hWork, Size((geo.width * ss).roundToInt().toDouble(), (geo.height * ss).roundToInt().toDouble()))
                val score = latticeScore(canon, geo, ss)
                val cur = best
                if (cur == null || score > cur.score) {
                    cur?.hWork?.release(); cur?.hMarks?.release()
                    best = Cand(hWork, hm1, score, geo)
                } else {
                    hWork.release(); hm1.release()
                }
                if (score >= MIN_SCORE) break
            }
            dInv?.release()
            val b = best ?: return null
            val geo = b.geo
            var score = b.score
            val hFull: Mat
            var refined = false
            if (score >= MIN_SCORE) {
                // Уверенно и без подгонки: в координаты полного кадра и масштаб канона SCALE.
                val toCanon = scaleMat(SCALE / ss)
                val kM = scaleMat(k)
                val tmp = mul(toCanon, b.hWork)
                hFull = mul(tmp, kM)
                tmp.release(); toCanon.release(); kM.release()
            } else if (score >= 0.03f && correction == null) {
                // Снимок под углом, лист изогнут или поправка ещё не известна: подгонка по шаблону сетки (ECC)
                // от положения по меткам — даже при низкой первой оценке это хорошее начальное приближение.
                refined = true
                val es = ECC_SCALE
                val ew = (geo.width * es).roundToInt()
                val eh = (geo.height * es).roundToInt()
                val toEcc = scaleMat(es / ss)
                val hEcc0 = mul(toEcc, b.hWork)
                toEcc.release()
                val template = renderTemplate(geo, ew, eh, es)
                val hEcc = try { refineEcc(gray, hEcc0, template, ew, eh) } finally { template.release() }
                val toScore = scaleMat(ss / es)
                val hScore = mul(toScore, hEcc)
                Imgproc.warpPerspective(gray, canon, hScore, Size((geo.width * ss).roundToInt().toDouble(), (geo.height * ss).roundToInt().toDouble()))
                score = latticeScore(canon, geo, ss)
                toScore.release(); hScore.release()
                val toCanon = scaleMat(SCALE / es)
                val kM = scaleMat(k)
                val tmp = mul(toCanon, hEcc)
                hFull = mul(tmp, kM)
                tmp.release(); toCanon.release(); kM.release(); hEcc.release()
            } else {
                Log.i(PERF, "locateByMarkers: кружки не совпали (score=${"%.2f".format(b.score)})")
                return null
            }
            Log.i(PERF, "locateByMarkers: score ${"%.2f".format(b.score)}${if (refined) " → ECC ${"%.2f".format(score)}" else ""} за ${(System.nanoTime() - t0) / 1_000_000} мс${if (correction != null) " (с поправкой)" else ""}")
            if (score < MIN_SCORE) {
                hFull.release()
                return null
            }
            // Поправка D = (кадр → сетка по меткам) · (сетка → кадр по найденной сетке): истинная сетка → «сетка по меткам».
            val inv = Mat()
            Core.invert(hFull, inv)
            val sc = scaleMat(SCALE)
            val t1 = mul(b.hMarks, inv)
            val d = mul(t1, sc)
            val dv = DoubleArray(9)
            d.get(0, 0, dv)
            val n = dv[8]
            if (kotlin.math.abs(n) > 1e-12) for (i in 0..8) dv[i] /= n
            inv.release(); sc.release(); t1.release(); d.release()
            return GridLocation(hFull, score, flipped = false, orientationConfidence = 1f, geometry = geo, markers = markers.size, markerCorrection = dv)
        } finally {
            best?.hWork?.release(); best?.hMarks?.release()
            grayFull.release(); gray.release(); canon.release()
        }
    }

    private fun scaleMat(f: Double): Mat = Mat.eye(3, 3, CvType.CV_64F).apply { put(0, 0, f); put(1, 1, f) }

    private fun mul(a: Mat, b: Mat): Mat {
        val out = Mat()
        Core.gemm(a, b, 1.0, Mat(), 0.0, out)
        return out
    }

    /** Выпрямленный цветной канон с учётом ориентации. */
    fun warpCanon(bgr: Mat, loc: GridLocation): Mat {
        val tw = (loc.geometry.width * SCALE).roundToInt()
        val th = (loc.geometry.height * SCALE).roundToInt()
        val out = Mat()
        Imgproc.warpPerspective(bgr, out, loc.homography, Size(tw.toDouble(), th.toDouble()), Imgproc.INTER_LINEAR)
        if (loc.flipped) Core.rotate(out, out, Core.ROTATE_180)
        return out
    }

    /**
     * Вырезает из исходного кадра прямоугольник, заданный в логических координатах относительно
     * сетки (может выходить за неё — например, шестиугольник варианта или поле имени).
     */
    fun warpRegion(bgr: Mat, loc: GridLocation, left: Double, top: Double, right: Double, bottom: Double, scale: Double = SCALE): Mat {
        val g = loc.geometry
        // В каноне (неперевёрнутом) логическая точка p соответствует: flipped ? (W-p) : p.
        val sx = scale / SCALE
        val t = Mat.eye(3, 3, CvType.CV_64F)
        if (!loc.flipped) {
            t.put(0, 0, sx); t.put(1, 1, sx)
            t.put(0, 2, -left * scale); t.put(1, 2, -top * scale)
        } else {
            // x' = (W - x)·scale - left·scale  ⇒  x' = -sx·X + (W·scale - left·scale), где X — пиксели канона
            t.put(0, 0, -sx); t.put(1, 1, -sx)
            t.put(0, 2, (g.width - left) * scale); t.put(1, 2, (g.height - top) * scale)
        }
        val h = Mat()
        Core.gemm(t, loc.homography, 1.0, Mat(), 0.0, h)
        t.release()
        val out = Mat()
        val w = ((right - left) * scale).roundToInt().coerceAtLeast(1)
        val hh = ((bottom - top) * scale).roundToInt().coerceAtLeast(1)
        Imgproc.warpPerspective(bgr, out, h, Size(w.toDouble(), hh.toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0, 255.0, 255.0))
        h.release()
        return out
    }

    /**
     * Растягивает яркость канона: бумага → белая, чернила → чёрные. Слабые и размытые
     * (снятые издалека) отметки становятся заметнее для нейросети.
     */
    fun normalizeContrast(canonBgr: Mat) {
        val gray = Mat()
        Imgproc.cvtColor(canonBgr, gray, Imgproc.COLOR_BGR2GRAY)
        val hist = IntArray(256)
        val px = ByteArray(gray.rows() * gray.cols())
        gray.get(0, 0, px)
        gray.release()
        for (v in px) hist[v.toInt() and 0xFF]++
        fun pct(p: Double): Int {
            val target = (px.size * p).toLong()
            var acc = 0L
            for (i in 0 until 256) { acc += hist[i]; if (acc >= target) return i }
            return 255
        }
        val white = pct(0.92)
        val black = pct(0.03)
        if (white - black <= 20) return
        val alpha = 255.0 / (white - black)
        canonBgr.convertTo(canonBgr, -1, alpha, -black * alpha)
    }

    /**
     * Уточняет центры кружков на изогнутом листе: каждый ищется по кольцу в окрестности,
     * затем по всем найденным строится гладкая (квадратичная) поправка — выбросы отбрасываются.
     * Возвращает центры в логических единицах в порядке [GridGeometry.bubbles].
     */
    fun refineBubbleCenters(canonBgr: Mat, geo: GridGeometry): List<Pair<Float, Float>> {
        val base = geo.bubbles.map { it.cx to it.cy }
        val s = SCALE
        val gray = Mat()
        val inv = Mat()
        val tm = Mat()
        try {
            Imgproc.cvtColor(canonBgr, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(0.0, 0.0), 1.0)
            Core.bitwise_not(gray, inv)
            val pad = 4.0
            val n = (2 * (geo.bubbleSize / 2 + pad) * s).toInt() or 1
            tm.create(n, n, CvType.CV_8UC1); tm.setTo(Scalar(0.0))
            Imgproc.circle(tm, Point(n / 2.0, n / 2.0), ((geo.bubbleSize / 2 - STROKE / 2) * s).roundToInt(), Scalar(255.0), max(1, (STROKE * s).roundToInt()), Imgproc.LINE_AA)
            val win = (12.0 * s).toInt()
            val px = ArrayList<Double>(); val py = ArrayList<Double>()
            val ox = ArrayList<Double>(); val oy = ArrayList<Double>()
            val res = Mat()
            for ((cx, cy) in base) {
                val x0 = (cx * s - n / 2.0 - win).toInt(); val y0 = (cy * s - n / 2.0 - win).toInt()
                val x1 = x0 + n + 2 * win; val y1 = y0 + n + 2 * win
                if (x0 < 0 || y0 < 0 || x1 > inv.cols() || y1 > inv.rows()) continue
                val roi = inv.submat(y0, y1, x0, x1)
                Imgproc.matchTemplate(roi, tm, res, Imgproc.TM_CCOEFF_NORMED)
                val mm = Core.minMaxLoc(res)
                roi.release()
                if (mm.maxVal < 0.35) continue
                val nx = (x0 + mm.maxLoc.x + n / 2.0) / s
                val ny = (y0 + mm.maxLoc.y + n / 2.0) / s
                px += cx.toDouble(); py += cy.toDouble(); ox += nx - cx; oy += ny - cy
            }
            res.release()
            if (px.size < 12) return base
            var keep = BooleanArray(px.size) { true }
            var cxCoef = DoubleArray(6); var cyCoef = DoubleArray(6)
            val resid = DoubleArray(px.size)
            repeat(3) {
                cxCoef = fitPoly2(px, py, ox, keep) ?: return base
                cyCoef = fitPoly2(px, py, oy, keep) ?: return base
                for (i in px.indices) {
                    val dx = evalPoly2(cxCoef, px[i], py[i]) - ox[i]
                    val dy = evalPoly2(cyCoef, px[i], py[i]) - oy[i]
                    resid[i] = sqrt(dx * dx + dy * dy)
                }
                val thr = max(3.0, resid.sorted()[(resid.size * 0.8).toInt().coerceAtMost(resid.size - 1)])
                keep = BooleanArray(px.size) { resid[it] < thr }
            }
            return base.map { (cx, cy) ->
                (cx + evalPoly2(cxCoef, cx.toDouble(), cy.toDouble()).toFloat()) to
                    (cy + evalPoly2(cyCoef, cx.toDouble(), cy.toDouble()).toFloat())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Уточнение центров не удалось: ${e.message}")
            return base
        } finally {
            gray.release(); inv.release(); tm.release()
        }
    }

    private fun poly2Terms(x: Double, y: Double): DoubleArray {
        val a = x / 100; val b = y / 100
        return doubleArrayOf(1.0, a, b, a * a, a * b, b * b)
    }

    private fun evalPoly2(c: DoubleArray, x: Double, y: Double): Double {
        val t = poly2Terms(x, y)
        var v = 0.0
        for (i in 0 until 6) v += c[i] * t[i]
        return v
    }

    /** Наименьшие квадраты для 6 коэффициентов: нормальные уравнения + Гаусс с выбором ведущего. */
    private fun fitPoly2(xs: List<Double>, ys: List<Double>, vs: List<Double>, keep: BooleanArray): DoubleArray? {
        val ata = Array(6) { DoubleArray(6) }
        val atb = DoubleArray(6)
        var n = 0
        for (i in xs.indices) {
            if (!keep[i]) continue
            n++
            val t = poly2Terms(xs[i], ys[i])
            for (r in 0 until 6) {
                atb[r] += t[r] * vs[i]
                for (c in 0 until 6) ata[r][c] += t[r] * t[c]
            }
        }
        if (n < 8) return null
        for (r in 0 until 6) ata[r][r] += 1e-6
        for (col in 0 until 6) {
            var piv = col
            for (r in col + 1 until 6) if (abs(ata[r][col]) > abs(ata[piv][col])) piv = r
            if (abs(ata[piv][col]) < 1e-12) return null
            val tmpRow = ata[col]; ata[col] = ata[piv]; ata[piv] = tmpRow
            val tmpB = atb[col]; atb[col] = atb[piv]; atb[piv] = tmpB
            for (r in 0 until 6) {
                if (r == col) continue
                val f = ata[r][col] / ata[col][col]
                for (c in col until 6) ata[r][c] -= f * ata[col][c]
                atb[r] -= f * atb[col]
            }
        }
        return DoubleArray(6) { atb[it] / ata[it][it] }
    }

    // ---------- кандидаты ----------

    private fun candidateQuads(gray: Mat): List<Array<Point>> {
        val bw = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        val out = ArrayList<Array<Point>>()
        try {
            val blk = max(15, (min(gray.rows(), gray.cols()) / 25) or 1)
            Imgproc.adaptiveThreshold(gray, bw, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, blk, 10.0)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.morphologyEx(bw, bw, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()
            Imgproc.findContours(bw, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val minArea = 0.01 * gray.rows() * gray.cols()
            for (c in contours) {
                if (Imgproc.contourArea(c) < minArea) continue
                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                var quad: Array<Point>? = null
                for (eps in doubleArrayOf(0.02, 0.035, 0.05)) {
                    val ap = MatOfPoint2f()
                    Imgproc.approxPolyDP(c2f, ap, eps * peri, true)
                    val pts = ap.toArray()
                    ap.release()
                    if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                        quad = pts
                        break
                    }
                }
                if (quad == null) {
                    val hullIdx = MatOfInt()
                    Imgproc.convexHull(c, hullIdx)
                    val src = c.toArray()
                    val hull = MatOfPoint2f(*hullIdx.toArray().map { src[it] }.toTypedArray())
                    val ap = MatOfPoint2f()
                    Imgproc.approxPolyDP(hull, ap, 0.04 * Imgproc.arcLength(hull, true), true)
                    val pts = ap.toArray()
                    if (pts.size == 4) quad = pts
                    hullIdx.release(); hull.release(); ap.release()
                }
                c2f.release()
                if (quad != null) out += orderQuad(quad)
            }
        } finally {
            bw.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
        return out
    }

    /** Порядок: левый верх, правый верх, правый низ, левый низ (по часовой в координатах кадра). */
    private fun orderQuad(p: Array<Point>): Array<Point> {
        val cx = p.sumOf { it.x } / 4
        val cy = p.sumOf { it.y } / 4
        val sorted = p.sortedBy { atan2(it.y - cy, it.x - cx) }
        val k = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: 0
        var r = Array(4) { sorted[(k + it) % 4] }
        val ax = r[1].x - r[0].x; val ay = r[1].y - r[0].y
        val bx = r[3].x - r[0].x; val by = r[3].y - r[0].y
        if (ax * by - ay * bx < 0) r = arrayOf(r[0], r[3], r[2], r[1])
        return r
    }

    // ---------- проверка решётки ----------

    private val ANG_COS = DoubleArray(24) { cos(2 * Math.PI * it / 24) }
    private val ANG_SIN = DoubleArray(24) { sin(2 * Math.PI * it / 24) }

    /** Доля кружков, чьё кольцо темнее фона вокруг; ×0.7, если не видна тёмная рамка. */
    private fun latticeScore(canonGray: Mat, geo: GridGeometry, scale: Double): Float {
        val w = canonGray.cols()
        val h = canonGray.rows()
        val blurred = Mat()
        Imgproc.GaussianBlur(canonGray, blurred, Size(0.0, 0.0), 1.0)
        val px = ByteArray(w * h)
        blurred.get(0, 0, px)
        blurred.release()
        fun at(x: Int, y: Int): Int = px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)].toInt() and 0xFF
        val rRing = (geo.bubbleSize / 2 - STROKE / 2) * scale
        val rOut = (geo.bubbleSize / 2 + 4f) * scale
        val ring = IntArray(24)
        val outer = IntArray(24)
        var ok = 0
        for (b in geo.bubbles) {
            val cx = b.cx * scale
            val cy = b.cy * scale
            for (i in 0 until 24) {
                ring[i] = at((cx + rRing * ANG_COS[i]).toInt(), (cy + rRing * ANG_SIN[i]).toInt())
                outer[i] = at((cx + rOut * ANG_COS[i]).toInt(), (cy + rOut * ANG_SIN[i]).toInt())
            }
            ring.sort(); outer.sort()
            if (outer[12] - ring[12] > 18) ok++
        }
        var score = ok.toFloat() / geo.bubbles.size.coerceAtLeast(1)
        // Тёмная рамка: медиана полосы по краю каждой рамки против медианы внутри.
        val bw = (geo.borderWidth * scale * 0.6).toInt().coerceAtLeast(1)
        val border = ArrayList<Int>()
        val inside = ArrayList<Int>()
        for (f in geo.frames) {
            val l = (f[0] * scale).toInt(); val t = (f[1] * scale).toInt()
            val r = (f[2] * scale).toInt() - 1; val btm = (f[3] * scale).toInt() - 1
            var x = l
            while (x <= r) { border += at(x, t + bw / 2); border += at(x, btm - bw / 2); x += 3 }
            var y = t
            while (y <= btm) { border += at(l + bw / 2, y); border += at(r - bw / 2, y); y += 3 }
            val m = (geo.borderWidth * scale * 1.6).toInt()
            y = t + m
            while (y < btm - m) { x = l + m; while (x < r - m) { inside += at(x, y); x += 9 }; y += 9 }
        }
        border.sort(); inside.sort()
        val borderOk = inside.isNotEmpty() && border.isNotEmpty() && inside[inside.size / 2] - border[border.size / 2] > 40
        if (!borderOk) score *= 0.7f
        return score
    }

    // ---------- шаблон и ECC ----------

    private fun renderTemplate(geo: GridGeometry, tw: Int, th: Int, scale: Double): Mat {
        val t = Mat(th, tw, CvType.CV_8UC1, Scalar(255.0))
        val b = (geo.borderWidth * scale).roundToInt()
        for (f in geo.frames) {
            val hb = b / 2.0
            Imgproc.rectangle(t, Point(f[0] * scale + hb, f[1] * scale + hb), Point(f[2] * scale - 1 - hb, f[3] * scale - 1 - hb), Scalar(0.0), b)
        }
        val r = ((geo.bubbleSize / 2 - STROKE / 2) * scale).roundToInt()
        val s = max(1, (STROKE * scale).roundToInt())
        for (bb in geo.bubbles) {
            Imgproc.circle(t, Point(bb.cx * scale, bb.cy * scale), r, Scalar(0.0), s, Imgproc.LINE_AA)
        }
        return t
    }

    private fun refineEcc(gray: Mat, h0: Mat, template: Mat, tw: Int, th: Int): Mat {
        val warp = Mat()
        val t32 = Mat()
        val i32 = Mat()
        val m = Mat.eye(3, 3, CvType.CV_32F)
        try {
            Imgproc.warpPerspective(gray, warp, h0, Size(tw.toDouble(), th.toDouble()))
            Imgproc.GaussianBlur(template, t32, Size(0.0, 0.0), 2.0)
            Imgproc.GaussianBlur(warp, i32, Size(0.0, 0.0), 2.0)
            t32.convertTo(t32, CvType.CV_32F)
            i32.convertTo(i32, CvType.CV_32F)
            Video.findTransformECC(
                t32, i32, m, Video.MOTION_HOMOGRAPHY,
                TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 35, 1e-4), Mat(), 1,
            )
            val m64 = Mat()
            m.convertTo(m64, CvType.CV_64F)
            val inv = Mat()
            Core.invert(m64, inv)
            val h = Mat()
            Core.gemm(inv, h0, 1.0, Mat(), 0.0, h)
            m64.release(); inv.release()
            h0.release()
            return h
        } catch (e: Exception) {
            Log.w(TAG, "ECC не сошёлся: ${e.message}")
            return h0
        } finally {
            warp.release(); t32.release(); i32.release(); m.release()
        }
    }

    // ---------- ориентация ----------

    private const val OS = 1.5 // масштаб поиска шестиугольника и поля имени

    private fun decideOrientation(grayFull: Mat, hCanon: Mat, geo: GridGeometry): Pair<Boolean, Float> {
        val m = MARGIN * OS
        val t = Mat.eye(3, 3, CvType.CV_64F).apply { put(0, 0, OS / SCALE); put(1, 1, OS / SCALE); put(0, 2, m); put(1, 2, m) }
        val ht = Mat()
        Core.gemm(t, hCanon, 1.0, Mat(), 0.0, ht)
        val ext = Mat()
        val w = (geo.width * OS + 2 * m).roundToInt()
        val hh = (geo.height * OS + 2 * m).roundToInt()
        Imgproc.warpPerspective(grayFull, ext, ht, Size(w.toDouble(), hh.toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0))
        val rot = Mat()
        Core.rotate(ext, rot, Core.ROTATE_180)
        try {
            val up = markerScore(ext)
            val dn = markerScore(rot)
            val flipped = dn > up + 0.08f
            return flipped to abs(up - dn)
        } finally {
            t.release(); ht.release(); ext.release(); rot.release()
        }
    }

    private val hexTemplate: Mat by lazy {
        val size = 34.0; val pad = 6.0
        val r = size / 2 * OS
        val n = ((size + 2 * pad) * OS).toInt()
        val tm = Mat(n, n, CvType.CV_8UC1, Scalar(255.0))
        val c = n / 2.0
        val k = sqrt(3.0) / 2
        val pts = MatOfPoint(
            Point(c, c - r), Point(c + k * r, c - r / 2), Point(c + k * r, c + r / 2),
            Point(c, c + r), Point(c - k * r, c + r / 2), Point(c - k * r, c - r / 2),
        )
        Imgproc.polylines(tm, listOf(pts), true, Scalar(0.0), (2 * OS).roundToInt(), Imgproc.LINE_AA)
        tm
    }

    private val boxTemplate: Mat by lazy {
        val pad = 6.0
        val w = ((210 + 2 * pad) * OS).toInt()
        val h = ((34 + 2 * pad) * OS).toInt()
        val tm = Mat(h, w, CvType.CV_8UC1, Scalar(255.0))
        val p = pad * OS
        Imgproc.rectangle(tm, Point(p, p), Point(w - p, h - p), Scalar(0.0), (2 * OS).roundToInt())
        tm
    }

    /** Насколько похоже, что над левым верхним углом стоят шестиугольник и поле имени. */
    private fun markerScore(ext: Mat): Float {
        val m = MARGIN * OS
        fun best(tm: Mat, cx: Double, cy: Double, slack: Double): Float {
            val x0 = (m + cx * OS - tm.cols() / 2.0 - slack * OS).toInt().coerceAtLeast(0)
            val y0 = (m + cy * OS - tm.rows() / 2.0 - slack * OS).toInt().coerceAtLeast(0)
            val x1 = (x0 + tm.cols() + 2 * slack * OS).toInt().coerceAtMost(ext.cols())
            val y1 = (y0 + tm.rows() + 2 * slack * OS).toInt().coerceAtMost(ext.rows())
            if (x1 - x0 < tm.cols() || y1 - y0 < tm.rows()) return 0f
            val roi = ext.submat(y0, y1, x0, x1)
            val res = Mat()
            return try {
                Imgproc.matchTemplate(roi, tm, res, Imgproc.TM_CCOEFF_NORMED)
                val v = Core.minMaxLoc(res).maxVal.toFloat()
                if (v.isNaN()) 0f else v
            } finally {
                res.release(); roi.release()
            }
        }
        return max(best(hexTemplate, 17.0, -29.0, 10.0), best(boxTemplate, 105.0, -73.0, 12.0))
    }

    // ---------- утилиты ----------

    private fun dist(a: Point, b: Point): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    private fun perspective(src: Array<Point>, dst: Array<Point>): Mat {
        val s = MatOfPoint2f(*src)
        val d = MatOfPoint2f(*dst)
        return try {
            Imgproc.getPerspectiveTransform(s, d)
        } finally {
            s.release(); d.release()
        }
    }
}
