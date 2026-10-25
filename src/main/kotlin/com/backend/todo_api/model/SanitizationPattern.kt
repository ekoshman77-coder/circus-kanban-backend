package com.backend.todo_api.model

import com.backend.todo_api.utils.AiTextUtil

sealed class SanitizationPattern(val pattern: String) {
    abstract fun createRegex(): Regex
    abstract fun getType(): PatternTypeEnum

    class RegexPattern(pattern: String) : SanitizationPattern(pattern) {
        override fun createRegex(): Regex = Regex(pattern)
        override fun getType(): PatternTypeEnum = PatternTypeEnum.REGEX
    }

    class ExactPattern(pattern: String) : SanitizationPattern(pattern) {
        override fun createRegex(): Regex = Regex("(?i)\\b${pattern}\\b")
        override fun getType(): PatternTypeEnum = PatternTypeEnum.EXACT
    }

    class AfterPattern(pattern: String) : SanitizationPattern(pattern) {
        override fun createRegex(): Regex {
            val escapedPattern = Regex.escape(pattern)

            // Erklärung des Regex:
            // 1. (?i)\b$escapedPattern\b\s*[:=\-]?\s* -> Das Keyword mit optionalem Trenner
            // 2. ("[^"]+"|[^\s,.;]+)                -> Entweder alles in Anführungszeichen ODER ein einzelnes Wort ohne Satzzeichen/Leerzeichen
            return Regex(
                "(?i)\\b$escapedPattern\\b\\s*[:=\\-]?\\s*(?:(?:$STOP_WORDS_PART)\\s+)*(\"[^\"]+\"|[^\\s,.;]+)"
            )
        }

        override fun getType(): PatternTypeEnum = PatternTypeEnum.KEYWORD_VALUE_AFTER
    }

    class BeforePattern(pattern: String) : SanitizationPattern(pattern) { // Korrigiert von Bevore zu Before
        override fun createRegex(): Regex {
            val escapedPattern = Regex.escape(pattern)
            // Erklärt:
            // 1. ([^\s,.;]+) -> Erfasst das Wort vor dem Keyword (der sensible Wert)
            // 2. \s*[:=\-]?\s* -> Optionaler Trenner dazwischen
            // 3. \b$escapedPattern\b -> Das eigentliche Keyword als Anker dahinter
            return Regex(
                "(\"[^\"]+\"|[^\\s,.;]+)\\s*(?:(?:$STOP_WORDS_PART)\\s+)*\\s*[:=\\-]?\\s*\\b$escapedPattern\\b",
                RegexOption.IGNORE_CASE
            )
        }

        override fun getType(): PatternTypeEnum = PatternTypeEnum.KEYWORD_VALUE_BEFORE
    }

    companion object {
        private val STOP_WORDS_PART = AiTextUtil.stopWordsRegexPattern

        fun create(pattern: String, type: PatternTypeEnum): SanitizationPattern {
            return when (type) {
                PatternTypeEnum.REGEX -> RegexPattern(pattern)
                PatternTypeEnum.EXACT -> ExactPattern(pattern)
                PatternTypeEnum.KEYWORD_VALUE_BEFORE -> BeforePattern(pattern)
                PatternTypeEnum.KEYWORD_VALUE_AFTER -> AfterPattern(pattern)
            }
        }
    }
}