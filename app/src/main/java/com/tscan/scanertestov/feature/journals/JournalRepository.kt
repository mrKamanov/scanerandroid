package com.tscan.scanertestov.feature.journals

/**
 * Описание: локальное хранение списков учеников журнала (SharedPreferences + JSON).
 */
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class JournalRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<JournalStudent> {
        val raw = prefs.getString(KEY_STUDENTS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    JournalStudent(
                        className = o.getString("className"),
                        surname = o.getString("surname"),
                        name = o.getString("name"),
                        middleName = o.optString("middleName").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    fun saveAll(items: List<JournalStudent>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("className", s.className)
                    put("surname", s.surname)
                    put("name", s.name)
                    put("middleName", s.middleName ?: "")
                },
            )
        }
        prefs.edit().putString(KEY_STUDENTS, arr.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "tscan_journal_v1"
        private const val KEY_STUDENTS = "students_json"
    }
}
