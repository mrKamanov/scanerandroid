package com.tscan.scanertestov.feature.tests

/**
 * Описание: кому по тесту выдан какой номер (код ученика на именном бланке v2, 8 квадратов → 1…63).
 *
 * Номера выдаются на весь тест, а не на класс: для 7 И — №1–25, для 7 Л — продолжение. Ученик при повторной
 * печати получает свой прежний номер. Фамилия, имя и класс запоминаются на момент печати — если ученика потом
 * переведут или переименуют в журнале, старые бланки всё равно указывают на него. По прочитанному коду
 * проверка (пакетная и «Один бланк») сразу подставляет ученика.
 *
 * Хранится отдельно от теста (SharedPreferences, ключ — id теста), одним JSON-массивом.
 */
import android.content.Context
import com.tscan.scanertestov.feature.journals.JournalStudent
import org.json.JSONArray
import org.json.JSONObject

/** Номер [code], выданный ученику; [variant] — вариант, напечатанный на его бланке (null — отмечает сам). */
data class IssuedStudentCode(
    val code: Int,
    val className: String,
    val surname: String,
    val name: String,
    val variant: Int? = null,
) {
    val fullName: String get() = listOf(surname, name).filter { it.isNotBlank() }.joinToString(" ")

    fun isSameStudent(st: JournalStudent): Boolean =
        className.trim().equals(st.className.trim(), true) &&
            surname.trim().equals(st.surname.trim(), true) &&
            name.trim().equals(st.name.trim(), true)
}

object StudentCodeStore {
    const val MAX_CODE = 63
    private const val PREFS = "student_codes"

    fun load(context: Context, testId: String): List<IssuedStudentCode> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(testId, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                IssuedStudentCode(
                    code = o.getInt("c"),
                    className = o.optString("cls"),
                    surname = o.optString("s"),
                    name = o.optString("n"),
                    variant = if (o.has("v")) o.getInt("v") else null,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun lookup(context: Context, testId: String?, code: Int?): IssuedStudentCode? {
        if (testId == null || code == null) return null
        return load(context, testId).firstOrNull { it.code == code }
    }

    /**
     * Номера для учеников [students] (порядок — как в списке): уже выданные остаются, новым — наименьшие свободные.
     * Ничего не сохраняет. null у ученика — свободных номеров не осталось.
     */
    fun plan(existing: List<IssuedStudentCode>, students: List<JournalStudent>): List<Int?> {
        val used = existing.map { it.code }.toMutableSet()
        return students.map { st ->
            existing.firstOrNull { it.isSameStudent(st) }?.code ?: (1..MAX_CODE).firstOrNull { it !in used }?.also { used += it }
        }
    }

    /** Забыть все номера теста — вызывается при удалении теста. */
    fun deleteTest(context: Context, testId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(testId).apply()
    }

    /** Запомнить выданные номера (с вариантами); у этих учеников прежние записи заменяются. */
    fun issue(context: Context, testId: String, issued: List<IssuedStudentCode>) {
        if (issued.isEmpty()) return
        val codes = issued.map { it.code }.toSet()
        val keep = load(context, testId).filter { it.code !in codes }
        val arr = JSONArray()
        (keep + issued).sortedBy { it.code }.forEach { c ->
            arr.put(
                JSONObject().put("c", c.code).put("cls", c.className).put("s", c.surname).put("n", c.name)
                    .apply { c.variant?.let { put("v", it) } },
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(testId, arr.toString()).apply()
    }
}
