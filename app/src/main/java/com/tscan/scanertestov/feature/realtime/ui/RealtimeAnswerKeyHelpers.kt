package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: эталонные ответы — дополнение списка и переключение вариантов по вопросу.
 */
internal fun padAnswerKey(existing: List<Set<Int>>, questionsCount: Int): List<Set<Int>> =
    List(questionsCount) { q -> existing.getOrNull(q).orEmpty() }

internal fun toggleChoice(
    current: List<Set<Int>>,
    questionIndex: Int,
    choiceIndex: Int,
    choicesPerQuestion: Int,
    questionsCount: Int,
): List<Set<Int>> {
    val padded = padAnswerKey(current, questionsCount)
    val row = padded[questionIndex].toMutableSet()
    if (choiceIndex in row) {
        row.remove(choiceIndex)
    } else {
        if (row.size >= choicesPerQuestion - 1) {
            return padded
        }
        row.add(choiceIndex)
    }
    return padded.mapIndexed { i, s -> if (i == questionIndex) row.toSet() else s }
}
