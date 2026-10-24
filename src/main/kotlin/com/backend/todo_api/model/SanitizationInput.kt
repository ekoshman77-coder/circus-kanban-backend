package com.backend.todo_api.model

import com.backend.todo_api.utils.AiTextUtil

data class SanitizationInput(
    val selectedText: String,
    val patternType: PatternTypeEnum
){
    /**
     * Extrahiert typsicher genau das relevante Ankerwort basierend auf dem PatternType,
     * anstatt den rohen Text zu verwenden!
     */
    fun extractRelevantKeyword(): String {
        val tokens = AiTextUtil.tokenizeWithIndices(selectedText) { word ->
            AiTextUtil.isStopWord(word)
        }
        val meaningfulTokens = tokens.filter { !it.isStopWord }
        if (meaningfulTokens.isEmpty()) return selectedText

        return when (patternType) {
            PatternTypeEnum.KEYWORD_VALUE_BEFORE -> meaningfulTokens.last().text
            PatternTypeEnum.KEYWORD_VALUE_AFTER -> meaningfulTokens.first().text
            else -> meaningfulTokens.first().text
        }
    }
}
