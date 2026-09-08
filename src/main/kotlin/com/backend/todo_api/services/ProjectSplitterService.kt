package com.backend.todo_api.services

import com.backend.todo_api.data.entity.AiMilestoneKnowledgeEntity
import com.backend.todo_api.data.repository.AiMilestoneKnowledgeRepository
import com.backend.todo_api.dto.MilestoneSuggestionDto
import com.backend.todo_api.dto.MilestoneSuggestionsResponse
import com.backend.todo_api.model.MilestonePointsKnowledge
import com.backend.todo_api.utils.AiTextUtil
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ProjectSplitterService(
    private val aiKnowledgeRepository: AiMilestoneKnowledgeRepository
) {

    // Synonyme-Map: Leitet Alias-Wörter auf das Haupt-Schlüsselwort um
    private val synonymMap = mapOf(
        "roman" to "buch",
        "app" to "software",
        "programm" to "software",
        "studieren" to "lernen",
        "kurs" to "lernen",
        "konferenz" to "meeting",
        "video" to "film"
    )

    // Schablonen-Basis: Nur noch die echten Haupt-Schlüsselwörter
    private val templateKnowledge = mapOf(
        "buch" to listOf("Idee entwickeln", "Handlung ausarbeiten", "Kapitel planen", "Text schreiben"),
        "film" to listOf("Idee entwickeln", "Drehbuch schreiben", "Schauspieler finden", "Episoden planen", "Szenen drehen"),
        "meeting" to listOf("Agenda planen", "Teilnehmer benachrichtigen", "Bericht vorbereiten", "Folien machen"),
        "software" to listOf("Anforderungen analysieren", "Architektur planen", "Datenbank-Design erstellen", "Code schreiben", "Tests durchführen", "Deployment vorbereiten"),
        "lernen" to listOf("Thema eingrenzen", "Lernmaterial sammeln", "Zeitplan erstellen", "Theorie durcharbeiten", "Praxis-Übungen machen", "Wissen prüfen")
    )

    // 💡 UNSER UNIVERSAL-FALLBACK TEMPLATE: Greift immer, wenn Bayes & Keywords noch nichts wissen!
    private val defaultUniversalTemplate = listOf(
        "Anforderungen & Ziel festlegen",
        "Vorbereitung & Planung",
        "Hauptphase & Umsetzung",
        "Review & Abschluss"
    )

    /**
     * Berechnet die passenden Meilenstein-Vorschläge für einen Projekttitel
     */
    fun suggestMilestones(projectTitle: String, area: String, userId: String): MilestoneSuggestionsResponse {
        val calculatedSuggestions = mutableMapOf<String, MilestonePointsKnowledge>()

        // Text säubern
        val tokens = AiTextUtil.tokenizeAndClean("${projectTitle} ${area}")
        if (tokens.isEmpty()) return MilestoneSuggestionsResponse(emptyList(), emptyList())

        // 1. Schablonen-Basis einrechnen (inkl. Synonym-Auflösung!)
        for (word in tokens) {
            val mainKeyword = synonymMap.getOrDefault(word, word)

            val templates = templateKnowledge[mainKeyword]
            if (templates != null) {
                for (milestoneTitle in templates) {
                    val milestonePointsKnowledge = calculatedSuggestions.getOrDefault(
                        milestoneTitle,
                        MilestonePointsKnowledge(0, mutableSetOf())
                    )
                    milestonePointsKnowledge.score += 100
                    milestonePointsKnowledge.words.add(mainKeyword)
                    calculatedSuggestions[milestoneTitle] = milestonePointsKnowledge
                }
            }
        }

        // 2. Dynamische User-Historie aus der DB laden
        val userKnowledgeList = aiKnowledgeRepository.findByUserIdAndKeywordIn(userId, tokens)

        for (userKnowledge in userKnowledgeList) {
            val scoreToAdd = userKnowledge.scores

            // Mengenabgleich der Wörter ("Tickets drucken" == "Drucken Tickets")
            val existingMatch = calculatedSuggestions.keys.find { existingTitle ->
                areTitlesEquivalent(existingTitle, userKnowledge.milestoneTitle)
            }

            val targetTitle = existingMatch ?: userKnowledge.milestoneTitle

            val milestonePointsKnowledge = calculatedSuggestions.getOrPut(targetTitle) {
                MilestonePointsKnowledge(0, mutableSetOf())
            }

            milestonePointsKnowledge.score += scoreToAdd

            if (scoreToAdd > 0) {
                milestonePointsKnowledge.words.add(userKnowledge.keyword)
            }
        }

        // 3. Sortieren und filtern
        val allSuggestions = calculatedSuggestions.map { (title, milestoneKnowledge) ->
            MilestoneSuggestionDto(title, milestoneKnowledge.score, milestoneKnowledge.words.toList())
        }

        var recommendedList = allSuggestions
            .filter { it.score >= 0 }
            .sortedByDescending { it.score }

        val degradedList = allSuggestions
            .filter { it.score < 0 }
            .sortedByDescending { it.score }

        // 💡 4. UNIVERSAL-FALLBACK: Falls weder Schablone noch Bayes-Historie Treffer geliefert haben
        if (recommendedList.isEmpty()) {
            recommendedList = defaultUniversalTemplate.map { milestoneTitle ->
                MilestoneSuggestionDto(
                    title = milestoneTitle,
                    score = 10, // Leicht positiver Start-Score für ordentliches Logging
                    words = listOf("Basis")
                )
            }
        }

        return MilestoneSuggestionsResponse(
            recommended = recommendedList,
            degraded = degradedList
        )
    }

    /**
     * LERN-FUNKTION 1: Erfolg verbuchen
     */
    @Transactional
    fun trackMilestoneSelection(projectTitle: String, projectArea: String, milestoneTitle: String, userId: String) {
        val tokens = AiTextUtil.tokenizeAndClean("${projectTitle} ${projectArea}")

        for (word in tokens) {
            val existingKnowledge = aiKnowledgeRepository.findByUserIdAndKeywordAndMilestoneTitle(userId, word, milestoneTitle)

            if (existingKnowledge != null) {
                existingKnowledge.scores = existingKnowledge.scores + 30
                aiKnowledgeRepository.save(existingKnowledge)
            } else {
                val newKnowledge = AiMilestoneKnowledgeEntity(
                    userId = userId,
                    keyword = word,
                    milestoneTitle = milestoneTitle,
                    scores = 10,
                )
                println("[KI-LERNEN] trackMilestoneSelection Wort: $word | Meilenstein: $milestoneTitle | Neuer Score: 10")
                aiKnowledgeRepository.save(newKnowledge)
            }
        }
    }

    /**
     * LERN-FUNKTION 2: Vorschlag ablehnen (Strafbank)
     */
    @Transactional
    fun trackMilestoneDegradation(projectTitle: String, projectArea: String, milestoneTitle: String, userId: String) {
        val tokens = AiTextUtil.tokenizeAndClean("${projectTitle} ${projectArea}")

        for (word in tokens) {
            val existingKnowledge = aiKnowledgeRepository.findByUserIdAndKeywordAndMilestoneTitle(userId, word, milestoneTitle)

            if (existingKnowledge != null) {
                existingKnowledge.scores = existingKnowledge.scores - 35
                aiKnowledgeRepository.save(existingKnowledge)
            } else {
                val degradedKnowledge = AiMilestoneKnowledgeEntity(
                    userId = userId,
                    keyword = word,
                    milestoneTitle = milestoneTitle,
                    scores = 0
                )
                println("[KI-LERNEN] trackMilestoneDegradation Wort: $word | Meilenstein: $milestoneTitle | Neuer Score: 0")
                aiKnowledgeRepository.save(degradedKnowledge)
            }
        }
    }

    private fun areTitlesEquivalent(title1: String, title2: String): Boolean {
        val words1 = AiTextUtil.tokenizeAndClean(title1).toSet()
        val words2 = AiTextUtil.tokenizeAndClean(title2).toSet()
        return words1 == words2 && words1.isNotEmpty()
    }

    /**
     * Verarbeitet eine Liste von ignorierten Meilensteinen gesammelt beim Speichern.
     */
    fun trackMultipleMilestoneDegradations(projectTitle: String, projectArea: String, milestoneTitles: List<String>, userId: String) {
        for (milestoneTitle in milestoneTitles) {
            trackMilestoneDegradation(projectTitle, projectArea, milestoneTitle, userId)
        }
    }
}