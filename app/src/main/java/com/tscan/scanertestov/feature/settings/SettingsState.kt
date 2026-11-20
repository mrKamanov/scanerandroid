package com.tscan.scanertestov.feature.settings

import com.tscan.scanertestov.data.GradingCriteriaConfig

/**
 * Описание: состояние экрана настроек и подписи к разделам.
 */
data class SettingsState(
    val navigationTitle: String = "Навигация",
    val quickAccessBarSwitchText: String = "Панель быстрого доступа",
    val isQuickAccessBarEnabled: Boolean = false,
    val aiModelTitle: String = "Модель распознавания (ИИ)",
    val aiModelSummary: String = "Каждый кружок бланка проверяет нейросеть, дообученная на настоящих бланках этого " +
        "приложения. Она отличает пустой кружок, закрашенный и исправленный (закрашен и перечёркнут). Работает прямо " +
        "на телефоне, без интернета. Выбирать ничего не нужно.",
    val aiModelFacts: List<Pair<String, String>> = listOf(
        "Что видит" to "Вырезку одного кружка 128×128 точек",
        "Три ответа" to "Пусто · закрашен · исправлен",
        "Точность" to "99,87 % при нормальных условиях: ровный снимок, хороший свет, плотная закраска",
        "Слабая штриховка" to "Дообучена на бледных и неполных закрасках — такие кружки больше не пропускает",
        "Второе мнение" to "Если нейросеть сомневается, приложение дополнительно сверяет темноту кружка",
        "Скорость" to "Около секунды на бланк в «Один бланк в камере», в пакетной проверке — быстрее",
    ),
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
    val gradingTitle: String = "Критерии оценивания по умолчанию",
    val gradingHint: String = "Действуют для тестов, у которых не заданы свои критерии (их задают в «Тесты» → тест → «Критерии оценки»). Задают соответствие результата проверки бланка оценке от 2 до 5 на экране итогов пакетной обработки. В режиме «Проценты» используется средний процент по вопросам; в режиме «Баллы» — сумма баллов за вопросы (с учётом частичного зачёта, если он включён в шаблоне).",
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
)
