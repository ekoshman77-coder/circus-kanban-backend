package com.backend.todo_api.services

import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.providers.AiGlobalDataProvider
import com.backend.todo_api.providers.AiInitialSeedProvider
import com.backend.todo_api.utils.AiTextUtil
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class TrainManager(
    // 🌍 Unser universelles KI-Framework (Sammelt alle Beans automatisch)
    private val globalDataProviders: List<AiGlobalDataProvider<*>>,
    private val initialSeedProviders: List<AiInitialSeedProvider<*>>,

    private val categoryPredictorService: CategoryPredictorService,
    private val focusPredictorService: FocusPredictorService // 🧠 JETZT ERREICHBAR!
) {

    /**
     * 🌱 STARTUP-TRIGGER: Sobald der Server hochfährt und bereit ist,
     * wird das Fokus-KI-Modell im RAM automatisch mit allen Daten gefüttert!
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        println(" Server gestartet. Initialisiere KI-Modell-Training...")
        trainAllModels()
    }

    /**
     * Startet das globale Training für alle zustandsbehafteten KI-Kontexte
     */
    fun trainAllModels() {
        // --- 1. FOKUS-TRAINING ---
        // Holt echte + künstliche Daten für "todo_focus" und füttert das Gehirn im RAM
        trainModelStateful(AiContextType.TODO_FOCUS) { trainingPairs ->
            @Suppress("UNCHECKED_CAST")
            val focusPairs = trainingPairs as List<Pair<String, FocusType>>
            focusPredictorService.train(focusPairs)
        }

        // Hinweis: Kategorien ("todo_category" / "note") und Aufwand ("todo_effort")
        // laufen zustandslos (stateless) direkt bei der Vorhersage ab.
    }

    /**
     * GLOBALER KATEGORIE-PREDICTOR (Zustandslos über dein neues Framework)
     */
    fun trainAndPredictGlobal(text: String, contextType: AiContextType): String {
        val matchingProvider = globalDataProviders.find { it.getContextType() == contextType }
            ?: throw IllegalArgumentException("Kein KI-Provider für Typ '$contextType' registriert!")

        @Suppress("UNCHECKED_CAST")
        val globalData = matchingProvider.getGlobalTrainingPairs() as List<Pair<String, String>>

        return categoryPredictorService.predictStateless(text, globalData)
    }

    /**
     * GLOBALER AUFWANDS-SCHÄTZER (Wort-Ähnlichkeits-Regression)
     */
    fun predictGlobalEffort(text: String): Int {
        val todoEffortProvider = globalDataProviders.find { it.getContextType() == AiContextType.TODO_EFFORT } ?: return 2

        @Suppress("UNCHECKED_CAST")
        val globalData = todoEffortProvider.getGlobalTrainingPairs() as List<Pair<String, Int>>

        if (globalData.isEmpty()) return 2

        val inputWords = AiTextUtil.tokenizeAndClean(text)
        if (inputWords.isEmpty()) return 2

        var totalHours = 0
        var matchCount = 0

        for ((oldTaskText, usedEffort) in globalData) {
            val oldWords = AiTextUtil.tokenizeAndClean(oldTaskText)
            val overlapCount = inputWords.intersect(oldWords.toSet()).size

            if (overlapCount > 0) {
                totalHours += usedEffort
                matchCount++
            }
        }

        if (matchCount > 0) {
            return (totalHours.toDouble() / matchCount).roundToInt()
        }

        val globalAverage = globalData.map { it.second }.average()
        return if (globalAverage.isNaN()) 2 else globalAverage.roundToInt()
    }

    /**
     * Holt alle global verfügbaren Kategorien des Kontextes für Vorschlagslisten im Frontend
     */
    fun getAllGlobalCategories(contextType: AiContextType): List<String> {
        val matchingProvider = globalDataProviders.find { it.getContextType() == contextType }
            ?: throw IllegalArgumentException("Kein KI-Provider für Typ '$contextType' registriert!")

        @Suppress("UNCHECKED_CAST")
        val pairs = matchingProvider.getGlobalTrainingPairs() as List<Pair<String, String>>

        return pairs
            .map { it.second }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    /**
     * GLOBALER FOKUS-PREDICTOR (Zustandsbehaftet - nutzt das trainierte Modell im RAM)
     */
    fun predictGlobalFocus(text: String): FocusType {
        return focusPredictorService.predict(text)
    }

    /**
     * Klebt die echten Daten mit den Seed-Daten (Lehrbuch) für einen Kontext zusammen.
     */
    fun trainModelStateful(context: AiContextType, trainingBlock: (List<Pair<String, *>>) -> Unit) {
        val dataProvider = globalDataProviders.find { it.getContextType() == context }
        val seedProvider = initialSeedProviders.find { it.getContextType() == context }

        val realPairs = dataProvider?.getGlobalTrainingPairs() ?: emptyList()
        val seedPairs = seedProvider?.getInitialSeeds() ?: emptyList()

        val combinedTrainingData = seedPairs + realPairs

        if (combinedTrainingData.isNotEmpty()) {
            trainingBlock(combinedTrainingData)
            println("🤖 KI-Framework: '$context' erfolgreich im Speicher trainiert! (Gesamt: ${combinedTrainingData.size} | Lehrbuch: ${seedPairs.size}, User-Daten: ${realPairs.size})")
        }
    }
}