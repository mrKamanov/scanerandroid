package com.tscan.scanertestov.feature.live

/**
 * Описание: слежение за листом «как в приложениях с масками»: искать редко, следить часто.
 *
 *  - В каждом кадре — оптический поток (Лукас–Канаде, [Video.calcOpticalFlowPyrLK]) по ~150 заметным точкам
 *    самого листа (кольца кружков, линии рамки, углы меток). Лист плоский, поэтому по сдвигу точек
 *    восстанавливается его положение целиком ([Calib3d.findHomography] с RANSAC — отдельные сбившиеся
 *    точки отбрасываются). Несколько миллисекунд; не теряется, если метку закрыл палец или кадр чуть смазан.
 *  - Угловые метки ArUco ищутся раз в [MARKERS_EVERY] кадров (и сразу, если поток потерял лист): они
 *    поправляют накопившийся увод и заново раздают точки.
 *
 * Положение — гомография «логические единицы сетки → пиксели кадра».
 */
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.hypot

internal class LiveSheetFollower {
    private var prevGray: Mat? = null
    /** Точки в предыдущем кадре (пиксели) и их места на листе (логические единицы сетки). */
    private var pts: List<Point> = emptyList()
    private var logical: List<Point> = emptyList()
    /** Логические единицы → пиксели кадра. */
    private var hGrid: Mat? = null
    private var frame = 0
    private var lastMarkersFrame = -1000

    /** Высота сетки, при которой сошлись метки (может отличаться от стандартной). */
    @Volatile var gridHeightUsed: Double = 0.0
        private set

    /**
     * Поправка «метки → сетка» (истинная сетка → сетка по меткам), 3×3 по строкам. Задаётся из потока
     * проверки; новое значение — сразу перепривязка по меткам.
     */
    @Volatile var correction: DoubleArray? = null
        set(v) { field = v; forceMarkers = true }
    @Volatile private var forceMarkers = false

    /** Ширина сетки текущего теста (от неё зависит место правых меток). */
    private var gridWidth: Double = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.PILOT_GRID_WIDTH

    /** Откуда положение в последнем кадре — для журнала. */
    var lastSource: String = ""
        private set

    fun reset() {
        prevGray?.release(); prevGray = null
        hGrid?.release(); hGrid = null
        pts = emptyList(); logical = emptyList()
        lastMarkersFrame = -1000
    }

    /** [gray] — кадр «как на экране» (уже повёрнут и уменьшен). null — листа в кадре нет. */
    fun step(gray: Mat, gridHeight: Double, probes: List<Pair<Double, Double>>, gridWidth: Double = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.PILOT_GRID_WIDTH): LiveTrack? {
        this.gridWidth = gridWidth
        frame++
        var h: Mat? = null
        var source = ""
        // 1. Оптический поток от прошлого кадра.
        val prev = prevGray
        if (prev != null && pts.size >= MIN_POINTS && hGrid != null) {
            h = flow(prev, gray)
            if (h != null) source = "поток"
        }
        // 2. Метки — для поправки или если поток потерял лист.
        val force = forceMarkers
        if (force) { forceMarkers = false; h?.release(); h = null }
        val needMarkers = h == null || frame - lastMarkersFrame >= MARKERS_EVERY || pts.size < RESEED_POINTS
        var reseed = false
        if (needMarkers) {
            val hm = markers(gray, gridHeight)
            if (hm != null) {
                lastMarkersFrame = frame
                if (h == null) {
                    h = hm
                    source = "метки"
                } else {
                    // Поток и метки расходятся больше чем на пару пикселей — верим меткам (увод потока).
                    val drift = cornerShift(h, hm, gridHeightUsed)
                    if (drift > DRIFT_PX) {
                        h.release(); h = hm; source = "метки (увод ${"%.1f".format(drift)} px)"
                    } else {
                        hm.release()
                    }
                }
                reseed = true
            }
        }
        if (h == null) {
            reset()
            return null
        }
        hGrid?.release()
        hGrid = h
        if (reseed || pts.size < RESEED_POINTS) seed(gray, h)
        prevGray?.release()
        prevGray = gray.clone()
        lastSource = source

        val m = DoubleArray(9)
        h.get(0, 0, m)
        val dark = LiveSheetTracker.darkProbes(gray, m, probes)
        val sw = gray.cols().toDouble()
        val sh = gray.rows().toDouble()
        for (i in 0..2) { m[i] /= sw; m[3 + i] /= sh }
        return LiveTrack(m, gray.cols(), gray.rows(), dark)
    }

    /** Сдвиг точек потоком → новое положение листа или null (точек мало / не согласуются). */
    private fun flow(prev: Mat, cur: Mat): Mat? {
        val p0 = MatOfPoint2f(*pts.toTypedArray())
        val p1 = MatOfPoint2f()
        val status = MatOfByte()
        val err = MatOfFloat()
        try {
            val crit = TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, 20, 0.03)
            Video.calcOpticalFlowPyrLK(prev, cur, p0, p1, status, err, Size(21.0, 21.0), 3, crit)
            // «Туда и обратно»: кружки одинаковые, и точка может перескочить на соседний. Проследим назад —
            // если не вернулась на своё место, точку выбрасываем.
            val back = MatOfPoint2f()
            val stBack = MatOfByte()
            val errBack = MatOfFloat()
            Video.calcOpticalFlowPyrLK(cur, prev, p1, back, stBack, errBack, Size(21.0, 21.0), 3, crit)
            val st = status.toArray()
            val sb = stBack.toArray()
            val next = p1.toArray()
            val bk = back.toArray()
            back.release(); stBack.release(); errBack.release()
            for (i in st.indices) {
                if (st[i].toInt() == 0 || sb[i].toInt() == 0) { st[i] = 0; continue }
                if (hypot(bk[i].x - pts[i].x, bk[i].y - pts[i].y) > FB_PX) st[i] = 0
            }
            val keepPx = ArrayList<Point>()
            val keepLg = ArrayList<Point>()
            for (i in st.indices) {
                if (st[i].toInt() == 0) continue
                val q = next[i]
                if (q.x < 0 || q.y < 0 || q.x >= cur.cols() || q.y >= cur.rows()) continue
                keepPx += q
                keepLg += logical[i]
            }
            if (keepPx.size < MIN_POINTS) return null
            val src = MatOfPoint2f(*keepLg.toTypedArray())
            val dst = MatOfPoint2f(*keepPx.toTypedArray())
            val mask = Mat()
            val h = Calib3d.findHomography(src, dst, Calib3d.RANSAC, RANSAC_PX, mask)
            src.release(); dst.release()
            if (h.empty()) { mask.release(); return null }
            // Оставляем только согласованные точки — сбившиеся (блик, палец) уходят.
            val inl = ArrayList<Point>()
            val inlLg = ArrayList<Point>()
            val mb = ByteArray(mask.rows())
            mask.get(0, 0, mb)
            mask.release()
            for (i in mb.indices) if (mb[i].toInt() != 0) { inl += keepPx[i]; inlLg += keepLg[i] }
            if (inl.size < MIN_POINTS) { h.release(); return null }
            pts = inl
            logical = inlLg
            if (h.type() != CvType.CV_64F) h.convertTo(h, CvType.CV_64F)
            return h
        } finally {
            p0.release(); p1.release(); status.release(); err.release()
        }
    }

    /** Положение по угловым меткам: логические единицы → пиксели. */
    private fun markers(gray: Mat, gridHeight: Double): Mat? {
        val found = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.find(gray, subpix = true)
        if (found.size < 3) return null
        var gh = gridHeight
        var hm = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.bestHomography(found, gh, 1.0, gridWidth)
        if (hm == null) {
            gh = LiveSheetTracker.estimateGridHeight(found, gridWidth) ?: return null
            hm = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.bestHomography(found, gh, 1.0, gridWidth) ?: return null
        }
        gridHeightUsed = gh
        val inv = Mat()
        Core.invert(hm, inv) // сетка по меткам → пиксели
        hm.release()
        val c = correction ?: return inv
        // Истинная сетка → сетка по меткам → пиксели.
        val d = Mat(3, 3, CvType.CV_64F)
        d.put(0, 0, *c)
        val out = Mat()
        Core.gemm(inv, d, 1.0, Mat(), 0.0, out)
        inv.release(); d.release()
        return out
    }

    /** Новые точки для потока — заметные уголки внутри листа (сетка + шапка с метками). */
    private fun seed(gray: Mat, h: Mat) {
        val gh = gridHeightUsed.takeIf { it > 0 } ?: return
        val rx = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.rightX(gridWidth) + 35.0
        val region = listOf(Point(-45.0, -132.0), Point(rx, -132.0), Point(rx, gh + 22.0), Point(-45.0, gh + 22.0))
        val src = MatOfPoint2f(*region.toTypedArray())
        val dst = MatOfPoint2f()
        Core.perspectiveTransform(src, dst, h)
        val poly = MatOfPoint(*dst.toArray().map { Point(it.x, it.y) }.toTypedArray())
        src.release(); dst.release()
        val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
        Imgproc.fillConvexPoly(mask, poly, Scalar(255.0))
        poly.release()
        val corners = MatOfPoint()
        Imgproc.goodFeaturesToTrack(gray, corners, MAX_POINTS, 0.01, 8.0, mask, 5, false, 0.04)
        mask.release()
        val px = corners.toArray().map { Point(it.x, it.y) }
        corners.release()
        if (px.size < MIN_POINTS) return
        val inv = Mat()
        Core.invert(h, inv)
        val a = MatOfPoint2f(*px.toTypedArray())
        val b = MatOfPoint2f()
        Core.perspectiveTransform(a, b, inv)
        pts = px
        logical = b.toList()
        a.release(); b.release(); inv.release()
    }

    /** Наибольший сдвиг углов сетки между двумя положениями, пиксели. */
    private fun cornerShift(a: Mat, b: Mat, gh: Double): Double {
        val w = 440.0
        val c = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, gh), Point(0.0, gh))
        val pa = MatOfPoint2f()
        val pb = MatOfPoint2f()
        Core.perspectiveTransform(c, pa, a)
        Core.perspectiveTransform(c, pb, b)
        val d = pa.toArray().zip(pb.toArray()).maxOf { (u, v) -> hypot(u.x - v.x, u.y - v.y) }
        c.release(); pa.release(); pb.release()
        return d
    }

    private companion object {
        const val MAX_POINTS = 160
        const val MIN_POINTS = 12
        const val RESEED_POINTS = 50
        const val MARKERS_EVERY = 4
        /** Ошибка «туда и обратно» больше — точка ненадёжна. */
        const val FB_PX = 1.0
        const val RANSAC_PX = 2.5
        const val DRIFT_PX = 2.0
    }
}

/**
 * Фильтр «One Euro» (Casiez и др., 2012) — как в трекерах лиц: в покое сильно гасит дрожание,
 * при быстром движении почти не отстаёт (частота среза растёт со скоростью).
 */
internal class OneEuro(private val minCutoff: Double, private val beta: Double, private val dCutoff: Double = 1.0) {
    private var x: Double? = null
    private var dx = 0.0
    private var t = 0L

    fun reset() { x = null; dx = 0.0 }

    fun filter(value: Double, timeMs: Long): Double {
        val prev = x
        if (prev == null) { x = value; t = timeMs; return value }
        val dt = ((timeMs - t).coerceAtLeast(1)) / 1000.0
        t = timeMs
        val ad = alpha(dCutoff, dt)
        dx = ad * ((value - prev) / dt) + (1 - ad) * dx
        val cutoff = minCutoff + beta * kotlin.math.abs(dx)
        val a = alpha(cutoff, dt)
        val out = a * value + (1 - a) * prev
        x = out
        return out
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * Math.PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }
}
