package com.backend.todo_api.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SanitizationPatternTest {

    @Test
    fun testAfterPattern() {
        val pattern = SanitizationPattern.create("password", PatternTypeEnum.KEYWORD_VALUE_AFTER)
        val regex = pattern.createRegex()

        val text = "Mein password: 12345 ist sicher."
        val match = regex.find(text)

        // Prüft, ob das Keyword und der Wert dahinter korrekt als Treffer erkannt werden
        val extracted = match?.value
        assertEquals("password: 12345", extracted)
    }

    @Test
    fun testBeforePattern() {
        val pattern = SanitizationPattern.create("password", PatternTypeEnum.KEYWORD_VALUE_BEFORE)
        val regex = pattern.createRegex()

        val text = "Geheimes123 password ist das."
        val match = regex.find(text)

        // Prüft, ob der Wert vor dem Keyword erkannt wird
        val extracted = match?.value
        assertEquals("Geheimes123 password", extracted)
    }

    @Test
    fun testBetweenPattern() {
        // Wir übergeben Start- und Stoppwort getrennt durch Doppelpunkt
        val pattern = SanitizationPattern.create("start:stop", PatternTypeEnum.KEYWORD_VALUE_BETWEEN)
        val regex = pattern.createRegex()

        val text = "Hier steht start geheimnisvoller Inhalt stop im Text."
        val match = regex.find(text)

        // Prüft, ob die Gruppe dazwischen korrekt extrahiert wird
        val innerContent = match?.groupValues?.get(1)
        assertEquals("geheimnisvoller Inhalt", innerContent)
    }
}