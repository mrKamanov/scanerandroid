package com.tscan.scanertestov.debug

/**
 * Описание: стенд «несколько бланков на листе» (только debug).
 *  - mode=render: для набора тестов (вопросов × колонок, 4 ответа, бланк v2) считает раскладки 2 и 4 на лист и
 *    рисует лист тем же кодом, что PDF (renderTiledPreview, ~300 dpi). Пишет files/tilebench/<имя>.png и index.json
 *    (масштаб, поворот, размер кружка на бумаге, помещается ли по правилу).
 *  - mode=check: берёт files/tilebench/photos/<имя>.jpg (вырезанный и «сфотографированный» бланк с закрашенными
 *    ответами) и <имя>.json (вопросы, колонки, ключ) и прогоняет проверку класса; пишет check.txt.
 * Запуск: adb shell am start -n com.tscan.scanertestov/.debug.TilingBenchActivity --es mode render
 */
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.TextView
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine
import com.tscan.scanertestov.feature.blankeditor.BlankDesign
import com.tscan.scanertestov.feature.blankeditor.blankTileOptions
import com.tscan.scanertestov.feature.blankeditor.renderTiledPreview
import com.tscan.scanertestov.feature.realtime.engine.RealtimeOpenCvBootstrap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class TilingBenchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { textSize = 13f; setPadding(24, 96, 24, 24) }
        setContentView(text)
        val mode = intent.getStringExtra("mode") ?: "render"
        val dir = File(filesDir, "tilebench").apply { mkdirs() }
        thread(name = "tilebench") {
            val out = StringBuilder()
            try {
                RealtimeOpenCvBootstrap.ensureLoaded()
                if (mode == "render") render(dir, out) else check(dir, out)
            } catch (e: Throwable) {
                out.append("ошибка: ${e.message}\n")
            }
            out.append("done\n")
            File(dir, "$mode.txt").writeText(out.toString())
            runOnUiThread { text.text = out.toString() }
        }
    }

    private fun render(dir: File, out: StringBuilder) {
        val index = JSONArray()
        // Ответов в вопросе — из extra "options" (по умолчанию 4); раскладки — из "per" (например "1,2").
        val opts = (intent.getStringExtra("options") ?: "4").split(",").map { it.trim().toInt() }
        val pers = (intent.getStringExtra("per") ?: "2,4,6,8").split(",").map { it.trim().toInt() }.toSet()
        val configs = opts.flatMap { op -> listOf(5, 10, 20, 30, 35).flatMap { q -> listOf(Triple(q, 1, op), Triple(q, 2, op)) } }
        for ((q, cols, op) in configs) {
            if (cols == 1 && q > 25) continue
            val d = BlankDesign.defaultFor(q, op, cols, 1)
            val snap = d.exportSnapshot(1)
            for (o in blankTileOptions(snap).filter { it.perSheet in pers }) {
                val name = "q${q}c${cols}o${op}x${o.perSheet}"
                val bubbleMm = snap.renderParams.bubbleSizePx * snap.blankScale * o.scale / 595f * 210f
                // Рисуем всегда (даже если правило «не помещается»), чтобы проверить на деле.
                val bmp = renderTiledPreview(snap, o.copy(fits = true), scale = 300f / 72f)
                File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                index.put(
                    JSONObject().put("name", name).put("q", q).put("o", op).put("cols", cols).put("per", o.perSheet)
                        .put("gcols", o.cols).put("grows", o.rows).put("scale", o.scale.toDouble())
                        .put("rot", o.rotated).put("fits", o.fits).put("bubbleMm", bubbleMm.toDouble()),
                )
                out.append("%-12s масштаб %.2f%s кружок %.1f мм %s\n".format(
                    name, o.scale, if (o.rotated) " (повёрнут)" else "", bubbleMm, if (o.fits) "OK" else "мелко"))
            }
        }
        File(dir, "index.json").writeText(index.toString(1))
    }

    private fun check(dir: File, out: StringBuilder) {
        val photos = File(dir, "photos").listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name }.orEmpty()
        for (ph in photos) {
            val meta = JSONObject(File(ph.path.removeSuffix(".jpg") + ".json").readText())
            val q = meta.getInt("q")
            val key = meta.getJSONArray("key")
            val answers = List(q) { i -> setOf(key.getInt(i)) }
            val cfg = BatchOmrConfig(
                questionsCount = q, choicesCount = meta.optInt("o", 4), columnCount = meta.getInt("cols"),
                answerKey = answers, strictScoring = true,
            )
            val bmp = BitmapFactory.decodeFile(ph.path)
            val t0 = System.currentTimeMillis()
            val r = runCatching { BatchOmrEngine.process(context = this, bitmap = bmp, config = cfg) }.getOrNull()
            bmp.recycle()
            if (r == null || r.predictions.isEmpty()) {
                out.append("%-22s НЕ НАЙДЕН\n".format(ph.nameWithoutExtension)); continue
            }
            val filled = meta.getJSONArray("filled")
            val truth = HashSet<String>()
            for (i in 0 until filled.length()) truth += filled.getString(i)
            var wrong = 0
            for (p in r.predictions) {
                val id = "${p.questionIndex + 1}.${p.choiceIndex + 1}"
                val marked = p.klass != BatchCellClass.No
                if (marked != (id in truth)) wrong++
            }
            val correct = r.questionScores.count { it.correct }
            out.append("%-22s верно %d/%d (ожидалось %d) ошибок в кружках %d сетка %.2f %d мс\n".format(
                ph.nameWithoutExtension, correct, q, meta.getInt("expect"), wrong, r.gridScore ?: 0f, System.currentTimeMillis() - t0))
        }
    }
}
