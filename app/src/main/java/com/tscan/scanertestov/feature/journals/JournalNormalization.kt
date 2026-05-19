package com.tscan.scanertestov.feature.journals

/**
 * Описание: нормализация названий классов и полей ФИО ученика в журнале.
 */
import java.util.Locale

private val ruLocale: Locale = Locale.forLanguageTag("ru")

private val CLASS_GRADE_PATTERN = Regex("^\\s*(\\d+)\\s*([А-Яа-яЁёA-Za-z]*)\\s*$")

fun normalizeJournalClassName(raw: String): String {
    val trimmed = raw.trim().replace(Regex("\\s+"), " ")
    val m = CLASS_GRADE_PATTERN.matchEntire(trimmed) ?: return trimmed
    val num = m.groupValues[1]
    val suffixLetters = m.groupValues[2].filter { it.isLetter() }
    val suffix = suffixLetters.uppercase(ruLocale)
    if (suffix.isEmpty() || suffix == "И") {
        return "$num И"
    }
    return "$num $suffix"
}

fun normalizePersonNameField(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.split('-').joinToString("-") { segment ->
                segment.lowercase(ruLocale).replaceFirstChar { ch ->
                    if (ch.isLetter()) ch.titlecase(ruLocale) else ch.toString()
                }
            }
        }
}

fun JournalStudent.normalized(): JournalStudent =
    JournalStudent(
        className = normalizeJournalClassName(className),
        surname = normalizePersonNameField(surname),
        name = normalizePersonNameField(name),
        middleName = middleName?.trim()?.takeIf { it.isNotBlank() }?.let { normalizePersonNameField(it) },
    )
