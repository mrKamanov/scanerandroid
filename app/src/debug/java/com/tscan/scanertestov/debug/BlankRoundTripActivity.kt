package com.tscan.scanertestov.debug

/**
 * Описание: проверка «что печатаем, то и читаем» (только debug-сборка). Берёт тест, рисует его бланк тем же
 * кодом, что выгрузка (renderBlankSheetBitmap с оформлением теста), слегка «фотографирует» (поворот, перспектива,
 * размытие) и прогоняет через распознавание: метки, сетка, вариант, код, кружки.
 * Запуск: adb shell am start -n com.tscan.scanertestov/.debug.BlankRoundTripActivity [--es test "Проба 20"]
 * Выход: files/benchout/roundtrip.txt и roundtrip_*.png (нарисованный бланк и «снимок»).
 */
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.TextView
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaRepository
import com.tscan.scanertestov.feature.batch.readBlankV2
import com.tscan.scanertestov.feature.blankeditor.renderBlankSheetBitmap
import com.tscan.scanertestov.feature.live.LiveMethod
import com.tscan.scanertestov.feature.live.LiveRecognizer
import com.tscan.scanertestov.feature.realtime.engine.RealtimeOpenCvBootstrap
import com.tscan.scanertestov.feature.tests.blankDesign
import com.tscan.scanertestov.feature.tests.testVariantsCount
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.concurrent.thread

class BlankRoundTripActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { textSize = 15f; setPadding(32, 96, 32, 32) }
        setContentView(text)
        val testName = intent.getStringExtra("test")
        val outDir = File(filesDir, "benchout").apply { mkdirs() }
        thread(name = "roundtrip") {
            val sb = StringBuilder()
            try {
                RealtimeOpenCvBootstrap.ensureLoaded()
                val all = BatchCriteriaRepository(this).getAll()
                val tests = if (testName != null) all.filter { it.name == testName } else all
                for (preset in tests) {
                    val d = preset.blankDesign()
                    val variants = preset.testVariantsCount
                    // Разные бланки вариантов — проверяем каждый; иначе один бланк с закрашенным последним вариантом.
                    val sheetsToTry: List<Int?> = if (d.variantsDiffer) (1..variants).toList() else listOf(if (variants >= 2) variants.coerceAtMost(4) else null)
                    for (pv in sheetsToTry) {
                        val base = d.exportSnapshot(variants, pv)
                        val snap = base.copy(
                            variantPrintedMode = pv != null,
                            variantPrintedNumber = (pv ?: 1).toString(),
                            v2 = base.v2?.copy(studentCode = 17),
                        )
                        val tag = "${preset.name}_v${pv ?: 0}"
                        val sheet = renderBlankSheetBitmap(snap, withBackground = true, scale = 3f)
                        val photo = fakePhoto(sheet)
                        save(sheet, File(outDir, "roundtrip_${tag}_sheet.png"))
                        save(photo, File(outDir, "roundtrip_${tag}_photo.png"))
                        sb.append("== ${preset.name} (бланк варианта ${pv ?: "—"}): ${preset.questionsCount}×${preset.choicesCount}, колонок ${preset.columnCount}, вариантов $variants, ")
                            .append("v2=${d.formatV2}, скрыто у всех ${d.hiddenBubbleIds.size}, у этого варианта ${d.hiddenFor(pv).size}\n")
                        for ((label, bmp) in listOf("ровный" to sheet, "снимок" to photo)) {
                            val v2 = readBlankV2(this, bmp, preset.questionsCount, preset.choicesCount, preset.columnCount, d.hiddenBubbleIds)
                            sb.append("  $label: метки=${v2?.markers} вариант=${v2?.variant} код=${v2?.code}\n")
                            v2?.nameCrop?.release()
                            // Как проверка класса: кружки и ключ — по прочитанному варианту.
                            val v = v2?.variant
                            val keyRaw = preset.variantAnswerKeys.firstOrNull { it.variant == v }?.correctAnswers ?: preset.correctAnswers
                            val cfg = com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig(
                                questionsCount = preset.questionsCount,
                                choicesCount = preset.choicesCount,
                                columnCount = preset.columnCount,
                                answerKey = List(preset.questionsCount) { i -> keyRaw.getOrNull(i).orEmpty().filter { it >= 0 }.toSet() },
                                strictScoring = true,
                                hiddenBubbleIds = d.hiddenFor(v),
                            )
                            val omr = runCatching { com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine.process(context = this, bitmap = bmp, config = cfg) }.getOrNull()
                            val marked = omr?.predictions?.count { it.klass != com.tscan.scanertestov.feature.batch.engine.BatchCellClass.No }
                            sb.append("    проверка класса: сетка=${omr?.gridScore?.let { "%.2f".format(it) }} кружков=${omr?.predictions?.size} отмечено=$marked\n")
                            val r = LiveRecognizer.recognize(this, bmp, preset, LiveMethod.Model)
                            val liveMarked = r.omr?.predictions?.count { it.klass != com.tscan.scanertestov.feature.batch.engine.BatchCellClass.No }
                            sb.append("    живая: ${r.ms} мс [${r.stages}] вар=${r.variant} код=${r.code} отмечено кружков=$liveMarked ${r.error ?: ""}\n")
                        }
                        sheet.recycle(); photo.recycle()
                    }
                }
                sb.append("done\n")
            } catch (e: Throwable) {
                sb.append("ошибка: ${e.message}\ndone\n")
            }
            File(outDir, "roundtrip.txt").writeText(sb.toString())
            runOnUiThread { text.text = sb.toString() }
        }
    }

    /** «Снимок»: перспектива + лёгкий поворот + размытие + неравномерный свет. */
    private fun fakePhoto(src: Bitmap): Bitmap {
        val m = Mat()
        Utils.bitmapToMat(src, m)
        val w = m.cols().toDouble()
        val h = m.rows().toDouble()
        val from = MatOfPoint2f(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        val to = MatOfPoint2f(Point(w * 0.06, h * 0.03), Point(w * 0.95, 0.0), Point(w, h * 0.97), Point(w * 0.02, h * 0.99))
        val hm = Imgproc.getPerspectiveTransform(from, to)
        val out = Mat()
        Imgproc.warpPerspective(m, out, hm, Size(w, h), Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, Scalar(120.0, 120.0, 120.0, 255.0))
        Imgproc.GaussianBlur(out, out, Size(3.0, 3.0), 0.8)
        val bmp = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(out, bmp)
        m.release(); out.release(); hm.release(); from.release(); to.release()
        return bmp
    }

    private fun save(b: Bitmap, f: File) = f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
