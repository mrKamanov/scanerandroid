package com.tscan.scanertestov.feature.settings

import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.data.InferenceModelVariant

/**
 * Описание: состояние экрана настроек и подписи к разделам.
 */
data class SettingsState(
    val navigationTitle: String = "Навигация",
    val quickAccessBarSwitchText: String = "Панель быстрого доступа",
    val isQuickAccessBarEnabled: Boolean = false,
    val aiModelTitle: String = "Модель распознавания (ИИ)",
    val aiModelFastTitle: String = "Быстрая",
    val aiModelFastSubtitle: String = "Для слабых устройств, меньше нагрузка на устройство",
    val aiModelBalancedTitle: String = "Сбалансированная",
    val aiModelBalancedSubtitle: String = "Рекомендуется: баланс скорости и точности",
    val aiModelPreciseTitle: String = "Максимальная",
    val aiModelPreciseSubtitle: String = "Для мощных устройств, выше нагрузка и точность",
    val selectedInferenceModel: InferenceModelVariant = InferenceModelVariant.BALANCED_128,
    val omrConfidenceTitle: String = "Порог уверенности ИИ (OMR)",
    val omrConfidenceHint: String =
        "Максимальная вероятность softmax по классам ячейки. Ниже порога — ответ «сомнительный». Меньше порог — реже сомнительные при спорных вероятностях; если модель уже уверенно выбрала «пусто» или «закрашено», ползунок это не перевернёт. Действует с нового прогона пакетной обработки (уже посчитанный бланк не пересчитывается сам).",
    val omrConfidenceValueLabel: String = "Сейчас",
    val omrConfidenceResetText: String = "Сбросить к 0,70",
    val omrMlConfidenceThreshold: Float = 0.70f,
    val dataTitle: String = "Данные",
    val importStudentsText: String = "Импорт учеников (Excel)",
    val exportJournalsText: String = "Экспорт журналов",
    val importJournalsText: String = "Импорт журналов",
    val gradingTitle: String = "Критерии оценивания",
    val gradingHint: String = "Задают соответствие результата проверки бланка оценке от 2 до 5 на экране итогов пакетной обработки. В режиме «Проценты» используется средний процент по вопросам; в режиме «Баллы» — сумма баллов за вопросы (с учётом частичного зачёта, если он включён в шаблоне).",
    val gradingModePercent: String = "Проценты",
    val gradingModePoints: String = "Баллы",
    val gradingPercentIntro: String = "Границы целых процентов (0–100), сравнение по округлению вниз.",
    val gradingPointsIntro: String = "Границы суммы баллов за работу. Поля изначально пустые — введите свои «от» и «до» для каждой оценки.",
    val gradingRowFrom: String = "от",
    val gradingRowTo: String = "до",
    val gradingResetText: String = "Сбросить к стандартным",
    val gradingCriteria: GradingCriteriaConfig = GradingCriteriaConfig.defaultPercent(),
    val legalTitle: String = "Правовая информация",
    val legalPrivacyText: String = "Политика конфиденциальности",
    val legalPrivacyHint: String = "Откроется на GitHub в браузере",
    val legalSourceCodeText: String = "Исходный код (GPL-3.0)",
)
