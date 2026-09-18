# Модели для сборки APK

В репозитории приложения на GitHub **нет** файлов `.onnx` — они слишком большие (см. `.gitignore`). Перед запуском/сборкой положите модели локально.

## OMR (ячейки ответов)

Источник: [TscanSDK_IIMODELS/omr_models](https://github.com/mrKamanov/TscanSDK_IIMODELS/tree/master/omr_models)

| Путь в `assets/` | Файл в репозитории моделей |
|------------------|----------------------------|
| `models/64x64_model/best_medium_model.onnx` | `omr_models/64x64_model/best_medium_model.onnx` |
| `models/128x128_model/best_128x128_model.onnx` | `omr_models/128x128_model/best_128x128_model.onnx` |
| `models/256x256_model/best_256x256_model.onnx` | `omr_models/256x256_model/best_256x256_model.onnx` |

Скачать одной командой (PowerShell, из корня проекта):

```powershell
$base = "https://raw.githubusercontent.com/mrKamanov/TscanSDK_IIMODELS/master/omr_models"
$dest = "app/src/main/assets/models"
New-Item -ItemType Directory -Force -Path "$dest/64x64_model","$dest/128x128_model","$dest/256x256_model" | Out-Null
Invoke-WebRequest "$base/64x64_model/best_medium_model.onnx" -OutFile "$dest/64x64_model/best_medium_model.onnx"
Invoke-WebRequest "$base/128x128_model/best_128x128_model.onnx" -OutFile "$dest/128x128_model/best_128x128_model.onnx"
Invoke-WebRequest "$base/256x256_model/best_256x256_model.onnx" -OutFile "$dest/256x256_model/best_256x256_model.onnx"
```

## OCR (имена / цифры)

В `TscanSDK_IIMODELS` OCR-моделей нет. Нужны отдельно:

| Путь в `assets/` | Назначение |
|------------------|------------|
| `models/ocr/eslav_PP-OCRv5_mobile_rec/model.onnx` | OCR (имена) |
| `models/ocr/cyrillic_PP-OCRv3_rec/model.onnx` | OCR (кириллица) |
| `models/ocr/en_number_mobile_v2.0_rec/model.onnx` | OCR (цифры / вариант) |

Файлы `dict.txt` в подпапках OCR уже в git. Без OCR `.onnx` пакетная проверка ячеек может работать на OMR, но авто-распознавание ФИО и номера варианта — нет.

Обучение и датасеты — в локальной папке `iimodel/` (в git не входит).
