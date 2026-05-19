package com.tscan.scanertestov.feature.instructions

/**
 * Описание: состояние экрана инструкций (список тем из каталога).
 */
data class InstructionsState(
    val topics: List<InstructionTopic> = InstructionsCatalog.topics,
)
