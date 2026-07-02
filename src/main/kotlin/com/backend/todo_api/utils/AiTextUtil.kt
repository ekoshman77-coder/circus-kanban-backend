package com.backend.todo_api.utils

object AiTextUtil {

    // 📋 Exakte Wörter, die ohne Wenn und Aber rausfliegen
    private val exactStopWords = setOf(
        "ja", "damit", "sowohl", "um", "auch", "noch", "mehr",
        "und", "oder", "aber", "weil", "dass", "wenn",
        "der", "die", "das", "ein", "eine", "einen", "einem", "einer", "eines",
        "in", "auf", "zu", "nach", "mit", "von", "im", "am", "für", "über", "unter", "vor", "aus", "bei"
    )

    // 🔬 Deine genialen Wortstämme mit der Längen-Toleranz-Regel
    private val stopWordStems = listOf(
        "mein", "dein", "sein", "unser", "euer", "eure",
        "wichtig",
        "jed"
    )

    /**
     * 🧼 Säubert einen Text, zerlegt ihn in Wörter (Tokenisierung)
     * und wendet deine Stoppwort-Bremse an.
     */
    fun tokenizeAndClean(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()

        // 1. Alles kleinmachen & Sonderzeichen entfernen (wie gestern beim Bayes!)
        val cleanText = text.lowercase()
            .replace(Regex("[^a-zäöüß0-9 ]"), "")

        // 2. In einzelne Wörter zerlegen
        val allWords = cleanText.split(" ")
            .filter { it.isNotBlank() && it.length > 1 }

        // 3. Jedes Wort durch deine KI-Stoppwort-Bremse jagen
        return allWords.filter { word ->
            !isStopWord(word)
        }
    }

    /**
     * Die Herzstück-Logik: Entscheidet, ob ein Wort ausgefiltert wird
     */
    private fun isStopWord(word: String): Boolean {
        // Regel 1: Ist es ein exaktes Stoppwort?
        if (exactStopWords.contains(word)) return true

        // Regel 2: Passt es zu einem Stamm mit maximal +2 Buchstaben Abweichung?
        for (stem in stopWordStems) {
            if (word.startsWith(stem) && (word.length - stem.length) <= 2) {
                return true // Treffer! Es ist ein abgewandeltes Füllwort.
            }
        }

        return false // Es ist ein wichtiges Wort für die KI!
    }
}