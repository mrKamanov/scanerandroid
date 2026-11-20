package com.tscan.scanertestov.feature.batch

/**
 * Описание: хранилище последнего пакетного прогона.
 *
 * Раньше результаты жили только в памяти и пропадали, как только Android выгружал
 * приложение (например, когда учитель переключился в электронный журнал).
 * Теперь прогон сохраняется в `filesDir/batch_runs/<runId>/`:
 *  - `run.json` — структурированные результаты (ячейки, баллы, ФИО, вариант);
 *  - `decisions.json` — решения учителя по спорным ячейкам (fixed);
 *  - `sheet_<i>.jpg` — выпрямленный бланк для просмотра (грузится по требованию);
 *  - `thumb_<i>.jpg` — миниатюра для списка (держится в памяти).
 *
 * В памяти больше не хранятся полноразмерные кадры бланков (по ~14 МБ на работу),
 * поэтому класс из 30 работ не приводит к OutOfMemoryError.
 */
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction
import com.tscan.scanertestov.feature.batch.engine.BatchColumnFrameGrid
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import com.tscan.scanertestov.feature.batch.engine.BatchQuestionScore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class BatchRun(
    val runId: String,
    val createdAtMs: Long,
    val items: List<BatchRunItemResult>,
    val decisions: Map<String, Boolean> = emptyMap(),
    /** Ученик, которого учитель выбрал вручную («Чья работа?»), по номеру работы. */
    val manualNames: Map<Int, BatchManualName> = emptyMap(),
    /** Критерии оценки, с которыми проверяли (из теста); null — общие из Настроек. */
    val gradingCriteriaJson: String? = null,
    /** Тест, по которому проверяли (null — ключ вводили вручную). */
    val testId: String? = null,
    val testTitle: String? = null,
)

data class BatchManualName(val surname: String, val name: String, val className: String?)

object BatchLastRunStore {
    private const val TAG = "BatchLastRunStore"
    private const val ROOT_DIR = "batch_runs"
    private const val RUN_FILE = "run.json"
    private const val DECISIONS_FILE = "decisions.json"
    private const val NAMES_FILE = "names.json"
    private const val THUMB_MAX_SIDE = 240
    private const val SHEET_JPEG_QUALITY = 88

    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var currentRun: BatchRun? = null

    /** Вызывается один раз из Application. Чтение с диска — лениво. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Текущий прогон (при первом обращении читается с диска; блокирующий вызов). */
    val current: BatchRun?
        get() {
            ensureLoaded()
            return currentRun
        }

    /** Можно вызвать заранее из фонового потока, чтобы экран результатов открылся мгновенно. */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            currentRun = runCatching { loadLatestFromDisk() }
                .onFailure { Log.w(TAG, "Не удалось прочитать прошлый прогон", it) }
                .getOrNull()
            loaded = true
        }
    }

    fun newRunId(): String = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"

    fun runDir(context: Context, runId: String): File =
        File(File(context.applicationContext.filesDir, ROOT_DIR), runId).apply { mkdirs() }

    /**
     * Сохраняет кадр бланка на диск и возвращает копию результата без полноразмерного битмапа,
     * но с миниатюрой и путём к файлу. Исходный [BatchOmrResult.sheetCropBitmap] освобождается.
     */
    fun detachSheetImage(
        context: Context,
        runId: String,
        itemIndex: Int,
        item: BatchRunItemResult,
    ): BatchRunItemResult {
        val omr = item.omr ?: return item
        val crop = omr.sheetCropBitmap ?: return item
        val dir = runDir(context, runId)
        val sheetFile = File(dir, "sheet_$itemIndex.jpg")
        val thumbFile = File(dir, "thumb_$itemIndex.jpg")
        var thumb: Bitmap? = null
        val ok = runCatching {
            FileOutputStream(sheetFile).use { crop.compress(Bitmap.CompressFormat.JPEG, SHEET_JPEG_QUALITY, it) }
            thumb = scaleToMaxSide(crop, THUMB_MAX_SIDE)
            FileOutputStream(thumbFile).use { thumb!!.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            true
        }.getOrElse {
            Log.w(TAG, "Не удалось сохранить кадр бланка $itemIndex", it)
            false
        }
        if (!crop.isRecycled && crop !== thumb) crop.recycle()
        return item.copy(
            omr = omr.copy(sheetCropBitmap = null),
            sheetImagePath = if (ok) sheetFile.absolutePath else null,
            thumbnail = thumb,
        )
    }

    /** Публикует новый прогон: пишет run.json и удаляет каталоги предыдущих прогонов. */
    fun publish(
        context: Context,
        runId: String,
        createdAtMs: Long,
        items: List<BatchRunItemResult>,
        gradingCriteriaJson: String? = null,
        testId: String? = null,
        testTitle: String? = null,
    ) {
        val run = BatchRun(
            runId = runId, createdAtMs = createdAtMs, items = items, gradingCriteriaJson = gradingCriteriaJson,
            testId = testId, testTitle = testTitle,
        )
        synchronized(lock) {
            currentRun = run
            loaded = true
        }
        runCatching {
            val dir = runDir(context, runId)
            writeAtomically(File(dir, RUN_FILE), runToJson(run).toString())
            File(dir, DECISIONS_FILE).delete()
            File(dir, NAMES_FILE).delete()
            deleteOtherRuns(context, keepRunId = runId)
        }.onFailure { Log.w(TAG, "Не удалось сохранить прогон", it) }
    }

    /** Решение учителя по спорной ячейке: true — засчитать как отмеченную, false — пустая, null — сбросить. */
    fun setDecision(context: Context, key: String, value: Boolean?) {
        val updated = synchronized(lock) {
            val run = currentRun ?: return
            val next = run.decisions.toMutableMap()
            if (value == null) next.remove(key) else next[key] = value
            run.copy(decisions = next).also { currentRun = it }
        }
        runCatching {
            val obj = JSONObject()
            updated.decisions.forEach { (k, v) -> obj.put(k, v) }
            writeAtomically(File(runDir(context, updated.runId), DECISIONS_FILE), obj.toString())
        }.onFailure { Log.w(TAG, "Не удалось сохранить решения", it) }
    }

    /** Учитель указал, чья это работа. null — сбросить к распознанному. */
    fun setManualName(context: Context, itemIndex: Int, value: BatchManualName?) {
        val updated = synchronized(lock) {
            val run = currentRun ?: return
            val next = run.manualNames.toMutableMap()
            if (value == null) next.remove(itemIndex) else next[itemIndex] = value
            run.copy(manualNames = next).also { currentRun = it }
        }
        runCatching {
            val obj = JSONObject()
            updated.manualNames.forEach { (k, v) ->
                obj.put(k.toString(), JSONObject().put("s", v.surname).put("n", v.name).apply { v.className?.let { put("c", it) } })
            }
            writeAtomically(File(runDir(context, updated.runId), NAMES_FILE), obj.toString())
        }.onFailure { Log.w(TAG, "Не удалось сохранить имена", it) }
    }

    /** Полноразмерный кадр бланка для детального просмотра. Блокирующий: вызывать в фоне. */
    fun loadSheetBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            BitmapFactory.decodeFile(path)?.let { decoded ->
                // Оверлей ячеек рисуется на изменяемой копии, а координаты сетки заданы
                // в пикселях исходного кадра, поэтому размер не меняем.
                if (decoded.config == Bitmap.Config.ARGB_8888) {
                    decoded
                } else {
                    decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
                }
            }
        }.getOrNull()
    }

    // region disk

    private fun rootDir(context: Context): File = File(context.filesDir, ROOT_DIR)

    private fun loadLatestFromDisk(): BatchRun? {
        val ctx = appContext ?: return null
        val dirs = rootDir(ctx).listFiles()?.filter { it.isDirectory && File(it, RUN_FILE).exists() }
            ?: return null
        val latest = dirs.maxByOrNull { File(it, RUN_FILE).lastModified() } ?: return null
        val run = runFromJson(JSONObject(File(latest, RUN_FILE).readText()), latest)
        val decisionsFile = File(latest, DECISIONS_FILE)
        val decisions = if (decisionsFile.exists()) {
            runCatching {
                val o = JSONObject(decisionsFile.readText())
                buildMap { o.keys().forEach { k -> put(k, o.getBoolean(k)) } }
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        val namesFile = File(latest, NAMES_FILE)
        val names = if (namesFile.exists()) {
            runCatching {
                val o = JSONObject(namesFile.readText())
                buildMap {
                    o.keys().forEach { k ->
                        val v = o.getJSONObject(k)
                        put(k.toInt(), BatchManualName(v.optString("s"), v.optString("n"), v.optStringOrNull("c")))
                    }
                }
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        return run.copy(decisions = decisions, manualNames = names)
    }

    private fun deleteOtherRuns(context: Context, keepRunId: String) {
        rootDir(context).listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != keepRunId) f.deleteRecursively()
        }
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun scaleToMaxSide(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src.copy(Bitmap.Config.ARGB_8888, false)
        val k = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * k).toInt().coerceAtLeast(1),
            (src.height * k).toInt().coerceAtLeast(1),
            true,
        )
    }

    // endregion

    // region json

    internal fun runToJson(run: BatchRun): JSONObject = JSONObject().apply {
        put("v", 1)
        put("runId", run.runId)
        put("createdAt", run.createdAtMs)
        put("items", JSONArray().apply { run.items.forEach { put(itemToJson(it)) } })
        run.gradingCriteriaJson?.let { put("grading", it) }
        run.testId?.let { put("testId", it) }
        run.testTitle?.let { put("testTitle", it) }
    }

    internal fun runFromJson(o: JSONObject, dir: File): BatchRun {
        val arr = o.getJSONArray("items")
        val items = List(arr.length()) { i -> itemFromJson(arr.getJSONObject(i), dir, i) }
        return BatchRun(
            runId = o.getString("runId"),
            createdAtMs = o.optLong("createdAt", 0L),
            items = items,
            gradingCriteriaJson = if (o.has("grading") && !o.isNull("grading")) o.optString("grading") else null,
            testId = o.optString("testId").takeIf { it.isNotBlank() },
            testTitle = o.optString("testTitle").takeIf { it.isNotBlank() },
        )
    }

    private fun itemToJson(item: BatchRunItemResult): JSONObject = JSONObject().apply {
        put("title", item.title)
        put("layout", item.layoutLabel)
        put("config", configToJson(item.config))
        item.detectedVariant?.let { put("variant", it) }
        item.detectedStudentSurname?.let { put("surname", it) }
        item.detectedStudentName?.let { put("name", it) }
        item.detectedJournalClassName?.let { put("class", it) }
        item.error?.let { put("error", it) }
        item.sheetImagePath?.let { put("sheet", File(it).name) }
        item.nameCropPath?.let { put("nameCrop", it) }
        item.studentNote?.let { put("studentNote", it) }
        item.warning?.let { put("warning", it) }
        item.omr?.let { put("omr", omrToJson(it)) }
    }

    private fun itemFromJson(o: JSONObject, dir: File, index: Int): BatchRunItemResult {
        val sheetName = o.optString("sheet").takeIf { it.isNotBlank() }
        val sheetPath = sheetName?.let { File(dir, it) }?.takeIf { it.exists() }?.absolutePath
        val thumbFile = File(dir, "thumb_$index.jpg")
        val thumb = if (thumbFile.exists()) runCatching { BitmapFactory.decodeFile(thumbFile.absolutePath) }.getOrNull() else null
        return BatchRunItemResult(
            title = o.optString("title"),
            layoutLabel = o.optString("layout"),
            config = configFromJson(o.getJSONObject("config")),
            detectedVariant = if (o.has("variant")) o.getInt("variant") else null,
            detectedStudentSurname = o.optStringOrNull("surname"),
            detectedStudentName = o.optStringOrNull("name"),
            detectedJournalClassName = o.optStringOrNull("class"),
            omr = o.optJSONObject("omr")?.let { omrFromJson(it) },
            error = o.optStringOrNull("error"),
            sheetImagePath = sheetPath,
            thumbnail = thumb,
            nameCropPath = o.optStringOrNull("nameCrop")?.takeIf { File(it).exists() },
            studentNote = o.optStringOrNull("studentNote"),
            warning = o.optStringOrNull("warning"),
        )
    }

    private fun configToJson(c: BatchOmrConfig): JSONObject = JSONObject().apply {
        put("q", c.questionsCount)
        put("c", c.choicesCount)
        put("cols", c.columnCount)
        put("strict", c.strictScoring)
        put("key", JSONArray().apply { c.answerKey.forEach { put(intSetToJson(it)) } })
    }

    private fun configFromJson(o: JSONObject): BatchOmrConfig {
        val key = o.getJSONArray("key")
        return BatchOmrConfig(
            questionsCount = o.getInt("q"),
            choicesCount = o.getInt("c"),
            columnCount = o.getInt("cols"),
            answerKey = List(key.length()) { i -> intSetFromJson(key.getJSONArray(i)) },
            strictScoring = o.optBoolean("strict", true),
        )
    }

    private fun omrToJson(r: BatchOmrResult): JSONObject = JSONObject().apply {
        put("contour", r.contourFound)
        // Компактно: [q, c, класс, уверенность].
        put("cells", JSONArray().apply {
            r.predictions.forEach { p ->
                val a = JSONArray().put(p.questionIndex).put(p.choiceIndex).put(p.klass.ordinal).put(p.confidence.toDouble())
                // Вероятности нейросети нужны для авто-решения очевидных исправлений после перезапуска.
                p.probs?.takeIf { it.size >= 3 }?.let { pr -> a.put(pr[0].toDouble()).put(pr[1].toDouble()).put(pr[2].toDouble()) }
                put(a)
            }
        })
        put("scores", JSONArray().apply {
            r.questionScores.forEach { s ->
                put(JSONObject().apply {
                    put("q", s.questionIndex)
                    put("sel", intSetToJson(s.selectedChoices))
                    put("score", s.score.toDouble())
                    put("ok", s.correct)
                    put("fixed", s.hasFixed)
                })
            }
        })
        r.columnFrames?.let { frames ->
            put("frames", JSONArray().apply {
                frames.forEach { f ->
                    put(JSONArray().put(f.questionStart).put(f.questionCount).put(f.frameBubbleRows)
                        .put(f.innerLeft).put(f.innerTop).put(f.innerRight).put(f.innerBottom))
                }
            })
        }
    }

    private fun omrFromJson(o: JSONObject): BatchOmrResult {
        val classes = BatchCellClass.entries
        val cellsArr = o.getJSONArray("cells")
        val predictions = List(cellsArr.length()) { i ->
            val a = cellsArr.getJSONArray(i)
            BatchCellPrediction(
                questionIndex = a.getInt(0),
                choiceIndex = a.getInt(1),
                klass = classes.getOrElse(a.getInt(2)) { BatchCellClass.No },
                confidence = a.getDouble(3).toFloat(),
                probs = if (a.length() >= 7) {
                    floatArrayOf(a.getDouble(4).toFloat(), a.getDouble(5).toFloat(), a.getDouble(6).toFloat())
                } else {
                    null
                },
            )
        }
        val scoresArr = o.optJSONArray("scores") ?: JSONArray()
        val scores = List(scoresArr.length()) { i ->
            val s = scoresArr.getJSONObject(i)
            BatchQuestionScore(
                questionIndex = s.getInt("q"),
                selectedChoices = intSetFromJson(s.getJSONArray("sel")),
                score = s.getDouble("score").toFloat(),
                correct = s.getBoolean("ok"),
                hasFixed = s.optBoolean("fixed", false),
            )
        }
        val frames = o.optJSONArray("frames")?.let { arr ->
            List(arr.length()) { i ->
                val a = arr.getJSONArray(i)
                BatchColumnFrameGrid(
                    questionStart = a.getInt(0),
                    questionCount = a.getInt(1),
                    frameBubbleRows = a.getInt(2),
                    innerLeft = a.getInt(3),
                    innerTop = a.getInt(4),
                    innerRight = a.getInt(5),
                    innerBottom = a.getInt(6),
                )
            }
        }
        return BatchOmrResult(
            predictions = predictions,
            questionScores = scores,
            fixedCells = predictions.filter { it.klass == BatchCellClass.Fixed },
            contourFound = o.optBoolean("contour", false),
            sheetCropBitmap = null,
            columnFrames = frames,
        )
    }

    private fun intSetToJson(s: Set<Int>): JSONArray = JSONArray().apply { s.sorted().forEach { put(it) } }

    private fun intSetFromJson(a: JSONArray): Set<Int> = buildSet { for (i in 0 until a.length()) add(a.getInt(i)) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    // endregion
}
