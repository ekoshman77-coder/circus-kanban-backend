package com.backend.todo_api.services

import com.backend.todo_api.utils.AiTextUtil
import org.springframework.stereotype.Service
import kotlin.math.log

@Service
class CategoryPredictorService {

    /**
     * 🔮 DIE ZUSTANDSLOSE VORHERSAGE (Flüchtig & Sicher!)
     * @param text Der aktuell vom User eingetippte Text (Vorschau)
     * @param trainingData Die frisch geholten DB-Daten GENAU dieses Users (Pair(Kategorie, Text))
     */
    fun <I> predictStateless(
        input: I,
        trainingData: List<Pair<I, String>>,
        textExtractor: (I) -> String
    ): String {
        val textToPredict = textExtractor(input)
        val wordsToPredict = AiTextUtil.tokenizeAndClean(textToPredict)
        if (wordsToPredict.isEmpty() || trainingData.isEmpty()) return "Allgemein"

        val localWordCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val localCategoryCounts = mutableMapOf<String, Int>()
        val localVocabulary = mutableSetOf<String>()

        for ((trainInput, category) in trainingData) {
            if (category.isBlank()) continue

            localCategoryCounts[category] = localCategoryCounts.getOrDefault(category, 0) + 1

            val trainText = textExtractor(trainInput)
            val words = AiTextUtil.tokenizeAndClean(trainText)
            val categoryMap = localWordCounts.getOrPut(category) { mutableMapOf() }
            for (word in words) {
                categoryMap[word] = categoryMap.getOrDefault(word, 0) + 1
                localVocabulary.add(word)
            }
        }

        if (localCategoryCounts.isEmpty()) return "Allgemein"

        val totalDocs = localCategoryCounts.values.sum().toDouble()
        var bestCategory = "Allgemein"
        var highestScore = -Double.MAX_VALUE

        for ((category, count) in localCategoryCounts) {
            var score = log(count.toDouble() / totalDocs, 10.0)

            val categoryWords = localWordCounts[category] ?: mutableMapOf()
            val totalWordsInCategory = categoryWords.values.sum()

            for (word in wordsToPredict) {
                val wordCountInCategory = categoryWords.getOrDefault(word, 0)
                val wordProbability = (wordCountInCategory + 1).toDouble() /
                        (totalWordsInCategory + localVocabulary.size)

                score += log(wordProbability, 10.0)
            }

            if (score > highestScore) {
                highestScore = score
                bestCategory = category
            }
        }

        return bestCategory
    }
}