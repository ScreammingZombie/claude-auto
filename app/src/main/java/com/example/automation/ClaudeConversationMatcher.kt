package com.example.automation

data class ConversationCandidate<T>(
    val title: String,
    val payload: T,
)

sealed class ConversationMatchResult<out T> {
    data class SingleMatch<T>(
        val candidate: ConversationCandidate<T>,
        val matchType: String,
    ) : ConversationMatchResult<T>()

    data class AmbiguousMatch<T>(
        val candidates: List<ConversationCandidate<T>>,
    ) : ConversationMatchResult<T>()

    data object NoMatch : ConversationMatchResult<Nothing>()
}

object ClaudeConversationMatcher {

    fun <T> findMatch(
        targetQuery: String,
        candidates: List<ConversationCandidate<T>>,
    ): ConversationMatchResult<T> {
        val normQuery = LimitDetector.normalize(targetQuery)
        if (normQuery.isBlank() || candidates.isEmpty()) {
            return ConversationMatchResult.NoMatch
        }

        val cleanedCandidates = candidates
            .map { it to LimitDetector.normalize(it.title) }
            .filter { (_, normTitle) -> normTitle.length >= 2 }

        // 1. Coincidencia exacta
        val exactMatches = cleanedCandidates.filter { (_, normTitle) -> normTitle == normQuery }
        if (exactMatches.size == 1) {
            return ConversationMatchResult.SingleMatch(exactMatches.first().first, "Coincidencia exacta")
        }
        if (exactMatches.size > 1) {
            return ConversationMatchResult.AmbiguousMatch(exactMatches.map { it.first })
        }

        // 2. Coincidencia por subcadena directa
        val containsMatches = cleanedCandidates.filter { (_, normTitle) ->
            normTitle.contains(normQuery) || (normTitle.length >= 5 && normQuery.contains(normTitle))
        }
        if (containsMatches.size == 1) {
            return ConversationMatchResult.SingleMatch(containsMatches.first().first, "Coincidencia por título")
        }
        if (containsMatches.size > 1) {
            // Comprobar si uno empieza exactamente por la consulta mientras los otros no
            val prefixMatches = containsMatches.filter { (_, normTitle) -> normTitle.startsWith(normQuery) }
            if (prefixMatches.size == 1) {
                return ConversationMatchResult.SingleMatch(prefixMatches.first().first, "Coincidencia por prefijo")
            }
            return ConversationMatchResult.AmbiguousMatch(containsMatches.map { it.first })
        }

        // 3. Coincidencia flexible por palabras clave (tokens)
        val queryTokens = normQuery.split(" ").filter { it.length >= 3 }.toSet()
        if (queryTokens.isEmpty()) return ConversationMatchResult.NoMatch

        val scored = cleanedCandidates.mapNotNull { (candidate, normTitle) ->
            val titleTokens = normTitle.split(" ").filter { it.length >= 3 }.toSet()
            val overlap = queryTokens.count { qToken ->
                titleTokens.any { tToken -> tToken.contains(qToken) || qToken.contains(tToken) }
            }
            if (overlap == 0) {
                null
            } else {
                val ratio = overlap.toDouble() / queryTokens.size.toDouble()
                if (ratio >= 0.6) candidate to ratio else null
            }
        }.sortedByDescending { it.second }

        return when {
            scored.isEmpty() -> ConversationMatchResult.NoMatch
            scored.size == 1 -> ConversationMatchResult.SingleMatch(scored.first().first, "Coincidencia flexible")
            scored[0].second - scored[1].second >= 0.25 -> {
                ConversationMatchResult.SingleMatch(scored.first().first, "Coincidencia más probable")
            }
            else -> {
                val topScore = scored.first().second
                val ambiguous = scored.filter { topScore - it.second < 0.25 }.map { it.first }
                ConversationMatchResult.AmbiguousMatch(ambiguous)
            }
        }
    }
}
