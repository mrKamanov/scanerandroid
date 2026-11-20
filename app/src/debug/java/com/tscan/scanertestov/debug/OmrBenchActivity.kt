package com.tscan.scanertestov.debug

/**
 * Описание: отладочный прогон OMR по папке фото (только debug-сборка).
 * Запуск: adb shell am start -n com.tscan.scanertestov/.debug.OmrBenchActivity --es tag NAME
 * Вход:  files/bench_in (jpg/png)
 * Выход: files/benchout/NAME/results.jsonl, crops/ (jpg), done.txt
 */
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.TextView
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine
import com.tscan.scanertestov.feature.batch.recognizeStudentNameFromBitmap
import com.tscan.scanertestov.feature.batch.recognizeVariantFromBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class OmrBenchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { textSize = 16f; setPadding(32, 96, 32, 32) }
        setContentView(text)
        val tag = intent.getStringExtra("tag") ?: "run"
        val q = intent.getIntExtra("q", 10)
        val ch = intent.getIntExtra("c", 5)
        val cols = intent.getIntExtra("cols", 1)
        val withOcr = intent.getBooleanExtra("ocr", true)
        val cropEvery = intent.getIntExtra("cropEvery", 1)
        // Внутренняя папка приложения: файлы, положенные adb во внешнюю, приложению не видны.
        // Заполняется через: adb shell run-as <pkg> cp /data/local/tmp/bench_in/* files/bench_in/
        val inDir = File(filesDir, intent.getStringExtra("in") ?: "bench_in")
        val outDir = File(File(filesDir, "benchout"), tag).apply { deleteRecursively(); mkdirs() }
        val crops = File(outDir, "crops").apply { mkdirs() }
        val files = inDir.listFiles { f -> f.name.endsWith(".jpg", true) || f.name.endsWith(".png", true) }
            ?.sortedBy { it.name }.orEmpty()
        val cfg = BatchOmrConfig(q, ch, cols, List(q) { setOf(0) }, strictScoring = false)
        thread(name = "omr-bench") { try {
            val out = File(outDir, "results.jsonl").bufferedWriter()
            val t0 = System.currentTimeMillis()
            files.forEachIndexed { i, f ->
                runOnUiThread { text.text = "OMR bench $tag: ${i + 1} / ${files.size}\n${f.name}" }
                val o = JSONObject().put("file", f.name)
                val bmp: Bitmap? = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp == null) {
                    o.put("error", "decode")
                } else try {
                    val ts = System.nanoTime()
                    val r = BatchOmrEngine.process(this, bmp, cfg, debugOverlay = true)
                    o.put("ms", (System.nanoTime() - ts) / 1_000_000)
                    o.put("found", r.contourFound).put("grid", (r.gridScore ?: -1f).toDouble()).put("flipped", r.flipped)
                    val sel = JSONArray()
                    val conf = JSONArray()
                    for (qi in 0 until q) {
                        val cells = r.predictions.filter { it.questionIndex == qi }.sortedBy { it.choiceIndex }
                        sel.put(JSONArray(cells.filter { it.klass == BatchCellClass.Yes }.map { it.choiceIndex }))
                        conf.put(JSONArray(cells.map { (if (it.klass == BatchCellClass.Yes) "Y" else if (it.klass == BatchCellClass.Fixed) "F" else "N") + "%.2f".format(it.confidence) }))
                    }
                    o.put("sel", sel).put("cells", conf)
                    o.put("fixed", JSONArray(r.fixedCells.map { "${it.questionIndex}:${it.choiceIndex}" }))
                    if (i % cropEvery == 0) r.sheetCropBitmap?.let { c ->
                        File(crops, f.nameWithoutExtension + ".jpg").outputStream().use { c.compress(Bitmap.CompressFormat.JPEG, 70, it) }
                    }
                    if (withOcr) {
                        val tv = System.nanoTime()
                        val v = recognizeVariantFromBitmap(this, bmp, q, ch, cols, null)
                        o.put("variant", v.variant ?: -1).put("variantRaw", v.rawText).put("variantConf", v.confidence.toDouble())
                        val n = recognizeStudentNameFromBitmap(this, bmp, q, ch, cols)
                        o.put("surname", n.surname ?: "").put("name", n.name ?: "").put("nameRaw", n.rawText)
                        o.put("ocrMs", (System.nanoTime() - tv) / 1_000_000)
                    }
                } catch (e: Throwable) {
                    o.put("error", e.toString())
                } finally {
                    bmp?.recycle()
                }
                out.write(o.toString()); out.newLine(); out.flush()
            }
            out.close()
            File(outDir, "done.txt").writeText("${files.size} files, ${System.currentTimeMillis() - t0} ms\n")
            runOnUiThread { text.text = "Готово: ${files.size} фото → benchout/$tag" }
        } catch (e: Throwable) {
            File(outDir, "error.txt").writeText(e.stackTraceToString())
            runOnUiThread { text.text = "Ошибка: $e" }
        } }
    }
}
