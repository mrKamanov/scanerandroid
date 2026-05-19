package com.tscan.scanertestov.feature.instructions

/**
 * Описание: тексты пользовательских инструкций по разделам приложения.
 */
internal object InstructionsCatalog {

    val topics: List<InstructionTopic> = listOf(
        topicStart(),
        topicPrivacy(),
        topicPhoto(),
        topicFilling(),
        topicRealtime(),
        topicCorrections(),
        topicBatch(),
        topicBlankEditor(),
        topicSettings(),
        topicReportsJournals(),
    )

    fun findTopic(id: String): InstructionTopic? = topics.find { it.id == id }

    fun topicIndex(id: String): Int = topics.indexOfFirst { it.id == id }.coerceAtLeast(0)

    fun previousTopic(id: String): InstructionTopic? =
        topics.getOrNull(topicIndex(id) - 1)

    fun nextTopic(id: String): InstructionTopic? =
        topics.getOrNull(topicIndex(id) + 1)

    private fun topicStart() = InstructionTopic(
        id = "start",
        title = "С чего начать",
        subtitle = "Первые шаги и выбор режима проверки",
        sections = listOf(
            InstructionSection(
                title = "Добро пожаловать",
                lead = "Приложение проверяет тестовые бланки по фото: сверяет отметки ученика с эталоном и показывает результат. " +
                    "На бланках используется нумерация ячеек вида 1.1, 1.2, 2.1 — номер вопроса и номер варианта ответа.",
            ),
            InstructionSection(
                title = "Шаг 1. Подготовьте бланк",
                steps = listOf(
                    InstructionStep(
                        title = "Создайте шаблон",
                        body = "В «Конструкторе бланков» настройте сетку, при необходимости скройте лишние ячейки и распечатайте бланки.",
                    ),
                    InstructionStep(
                        title = "Задайте эталон ответов",
                        body = "Перед проверкой отметьте правильные ячейки (например, 1.2 и 2.3) в «Быстрой проверке» или в «Пакетной обработке».",
                    ),
                ),
                tips = listOf(
                    "Объясните ученикам правила закрашивания — см. раздел «Как закрашивать ответы».",
                ),
            ),
            InstructionSection(
                title = "Шаг 2. Выберите режим",
                steps = listOf(
                    InstructionStep(
                        title = "Быстрая проверка",
                        body = "Один бланк сразу с экрана телефона: навести камеру, зафиксировать кадр и получить оценку.",
                    ),
                    InstructionStep(
                        title = "Пакетная обработка",
                        body = "Много работ подряд: снимаете или загружаете фото класса, один раз задаёте эталон и проверяете все листы.",
                    ),
                ),
                examples = listOf(
                    "Нужно проверить одну работу сразу после урока → «Быстрая проверка».",
                    "После контрольной сняли пачку бланков → «Пакетная обработка».",
                ),
            ),
            InstructionSection(
                title = "Шаг 3. Отчёты и журнал",
                steps = listOf(
                    InstructionStep(
                        title = "Отчёты",
                        body = "В разделе «Отчёты» появляются только те работы, которые вы сами добавили из результатов пакетной проверки. " +
                            "Там же можно выгрузить сводку в файл.",
                    ),
                    InstructionStep(
                        title = "Журналы",
                        body = "Заполните классы и ФИО учеников — это повышает точность распознавания имени на бланке и помогает формировать отчёты.",
                    ),
                ),
            ),
        ),
    )

    private fun topicPrivacy() = InstructionTopic(
        id = "privacy",
        title = "Данные на вашем устройстве",
        subtitle = "Без интернета, без серверов, без передачи данных",
        sections = listOf(
            InstructionSection(
                title = "Всё локально",
                lead = "Проверка бланков, распознавание отметок и расчёт оценки выполняются только на вашем телефоне или планшете. " +
                    "Для работы приложения не нужен доступ в интернет.",
            ),
            InstructionSection(
                title = "Что это значит для вас",
                steps = listOf(
                    InstructionStep(
                        title = "Без отправки данных",
                        body = "Мы не собираем и не передаём фотографии бланков, ответы учеников, журналы и отчёты на внешние серверы. " +
                            "Обработка не уходит «в облако».",
                    ),
                    InstructionStep(
                        title = "Хранение на устройстве",
                        body = "Журналы, результаты проверок, сохранённые шаблоны бланков и отчёты остаются в памяти телефона, " +
                            "в пределах работы приложения на этом устройстве.",
                    ),
                    InstructionStep(
                        title = "Интернет не обязателен",
                        body = "Снимать, проверять и смотреть итоги можно в классе или дома без Wi‑Fi и мобильной сети. " +
                            "Сеть понадобится только если вы сами решите отправить экспортированный файл (почта, мессенджер) через стандартное окно Android.",
                    ),
                ),
                tips = listOf(
                    "Экспорт PDF, Excel или бланка — это сохранение файла на устройстве и выбор, куда его отправить; приложение само по себе данные никуда не выгружает.",
                ),
            ),
        ),
    )

    private fun topicPhoto() = InstructionTopic(
        id = "photo",
        title = "Как снимать бланк",
        subtitle = "Свет, ракурс и рамка",
        sections = listOf(
            InstructionSection(
                title = "Главное правило",
                lead = "Весь бланк должен быть в кадре, без сильных бликов и обрезанных углов. " +
                    "Точность распознавания моделей высокая (около 99,88%), но сильнее всего на результат влияет угол съёмки: " +
                    "телефон держите почти под прямым углом к листу (ближе к 90°), а не «сбоку».",
            ),
            InstructionSection(
                title = "Пошагово",
                steps = listOf(
                    InstructionStep(
                        title = "Ровная поверхность",
                        body = "Положите лист на стол или парту без загибов по краям.",
                    ),
                    InstructionStep(
                        title = "Параллельно листу",
                        body = "Камера смотрит сверху на бланк, а не под наклоном — так сетка ячеек 1.1, 1.2… не искажается.",
                    ),
                    InstructionStep(
                        title = "Контур на экране",
                        body = "В быстрой проверке дождитесь устойчивой рамки вокруг листа. " +
                            "В пакетной камере вложите чёрную рамку бланка в белый ориентир.",
                    ),
                    InstructionStep(
                        title = "Свет и вспышка",
                        body = "При недостатке света включите вспышку.",
                        buttonHint = InstructionUiRefs.RealtimeFlashOff,
                    ),
                    InstructionStep(
                        title = "Ничего не перекрывать",
                        body = "Пальцы и посторонние предметы не должны закрывать кружки ответов и поле с именем.",
                    ),
                ),
                tips = listOf(
                    "Лучше переснять, чем проверять смазанное или тёмное фото.",
                    "Один кадр — один бланк.",
                ),
            ),
        ),
    )

    private fun topicFilling() = InstructionTopic(
        id = "filling",
        title = "Как закрашивать ответы",
        subtitle = "Правила для учеников — объясните перед работой",
        sections = listOf(
            InstructionSection(
                title = "Чем закрашивать",
                lead = "Подойдут карандаш (лучше мягкий, например 2B) или ручка. " +
                    "Главное — закрасить кружок плотно и аккуратно, без лишних помарок вокруг.",
                steps = listOf(
                    InstructionStep(
                        title = "Закрашивайте уверенно",
                        body = "Чем плотнее и ровнее заливка внутри кружка, тем надёжнее программа распознает ответ.",
                    ),
                    InstructionStep(
                        title = "Без лишних штрихов",
                        body = "Не ставьте галочки и крестики рядом с кружком — только закрашенный кружок считается ответом.",
                    ),
                ),
            ),
            InstructionSection(
                title = "Если нужно исправить ответ",
                steps = listOf(
                    InstructionStep(
                        title = "Ручка: зачеркните жирным крестиком",
                        body = "Закрашенный кружок перечёркивают накрест жирными линиями — так видно, что ответ отменён. " +
                            "Новый вариант закрашивают в другой ячейке (например, сначала 3.1, потом 3.2).",
                    ),
                    InstructionStep(
                        title = "Карандаш: можно стереть",
                        body = "Карандашом закраску можно аккуратно стереть ластиком и закрасить другой кружок.",
                    ),
                ),
                tips = listOf(
                    "После исправления при проверке приложение может спросить, как засчитать спорную ячейку — см. раздел «Исправления на бланке».",
                ),
                examples = listOf(
                    "Ученик закрасил ячейку 2.1, зачеркнул крестиком и закрасил 2.3 — при проверке ячейку 2.1 обычно отмечают как «Пусто», 2.3 — как «Отмечено».",
                ),
            ),
        ),
    )

    private fun topicRealtime() = InstructionTopic(
        id = "realtime",
        title = "Быстрая проверка",
        subtitle = "Один бланк через камеру",
        sections = listOf(
            InstructionSection(
                title = "Подготовка",
                steps = listOf(
                    InstructionStep(
                        title = "Задайте эталон",
                        body = "Нажмите кнопку обновления эталона и отметьте правильные ячейки (например, 1.2, 2.1, 3.4). " +
                            "В сетке подписи совпадают с бланком: вопрос.вариант.",
                        buttonHint = InstructionUiRefs.RealtimeSyncEtalon,
                    ),
                    InstructionStep(
                        title = "Сетка на кадре",
                        body = "Включите сетку, чтобы сверить эталон поверх замороженного изображения.",
                        buttonHint = InstructionUiRefs.RealtimeGridOn,
                    ),
                    InstructionStep(
                        title = "Число вопросов и вариантов",
                        body = "В панели параметров укажите структуру бланка, если она отличается от текущей.",
                        buttonHint = InstructionUiRefs.RealtimeCameraSettings,
                    ),
                ),
            ),
            InstructionSection(
                title = "Проверка",
                steps = listOf(
                    InstructionStep(
                        title = "Наведите камеру",
                        body = "Дождитесь устойчивого контура бланка на превью.",
                    ),
                    InstructionStep(
                        title = "Зафиксировать кадр",
                        body = "Нажмите большую кнопку со значком «стоп» — изображение замрёт, можно спокойно проверить кадр перед расчётом.",
                        buttonHint = InstructionUiRefs.RealtimeFreezeFrame,
                    ),
                    InstructionStep(
                        title = "Запуск проверки",
                        body = "Нажмите кнопку с галочкой и дождитесь результата.",
                        buttonHint = InstructionUiRefs.RealtimeRunCheck,
                    ),
                ),
                examples = listOf(
                    "Зелёная подсветка — совпало с эталоном, красная — ошибка, жёлтая — нужно уточнить исправление.",
                ),
            ),
            InstructionSection(
                title = "Нижняя панель",
                steps = listOf(
                    InstructionStep(
                        title = "Обновить эталон",
                        body = "Изменить правильные ячейки перед следующей работой.",
                        buttonHint = InstructionUiRefs.RealtimeSyncEtalon,
                    ),
                    InstructionStep(
                        title = "Сетка эталона",
                        body = "Показать или скрыть сетку на кадре.",
                        buttonHint = InstructionUiRefs.RealtimeGridOn,
                    ),
                    InstructionStep(
                        title = "Параметры",
                        body = "Количество вопросов, вариантов, колонок на листе.",
                        buttonHint = InstructionUiRefs.RealtimeCameraSettings,
                    ),
                ),
            ),
        ),
    )

    private fun topicCorrections() = InstructionTopic(
        id = "corrections",
        title = "Исправления на бланке",
        subtitle = "Зачёркнутые или перечёркнутые отметки",
        sections = listOf(
            InstructionSection(
                title = "Что происходит",
                lead = "Если ученик исправил ответ (крестиком, стёр ластиком и закрасил другую ячейку), программа просит вас уточнить спорные ячейки.",
            ),
            InstructionSection(
                title = "Как ответить",
                steps = listOf(
                    InstructionStep(
                        title = "Смотрите на превью и фрагмент",
                        body = "Сверху подсвечена текущая ячейка (например, 4.2), внизу — увеличенный фрагмент.",
                    ),
                    InstructionStep(
                        title = "«Отмечено»",
                        body = "Ячейку засчитывают как закрашенную.",
                        buttonHint = InstructionUiRefs.FixedAnswered,
                    ),
                    InstructionStep(
                        title = "«Пусто»",
                        body = "Ячейку считают пустой — отметка отменена.",
                        buttonHint = InstructionUiRefs.FixedEmpty,
                    ),
                ),
                examples = listOf(
                    "Закрасили 5.1, зачеркнули крестиком, закрасили 5.3 → для 5.1 чаще выбирают «Пусто», для 5.3 — «Отмечено».",
                ),
            ),
        ),
    )

    private fun topicBatch() = InstructionTopic(
        id = "batch",
        title = "Пакетная обработка",
        subtitle = "Много бланков за один запуск",
        sections = listOf(
            InstructionSection(
                title = "Шаг 1. Добавьте работы",
                steps = listOf(
                    InstructionStep(
                        title = "Сделать фото",
                        body = "Камера с ориентиром: вложите рамку бланка в контур на экране.",
                        buttonHint = InstructionUiRefs.BatchCamera,
                    ),
                    InstructionStep(
                        title = "Галерея",
                        body = "Выберите готовые снимки с телефона.",
                        buttonHint = InstructionUiRefs.BatchGallery,
                    ),
                ),
            ),
            InstructionSection(
                title = "Шаг 2. Настройте проверку",
                steps = listOf(
                    InstructionStep(
                        title = "Структура бланка",
                        body = "Укажите число вопросов и вариантов ответа (ячейки 1.1, 1.2… по вашему шаблону).",
                    ),
                    InstructionStep(
                        title = "Правильные ответы",
                        body = "В сетке отметьте верные ячейки. Если несколько версий теста — добавьте варианты ключей кнопками «Добавить» / «Удалить».",
                    ),
                    InstructionStep(
                        title = "Колонки и строгая проверка",
                        body = "Выберите 1 или 2 колонки на листе. «Проверять строго» — только полное совпадение с эталоном.",
                    ),
                ),
            ),
            InstructionSection(
                title = "Шаг 3. Проверка и отчёт",
                steps = listOf(
                    InstructionStep(
                        title = "Проверить работы",
                        body = "Запустите расчёт по всей очереди.",
                        buttonHint = InstructionUiRefs.BatchRunCheck,
                    ),
                    InstructionStep(
                        title = "Добавить в отчёты",
                        body = "На экране результатов нажмите кнопку отправки работ в отчёты (все сразу или по отдельности). " +
                            "Пока вы этого не сделаете, в разделе «Отчёты» данных не будет.",
                    ),
                ),
            ),
        ),
    )

    private fun topicBlankEditor() = InstructionTopic(
        id = "blank_editor",
        title = "Конструктор бланков",
        subtitle = "Шаблон, элементы на листе и экспорт",
        sections = listOf(
            InstructionSection(
                title = "Редактирование листа",
                steps = listOf(
                    InstructionStep(
                        title = "Скрыть лишние ячейки",
                        body = "На превью листа нажимайте на кружки ответов — можно убирать ненужные варианты в строке вопроса " +
                            "(например, оставить только 1.1–1.3 из пяти). В строке должно остаться не меньше двух видимых ячеек.",
                    ),
                    InstructionStep(
                        title = "Переместить блок бланка",
                        body = "Нажмите на область с кружками (рамка бланка), затем перетащите её пальцем по листу.",
                    ),
                    InstructionStep(
                        title = "Масштаб",
                        body = "Выделите блок бланка или текстовый элемент. Кнопками «−» и «+» измените масштаб — процент показан рядом.",
                        buttonHints = listOf(
                            InstructionUiRefs.BlankScaleMinus,
                            InstructionUiRefs.BlankScalePlus,
                        ),
                    ),
                    InstructionStep(
                        title = "Текстовые блоки",
                        body = "Переключателями «Показывать…» включайте или скрывайте подписи и инструкцию. Текст можно перетаскивать по листу. " +
                            "Для каждого блока настраиваются видимость и размер шрифта.",
                    ),
                ),
            ),
            InstructionSection(
                title = "Два способа сохранить",
                lead = "Кнопка «Экспорт» готовит файл и открывает системное окно «Отправить» — оттуда можно сохранить, отправить на почту или в мессенджер.",
                steps = listOf(
                    InstructionStep(
                        title = "Только сетка бланка (картинка)",
                        body = "Формат PNG без фона — удобно вставить картинку бланка в лист с заданиями в Word или на слайд, " +
                            "не печатая отдельный полный лист.",
                        buttonHint = InstructionUiRefs.BlankExport,
                    ),
                    InstructionStep(
                        title = "Полный лист для печати",
                        body = "PDF или DOCX — готовый бланк со всеми полями. PNG с фоном — картинка целого листа A4.",
                    ),
                    InstructionStep(
                        title = "Как отправить",
                        body = "После экспорта выберите в окне «Отправить» приложение: почта, Telegram, «Сохранить в файлы», Google Drive и т.д.",
                    ),
                ),
                tips = listOf(
                    "Для одного класса можно выгрузить пакет бланков с ФИО из журнала — в диалоге экспорта выберите класс и формат.",
                ),
            ),
        ),
    )

    private fun topicSettings() = InstructionTopic(
        id = "settings",
        title = "Настройки и оценки",
        subtitle = "Критерии и модели распознавания",
        sections = listOf(
            InstructionSection(
                title = "Критерии оценивания",
                lead = "Шкала гибкая: вы сами задаёте границы оценок от 2 до 5.",
                steps = listOf(
                    InstructionStep(
                        title = "Режим «Проценты» или «Баллы»",
                        body = "В настройках переключите тип: оценка по среднему проценту правильных ответов или по сумме баллов за вопросы.",
                    ),
                    InstructionStep(
                        title = "Свои пороги",
                        body = "Для каждой оценки укажите диапазон «от» и «до». Можно сбросить к стандартным и настроить заново.",
                    ),
                    InstructionStep(
                        title = "Шаблон в пакетной проверке",
                        body = "Имя и сохранённый набор критериев можно задать на экране пакетной обработки и быстро загружать перед проверкой.",
                    ),
                ),
            ),
            InstructionSection(
                title = "Модели распознавания",
                steps = listOf(
                    InstructionStep(
                        title = "Три варианта",
                        body = "«Быстрая», «Сбалансированная» (рекомендуется) и «Максимальная». Точность распознавания отметок у всех моделей высокая — около 99,88%.",
                    ),
                    InstructionStep(
                        title = "Что важнее модели",
                        body = "На практике сильнее влияют качество снимка и угол камеры (ближе к 90° к листу), плотность закраски и аккуратность эталона ответов.",
                    ),
                ),
            ),
        ),
    )

    private fun topicReportsJournals() = InstructionTopic(
        id = "reports_journals",
        title = "Отчёты и журналы",
        subtitle = "Сводки, экспорт и списки учеников",
        sections = listOf(
            InstructionSection(
                title = "Журналы",
                lead = "Журнал нужен, чтобы повышать точность распознавания ФИО на бланке и чтобы в отчётах были привязаны ученики и классы.",
                steps = listOf(
                    InstructionStep(
                        title = "Заполните классы",
                        body = "Добавьте учеников вручную или импортируйте список. Фамилия и имя должны быть близки к тому, как ученик пишет на бланке.",
                    ),
                    InstructionStep(
                        title = "При пакетной проверке",
                        body = "Если имя на снимке совпало с одной записью журнала, приложение подставит класс в карточку работы.",
                    ),
                ),
            ),
            InstructionSection(
                title = "Отчёты",
                steps = listOf(
                    InstructionStep(
                        title = "Как попадают работы",
                        body = "Данные в «Отчётах» появляются только после того, как вы добавили работы с экрана результатов пакетной проверки " +
                            "(кнопка отправки в отчёты — для всех сразу или для выбранных).",
                    ),
                    InstructionStep(
                        title = "Экспорт отчёта",
                        body = "Внизу экрана «Отчёты» нажмите «Экспорт» и выберите PDF или Excel.",
                        buttonHint = InstructionUiRefs.ReportsExport,
                    ),
                    InstructionStep(
                        title = "Отправить файл",
                        body = "После подготовки файла откроется окно «Отправить» — выберите почту, мессенджер или «Сохранить в файлы», как при экспорте бланка.",
                    ),
                ),
                examples = listOf(
                    "Проверили класс → на результатах нажали «Отправить в отчёты» → в «Отчётах» смотрите сводку → «Экспорт» → PDF на почту коллеге.",
                ),
            ),
        ),
    )
}
