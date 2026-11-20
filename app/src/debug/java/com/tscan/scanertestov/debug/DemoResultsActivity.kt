package com.tscan.scanertestov.debug

/**
 * Описание: отладочный показ экрана результатов на реальных фото (только debug-сборка).
 * Прогоняет несколько кадров из files/bench_in через движок и OCR, публикует прогон и
 * открывает экран результатов — чтобы проверять его вид без ручной съёмки.
 * Запуск: adb shell am start -n com.tscan.scanertestov/.debug.DemoResultsActivity --es files "1,10,19,30,46,84,109,127"
 */
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.tscan.scanertestov.feature.batch.BatchLastRunStore
import com.tscan.scanertestov.feature.batch.BatchResultsRoute
import com.tscan.scanertestov.feature.batch.BatchRunItemResult
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine
import com.tscan.scanertestov.feature.batch.recognizeStudentNameFromBitmap
import com.tscan.scanertestov.feature.batch.recognizeVariantFromBitmap
import com.tscan.scanertestov.ui.theme.ScanerTestovTheme
import java.io.File
import kotlin.concurrent.thread

class DemoResultsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var status by mutableStateOf("Готовлю демо-прогон…")
        var ready by mutableStateOf(false)
        setContent {
            ScanerTestovTheme {
                if (ready) {
                    BatchResultsRoute(navController = rememberNavController())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(status) }
                }
            }
        }
        val ids = (intent.getStringExtra("files") ?: "1,10,19,30,46,84,109,127").split(',').mapNotNull { it.trim().toIntOrNull() }
        val key = (intent.getStringExtra("key") ?: "1111123112").map { setOf(it.digitToInt() - 1) }
        val cfg = BatchOmrConfig(key.size, 5, 1, key, strictScoring = false)
        thread(name = "demo-run") {
            val runId = BatchLastRunStore.newRunId()
            val items = ArrayList<BatchRunItemResult>()
            ids.forEachIndexed { index, id ->
                runOnUiThread { status = "Бланк ${index + 1} из ${ids.size}" }
                val f = File(File(filesDir, "bench_in"), "sheet_%06d.jpg".format(id))
                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                val item = if (bmp == null) {
                    BatchRunItemResult(title = "Работа ${index + 1}", layoutLabel = "Один столбец", config = cfg, error = "не удалось открыть изображение")
                } else try {
                    val v = recognizeVariantFromBitmap(this, bmp, cfg.questionsCount, cfg.choicesCount, 1, 4)
                    val n = recognizeStudentNameFromBitmap(this, bmp, cfg.questionsCount, cfg.choicesCount, 1)
                    BatchRunItemResult(
                        title = "Работа ${index + 1}",
                        layoutLabel = "Один столбец",
                        config = cfg,
                        detectedVariant = v.variant,
                        detectedStudentSurname = n.surname,
                        detectedStudentName = n.name,
                        omr = BatchOmrEngine.process(this, bmp, cfg),
                    )
                } finally {
                    bmp.recycle()
                }
                items += BatchLastRunStore.detachSheetImage(this, runId, index, item)
            }
            BatchLastRunStore.publish(this, runId, System.currentTimeMillis(), items)
            runOnUiThread { ready = true }
        }
    }
}
