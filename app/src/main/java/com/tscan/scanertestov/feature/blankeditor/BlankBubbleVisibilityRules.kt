package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: правила скрытия и показа вариантов ответа в строке вопроса.
 */
private val numberedBubbleId = Regex("""^(\d+)\.(\d+)$""")

private fun isSuffixFromK(hiddenOptions: Set<Int>, optionCount: Int): Boolean {
    if (hiddenOptions.isEmpty()) return true
    val k = hiddenOptions.minOrNull() ?: return false
    if (hiddenOptions.maxOrNull() != optionCount) return false
    return hiddenOptions == (k..optionCount).toSet()
}

private fun hiddenOptionsForQuestion(hiddenIds: Set<String>, question: Int, optionCount: Int): Set<Int> =
    (1..optionCount).filter { o -> "$question.$o" in hiddenIds }.toSet()

fun applyBubbleVisibilityToggle(
    bubbleId: String,
    optionCount: Int,
    hiddenIds: Set<String>,
): Set<String>? {
    if (bubbleId.startsWith("empty:")) return null

    val m = numberedBubbleId.matchEntire(bubbleId) ?: return null
    val q = m.groupValues[1].toInt()
    val o = m.groupValues[2].toInt()
    if (o !in 1..optionCount) return null

    val key = "$q.$o"
    val rowHidden = hiddenOptionsForQuestion(hiddenIds, q, optionCount)
    if (!isSuffixFromK(rowHidden, optionCount)) return null

    return if (key in hiddenIds) {
        if (rowHidden.minOrNull() != o) return null
        hiddenIds - key
    } else {
        val mustBeHiddenToTheRight = ((o + 1)..optionCount).toSet()
        if (rowHidden != mustBeHiddenToTheRight) return null
        if (o - 1 < 2) return null
        hiddenIds + key
    }
}
