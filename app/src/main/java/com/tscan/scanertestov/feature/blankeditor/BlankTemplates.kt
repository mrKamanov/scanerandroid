package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: оформление бланка (BlankDesign) и шаблоны (BlankTemplate).
 *
 * Оформление — всё, что видно на листе: число вопросов и ответов, колонки, размеры кружков и сетки, где стоит
 * рамка и её масштаб, скрытые кружки, надписи шапки (текст, место, шрифт), поле «Вариант» и поле «Имя ученика».
 * Оно хранится внутри теста (BatchCriteriaPreset.blankDesignJson), поэтому бланк и ключ теста не расходятся.
 *
 * Шаблон — оформление + число вариантов теста + критерии оценки, с названием. Готовые шаблоны зашиты в код,
 * свои хранятся в SharedPreferences одним JSON-массивом.
 */
import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.tscan.scanertestov.data.PrintedBlankParamsStore
import org.json.JSONArray
import org.json.JSONObject

data class BlankDesign(
    val questions: Int,
    val options: Int,
    val columns: Int,
    val render: BlankBubbleRenderParams,
    val pan: Offset,
    val scale: Float,
    val hiddenBubbleIds: Set<String>,
    val textBlocks: List<BlankTextBlock>,
    val variantMarker: Boolean,
    val variantPrinted: Boolean,
    val variantNumber: String,
    val nameMarker: Boolean,
    val namePrinted: Boolean,
    /** Бланк v2 с угловыми метками (по умолчанию). false — старый формат, только для совместимости. */
    val formatV2: Boolean = true,
    /**
     * Кружки, которых нет на бланке конкретного варианта (сверх [hiddenBubbleIds]): вариант → id «q.o».
     * Если хоть у одного варианта они есть, бланки вариантов разные и печатаются с закрашенным номером варианта.
     * Только для бланка v2 (номер варианта — кружками).
     */
    val variantHidden: Map<Int, Set<String>> = emptyMap(),
) {
    /** Кружки, которых нет на бланке варианта [variant] (null — общие для всех). */
    fun hiddenFor(variant: Int?): Set<String> =
        if (variant == null) hiddenBubbleIds else hiddenBubbleIds + variantHidden[variant].orEmpty()

    /** У вариантов разные бланки: печать по варианту, номер варианта закрашен. */
    val variantsDiffer: Boolean get() = formatV2 && variantHidden.values.any { it.isNotEmpty() }

    /** Сколько кружков вопроса [q] (с 1) на бланке варианта [variant] (null — общий бланк). */
    fun visibleCount(q: Int, variant: Int?): Int {
        val h = hiddenFor(variant)
        return options - (1..options).count { "$q.$it" in h }
    }

    /**
     * Оставить в вопросе [q] на всех бланках [k] первых кружков. У вариантов, где в этом вопросе кружков было
     * меньше, их остаётся столько же (но не больше, чем у всех) — убранные всегда идут с конца строки.
     */
    fun withVisibleForAll(q: Int, k: Int, variants: Int): BlankDesign {
        val kept = hiddenBubbleIds.filterNot { it.startsWith("$q.") }.toSet()
        val vh = variantHidden.mapValues { (v, ids) ->
            val had = ids.any { it.startsWith("$q.") }
            val rest = ids.filterNot { it.startsWith("$q.") }.toSet()
            if (!had) rest else rest + ((minOf(visibleCount(q, v), k) + 1)..k).map { "$q.$it" }
        }
        return copy(hiddenBubbleIds = kept + ((k + 1)..options).map { "$q.$it" }, variantHidden = vh)
            .cleanedVariantHidden(variants)
    }

    /** Оставить в вопросе [q] на бланке варианта [v] [k] первых кружков (не больше, чем на общем). */
    fun withVisibleForVariant(q: Int, v: Int, k: Int, variants: Int): BlankDesign {
        val g = visibleCount(q, null)
        val rest = variantHidden[v].orEmpty().filterNot { it.startsWith("$q.") }.toSet()
        return copy(variantHidden = variantHidden + (v to rest + ((k + 1)..g).map { "$q.$it" }))
            .cleanedVariantHidden(variants)
    }

    /**
     * Нажатие на кружок «q.o» — одно правило для карточки «Лишние кружки» и для листа.
     * [variant] null — все бланки, иначе только бланк этого варианта. Нажатие на кружок убирает его и все правее
     * (меньше двух не остаётся), на убранный — возвращает его и те, что левее.
     * Возвращает новое оформление или текст, почему нельзя.
     */
    fun tapBubble(id: String, variant: Int?, variants: Int): Pair<BlankDesign?, String?> {
        val q = id.substringBefore('.').toIntOrNull() ?: return null to null
        val o = id.substringAfter('.').toIntOrNull() ?: return null to null
        if (q !in 1..questions || o !in 1..options) return null to null
        val g = visibleCount(q, null)
        val k = visibleCount(q, variant)
        val removed = o > k
        return when {
            variant != null && o > g -> null to "Этот кружок убран у всех вариантов — вернуть его можно на «Все»"
            removed -> (if (variant == null) withVisibleForAll(q, o, variants) else withVisibleForVariant(q, variant, o, variants)) to null
            o <= 2 -> null to "В вопросе должно остаться минимум 2 кружка"
            else -> (if (variant == null) withVisibleForAll(q, o - 1, variants) else withVisibleForVariant(q, variant, o - 1, variants)) to null
        }
    }

    /** Убрать лишнее: варианты больше [variants], кружки за пределами сетки и уже убранные у всех. */
    fun cleanedVariantHidden(variants: Int): BlankDesign {
        val clean = variantHidden
            .filterKeys { it in 1..variants }
            .mapValues { (_, ids) ->
                ids.filter { id ->
                    val q = id.substringBefore('.').toIntOrNull() ?: 0
                    val o = id.substringAfter('.').toIntOrNull() ?: 0
                    q in 1..questions && o in 1..options && id !in hiddenBubbleIds
                }.toSet()
            }
            .filterValues { it.isNotEmpty() }
        return if (clean == variantHidden) this else copy(variantHidden = clean)
    }

    val layoutConfig: BlankEditorLayoutConfig get() = BlankEditorLayoutConfig(questions, options, columns)

    /**
     * Снимок для печати/предпросмотра этого оформления — то же, что уходит в PDF и картинку
     * ([renderBlankSheetBitmap], [drawBlankSheet]).
     */
    fun exportSnapshot(variants: Int, variant: Int? = null): BlankSheetExportSnapshot = BlankSheetExportSnapshot(
        layoutConfig = layoutConfig,
        renderParams = render,
        hiddenBubbleIds = hiddenFor(variant),
        blankPanLogical = pan,
        blankScale = scale,
        textBlocks = textBlocks,
        showVariantMarker = variantMarker,
        showStudentNameMarker = nameMarker,
        studentNamePrintedMode = false,
        studentSurnameText = "",
        studentNameText = "",
        // Номер закрашен только у бланка конкретного варианта (печать «по странице на вариант» или именные).
        variantPrintedMode = variant != null,
        variantPrintedNumber = (variant ?: 1).toString(),
        v2 = v2Options(variants, variant),
    )

    /**
     * Шапка бланка v2 для этого оформления ([variants] — вариантов у теста); null — старый формат.
     * [variant] — бланк конкретного варианта: номер закрашен при печати.
     */
    fun v2Options(variants: Int, variant: Int? = null): BlankV2Options? = if (!formatV2) null else BlankV2Options(
        variantCount = if (variantMarker || variant != null) variants else 0,
        printedVariant = variant?.coerceIn(1, 4),
        nameField = nameMarker,
    )

    /** Верх рамки с кружками на листе (логические единицы). */
    fun gridTop(): Float =
        blankLayoutBoundingRectTransformed(buildBlankBubbleLayout(layoutConfig, render), pan, scale).top

    /**
     * То же оформление под другое число вопросов/ответов/колонок: надписи и поля остаются, рамка ставится
     * на прежнюю высоту и при необходимости уменьшается, скрытые кружки сбрасываются.
     */
    fun withGrid(q: Int, o: Int, cols: Int): BlankDesign {
        if (q == questions && o == options && cols == columns) return this
        val (p, s) = fitBlankOnSheet(BlankEditorLayoutConfig(q, o, cols), render, gridTop())
        return copy(questions = q, options = o, columns = cols, pan = p, scale = s, hiddenBubbleIds = emptySet(), variantHidden = emptyMap())
    }

    /** Поместится ли рамка с уголками на лист при таком масштабе. */
    fun fitsAt(scaleTry: Float): Boolean {
        val layout = buildBlankBubbleLayout(layoutConfig, render)
        val r = blankLayoutBoundingRectTransformed(layout, Offset.Zero, scaleTry)
        val m = BLANK_SHEET_SAFE_MARGIN + cornerMarkOutset(layout) * scaleTry
        return (r.bottom - r.top) + 2 * m <= BlankSheetSpec.LOGICAL_HEIGHT_PX &&
            (r.right - r.left) + 2 * m <= BlankSheetSpec.LOGICAL_WIDTH_PX
    }

    /** Рамка с уголками целиком на листе: если вылезает — уменьшить (не мельче допустимого) и сдвинуть внутрь. */
    fun keptOnSheet(): BlankDesign {
        val layout = buildBlankBubbleLayout(layoutConfig, render)
        val r = blankLayoutBoundingRectTransformed(layout, pan, scale)
        val m = BLANK_SHEET_SAFE_MARGIN + cornerMarkOutset(layout) * scale
        val tooTall = (r.bottom - r.top) + 2 * m > BlankSheetSpec.LOGICAL_HEIGHT_PX
        val tooWide = (r.right - r.left) + 2 * m > BlankSheetSpec.LOGICAL_WIDTH_PX
        if (tooTall || tooWide) {
            val (p, s) = fitBlankOnSheet(layoutConfig, render, gridTop().coerceAtMost(BlankBubbleDefaults.GRID_ORIGIN_Y_PX))
            return copy(pan = p, scale = s)
        }
        val clamped = clampBlankPanOnSheet(pan, layout, scale)
        return if (clamped == pan) this else copy(pan = clamped)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("q", questions)
        .put("o", options)
        .put("cols", columns)
        .put("render", PrintedBlankParamsStore.toJson(render))
        .put("panX", pan.x.toDouble())
        .put("panY", pan.y.toDouble())
        .put("scale", scale.toDouble())
        .put("hidden", JSONArray(hiddenBubbleIds.toList()))
        .put(
            "blocks",
            JSONArray().apply {
                textBlocks.forEach { b ->
                    put(
                        JSONObject()
                            .put("id", b.id)
                            .put("label", b.label)
                            .put("text", b.text)
                            .put("font", b.fontSizePx.toDouble())
                            .put("x", b.position.x.toDouble())
                            .put("y", b.position.y.toDouble())
                            .put("visible", b.isVisible)
                            .put("scale", b.scale.toDouble()),
                    )
                }
            },
        )
        .put("variant", variantMarker)
        .put("variantPrinted", variantPrinted)
        .put("variantNumber", variantNumber)
        .put("nameMarker", nameMarker)
        .put("namePrinted", namePrinted)
        .put("v2", formatV2)
        .put(
            "variantHidden",
            JSONObject().apply { variantHidden.forEach { (v, ids) -> put(v.toString(), JSONArray(ids.toList())) } },
        )

    companion object {
        fun fromJson(o: JSONObject): BlankDesign {
            val hiddenArr = o.optJSONArray("hidden") ?: JSONArray()
            val blocksArr = o.optJSONArray("blocks") ?: JSONArray()
            val blocks = (0 until blocksArr.length()).map { i ->
                val b = blocksArr.getJSONObject(i)
                BlankTextBlock(
                    id = b.getString("id"),
                    label = b.optString("label", b.getString("id")),
                    text = b.optString("text", ""),
                    fontSizePx = clampTextFontSizePx(b.optDouble("font", 18.0).toFloat()),
                    position = clampTextPositionOnSheet(Offset(b.optDouble("x", 70.0).toFloat(), b.optDouble("y", 70.0).toFloat())),
                    isVisible = b.optBoolean("visible", true),
                    scale = clampTextScale(b.optDouble("scale", 1.0).toFloat()),
                )
            }
            return BlankDesign(
                questions = o.getInt("q").coerceIn(1, 35),
                options = o.getInt("o").coerceIn(2, 9),
                columns = o.getInt("cols").coerceIn(1, 2),
                render = o.optJSONObject("render")?.let { PrintedBlankParamsStore.fromJson(it) } ?: BlankBubbleRenderParams.defaults(),
                pan = Offset(o.optDouble("panX", 0.0).toFloat(), o.optDouble("panY", 0.0).toFloat()),
                scale = clampBlankScale(o.optDouble("scale", 1.0).toFloat()),
                hiddenBubbleIds = (0 until hiddenArr.length()).map { hiddenArr.getString(it) }.toSet(),
                textBlocks = blocks.ifEmpty { defaultBlankTextBlocks() },
                variantMarker = o.optBoolean("variant", false),
                variantPrinted = o.optBoolean("variantPrinted", false),
                variantNumber = o.optString("variantNumber", "1"),
                nameMarker = o.optBoolean("nameMarker", false),
                namePrinted = o.optBoolean("namePrinted", false),
                // Старые сохранённые оформления — тоже новый бланк: старый остаётся только выбором вручную.
                formatV2 = o.optBoolean("v2", true),
                variantHidden = o.optJSONObject("variantHidden")?.let { vh ->
                    vh.keys().asSequence().mapNotNull { k ->
                        val arr = vh.optJSONArray(k) ?: return@mapNotNull null
                        k.toIntOrNull()?.let { it to (0 until arr.length()).map { i -> arr.getString(i) }.toSet() }
                    }.toMap()
                }.orEmpty(),
            ).keptOnSheet()
        }

        fun fromJsonString(raw: String?): BlankDesign? =
            raw?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

        /** Бланк по умолчанию под тест: стандартная шапка, поле имени, шестиугольник — если вариантов больше одного. */
        fun defaultFor(q: Int, o: Int, cols: Int, variants: Int): BlankDesign {
            val render = BlankBubbleRenderParams.defaults()
            val (pan, scale) = fitBlankOnSheet(BlankEditorLayoutConfig(q, o, cols), render, BlankBubbleDefaults.GRID_ORIGIN_Y_PX)
            return BlankDesign(
                questions = q, options = o, columns = cols, render = render, pan = pan, scale = scale,
                hiddenBubbleIds = emptySet(), textBlocks = defaultBlankTextBlocks(),
                variantMarker = variants > 1, variantPrinted = false, variantNumber = "1",
                nameMarker = true, namePrinted = false,
            )
        }
    }
}

data class BlankTemplate(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val design: BlankDesign,
    /** Сколько вариантов теста предлагать по этому шаблону. */
    val testVariants: Int = 1,
    /** Критерии оценки шаблона (JSON как в Настройках); null — общие. */
    val gradingCriteriaJson: String? = null,
) {
    val summary: String
        get() = buildString {
            append("${design.questions} ${ruPlural(design.questions, "вопрос", "вопроса", "вопросов")}")
            append(" · ${design.options} ${ruPlural(design.options, "ответ", "ответа", "ответов")}")
            if (testVariants > 1) append(" · $testVariants ${ruPlural(testVariants, "вариант", "варианта", "вариантов")}")
        }
}

internal fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

/**
 * Поставить рамку с кружками так, чтобы её верх был на [top], а низ не вылезал за лист.
 * Если рамка выше, чем место до низа листа, она уменьшается (не мельче допустимого конструктором).
 */
fun fitBlankOnSheet(
    layoutConfig: BlankEditorLayoutConfig,
    render: BlankBubbleRenderParams,
    top: Float,
): Pair<Offset, Float> {
    val layout = buildBlankBubbleLayout(layoutConfig, render)
    val r0 = blankLayoutBoundingRectTransformed(layout, pan = Offset.Zero, scale = 1f)
    // Высота вместе с уголками-ориентирами снизу; плюс поле ≈5 мм до края листа.
    val h = r0.bottom - r0.top + cornerMarkOutset(layout)
    val avail = BlankSheetSpec.LOGICAL_HEIGHT_PX - BLANK_SHEET_SAFE_MARGIN - top
    val scale = clampBlankScale(if (h > avail) avail / h else 1f)
    val r = blankLayoutBoundingRectTransformed(layout, pan = Offset.Zero, scale = scale)
    val pan = clampBlankPanOnSheet(Offset(0f, top - r.top), layout, scale)
    return pan to scale
}

/** Шапка покороче: название и строка «ФИО», без даты и инструкции — для длинных тестов. */
private fun compactHeader(): List<BlankTextBlock> = defaultBlankTextBlocks().map { b ->
    when (b.id) {
        "title" -> b.copy(position = Offset(70f, 36f))
        "name" -> b.copy(position = Offset(70f, 92f))
        else -> b.copy(isVisible = false)
    }
}

object BlankBuiltInTemplates {
    private fun make(
        id: String,
        name: String,
        q: Int,
        o: Int,
        cols: Int,
        variants: Int = 1,
        blocks: List<BlankTextBlock> = defaultBlankTextBlocks(),
        top: Float = BlankBubbleDefaults.GRID_ORIGIN_Y_PX,
    ): BlankTemplate {
        val render = BlankBubbleRenderParams.defaults()
        val (pan, scale) = fitBlankOnSheet(BlankEditorLayoutConfig(q, o, cols), render, top)
        return BlankTemplate(
            id = id, name = name, builtIn = true, testVariants = variants,
            design = BlankDesign(
                questions = q, options = o, columns = cols,
                render = render, pan = pan, scale = scale,
                hiddenBubbleIds = emptySet(), textBlocks = blocks,
                variantMarker = variants > 1, variantPrinted = false, variantNumber = "1",
                nameMarker = true, namePrinted = false,
            ),
        )
    }

    val empty: BlankTemplate by lazy { make("empty", "Пустой", 10, 4, 1) }

    val all: List<BlankTemplate> by lazy {
        listOf(
            empty,
            make("short10x5", "Короткий", 10, 5, 1),
            make("var20x4", "С вариантами", 20, 4, 2, variants = 2),
            make("long30x4", "Длинный", 30, 4, 2, blocks = compactHeader(), top = 225f),
            make("grid20x4", "Только сетка", 20, 4, 2, blocks = defaultBlankTextBlocks().map { it.copy(isVisible = false) }, top = 110f),
        )
    }

    fun hint(id: String): String = when (id) {
        "empty" -> "шапка, 1 колонка"
        "short10x5" -> "5 ответов в вопросе"
        "var20x4" -> "2 колонки, номер варианта"
        "long30x4" -> "короткая шапка"
        "grid20x4" -> "без надписей, для Word"
        else -> ""
    }
}

object BlankTemplateStore {
    private const val PREFS = "blank_templates"
    private const val KEY = "user"

    fun load(context: Context): List<BlankTemplate> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    BlankTemplate(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        builtIn = false,
                        design = BlankDesign.fromJson(o.optJSONObject("design") ?: o),
                        testVariants = o.optInt("variants", 1).coerceIn(1, 4),
                        gradingCriteriaJson = if (o.has("grading") && !o.isNull("grading")) o.optString("grading") else null,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /** Добавить шаблон; шаблон с тем же id или тем же названием заменяется. */
    fun save(context: Context, template: BlankTemplate) {
        val list = load(context).filterNot {
            it.id == template.id || it.name.trim().equals(template.name.trim(), ignoreCase = true)
        }.toMutableList()
        list.add(0, template)
        write(context, list)
    }

    fun delete(context: Context, id: String) {
        write(context, load(context).filterNot { it.id == id })
    }

    private fun write(context: Context, list: List<BlankTemplate>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("design", t.design.toJson())
                    .put("variants", t.testVariants)
                    .apply { t.gradingCriteriaJson?.let { put("grading", it) } },
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
