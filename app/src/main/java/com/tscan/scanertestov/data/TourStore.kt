package com.tscan.scanertestov.data

/**
 * Описание: подсказки-экскурсии — включены ли они и какие экскурсии учитель уже прошёл.
 * Каждая экскурсия показывается один раз; в Настройках можно выключить подсказки или пройти всё заново.
 */
import android.content.Context

object TourStore {
    private const val PREFS = "tscan_tour_prefs"
    private const val KEY_ENABLED = "hints_enabled"
    private const val KEY_SEEN = "seen_tours"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isSeen(context: Context, tourId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SEEN, emptySet()).orEmpty().contains(tourId)

    fun markSeen(context: Context, tourId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet().apply { add(tourId) }
        prefs.edit().putStringSet(KEY_SEEN, next).apply()
    }

    /** «Пройти экскурсию заново»: все экскурсии покажутся снова при входе на экраны. */
    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_SEEN)
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    /** Показать экскурсию, если подсказки включены и она ещё не пройдена. */
    fun shouldShow(context: Context, tourId: String): Boolean = isEnabled(context) && !isSeen(context, tourId)
}
