package com.backend.todo_api.services

import com.backend.todo_api.model.FocusType
import com.backend.todo_api.utils.AiTextUtil
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log

@Service
class FocusPredictorService {

    // 🧠 Das Gedächtnis des Modells im Speicher
    private val logPrior = ConcurrentHashMap<FocusType, Double>()
    private val logLikelihood = ConcurrentHashMap<String, Map<FocusType, Double>>()
    private val vocab = ConcurrentHashMap.newKeySet<String>()

    // Ein Sicherheitsnetz, falls das Modell noch nie trainiert wurde
    @Volatile
    private var isTrained = false

    /**
     * Mathematisches Training (Naive-Bayes) mit kombinierten Seed- und Echt-Daten
     */
    fun train(trainingPairs: List<Pair<String, FocusType>>) {
        if (trainingPairs.isEmpty()) return

        val totalDocs = trainingPairs.size
        val docCountPerClass = mutableMapOf<FocusType, Int>()
        val wordCountsPerClass = mutableMapOf<FocusType, MutableMap<String, Int>>()
        val totalWordsPerClass = mutableMapOf<FocusType, Int>()

        // Enums initialisieren
        FocusType.values().forEach { type ->
            docCountPerClass[type] = 0
            wordCountsPerClass[type] = mutableMapOf()
            totalWordsPerClass[type] = 0
        }

        // Zählen der Vorkommnisse
        val localVocab = mutableSetOf<String>()
        for ((text, focusType) in trainingPairs) {
            docCountPerClass[focusType] = docCountPerClass[focusType]!! + 1
            val words = AiTextUtil.tokenizeAndClean(text)

            for (word in words) {
                localVocab.add(word)
                val classWords = wordCountsPerClass[focusType]!!
                classWords[word] = classWords.getOrDefault(word, 0) + 1
                totalWordsPerClass[focusType] = totalWordsPerClass[focusType]!! + 1
            }
        }

        // Thread-sicher ins globale Gedächtnis übertragen
        vocab.clear()
        vocab.addAll(localVocab)
        val vocabSize = vocab.size

        // 2. Log-Priors berechnen ($P(C)$)
        FocusType.values().forEach { type ->
            val count = docCountPerClass[type]!!
            // Laplace-Smoothing für die Klassen, falls eine Klasse mal 0 Dokumente hat
            logPrior[type] = log((count + 1).toDouble() / (totalDocs + FocusType.values().size), 2.0)
        }

        // 3. Log-Likelihoods berechnen ($P(W|C)$) mit Laplace-Smoothing
        val localLikelihood = ConcurrentHashMap<String, Map<FocusType, Double>>()
        for (word in vocab) {
            val classProbabilities = mutableMapOf<FocusType, Double>()

            FocusType.values().forEach { type ->
                val wordCountInClass = wordCountsPerClass[type]!!.getOrDefault(word, 0)
                val totalWordsInClass = totalWordsPerClass[type]!!

                // Formel: log((WortAnzahlInKlasse + 1) / (AlleWörterInKlasse + VokabularGröße))
                val pWordGivenClass = (wordCountInClass + 1).toDouble() / (totalWordsInClass + vocabSize)
                classProbabilities[type] = log(pWordGivenClass, 2.0)
            }
            localLikelihood[word] = classProbabilities
        }

        logLikelihood.clear()
        logLikelihood.putAll(localLikelihood)
        isTrained = true
    }

    /**
     * Sagt den Fokus-Typ für einen neuen Task-Text voraus
     */
    fun predict(text: String): FocusType {
        // Wenn die DB komplett leer ist und noch nie trainiert wurde -> Standard zurückgeben
        if (!isTrained) return FocusType.LOW_FOCUS

        val words = AiTextUtil.tokenizeAndClean(text)
        val scorePerClass = mutableMapOf<FocusType, Double>()

        // Wir starten mit dem Log-Prior der jeweiligen Klasse
        FocusType.values().forEach { type ->
            scorePerClass[type] = logPrior[type] ?: 0.0
        }

        // Wir addieren die Log-Likelihoods aller bekannten Wörter im Text
        for (word in words) {
            val wordProbabilities = logLikelihood[word] ?: continue // Unbekannte Wörter ignorieren
            FocusType.values().forEach { type ->
                scorePerClass[type] = scorePerClass[type]!! + (wordProbabilities[type] ?: 0.0)
            }
        }

        // Die Klasse mit dem höchsten Score gewinnt!
        return scorePerClass.maxByOrNull { it.value }?.key ?: FocusType.LOW_FOCUS
    }
}