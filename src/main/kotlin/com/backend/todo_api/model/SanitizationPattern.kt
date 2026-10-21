package com.backend.todo_api.model

sealed class SanitizationPattern(val pattern: String) {
    abstract fun createRegex(): Regex
    abstract fun getType(): PatternTypeEnum

    class RegexPattern(pattern: String): SanitizationPattern(pattern) {
        override fun createRegex(): Regex = Regex(pattern)
        override fun getType(): PatternTypeEnum = PatternTypeEnum.REGEX
    }

    class ExactPattern(pattern: String): SanitizationPattern(pattern) {
        override fun createRegex(): Regex = Regex("(?i)\\b${pattern}\\b")
        override fun getType(): PatternTypeEnum = PatternTypeEnum.EXACT
    }

    class AfterPattern(pattern: String): SanitizationPattern(pattern) {
        override fun createRegex(): Regex {
            val escapedPattern = Regex.escape(pattern)

            // Erklärung des Regex:
            // 1. (?i)\b$escapedPattern\b\s*[:=\-]?\s* -> Das Keyword mit optionalem Trenner
            // 2. ("[^"]+"|[^\s,.;]+)                -> Entweder alles in Anführungszeichen ODER ein einzelnes Wort ohne Satzzeichen/Leerzeichen
            return Regex("(?i)\\b$escapedPattern\\b\\s*[:=\\-]?\\s*(\"[^\"]+\"|[^\\s,.;]+)")
        }

        override fun getType(): PatternTypeEnum = PatternTypeEnum.KEYWORD_VALUE_AFTER
    }

    class BeforePattern(pattern: String): SanitizationPattern(pattern) { // Korrigiert von Bevore zu Before
        override fun createRegex(): Regex {
            val escapedPattern = Regex.escape(pattern)
            // Erklärt:
            // 1. ([^\s,.;]+) -> Erfasst das Wort vor dem Keyword (der sensible Wert)
            // 2. \s*[:=\-]?\s* -> Optionaler Trenner dazwischen
            // 3. \b$escapedPattern\b -> Das eigentliche Keyword als Anker dahinter
            return Regex("([^\\s,.;]+)\\s*[:=\\-]?\\s*\\b$escapedPattern\\b", RegexOption.IGNORE_CASE)
        }

        override fun getType(): PatternTypeEnum = PatternTypeEnum.KEYWORD_VALUE_BEFORE
    }

    class BetweenPattern(pattern: String): SanitizationPattern(pattern) {
        override fun createRegex(): Regex {
            // Wir teilen den Pattern-String am Doppelpunkt oder Pipe auf (z. "start:ende")
            val parts = pattern.split(":", "|")
            if (parts.size != 2) {
                // Fallback, falls der Admin sich vertippt hat und kein Trennzeichen da ist
                return Regex("")
            }

            val start = Regex.escape(parts[0].trim())
            val stop = Regex.escape(parts[1].trim())

            // Erklärt:
            // 1. \b$start\b\s* -> Das Startwort
            // 2. (.*?) -> Der Text dazwischen (non-greedy, damit es beim Stoppwort aufhört)
            // 3. \s*\b$stop\b -> Das Stoppwort
            return Regex("\\b$start\\b\\s*(.*?)\\s*\\b$stop\\b", RegexOption.IGNORE_CASE)
        }

        override fun getType(): PatternTypeEnum = PatternTypeEnum.KEYWORD_VALUE_BETWEEN
    }

    companion object {
        fun create(pattern: String, type: PatternTypeEnum): SanitizationPattern {
            return when (type) {
                PatternTypeEnum.REGEX -> RegexPattern(pattern)
                PatternTypeEnum.EXACT -> ExactPattern(pattern)
                PatternTypeEnum.KEYWORD_VALUE_BETWEEN -> BetweenPattern(pattern)
                PatternTypeEnum.KEYWORD_VALUE_BEFORE -> BeforePattern(pattern)
                PatternTypeEnum.KEYWORD_VALUE_AFTER -> AfterPattern(pattern)
            }
        }
    }
}