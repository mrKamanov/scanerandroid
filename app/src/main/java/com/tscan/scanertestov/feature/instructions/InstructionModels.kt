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

data class InstructionSection(
    val title: String,
    val lead: String? = null,
    val steps: List<InstructionStep> = emptyList(),
    val tips: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
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
