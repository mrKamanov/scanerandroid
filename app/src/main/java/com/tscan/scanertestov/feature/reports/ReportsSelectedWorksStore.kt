package com.tscan.scanertestov.feature.reports

/**
 * Описание: снимки работ для отчётов (источник — экран результатов пакета) и хранилище списка.
 *
 * Список сохраняется в `filesDir/reports/works.json`, поэтому отчёты переживают
 * выгрузку приложения из памяти и перезапуск.
 * [ReportsWorkSnapshot.sourceKey] включает id пакетного прогона: работы разных
 * классов/прогонов с одинаковым номером («Работа №1») больше не перезаписывают друг друга.
 */
import android.content.Context
import android.util.Log
import com.tscan.scanertestov.feature.batch.engine.BatchQuestionScore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ReportsWorkSnapshot(
    val sourceKey: String,
    val title: String,
    val grade: Int,
    val percent: Float,
    val scoreCorrect: Int,
    val scoreTotal: Int,
    val questionScores: List<BatchQuestionScore>,
    val variant: Int? = null,
    val studentSurname: String? = null,
    val studentName: String? = null,
    val journalClassName: String? = null,
    val strictScoring: Boolean = true,
    val partialCompletedCount: Int = 0,
    val hasMultiChoiceQuestions: Boolean = false,
    /** Тест: отчёт собирается по тесту (все классы вместе). null — проверка без сохранённого теста. */
    val testId: String? = null,
    val testTitle: String? = null,
    val runId: String = "",
    /** Когда проверено (мс). */
    val checkedAtMs: Long = 0L,
    /** Верные ответы для варианта этой работы — для разбора «что выбирали». */
    val answerKey: List<Set<Int>> = emptyList(),
    val choicesCount: Int = 0,
    /** Копия выпрямленного бланка в filesDir/reports/sheets — чтобы бланк открывался и после новых проверок. */
    val sheetPath: String? = null,
) {
    /** Ключ отчёта: тест, а если теста нет — сама проверка. */
    val reportKey: String get() = testId?.let { "test:$it" } ?: "run:$runId"

    val fullName: String? get() = listOfNotNull(studentSurname, studentName).joinToString(" ").trim().ifBlank { null }
}

object ReportsSelectedWorksStore {
    private const val TAG = "ReportsWorksStore"
    private const val DIR = "reports"
    private const val FILE = "works.json"

    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: List<ReportsWorkSnapshot> = emptyList()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val works: List<ReportsWorkSnapshot>
        get() {
            ensureLoaded()
            return cached
        }

    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            cached = runCatching { readFromDisk() }
                .onFailure { Log.w(TAG, "Не удалось прочитать отчёты", it) }
                .getOrDefault(emptyList())
            loaded = true
        }
    }

    fun upsert(work: ReportsWorkSnapshot) = upsertAll(listOf(work))

    fun upsertAll(items: List<ReportsWorkSnapshot>) {
        if (items.isEmpty()) return
        update { current ->
            val next = current.toMutableList()
            items.forEach { work ->
                val idx = next.indexOfFirst { it.sourceKey == work.sourceKey }
                if (idx >= 0) next[idx] = work else next += work
            }
            next
        }
    }

    fun remove(sourceKey: String) {
        val gone = works.firstOrNull { it.sourceKey == sourceKey }
        update { current -> current.filterNot { it.sourceKey == sourceKey } }
        gone?.sheetPath?.let { runCatching { File(it).delete() } }
    }

    fun clear() = update { emptyList() }

    /** Удалить отчёт целиком (все работы теста) и копии бланков. */
    fun removeReport(reportKey: String) {
        val gone = works.filter { it.reportKey == reportKey }
        update { current -> current.filterNot { it.reportKey == reportKey } }
        gone.forEach { w -> w.sheetPath?.let { runCatching { File(it).delete() } } }
    }

    /** Копия бланка для отчёта (filesDir/reports/sheets/<имя>.jpg); null — нет исходного файла. */
    fun copySheet(sourcePath: String?, sourceKey: String): String? {
        val ctx = appContext ?: return null
        val src = sourcePath?.let { File(it) }?.takeIf { it.exists() } ?: return null
        val dir = File(File(ctx.filesDir, DIR), "sheets").apply { mkdirs() }
        val dst = File(dir, sourceKey.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg")
        return runCatching { src.copyTo(dst, overwrite = true); dst.absolutePath }.getOrNull()
    }

    private fun update(transform: (List<ReportsWorkSnapshot>) -> List<ReportsWorkSnapshot>) {
        ensureLoaded()
        val snapshot = synchronized(lock) {
            transform(cached).also { cached = it }
        }
        runCatching { writeToDisk(snapshot) }
            .onFailure { Log.w(TAG, "Не удалось сохранить отчёты", it) }
    }

    // region disk

    private fun file(): File? {
        val ctx = appContext ?: return null
        return File(File(ctx.filesDir, DIR).apply { mkdirs() }, FILE)
    }

    private fun readFromDisk(): List<ReportsWorkSnapshot> {
        val f = file() ?: return emptyList()
        if (!f.exists()) return emptyList()
        val arr = JSONObject(f.readText()).getJSONArray("works")
        return List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
    }

    private fun writeToDisk(items: List<ReportsWorkSnapshot>) {
        val f = file() ?: return
        val root = JSONObject().apply {
            put("v", 1)
            put("works", JSONArray().apply { items.forEach { put(toJson(it)) } })
        }
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    internal fun toJson(w: ReportsWorkSnapshot): JSONObject = JSONObject().apply {
        put("key", w.sourceKey)
        put("title", w.title)
        put("grade", w.grade)
        put("percent", w.percent.toDouble())
        put("correct", w.scoreCorrect)
        put("total", w.scoreTotal)
        w.variant?.let { put("variant", it) }
        w.studentSurname?.let { put("surname", it) }
        w.studentName?.let { put("name", it) }
        w.journalClassName?.let { put("class", it) }
        put("strict", w.strictScoring)
        put("partial", w.partialCompletedCount)
        put("multi", w.hasMultiChoiceQuestions)
        w.testId?.let { put("testId", it) }
        w.testTitle?.let { put("testTitle", it) }
        put("runId", w.runId)
        put("at", w.checkedAtMs)
        put("choices", w.choicesCount)
        w.sheetPath?.let { put("sheet", it) }
        put("answerKey", JSONArray().apply { w.answerKey.forEach { set -> put(JSONArray().apply { set.sorted().forEach { put(it) } }) } })
        put("scores", JSONArray().apply {
            w.questionScores.forEach { s ->
                put(JSONObject().apply {
                    put("q", s.questionIndex)
                    put("sel", JSONArray().apply { s.selectedChoices.sorted().forEach { put(it) } })
                    put("score", s.score.toDouble())
                    put("ok", s.correct)
                    put("fixed", s.hasFixed)
                })
            }
        })
    }

    internal fun fromJson(o: JSONObject): ReportsWorkSnapshot {
        val scoresArr = o.optJSONArray("scores") ?: JSONArray()
        val scores = List(scoresArr.length()) { i ->
            val s = scoresArr.getJSONObject(i)
            val sel = s.getJSONArray("sel")
            BatchQuestionScore(
                questionIndex = s.getInt("q"),
                selectedChoices = buildSet { for (j in 0 until sel.length()) add(sel.getInt(j)) },
                score = s.getDouble("score").toFloat(),
                correct = s.getBoolean("ok"),
                hasFixed = s.optBoolean("fixed", false),
            )
        }
        fun str(key: String): String? = if (o.has(key) && !o.isNull(key)) o.optString(key).takeIf { it.isNotBlank() } else null
        return ReportsWorkSnapshot(
            sourceKey = o.getString("key"),
            title = o.optString("title"),
            grade = o.getInt("grade"),
            percent = o.getDouble("percent").toFloat(),
            scoreCorrect = o.getInt("correct"),
            scoreTotal = o.getInt("total"),
            questionScores = scores,
            variant = if (o.has("variant")) o.getInt("variant") else null,
            studentSurname = str("surname"),
            studentName = str("name"),
            journalClassName = str("class"),
            strictScoring = o.optBoolean("strict", true),
            partialCompletedCount = o.optInt("partial", 0),
            hasMultiChoiceQuestions = o.optBoolean("multi", false),
            testId = str("testId"),
            testTitle = str("testTitle"),
            runId = o.optString("runId").ifBlank { o.getString("key").substringBefore('#') },
            checkedAtMs = o.optLong("at", 0L),
            answerKey = o.optJSONArray("answerKey")?.let { a ->
                List(a.length()) { i -> val q = a.getJSONArray(i); buildSet { for (j in 0 until q.length()) add(q.getInt(j)) } }
            } ?: emptyList(),
            choicesCount = o.optInt("choices", 0),
            sheetPath = str("sheet"),
        )
    }

    // endregion
}
