package com.tscan.scanertestov.feature.tests

/**
 * Описание: «тест» = сохранённый эталон пакетной проверки (BatchCriteriaPreset): название, число вопросов
 * и вариантов ответа, колонки и ключи по вариантам теста. Отдельного хранилища нет — все эталоны,
 * сохранённые раньше на экране проверки, сразу видны в «Моих тестах».
 *
 * Здесь же — передача теста на другие экраны: с карточки теста учитель открывает мастер теста (правка, печать
 * бланков на последнем этапе) или проверку класса (ключ подставляется сам).
 */
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.blankeditor.BlankDesign
import com.tscan.scanertestov.feature.blankeditor.BlankEditorLayoutConfig
import com.tscan.scanertestov.feature.blankeditor.buildBlankBubbleLayout

/**
 * Этапы мастера. Тест: Название → Бланк (шаблон) → Вопросы и кружки → Правильные ответы → Критерии → Предпросмотр
 * и печать. Шаблон: Шаблон → Вопросы → Бланк на листе → Критерии → Готово. [Sheet] у теста открывается внутри
 * этапа «Бланк» (кнопка «Надписи и место на листе»).
 */
enum class TestWizardStep { Name, Template, Questions, Answers, Sheet, Grades, Done, Preview }

/** С чем открыть мастер: новый тест, правка теста, новый шаблон или правка шаблона. */
data class WizardRequest(
    val testId: String? = null,
    val templateMode: Boolean = false,
    val templateId: String? = null,
    val startStep: TestWizardStep? = null,
)

object PendingTestLaunch {
    @Volatile
    private var batchPresetId: String? = null

    @Volatile
    private var wizard: WizardRequest? = null

    fun openBatch(presetId: String) { batchPresetId = presetId }
    fun takeBatchPresetId(): String? = batchPresetId.also { batchPresetId = null }

    /** null — новый тест. */
    fun openEditor(presetId: String?, step: TestWizardStep? = null) {
        wizard = WizardRequest(testId = presetId, startStep = step)
    }

    /** null — новый шаблон. */
    fun openTemplateEditor(templateId: String?) {
        wizard = WizardRequest(templateMode = true, templateId = templateId)
    }

    fun takeWizardRequest(): WizardRequest = wizard.also { wizard = null } ?: WizardRequest()
}

/** Сколько вариантов теста у эталона (у старых эталонов без вариантов — один). */
val BatchCriteriaPreset.testVariantsCount: Int
    get() = variantAnswerKeys.size.coerceAtLeast(1)

/** Ключ задан полностью: в каждом вопросе каждого варианта выбран хотя бы один ответ. */
val BatchCriteriaPreset.isKeyComplete: Boolean
    get() {
        val keys = if (variantAnswerKeys.isNotEmpty()) variantAnswerKeys.map { it.correctAnswers } else listOf(correctAnswers)
        return keys.all { key -> key.size >= questionsCount && key.take(questionsCount).all { it.isNotEmpty() } }
    }

/** Оформление бланка теста; у старых тестов без оформления — бланк по умолчанию под их число вопросов. */
fun BatchCriteriaPreset.blankDesign(): BlankDesign =
    BlankDesign.fromJsonString(blankDesignJson)
        ?.takeIf { it.questions == questionsCount && it.options == choicesCount }
        ?: BlankDesign.defaultFor(questionsCount, choicesCount, columnCount, testVariantsCount)


/**
 * Одна или две колонки: одна, если рамка с кружками целиком помещается на лист А4 при стандартных размерах,
 * иначе две. Считается той же геометрией, что рисует бланк.
 */
fun autoColumnsFor(questions: Int, options: Int): Int {
    val layout = buildBlankBubbleLayout(BlankEditorLayoutConfig(questions.coerceIn(1, 35), options.coerceIn(2, 9), 1))
    // Высота самой рамки с кружками (сетка конструктора начинается с отступа, на лист бланк переносится отдельно).
    val frameHeight = layout.frames.maxOfOrNull { it.height } ?: 0f
    // Лист 842 по высоте; сверху нужны шапка, поле имени и шестиугольник варианта, снизу — поле.
    return if (frameHeight <= FRAME_MAX_HEIGHT_ONE_COLUMN) 1 else 2
}

/** Сколько места на листе остаётся рамке в одну колонку (лист 842 минус шапка, поле имени и поля). */
private const val FRAME_MAX_HEIGHT_ONE_COLUMN = 580f
