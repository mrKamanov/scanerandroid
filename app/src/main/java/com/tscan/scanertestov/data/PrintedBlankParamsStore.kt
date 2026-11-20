package com.tscan.scanertestov.data

/**
 * Описание: параметры сетки (размер кружков, зазоры, отступ, рамка) последних выгруженных
 * из конструктора бланков. Распознавание сначала ищет стандартную сетку, а если не нашло —
 * пробует эти параметры. Без этого бланк с изменёнными кружками не узнавался совсем.
 */
import android.content.Context
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleRenderParams
import org.json.JSONArray
import org.json.JSONObject

object PrintedBlankParamsStore {
    private const val PREFS = "printed_blank_params"
    private const val KEY = "recent"
    private const val MAX = 5

    fun remember(context: Context, params: BlankBubbleRenderParams) {
        if (sameGeometry(params, BlankBubbleRenderParams.defaults())) return
        val list = load(context).filterNot { sameGeometry(it, params) }.toMutableList()
        list.add(0, params)
        val arr = JSONArray()
        list.take(MAX).forEach { arr.put(toJson(it)) }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): List<BlankBubbleRenderParams> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Совпадает ли то, что влияет на положение кружков (шрифт подписи не важен). */
    fun sameGeometry(a: BlankBubbleRenderParams, b: BlankBubbleRenderParams): Boolean =
        a.bubbleSizePx == b.bubbleSizePx &&
            a.cellGapHorizontalPx == b.cellGapHorizontalPx &&
            a.cellGapVerticalPx == b.cellGapVerticalPx &&
            a.gridPaddingPx == b.gridPaddingPx &&
            a.borderWidthPx == b.borderWidthPx

    internal fun toJson(p: BlankBubbleRenderParams): JSONObject = JSONObject()
        .put("bubble", p.bubbleSizePx.toDouble())
        .put("font", p.fontSizePx.toDouble())
        .put("gapH", p.cellGapHorizontalPx.toDouble())
        .put("gapV", p.cellGapVerticalPx.toDouble())
        .put("pad", p.gridPaddingPx.toDouble())
        .put("border", p.borderWidthPx.toDouble())

    internal fun fromJson(o: JSONObject): BlankBubbleRenderParams = BlankBubbleRenderParams(
        bubbleSizePx = o.getDouble("bubble").toFloat(),
        fontSizePx = o.optDouble("font", 15.0).toFloat(),
        cellGapHorizontalPx = o.getDouble("gapH").toFloat(),
        cellGapVerticalPx = o.getDouble("gapV").toFloat(),
        gridPaddingPx = o.getDouble("pad").toFloat(),
        borderWidthPx = o.getDouble("border").toFloat(),
    )
}
