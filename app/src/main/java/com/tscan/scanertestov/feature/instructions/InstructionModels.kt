package com.tscan.scanertestov.feature.instructions

/**
 * Описание: модели данных разделов и шагов инструкции.
 */
data class InstructionTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val sections: List<InstructionSection>,
)

/** Зацикленная анимация-подсказка внутри раздела (рисуется кодом, см. FillingAnimations.kt). */
enum class InstructionAnimation {
    FillBubble, WrongMarks, EraseAndRefill, CrossOutAndRefill,
    PhotoFraming, LiveCheck, NamedCode, VariantSheets, ExcelImport,
}

data class InstructionSection(
    val title: String,
    val lead: String? = null,
    val animations: List<InstructionAnimation> = emptyList(),
    val steps: List<InstructionStep> = emptyList(),
    val tips: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    /** Карточки-примеры кружков: что не так и как надо. */
    val pictures: List<InstructionPicture> = emptyList(),
)

/** Правильный кружок, нарисованный кодом (для сравнения с плохими вырезками). */
enum class GoodBubble { Filled, CrossedOut, Empty }

/**
 * Пример кружка: вырезка из настоящей проверки ([asset] в assets) или правильный кружок ([good]).
 * [problem] — что не так (или почему так правильно), [fix] — как надо.
 */
data class InstructionPicture(
    val title: String,
    val problem: String,
    val fix: String? = null,
    val asset: String? = null,
    val good: GoodBubble? = null,
)

data class InstructionStep(
    val title: String,
    val body: String,
    val buttonHints: List<InstructionUiRef> = emptyList(),
) {
    constructor(
        title: String,
        body: String,
        buttonHint: InstructionUiRef?,
    ) : this(title, body, if (buttonHint != null) listOf(buttonHint) else emptyList())
}
