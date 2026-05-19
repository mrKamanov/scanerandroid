package com.tscan.scanertestov.feature.batch

/**
 * Описание: сопоставление OCR-ФИО со списком учеников журнала и нормализация написания.
 */
import com.tscan.scanertestov.feature.journals.JournalStudent
import java.util.Locale

data class JournalRosterSnapResult(
    val surname: String?,
    val name: String?,
    val matchedClassName: String?,
)

object JournalRosterNameMatcher {

    fun snapToRoster(
        roster: List<JournalStudent>,
        surname: String?,
        name: String?,
        confidence: Float,
    ): JournalRosterSnapResult {
        if (roster.isEmpty()) {
            return JournalRosterSnapResult(surname, name, matchedClassName = null)
        }
        val sn = surname?.trim().orEmpty()
        val nm = name?.trim().orEmpty()
        if (sn.isBlank() && nm.isBlank()) {
            return JournalRosterSnapResult(surname, name, matchedClassName = null)
        }

        val maxPair = maxPairDistanceSum(confidence)

        if (sn.isNotBlank() && nm.isNotBlank()) {
            var best: JournalStudent? = null
            var bestSum = Int.MAX_VALUE
            var bestSurnameDist = Int.MAX_VALUE
            var bestNameDist = Int.MAX_VALUE
            for (s in roster) {
                val ds = levenshtein(norm(sn), norm(s.surname))
                val dn = levenshtein(norm(nm), norm(s.name))
                val sum = ds + dn
                val better = sum < bestSum ||
                    (sum == bestSum && (ds < bestSurnameDist || (ds == bestSurnameDist && dn < bestNameDist)))
                if (better) {
                    bestSum = sum
                    bestSurnameDist = ds
                    bestNameDist = dn
                    best = s
                }
            }
            if (best != null && bestSum <= maxPair) {
                return JournalRosterSnapResult(
                    surname = best.surname,
                    name = best.name,
                    matchedClassName = best.className.trim().takeIf { it.isNotEmpty() },
                )
            }
            return JournalRosterSnapResult(surname, name, matchedClassName = null)
        }

        if (sn.isNotBlank() && nm.isBlank()) {
            val maxS = maxSingleFieldDist(confidence)
            val matches = roster.filter { levenshtein(norm(sn), norm(it.surname)) <= maxS }
            if (matches.size == 1) {
                val only = matches.first()
                return JournalRosterSnapResult(
                    surname = only.surname,
                    name = only.name,
                    matchedClassName = only.className.trim().takeIf { it.isNotEmpty() },
                )
            }
        } else if (sn.isBlank() && nm.isNotBlank()) {
            val maxN = maxSingleFieldDist(confidence)
            val matches = roster.filter { levenshtein(norm(nm), norm(it.name)) <= maxN }
            if (matches.size == 1) {
                val only = matches.first()
                return JournalRosterSnapResult(
                    surname = only.surname,
                    name = only.name,
                    matchedClassName = only.className.trim().takeIf { it.isNotEmpty() },
                )
            }
        }
        return JournalRosterSnapResult(surname, name, matchedClassName = null)
    }

    private fun maxPairDistanceSum(confidence: Float): Int =
        if (confidence >= 0.22f) 3 else 5

    private fun maxSingleFieldDist(confidence: Float): Int =
        if (confidence >= 0.22f) 1 else 2

    private fun norm(src: String): String {
        if (src.isBlank()) return ""
        return src
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
            .lowercase(Locale("ru"))
            .replace(Regex("[^а-яё\\-]"), "")
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
}
