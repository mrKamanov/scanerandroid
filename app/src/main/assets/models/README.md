# Модели для сборки APK

В репозитории на GitHub **нет** файлов `.onnx` — они слишком большие. Перед сборкой положите модели в эту папку в те же пути, что ожидает код:

| Путь в `assets/` | Назначение |
|------------------|------------|
| `models/64x64_model/best_medium_model.onnx` | OMR, вариант 64×64 |
| `models/128x128_model/best_128x128_model.onnx` | OMR, вариант 128×128 |
| `models/256x256_model/best_256x256_model.onnx` | OMR, вариант 256×256 |
| `models/ocr/eslav_PP-OCRv5_mobile_rec/model.onnx` | OCR (имена) |
| `models/ocr/cyrillic_PP-OCRv3_rec/model.onnx` | OCR (кириллица) |
| `models/ocr/en_number_mobile_v2.0_rec/model.onnx` | OCR (цифры) |

Файлы `dict.txt` в подпапках OCR уже в git. Без `.onnx` приложение соберётся, но распознавание не заработает до добавления моделей.

Обучение и датасеты — в локальной папке `iimodel/` (в git не входит).
