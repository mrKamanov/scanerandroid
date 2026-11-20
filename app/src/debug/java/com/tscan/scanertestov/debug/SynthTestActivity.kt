package com.tscan.scanertestov.debug

/**
 * Описание: проверка распознавания на синтетических бланках (только debug-сборка).
 * Бланк рисуется тем же кодом, что и выгрузка из конструктора, часть кружков «закрашивается»,
 * лист искажается перспективой (и переворачивается), затем прогоняется через движок.
 * Так проверяются раскладки, которых нет среди реальных фото: два столбца, нестандартные кружки.
 * Запуск: adb shell am start -n com.tscan.scanertestov/.debug.SynthTestActivity --es tag s1
 * Выход: files/benchout/TAG/synth.jsonl, input_*.jpg, crop_*.jpg, done.txt
 */
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.widget.TextView
import androidx.compose.ui.geometry.Offset
import com.tscan.scanertestov.data.PrintedBlankParamsStore
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleRenderParams
import com.tscan.scanertestov.feature.blankeditor.BlankEditorLayoutConfig
import com.tscan.scanertestov.feature.blankeditor.BlankSheetExportSnapshot
import com.tscan.scanertestov.feature.blankeditor.buildBlankBubbleLayout
import com.tscan.scanertestov.feature.blankeditor.defaultBlankTextBlocks
import com.tscan.scanertestov.feature.blankeditor.renderBlankSheetBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread
import kotlin.random.Random

class SynthTestActivity : Activity() {
    private data class Case(
        val name: String,
        val questions: Int,
        val choices: Int,
        val columns: Int,
        val params: BlankBubbleRenderParams = BlankBubbleRenderParams.defaults(),
        val blankScale: Float = 1f,
        val pan: Offset = Offset.Zero,
        val flipped: Boolean = false,
        val skew: Float = 0.06f,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { textSize = 16f; setPadding(32, 96, 32, 32) }
        setContentView(text)
        val tag = intent.getStringExtra("tag") ?: "synth"
        val outDir = File(File(filesDir, "benchout"), tag).apply { deleteRecursively(); mkdirs() }
        val d = BlankBubbleRenderParams.defaults()
        val big = d.copy(bubbleSizePx = 36f, cellGapHorizontalPx = 14f, cellGapVerticalPx = 8f)
        val small = d.copy(bubbleSizePx = 24f, cellGapHorizontalPx = 8f, cellGapVerticalPx = 8f, borderWidthPx = 4f)
        val cases = listOf(
            Case("1col_10x5", 10, 5, 1),
            Case("1col_10x5_flip", 10, 5, 1, flipped = true),
            Case("1col_8x4", 8, 4, 1),
            Case("1col_10x5_scale07", 10, 5, 1, blankScale = 0.7f, pan = Offset(120f, -40f)),
            Case("2col_20x5", 20, 5, 2, pan = Offset(0f, -60f)),
            Case("2col_20x5_flip", 20, 5, 2, pan = Offset(0f, -60f), flipped = true),
            Case("2col_15x4_odd", 15, 4, 2),
            Case("2col_30x4_scale08", 30, 4, 2, blankScale = 0.8f, pan = Offset(20f, -120f)),
            Case("1col_10x5_big", 10, 5, 1, params = big, pan = Offset(0f, -40f)),
            Case("1col_12x5_small", 12, 5, 1, params = small),
            Case("2col_20x5_small_flip", 20, 5, 2, params = small, flipped = true),
            Case("1col_10x5_strongskew", 10, 5, 1, skew = 0.14f),
        )
        thread(name = "synth") {
            try {
                // Нестандартные параметры «напечатаны» — как после выгрузки из конструктора.
                PrintedBlankParamsStore.remember(this, big)
                PrintedBlankParamsStore.remember(this, small)
                val out = File(outDir, "synth.jsonl").bufferedWriter()
                cases.forEachIndexed { i, c ->
                    runOnUiThread { text.text = "Synth $tag: ${i + 1} / ${cases.size}\n${c.name}" }
                    val o = JSONObject().put("case", c.name)
                    try {
                        runCase(c, i, outDir, o)
                    } catch (e: Throwable) {
                        o.put("error", e.toString())
                    }
                    out.write(o.toString()); out.newLine(); out.flush()
                }
                out.close()
                File(outDir, "done.txt").writeText("${cases.size} cases\n")
                runOnUiThread { text.text = "Готово: synth → benchout/$tag" }
            } catch (e: Throwable) {
                File(outDir, "error.txt").writeText(e.stackTraceToString())
            }
        }
    }

    private fun runCase(c: Case, index: Int, outDir: File, o: JSONObject) {
        val rnd = Random(1000 + index)
        val layoutConfig = BlankEditorLayoutConfig(questionCount = c.questions, optionCount = c.choices, columnCount = c.columns)
        val sheet = renderBlankSheetBitmap(
            BlankSheetExportSnapshot(
                layoutConfig = layoutConfig,
                renderParams = c.params,
                hiddenBubbleIds = emptySet(),
                blankPanLogical = c.pan,
                blankScale = c.blankScale,
                textBlocks = defaultBlankTextBlocks(),
                showVariantMarker = true,
                showStudentNameMarker = true,
                studentNamePrintedMode = true,
                studentSurnameText = "Иванов",
                studentNameText = "Пётр",
                variantPrintedMode = true,
                variantPrintedNumber = "2",
            ),
            withBackground = true,
        )
        // Увеличиваем лист до «фото» и закрашиваем по одному кружку на вопрос.
        val up = 3f
        val layout = buildBlankBubbleLayout(layoutConfig, c.params)
        val frames = layout.frames
        var minL = Float.MAX_VALUE; var minT = Float.MAX_VALUE; var maxR = -Float.MAX_VALUE; var maxB = -Float.MAX_VALUE
        for (f in frames) { minL = minOf(minL, f.left); minT = minOf(minT, f.top); maxR = maxOf(maxR, f.left + f.width); maxB = maxOf(maxB, f.top + f.height) }
        val cx0 = (minL + maxR) / 2f; val cy0 = (minT + maxB) / 2f
        fun toSheet(x: Float, y: Float): Pair<Float, Float> =
            (c.pan.x + cx0 + (x - cx0) * c.blankScale) to (c.pan.y + cy0 + (y - cy0) * c.blankScale)

        val big = Bitmap.createBitmap((595 * up).toInt(), (842 * up).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        canvas.drawColor(Color.rgb(236, 234, 228))
        val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.save(); canvas.scale(up, up); canvas.drawBitmap(sheet, 0f, 0f, bmpPaint); canvas.restore()
        sheet.recycle()
        val pencil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 70, 70, 78); style = Paint.Style.STROKE; strokeWidth = 2.2f * up * c.blankScale }
        val expected = IntArray(c.questions) { rnd.nextInt(c.choices) }
        for (b in layout.bubbles) {
            val label = b.label ?: continue
            val q = label.substringBefore('.').toInt() - 1
            val ch = label.substringAfter('.').toInt() - 1
            if (expected[q] != ch) continue
            val (sx, sy) = toSheet(b.centerX, b.centerY)
            val r = c.params.bubbleSizePx / 2f * c.blankScale * 0.78f
            // Штриховка карандашом: наклонные линии внутри кружка.
            var t = -r
            while (t <= r) {
                val half = kotlin.math.sqrt((r * r - t * t).coerceAtLeast(0f))
                canvas.drawLine((sx + t - half * 0.5f) * up, (sy + half) * up, (sx + t + half * 0.5f) * up, (sy - half) * up, pencil)
                t += 2.6f * c.blankScale
            }
        }
        // «Фото»: перспектива, поля стола вокруг, при необходимости переворот.
        val w = 1500; val h = 2100
        val photo = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pc = Canvas(photo)
        pc.drawColor(Color.rgb(96, 82, 70))
        val bw = big.width.toFloat(); val bh = big.height.toFloat()
        val s = c.skew
        val dst = floatArrayOf(
            w * (0.06f + s), h * 0.05f,
            w * (0.95f - s * 0.3f), h * (0.04f + s * 0.4f),
            w * 0.97f, h * (0.95f - s * 0.2f),
            w * 0.03f, h * 0.96f,
        )
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(0f, 0f, bw, 0f, bw, bh, 0f, bh), 0, dst, 0, 4)
        if (c.flipped) m.postRotate(180f, w / 2f, h / 2f)
        pc.drawBitmap(big, m, bmpPaint)
        big.recycle()
        File(outDir, "input_${c.name}.jpg").outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 80, it) }

        val cfg = BatchOmrConfig(c.questions, c.choices, c.columns, expected.map { setOf(it) }, strictScoring = true)
        val t0 = System.nanoTime()
        val r = BatchOmrEngine.process(this, photo, cfg, debugOverlay = true)
        photo.recycle()
        val got = (0 until c.questions).map { q ->
            r.predictions.filter { it.questionIndex == q && it.klass == BatchCellClass.Yes }.map { it.choiceIndex }
        }
        val wrong = (0 until c.questions).count { got[it] != listOf(expected[it]) }
        o.put("ms", (System.nanoTime() - t0) / 1_000_000)
            .put("found", r.contourFound).put("grid", (r.gridScore ?: -1f).toDouble()).put("flipped", r.flipped)
            .put("expectFlipped", c.flipped).put("wrong", wrong).put("questions", c.questions)
            .put("fixed", r.fixedCells.size)
            .put("expected", JSONArray(expected.toList())).put("got", JSONArray(got.map { JSONArray(it) }))
        r.sheetCropBitmap?.let { crop ->
            File(outDir, "crop_${c.name}.jpg").outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 75, it) }
        }
    }
}
