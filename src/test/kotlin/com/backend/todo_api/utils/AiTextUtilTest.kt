package com.backend.todo_api.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiTextUtilTest {

    @Test
    fun `test exact stop words are removed`() {
        // "ja", "damit" und "mehr" müssen komplett verschwinden
        val input = "Ja das ist damit viel mehr Arbeit"
        val result = AiTextUtil.tokenizeAndClean(input)

        // Übrig bleiben sollte nach der Bereinigung nur: ["viel", "arbeit"]
        // ("das", "ist" sind je nach deiner Liste auch Füllwörter, "viel" und "arbeit" bleiben!)
        assertTrue(result.contains("arbeit"))
        assertTrue(result.contains("viel"))

        // Prüfen, ob die Stoppwörter wirklich weg sind
        assertTrue(!result.contains("ja"))
        assertTrue(!result.contains("damit"))
        assertTrue(!result.contains("mehr"))
    }

    @Test
    fun `test custom stem rule with maximum length difference of two`() {
        // Stamm "mein" (Länge 4)

        // 1. "meine" -> Unterschied 1 (Länge 5) -> MUSS GEFILTERT WERDEN
        val resultMeine = AiTextUtil.tokenizeAndClean("meine")
        assertTrue(resultMeine.isEmpty(), "'meine' sollte gefiltert werden")

        // 2. "meinen" -> Unterschied 2 (Länge 6) -> MUSS GEFILTERT WERDEN
        val resultMeinen = AiTextUtil.tokenizeAndClean("meinen")
        assertTrue(resultMeinen.isEmpty(), "'meinen' sollte gefiltert werden")

        // 3. "meinung" -> Unterschied 3 (Länge 7) -> DARF NICHT GEFILTERT WERDEN
        val resultMeinung = AiTextUtil.tokenizeAndClean("meinung")
        assertEquals(listOf("meinung"), resultMeinung, "'meinung' muss erhalten bleiben!")
    }

    @Test
    fun `test the Bein trap`() {
        // Das Wort "Bein" enthält "ein", fängt aber mit B an.
        // Unsere 'startsWith'-Regel muss das wichtige Nomen "Bein" beschützen!
        val input = "Mein Bein tut weh"
        val result = AiTextUtil.tokenizeAndClean(input)

        // "mein" fliegt raus (Stamm), "tut" und "weh" bleiben.
        // "bein" MUSS unbedingt drin bleiben!
        assertTrue(result.contains("bein"), "Die 'Bein'-Falle hat zugeschnappt! Das Wort wurde fälschlicherweise gelöscht.")
    }

    @Test
    fun `test case insensitivity and special characters`() {
        // Groß-/Kleinschreibung und Satzzeichen
        val input = "SOFTWARE-Entwicklung, oder App?!"
        val result = AiTextUtil.tokenizeAndClean(input)

        // Erwartet: ["softwareentwicklung", "app"] -> "oder" fliegt als exaktes Stoppwort raus
        assertEquals(2, result.size)
        assertTrue(result.contains("softwareentwicklung"))
        assertTrue(result.contains("app"))
    }
}