package com.tscan.scanertestov.feature.batch

/**
 * Описание: выбор имени/фамилии по словарю с опорой на саму модель распознавания.
 *
 * Раньше прочитанное слово «исправлялось» на ближайшее по буквам слово словаря — так
 * «Фоминых» превращалась в «Долинный». Теперь у каждого кандидата (само прочитанное слово
 * и слова словаря на расстоянии ≤ 2 правок) считается правдоподобие по модели (CTC) и
 * прибавляется поправка на частоту слова (словари отсортированы от частых к редким).
 * Прочитанное слово участвует как «редкое», поэтому уверенно прочитанную фамилию не из
 * словаря оно не трогает. Параметры подобраны на тестовых кадрах: фамилия и имя — 115 из 117.
 */
import android.content.Context
import com.tscan.scanertestov.ml.ocr.EslavPpOcrV5RecEngine
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

internal object BatchNameLexicon {
    private const val FORENAMES_ASSET = "dictionaries/names/common-forenames.txt"
    private const val SURNAMES_ASSET = "dictionaries/names/common-surnames.txt"
    private const val ALPHA = 0.3
    private const val MAX_DISTANCE = 2
    private const val MAX_CANDIDATES = 40
    private val RU = Locale("ru")

    class Lexicon(
        val original: Array<String>,
        /** Нижний регистр, ё→е; индекс = ранг частоты. */
        val keys: Array<String>,
        val byLength: Map<Int, IntArray>,
        val rank: Map<String, Int>,
        /** Написание с «ё» для слов, записанных в словаре сначала через «е». */
        val yoSpelling: Map<Int, String>,
    )

    private val lock = Any()
    @Volatile private var forenames: Lexicon? = null
    @Volatile private var surnames: Lexicon? = null

    fun forenames(context: Context): Lexicon = forenames ?: synchronized(lock) {
        forenames ?: load(context.applicationContext, FORENAMES_ASSET).also { forenames = it }
    }

    fun surnames(context: Context): Lexicon = surnames ?: synchronized(lock) {
        surnames ?: load(context.applicationContext, SURNAMES_ASSET).also { surnames = it }
    }

    private fun key(w: String) = w.lowercase(RU).replace('ё', 'е')

    private fun load(context: Context, path: String): Lexicon {
        val original = ArrayList<String>()
        val keys = ArrayList<String>()
        val rank = HashMap<String, Int>()
        val yoSpelling = HashMap<Int, String>()
        context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val w = line.trim()
                if (w.length < 2) continue
                val k = key(w)
                val seen = rank[k]
                if (seen != null) {
                    // То же слово с «ё» (Артем/Артём): запоминаем написание с «ё» как второе.
                    if ('ё' in w.lowercase(RU) && seen !in yoSpelling) yoSpelling[seen] = w
                    continue
                }
                rank[k] = keys.size
                keys += k
                original += w
            }
        }
        val byLen = HashMap<Int, ArrayList<Int>>()
        keys.forEachIndexed { i, k -> byLen.getOrPut(k.length) { ArrayList() }.add(i) }
        return Lexicon(
            original = original.toTypedArray(),
            keys = keys.toTypedArray(),
            byLength = byLen.mapValues { it.value.toIntArray() },
            rank = rank,
            yoSpelling = yoSpelling,
        )
    }

    /** Лучшее слово для прочитанного [greedy] по вероятностям модели [probs]; null — если пусто. */
    fun pick(probs: Array<FloatArray>, greedy: String, lexicon: Lexicon): String? {
        val g = key(greedy)
        if (g.isEmpty()) return null
        val total = lexicon.keys.size
        fun prior(k: String): Double = -ALPHA * ln((lexicon.rank[k] ?: total) + 50.0)
        fun cap(w: String) = w.lowercase(RU).replaceFirstChar { it.titlecase(RU) }

        var best = cap(greedy)
        var bestScore = EslavPpOcrV5RecEngine.ctcLogProb(probs, cap(greedy)) + prior(g)
        for (idx in near(g, lexicon)) {
            val k = lexicon.keys[idx]
            if (k == g) continue
            for (spelling in listOfNotNull(lexicon.original[idx], lexicon.yoSpelling[idx])) {
                val word = cap(spelling)
                val score = EslavPpOcrV5RecEngine.ctcLogProb(probs, word) + prior(k)
                if (score > bestScore) {
                    bestScore = score
                    best = word
                }
            }
        }
        return best
    }

    /** Индексы слов на расстоянии Левенштейна ≤ [MAX_DISTANCE], ближайшие и самые частые — первыми. */
    private fun near(g: String, lexicon: Lexicon): List<Int> {
        val found = ArrayList<Long>() // расстояние в старших битах, индекс в младших
        val prev = IntArray(g.length + 1)
        val cur = IntArray(g.length + 1)
        for ((len, indices) in lexicon.byLength) {
            if (abs(len - g.length) > MAX_DISTANCE) continue
            for (idx in indices) {
                val d = boundedLevenshtein(lexicon.keys[idx], g, prev, cur)
                if (d <= MAX_DISTANCE) found += (d.toLong() shl 32) or idx.toLong()
            }
        }
        found.sort()
        return found.take(MAX_CANDIDATES).map { (it and 0xFFFFFFFFL).toInt() }
    }

    /** Расстояние Левенштейна с ранним выходом, когда оно заведомо больше [MAX_DISTANCE]. */
    private fun boundedLevenshtein(a: String, b: String, prev: IntArray, cur: IntArray): Int {
        for (j in 0..b.length) prev[j] = j
        var p = prev
        var c = cur
        for (i in a.indices) {
            c[0] = i + 1
            var rowMin = c[0]
            val ai = a[i]
            for (j in b.indices) {
                val v = minOf(c[j] + 1, p[j + 1] + 1, p[j] + if (ai == b[j]) 0 else 1)
                c[j + 1] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > MAX_DISTANCE) return MAX_DISTANCE + 1
            val t = p; p = c; c = t
        }
        return p[b.length]
    }
}
