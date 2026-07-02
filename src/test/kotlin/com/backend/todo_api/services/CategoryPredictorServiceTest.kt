package com.backend.todo_api.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CategoryPredictorServiceTest {

    private lateinit var predictorService: CategoryPredictorService

    @BeforeEach
    fun setUp() {
        predictorService = CategoryPredictorService()
    }

    @Test
    fun `KI sollte richtige Kategorie basierend auf gelernten Woertern vorhersagen`() {
        // 1. SETUP: Die Trainingsdaten für diesen speziellen Request
        val trainingsDaten = listOf(
            Pair("Milch kaufen im Supermarkt", "Einkauf"),
            Pair("Brot und frische Butter holen", "Einkauf"),
            Pair("Käse für das Abendessen einkaufen", "Einkauf"),

            Pair("Kotlin Bug im Backend fixen", "Arbeit"),
            Pair("Spring Boot Project für die Schulung programmieren", "Arbeit"),
            Pair("Neues Feature im Code umsetzen", "Arbeit")
        )

        // 2. AKTION & ÜBERPRÜFUNG: Direkt über die zustandslose Methode testen!
        // "Margarine" kennt sie nicht, aber "kaufen" zieht es stark zu Einkauf
        val vorschlag1 = predictorService.predictStateless("Margarine kaufen", trainingsDaten)
        assertEquals("Einkauf", vorschlag1)

        // "IntelliJ" kennt sie nicht, aber "Backend" und "fixen" zieht es zu Arbeit
        val vorschlag2 = predictorService.predictStateless("IntelliJ Backend Fehler fixen", trainingsDaten)
        assertEquals("Arbeit", vorschlag2)
    }

    @Test
    fun `Die Stoppwort-Bremse sollte verhindern dass das Wort UND die Kategorie verfaelscht`() {
        val trainingsDaten = listOf(
            Pair("Milch und Brot und Butter und Käse und Honig kaufen", "Einkauf"),
            Pair( "Code programmieren", "Arbeit")
        )

        // Wenn wir jetzt einen Satz mit "und" prüfen, darf die KI NICHT automatisch Einkauf vorschlagen.
        val vorschlag = predictorService.predictStateless("Neuen Code und Features programmieren", trainingsDaten)

        // Dank der Stoppwort-Bremse siegt "Arbeit"!
        assertEquals("Arbeit", vorschlag)
    }

    // 💡 HINWEIS: Die Methode getAllLearnedCategories() wurde im zustaendslosen Service entfernt,
    // da der Service keine Daten mehr speichert. Deswegen haben wir diesen Test hier entfernt!
}