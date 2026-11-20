# Модели для сборки APK

В репозитории приложения на GitHub **нет** файлов `.onnx` — они слишком большие (см. `.gitignore`). Перед запуском/сборкой положите модели локально.

## OMR (ячейки ответов)

Приложение использует **одну** модель: `models/128x128_model/best_128x128_model.onnx` — MobileNetV2, вход 128×128,
классы «пусто / закрашен / исправлен», **дообученная** на вырезках бланков приложения (скрипт
`claude/train/finetune_128.py`, результат — `claude/train/out/`). Прежние 64×64 и 256×256 убраны (на фото работали хуже,
выбора модели в настройках больше нет).

Исходная (до дообучения) модель — [TscanSDK_IIMODELS/omr_models/128x128_model](https://github.com/mrKamanov/TscanSDK_IIMODELS/tree/master/omr_models);
её копия — `claude/train/orig/`. Для сборки нужна именно дообученная — положите её из `claude/train/out/`.

## OCR (имена / цифры)

В `TscanSDK_IIMODELS` OCR-моделей нет. Нужны отдельно:

| Путь в `assets/` | Назначение |
|------------------|------------|
| `models/ocr/eslav_PP-OCRv5_mobile_rec/model.onnx` | OCR (имена) |
| `models/ocr/cyrillic_PP-OCRv3_rec/model.onnx` | OCR (кириллица) |
| `models/ocr/en_number_mobile_v2.0_rec/model.onnx` | OCR (цифры / вариант) |

Файлы `dict.txt` в подпапках OCR уже в git. Без OCR `.onnx` пакетная проверка ячеек может работать на OMR, но авто-распознавание ФИО и номера варианта — нет.

Обучение и датасеты — в локальной папке `iimodel/` (в git не входит).
