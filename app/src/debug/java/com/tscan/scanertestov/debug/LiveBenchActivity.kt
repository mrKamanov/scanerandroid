package com.tscan.scanertestov.debug

/**
 * Описание: отладочный прогон живой проверки по сохранённым кадрам (только debug-сборка).
 * Запуск: adb shell am start -n com.tscan.scanertestov/.debug.LiveBenchActivity [--es test "Проба 20"] [--ei rounds 2]
 * Вход:  jpg-кадры в files/live_frames (их пишет экран «Один бланк» в отладочной сборке)
 * Выход: files/benchout/live.txt — по каждому кадру и способу: время по шагам и итог; в конце — done.
 */
import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.TextView
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaRepository
import com.tscan.scanertestov.feature.live.LiveMethod
import com.tscan.scanertestov.feature.live.LiveRecognizer
import com.tscan.scanertestov.feature.realtime.engine.RealtimeOpenCvBootstrap
import java.io.File
import kotlin.concurrent.thread

class LiveBenchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { textSize = 16f; setPadding(32, 96, 32, 32) }
        setContentView(text)
        val testName = intent.getStringExtra("test") ?: "Проба 20"
        val rounds = intent.getIntExtra("rounds", 2)
        val out = File(File(filesDir, "benchout").apply { mkdirs() }, "live.txt")
        thread(name = "live-bench") {
            val sb = StringBuilder()
            try {
                RealtimeOpenCvBootstrap.ensureLoaded()
                val preset = BatchCriteriaRepository(this).getAll().firstOrNull { it.name == testName }
                    ?: error("нет теста «$testName»")
                val frames = File(filesDir, "live_frames").listFiles()?.sortedBy { it.name }.orEmpty()
                sb.append("тест ${preset.name}, кадров ${frames.size}\n")
                var corr: DoubleArray? = null
                for (round in 1..rounds) for (f in frames) for (m in LiveMethod.entries) {
                    runOnUiThread { text.text = "круг $round: ${f.name} ${m.title}" }
                    val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: continue
                    val r = LiveRecognizer.recognize(this, bmp, preset, m, corr)
                    bmp.recycle()
                    val had = corr != null
                    if (r.error == null && r.markerCorrection != null) corr = r.markerCorrection
                    sb.append("$round ${f.name} ${m.title}${if (had) " +поправка" else ""}: ${r.ms} мс [${r.stages}] ${r.correct}/${r.total} вар=${r.variant} код=${r.code} ${r.error ?: ""}\n")
                    out.writeText(sb.toString())
                }
                sb.append("done\n")
            } catch (e: Throwable) {
                sb.append("ошибка: ${e.message}\ndone\n")
            }
            out.writeText(sb.toString())
            runOnUiThread { text.text = "готово" }
        }
    }
}
