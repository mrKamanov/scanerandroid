package com.tscan.scanertestov.feature.batch.criteria

/**
 * Описание: чтение/запись пресетов эталона в SharedPreferences.
 */
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BatchCriteriaRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<BatchCriteriaPreset> {
        val raw = prefs.getString(KEY_DATA, "[]") ?: "[]"
        val out = mutableListOf<BatchCriteriaPreset>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val answersJson = obj.optJSONArray("correctAnswers") ?: JSONArray()
            val answers = mutableListOf<List<Int>>()
            for (j in 0 until answersJson.length()) {
                val cell = answersJson.get(j)
                when (cell) {
                    is JSONArray -> {
                        val one = mutableListOf<Int>()
                        for (k in 0 until cell.length()) {
                            one += cell.optInt(k, -1)
                        }
                        answers.add(one)
                    }
                    is Number -> {
                        answers.add(listOf(cell.toInt()))
                    }
                    else -> answers.add(emptyList())
                }
            }
            out += BatchCriteriaPreset(
                id = obj.getString("id"),
                name = obj.getString("name"),
                questionsCount = obj.optInt("questionsCount", 5),
                choicesCount = obj.optInt("choicesCount", 4),
                columnCount = obj.optInt("columnCount", 1).coerceIn(1, 2),
                correctAnswers = answers,
                variantAnswerKeys = readVariantAnswerKeys(obj),
            )
        }
        return out
    }

    fun saveAll(items: List<BatchCriteriaPreset>) {
        val arr = JSONArray()
        items.forEach { preset ->
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("questionsCount", preset.questionsCount)
                put("choicesCount", preset.choicesCount)
                put("columnCount", preset.columnCount)
                val answers = JSONArray()
                preset.correctAnswers.forEach { answerSet ->
                    answers.put(JSONArray(answerSet))
                }
                put("correctAnswers", answers)
                val variants = JSONArray()
                preset.variantAnswerKeys
                    .sortedBy { it.variant }
                    .forEach { variantItem ->
                        val vObj = JSONObject().apply {
                            put("variant", variantItem.variant)
                            val vAnswers = JSONArray()
                            variantItem.correctAnswers.forEach { set ->
                                vAnswers.put(JSONArray(set))
                            }
                            put("correctAnswers", vAnswers)
                        }
                        variants.put(vObj)
                    }
                put("variantAnswerKeys", variants)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_DATA, arr.toString()).apply()
    }

    fun add(preset: BatchCriteriaPreset) {
        val next = getAll().toMutableList().apply { add(preset) }
        saveAll(next)
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    fun getById(id: String): BatchCriteriaPreset? = getAll().firstOrNull { it.id == id }

    private companion object {
        const val PREF_NAME = "batch_criteria_presets"
        const val KEY_DATA = "presets_json"
    }

    private fun readVariantAnswerKeys(obj: JSONObject): List<BatchCriteriaVariantAnswers> {
        val arr = obj.optJSONArray("variantAnswerKeys") ?: return emptyList()
        val out = mutableListOf<BatchCriteriaVariantAnswers>()
        for (i in 0 until arr.length()) {
            val vObj = arr.optJSONObject(i) ?: continue
            val variant = vObj.optInt("variant", -1)
            if (variant < 1) continue
            val answersJson = vObj.optJSONArray("correctAnswers") ?: JSONArray()
            val answers = mutableListOf<List<Int>>()
            for (j in 0 until answersJson.length()) {
                val cell = answersJson.get(j)
                when (cell) {
                    is JSONArray -> {
                        val one = mutableListOf<Int>()
                        for (k in 0 until cell.length()) {
                            one += cell.optInt(k, -1)
                        }
                        answers.add(one)
                    }
                    is Number -> answers.add(listOf(cell.toInt()))
                    else -> answers.add(emptyList())
                }
            }
            out += BatchCriteriaVariantAnswers(
                variant = variant,
                correctAnswers = answers,
            )
        }
        return out.sortedBy { it.variant }
    }
}
