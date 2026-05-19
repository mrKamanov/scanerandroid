package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: параметры раскладки бланка (вопросы, варианты, столбцы).
 */
data class BlankEditorLayoutConfig(
    val questionCount: Int,
    val optionCount: Int,
    val columnCount: Int,
) {
    init {
        require(columnCount == 1 || columnCount == 2) { "columnCount must be 1 or 2" }
    }

    companion object {
        fun default(): BlankEditorLayoutConfig = BlankEditorLayoutConfig(
            questionCount = 10,
            optionCount = 5,
            columnCount = 1,
        )
    }
}
