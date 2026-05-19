package com.tscan.scanertestov.feature.batch

/**
 * Описание: словарная коррекция ФИО после OCR на базе открытых списков имён и фамилий.
 */
import android.content.Context
import java.util.Locale

object BatchNameDictionaryCorrector {
    private const val FORENAMES_ASSET = "dictionaries/names/common-forenames.txt"
    private const val SURNAMES_ASSET = "dictionaries/names/common-surnames.txt"

    private val lock = Any()
    @Volatile
    private var forenames: DictionaryIndex? = null
    @Volatile
    private var surnames: DictionaryIndex? = null

    fun correct(
        context: Context,
        surname: String?,
        name: String?,
        confidence: Float,
    ): Pair<String?, String?> {
        ensureLoaded(context)
        val sDict = surnames ?: DictionaryIndex.EMPTY
        val nDict = forenames ?: DictionaryIndex.EMPTY
        val correctedSurname = correctPart(surname, sDict, confidence)
        val correctedName = correctPart(name, nDict, confidence)
        return correctedSurname to correctedName
    }

    private fun ensureLoaded(context: Context) {
        if (forenames != null && surnames != null) return
        synchronized(lock) {
            if (forenames != null && surnames != null) return
            val app = context.applicationContext
            forenames = loadWords(app, FORENAMES_ASSET)
            surnames = loadWords(app, SURNAMES_ASSET)
        }
    }

    private fun loadWords(context: Context, path: String): DictionaryIndex {
        val words = context.assets.open(path).bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.map { it.trim() }
                .map { normalize(it) }
                .filter { it.length >= 2 && CYR_WORD.matches(it) }
                .toSet()
        }
        val byInitial = words.groupBy { it.first() }
        return DictionaryIndex(words = words, byInitial = byInitial)
    }

    private fun correctPart(raw: String?, dictionary: DictionaryIndex, confidence: Float): String? {
        val candidate = normalize(raw.orEmpty())
        if (candidate.length < 2) return raw?.takeIf { it.isNotBlank() }
        if (dictionary.words.isEmpty()) return candidate
        if (candidate in dictionary.words) return candidate

        var best: String? = null
        var bestDist = Int.MAX_VALUE
        val first = candidate.firstOrNull()
        val pool = if (first != null) {
            initialCandidates(first)
                .flatMap { ch -> dictionary.byInitial[ch].orEmpty() }
                .ifEmpty { dictionary.byInitial[first].orEmpty() }
        } else {
            dictionary.words
        }
        val maxLenDiff = when {
            confidence < 0.12f && candidate.length <= 4 -> 5
            confidence < 0.12f -> 4
            else -> 2
        }
        for (word in pool) {
            if (first != null && word.firstOrNull() != first) continue
            if (kotlin.math.abs(word.length - candidate.length) > maxLenDiff) continue
            val d = levenshtein(candidate, word)
            if (d < bestDist) {
                bestDist = d
                best = word
                if (d == 0) break
            }
        }
        val maxDist = when {
            confidence >= 0.22f -> 1
            confidence < 0.12f && candidate.length <= 4 -> 4
            confidence < 0.12f -> 3
            candidate.length <= 4 -> 2
            else -> 2
        }
        return if (best != null && bestDist <= maxDist) best else candidate
    }

    private fun normalize(src: String): String {
        if (src.isBlank()) return src
        val mapped = src
            .replace('A', 'А').replace('a', 'а')
            .replace('B', 'В')
            .replace('C', 'С').replace('c', 'с')
            .replace('E', 'Е').replace('e', 'е')
            .replace('H', 'Н')
            .replace('K', 'К')
            .replace('M', 'М')
            .replace('O', 'О').replace('o', 'о')
            .replace('P', 'Р').replace('p', 'р')
            .replace('T', 'Т')
            .replace('X', 'Х').replace('x', 'х')
            .replace('Y', 'У').replace('y', 'у')
            .replace(Regex("[^А-Яа-яЁё\\- ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val low = mapped.lowercase(Locale("ru"))
        return low.replaceFirstChar { it.titlecase(Locale("ru")) }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in a.indices) {
            cur[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                cur[j + 1] = minOf(
                    cur[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + cost,
                )
            }
            for (k in prev.indices) prev[k] = cur[k]
        }
        return prev[b.length]
    }

    private fun initialCandidates(ch: Char): Set<Char> {
        val ru = Locale("ru")
        val c = ch.lowercase(ru).firstOrNull() ?: ch
        return when (c) {
            'б' -> setOf('Б', 'б', 'В', 'в')
            'в' -> setOf('В', 'в', 'Б', 'б')
            'и' -> setOf('И', 'и', 'Й', 'й')
            'й' -> setOf('Й', 'й', 'И', 'и')
            else -> setOf(ch, ch.uppercaseChar(), ch.lowercaseChar())
        }
    }

    private val CYR_WORD = Regex("^[А-Яа-яЁё\\-]{2,}$")

    private data class DictionaryIndex(
        val words: Set<String>,
        val byInitial: Map<Char, List<String>>,
    ) {
        companion object {
            val EMPTY = DictionaryIndex(emptySet(), emptyMap())
        }
    }
}

