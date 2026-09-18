package com.tscan.scanertestov.feature.batch

/**
 * Описание: сохранение/восстановление состояния экрана пакетной обработки в SavedStateHandle.
 * Нужно, чтобы при возврате с камеры или экрана результатов ранее добавленные работы не терялись.
 */
import androidx.lifecycle.SavedStateHandle
import org.json.JSONArray
import org.json.JSONObject

object BatchProcessingPersistence {
    private const val KEY = "batch_processing_state_v1"

    fun restore(handle: SavedStateHandle): BatchProcessingState? {
        val json = handle.get<String>(KEY) ?: return null
        return runCatching { parse(json) }
            .onFailure { handle.remove<String>(KEY) }
            .getOrNull()
    }

    fun save(handle: SavedStateHandle, state: BatchProcessingState) {
        runCatching { handle[KEY] = toJson(state) }
    }

    fun clear(handle: SavedStateHandle) {
        handle.remove<String>(KEY)
    }

    private fun toJson(state: BatchProcessingState): String {
        val o = JSONObject()
        o.put("q", state.questionsCount)
        o.put("c", state.choicesCount)
        o.put("akCount", state.answerKeyVariantsCount)
        state.selectedAnswerKeyVariant?.let { o.put("akSel", it) }
        o.put("answers", answersToJson(state.correctAnswers))
        val variants = JSONObject()
        state.correctAnswersByVariant.forEach { (k, v) -> variants.put(k.toString(), answersToJson(v)) }
        o.put("variants", variants)
        o.put("draft", state.criteriaNameDraft)
        o.put("critExpanded", state.savedCriteriaExpanded)
        o.put("layout", state.pageLayout.name)
        o.put("strict", state.strictScoring)
        o.put("auto", state.autoRecognitionEnabled)
        state.previewWorkId?.let { o.put("preview", it) }
        val selected = JSONArray()
        state.selectedWorkIds.forEach { selected.put(it) }
        o.put("selected", selected)
        val items = JSONArray()
        state.workItems.forEach { items.put(workItemToJson(it)) }
        o.put("workItems", items)
        return o.toString()
    }

    private fun parse(json: String): BatchProcessingState {
        val o = JSONObject(json)
        val questions = o.optInt("q", 5).coerceIn(1, 35)
        val choices = o.optInt("c", 4).coerceIn(2, 9)
        val akCount = o.optInt("akCount", 0).coerceIn(0, 4)
        val itemsJson = o.optJSONArray("workItems") ?: JSONArray()
        val workItems = List(itemsJson.length()) { i -> parseWorkItem(itemsJson.getJSONObject(i)) }
        val preview = o.optString("preview").takeIf { it.isNotBlank() && workItems.any { w -> w.id == it } }
        val selected = buildSet {
            val arr = o.optJSONArray("selected") ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.optString(i))
        }.intersect(workItems.map { it.id }.toSet())
        return BatchProcessingState(
            workItems = workItems,
            selectedWorkIds = selected,
            previewWorkId = preview,
            answerKeyVariantsCount = akCount,
            selectedAnswerKeyVariant = if (o.has("akSel")) o.optInt("akSel").takeIf { it in 1..akCount } else null,
            questionsCount = questions,
            choicesCount = choices,
            correctAnswers = parseAnswers(o.optJSONArray("answers") ?: JSONArray()),
            correctAnswersByVariant = parseVariants(o.optJSONObject("variants")),
            criteriaNameDraft = o.optString("draft"),
            savedCriteriaExpanded = o.optBoolean("critExpanded", false),
            pageLayout = if (o.optString("layout").equals(BatchPageLayout.TwoColumns.name)) {
                BatchPageLayout.TwoColumns
            } else {
                BatchPageLayout.OneColumn
            },
            strictScoring = o.optBoolean("strict", true),
            autoRecognitionEnabled = o.optBoolean("auto", false),
            isProcessing = false,
        )
    }

    private fun workItemToJson(item: BatchWorkItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("displayName", item.displayName)
            put("subtitle", item.subtitle)
            put("uri", item.contentUri)
            put("src", item.source.name)
            put("status", item.status.name)
            put("auto", item.autoRecognitionRequested)
            item.detectedVariant?.let { put("dv", it) }
            item.detectedVariantConfidence?.let { put("dvc", it.toDouble()) }
            item.detectedVariantRawText?.let { put("dvrt", it) }
            put("det", item.isVariantDetecting)
            item.variantDetectionError?.let { put("vde", it) }
            item.variantOverride?.let { put("vo", it) }
            item.detectedStudentSurname?.let { put("ss", it) }
            item.detectedStudentName?.let { put("sn", it) }
            item.detectedStudentNameConfidence?.let { put("snc", it.toDouble()) }
            item.detectedStudentNameRawText?.let { put("snrt", it) }
            item.studentNameDetectionError?.let { put("sne", it) }
            item.detectedJournalClassName?.let { put("jcn", it) }
        }
    }

    private fun parseWorkItem(j: JSONObject): BatchWorkItem {
        return BatchWorkItem(
            id = j.optString("id"),
            title = j.optString("title"),
            displayName = j.optString("displayName"),
            subtitle = j.optString("subtitle"),
            contentUri = j.optString("uri"),
            source = runCatching { BatchWorkSource.valueOf(j.optString("src")) }.getOrDefault(BatchWorkSource.Gallery),
            status = runCatching { BatchWorkStatus.valueOf(j.optString("status")) }.getOrDefault(BatchWorkStatus.Queued),
            autoRecognitionRequested = j.optBoolean("auto", false),
            detectedVariant = if (j.has("dv")) j.optInt("dv") else null,
            detectedVariantConfidence = if (j.has("dvc")) j.optDouble("dvc", 0.0).toFloat() else null,
            detectedVariantRawText = if (j.has("dvrt")) j.optString("dvrt") else null,
            isVariantDetecting = j.optBoolean("det", false),
            variantDetectionError = if (j.has("vde")) j.optString("vde") else null,
            variantOverride = if (j.has("vo")) j.optInt("vo") else null,
            detectedStudentSurname = if (j.has("ss")) j.optString("ss") else null,
            detectedStudentName = if (j.has("sn")) j.optString("sn") else null,
            detectedStudentNameConfidence = if (j.has("snc")) j.optDouble("snc", 0.0).toFloat() else null,
            detectedStudentNameRawText = if (j.has("snrt")) j.optString("snrt") else null,
            studentNameDetectionError = if (j.has("sne")) j.optString("sne") else null,
            detectedJournalClassName = if (j.has("jcn")) j.optString("jcn") else null,
        )
    }

    private fun answersToJson(answers: List<Set<Int>>): JSONArray {
        val arr = JSONArray()
        answers.forEach { set ->
            val inner = JSONArray()
            set.sorted().forEach { inner.put(it) }
            arr.put(inner)
        }
        return arr
    }

    private fun parseAnswers(arr: JSONArray): List<Set<Int>> {
        return List(arr.length()) { i ->
            val inner = arr.optJSONArray(i) ?: JSONArray()
            buildSet {
                for (j in 0 until inner.length()) add(inner.optInt(j))
            }
        }
    }

    private fun parseVariants(obj: JSONObject?): Map<Int, List<Set<Int>>> {
        if (obj == null) return emptyMap()
        return buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = obj.optJSONArray(key) ?: continue
                val variant = key.toIntOrNull() ?: continue
                put(variant, parseAnswers(arr))
            }
        }
    }
}