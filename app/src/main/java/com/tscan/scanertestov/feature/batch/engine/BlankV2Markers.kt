package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: бланк v2 (пробный) — угловые метки ArUco, вариант кружками, код ученика.
 *
 * Расположение задано относительно левого верхнего угла сетки (внешний край первой рамки), в логических
 * единицах листа (595 = 210 мм), как на пробных листах «Бланки_новые_пробные.docx»:
 *  - метки 28×28 (10 мм): левые x = −40, правые x = 447; верхние y = −126, нижние y = (высота сетки − 10);
 *    номера: 0 — левый верх, 1 — правый верх, 2 — правый низ, 3 — левый низ (словарь 4×4_50);
 *  - кружки варианта 1–4: центры (95 + 40·i, −37), кружок 30, шаг 40 — как в сетке ответов;
 *  - код ученика: 8 квадратов 20×20 (7 мм) с левым верхним углом (230 + 24·k, −98), закрашенный = 1, старший бит
 *    слева; первые 6 — номер (0–63), последние 2 — проверка (число единиц в номере по модулю 4);
 *  - поле имени: прямоугольник (0, −98) – (210, −64), посередине черта: слева «Фамилия», справа «Имя»
 *    (как в старом бланке — под это настроено распознавание почерка).
 *
 * Метки дают ориентацию при любом повороте и положение сетки, даже если рамка частично закрыта.
 */
import android.util.Log
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import kotlin.math.max
import kotlin.math.min

internal object BlankV2Markers {
    private const val TAG = "BlankV2"
    const val MARKER = 28.0
    private const val LEFT_X = -40.0
    /** Ширина сетки пробного бланка (20 вопросов × 4 кружка в 2 колонки): при ней правые метки стоят на 447. */
    const val PILOT_GRID_WIDTH = 368.0
    private const val RIGHT_X_MIN = 447.0
    /** Зазор между правым краем сетки и правой меткой, если сетка шире пробной. */
    private const val RIGHT_GAP = 12.0
    private const val TOP_Y = -126.0
    private const val BOTTOM_OFFSET = -10.0 // нижние метки: y = высота сетки + это

    val VARIANT_CENTERS: List<Pair<Double, Double>> = List(4) { i -> (95.0 + 40.0 * i) to -37.0 }
    const val CODE_BITS = 8
    const val CODE_SIZE = 20.0
    fun codeSquare(k: Int): DoubleArray = doubleArrayOf(230.0 + 24.0 * k, -98.0, 230.0 + 24.0 * k + CODE_SIZE, -98.0 + CODE_SIZE)
    val NAME_BOX = doubleArrayOf(0.0, -98.0, 210.0, -64.0)
    /**
     * Левый край правых меток: как на пробных листах (447), а у сетки шире пробной — правее её края на [RIGHT_GAP].
     * Одно правило и для печати, и для распознавания.
     */
    fun rightX(gridWidth: Double): Double = maxOf(RIGHT_X_MIN, gridWidth + RIGHT_GAP)
    const val LEFT_MARKER_X = -40.0
    const val TOP_MARKER_Y = -126.0
    /** Верх нижних меток относительно низа сетки. */
    const val BOTTOM_MARKER_OFFSET = -10.0

    /** 8 бит → номер ученика 0–63 или null, если проверочные биты не сходятся. */
    fun decodeCode(bits: Int): Int? {
        val id = (bits shr 2) and 0x3F
        val check = bits and 0x3
        return if (Integer.bitCount(id) % 4 == check) id else null
    }

    /** Номер ученика 0–63 → 8 бит для печати. */
    fun encodeCode(id: Int): Int = ((id and 0x3F) shl 2) or (Integer.bitCount(id and 0x3F) % 4)

    /** Левый верхний угол метки [id] относительно левого верхнего угла сетки. */
    fun markerOrigin(id: Int, gridHeight: Double, gridWidth: Double = PILOT_GRID_WIDTH): Point {
        val bottom = gridHeight + BOTTOM_OFFSET
        val right = rightX(gridWidth)
        return when (id) {
            0 -> Point(LEFT_X, TOP_Y)
            1 -> Point(right, TOP_Y)
            2 -> Point(right, bottom)
            else -> Point(LEFT_X, bottom)
        }
    }

    /** Углы метки в логических координатах (по часовой от её левого верхнего, как отдаёт детектор). */
    fun markerCornersLogical(id: Int, gridHeight: Double, gridWidth: Double = PILOT_GRID_WIDTH): List<Point> {
        val o = markerOrigin(id, gridHeight, gridWidth)
        return listOf(Point(o.x, o.y), Point(o.x + MARKER, o.y), Point(o.x + MARKER, o.y + MARKER), Point(o.x, o.y + MARKER))
    }

    private val detector: ArucoDetector by lazy {
        val p = DetectorParameters()
        ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50), p)
    }

    /**
     * Метки 0–3 на сером кадре: id → все найденные экземпляры (по 4 угла в пикселях кадра). Экземпляров может
     * быть несколько — в кадр попадают метки соседнего бланка. Ищем на уменьшенной копии (до 2000 px).
     */
    /** Тот же поиск, но углы уточняются до долей пикселя — для живого слежения (меньше дрожание рисунка). */
    private val subpixDetector: ArucoDetector by lazy {
        val p = DetectorParameters()
        p.set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
        ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50), p)
    }

    fun find(grayFull: Mat, subpix: Boolean = false): Map<Int, List<List<Point>>> {
        val k = min(1.0, 2000.0 / max(grayFull.cols(), grayFull.rows()))
        val img = Mat()
        val corners = ArrayList<Mat>()
        val ids = Mat()
        try {
            if (k < 1.0) Imgproc.resize(grayFull, img, Size(), k, k, Imgproc.INTER_AREA) else grayFull.copyTo(img)
            if (subpix) synchronized(subpixDetector) { subpixDetector.detectMarkers(img, corners, ids) }
            else synchronized(this) { detector.detectMarkers(img, corners, ids) }
            val out = HashMap<Int, MutableList<List<Point>>>()
            for (i in 0 until ids.rows()) {
                val id = ids.get(i, 0)[0].toInt()
                if (id !in 0..3) continue
                val c = corners[i]
                out.getOrPut(id) { mutableListOf() } += (0 until 4).map { j -> val v = c.get(0, j); Point(v[0] / k, v[1] / k) }
            }
            return out
        } catch (e: Throwable) {
            Log.w(TAG, "Поиск меток не удался: ${e.message}")
            return emptyMap()
        } finally {
            img.release(); ids.release(); corners.forEach { it.release() }
        }
    }

    /**
     * Гомография «кадр → канон × [scale]» по найденным меткам (нужно ≥ 3, т. е. 12 точек).
     * Канон здесь — в напечатанной ориентации (верх листа сверху), поэтому переворот уже учтён.
     */
    /** Все сочетания «по одному экземпляру каждой метки» (не больше 16), если меток хотя бы 3. */
    fun combinations(found: Map<Int, List<List<Point>>>): List<Map<Int, List<Point>>> {
        if (found.size < 3) return emptyList()
        var acc: List<Map<Int, List<Point>>> = listOf(emptyMap())
        for ((id, list) in found) {
            acc = acc.flatMap { m -> list.take(3).map { m + (id to it) } }
            if (acc.size > 16) acc = acc.take(16)
        }
        return acc
    }

    /**
     * Самое согласованное сочетание меток (наименьшая ошибка перепроекции): метки соседнего бланка на том же
     * листе дают несогласованную геометрию и отбрасываются. Гомография «кадр → логические единицы × [scale]
     * относительно левого верхнего угла сетки», в напечатанной ориентации. null — меньше 3 меток или ошибка велика.
     */
    fun bestHomography(found: Map<Int, List<List<Point>>>, gridHeight: Double, scale: Double, gridWidth: Double = PILOT_GRID_WIDTH): Mat? {
        var best: Pair<Double, Mat>? = null
        for (combo in combinations(found)) {
            val h = homography(combo, gridHeight, scale, gridWidth) ?: continue
            val src = MatOfPoint2f(*combo.values.flatten().toTypedArray())
            val proj = MatOfPoint2f()
            org.opencv.core.Core.perspectiveTransform(src, proj, h)
            val dst = combo.keys.flatMap { id -> markerCornersLogical(id, gridHeight, gridWidth).map { Point(it.x * scale, it.y * scale) } }
            val err = proj.toArray().zip(dst).maxOf { (a, b) -> kotlin.math.hypot(a.x - b.x, a.y - b.y) } / scale
            src.release(); proj.release()
            val cur = best
            if (cur == null || err < cur.first) {
                cur?.second?.release()
                best = err to h
            } else {
                h.release()
            }
        }
        val b = best ?: return null
        // Больше 6 логических единиц (≈2 мм) — метки не от одного бланка.
        if (b.first > 6.0) {
            b.second.release()
            return null
        }
        return b.second
    }

    fun homography(markers: Map<Int, List<Point>>, gridHeight: Double, scale: Double, gridWidth: Double = PILOT_GRID_WIDTH): Mat? {
        if (markers.size < 3) return null
        val src = ArrayList<Point>()
        val dst = ArrayList<Point>()
        for ((id, pts) in markers) {
            src += pts
            dst += markerCornersLogical(id, gridHeight, gridWidth).map { Point(it.x * scale, it.y * scale) }
        }
        val s = MatOfPoint2f(*src.toTypedArray())
        val d = MatOfPoint2f(*dst.toTypedArray())
        return try {
            val h = Calib3d.findHomography(s, d, 0, 3.0)
            if (h.empty()) null else h.also { if (it.type() != CvType.CV_64F) it.convertTo(it, CvType.CV_64F) }
        } catch (e: Throwable) {
            null
        } finally {
            s.release(); d.release()
        }
    }

    /**
     * Перевёрнут ли канон, найденный по рамке ([hCanon]: кадр → канон × scale), относительно напечатанного листа.
     * Сравниваем, где в каноне оказались центры меток, с их местом в обеих ориентациях. null — меток нет.
     */
    fun flippedByMarkers(found: Map<Int, List<List<Point>>>, hCanon: Mat, gridW: Double, gridH: Double, scale: Double): Boolean? {
        if (found.isEmpty()) return null
        var up = 0.0
        var dn = 0.0
        // Из нескольких экземпляров одной метки берём ближайший к своему месту (в любой ориентации).
        for ((id, list) in found) for (pts in list.take(3)) {
            val cx = pts.sumOf { it.x } / 4
            val cy = pts.sumOf { it.y } / 4
            val m = MatOfPoint2f(Point(cx, cy))
            val r = MatOfPoint2f()
            org.opencv.core.Core.perspectiveTransform(m, r, hCanon)
            val p = r.toArray()[0]
            m.release(); r.release()
            val o = markerOrigin(id, gridH, gridW)
            val ex = (o.x + MARKER / 2) * scale
            val ey = (o.y + MARKER / 2) * scale
            val fx = (gridW - (o.x + MARKER / 2)) * scale
            val fy = (gridH - (o.y + MARKER / 2)) * scale
            val du = (p.x - ex) * (p.x - ex) + (p.y - ey) * (p.y - ey)
            val dd = (p.x - fx) * (p.x - fx) + (p.y - fy) * (p.y - fy)
            // Чужая метка далеко от обоих мест — не голосует.
            val near = (MARKER * 3 * scale) * (MARKER * 3 * scale)
            if (minOf(du, dd) > near) continue
            up += du
            dn += dd
        }
        if (up == 0.0 && dn == 0.0) return null
        return dn < up
    }
}
