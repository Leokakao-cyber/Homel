package com.lumen.keyboard

class SuggestionEngine(
    private val words: Set<String>,
    private val learned: UserLanguageModel,
    private val pack: DictionaryPackStore
) {
    private val commonWords = setOf(
        "a", "about", "and", "are", "be", "can", "do", "for", "from", "good",
        "have", "he", "help", "i", "in", "is", "it", "my", "not", "of", "on",
        "one", "our", "that", "the", "their", "this", "to", "was", "we", "will",
        "with", "you", "your", "build", "clarity", "focus", "growth", "lumen",
        "mature", "purpose", "rewrite", "suggestion"
    )

    private val nextWords = mapOf(
        "i" to listOf("am", "will", "have"),
        "you" to listOf("are", "can", "will"),
        "we" to listOf("are", "can", "will"),
        "the" to listOf("best", "next", "keyboard"),
        "to" to listOf("be", "the", "build"),
        "lumen" to listOf("keyboard", "can", "will"),
        "good" to listOf("morning", "work", "idea"),
        "thank" to listOf("you", "God", "them")
    )

    private val vocabulary = words + commonWords + nextWords.values.flatten()

    fun suggest(textBeforeCursor: String): List<String> {
        val tokens = Regex("[\\p{L}'-]+").findAll(textBeforeCursor.lowercase())
            .map { it.value }.toList()
        val endsWithSeparator = textBeforeCursor.lastOrNull()?.isWhitespace() == true
        if (tokens.isEmpty()) return listOf("I", "The", "We")

        val current = tokens.last()
        if (endsWithSeparator) return (learned.next(current) + (nextWords[current] ?: listOf("the", "and", "to"))).distinct().take(8)

        val prefixMatches = (learned.words(current) + pack.prefix(current) + vocabulary.asSequence()
            .filter { it.startsWith(current) && it != current }
            .sortedBy { it.length }
            .take(8)
            .toList()).distinct().take(8)
        if (prefixMatches.isNotEmpty()) return prefixMatches

        return vocabulary.asSequence()
            .map { it to distance(current, it) }
            .filter { (_, score) -> score <= if (current.length > 5) 2 else 1 }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .map { it.first }
            .take(8)
            .toList()
    }

    private fun distance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}

