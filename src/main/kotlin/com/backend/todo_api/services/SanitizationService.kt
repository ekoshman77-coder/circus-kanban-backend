package com.backend.todo_api.services

import com.backend.todo_api.data.entity.SensitivePatternEntity
import com.backend.todo_api.data.repository.SensitivePatternRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.dto.SanitizationFeedbackDto
import com.backend.todo_api.model.PatternTypeEnum
import com.backend.todo_api.model.SanitizationCleanResult
import com.backend.todo_api.model.SanitizationPattern
import com.backend.todo_api.model.SanitizationResult
import com.backend.todo_api.model.SensitiveMatch
//import com.backend.todo_api.model.SensitivePattern
import jakarta.annotation.PostConstruct
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException

@Service
class SanitizationService(
    private val patternRepository: SensitivePatternRepository
) {
    /**
     * 🧹 Bereinigt den Text basierend auf den bestätigten Matches,
     * evaluiert das Ergebnis neu und liefert die aktualisierten Daten zurück.
     * (Greift NICHT mehr auf das TodoRepository zu!)
     */
    fun sanitizeAndEvaluateText(
        originalText: String?,
        approvedMatches: List<SensitiveMatch>
    ): SanitizationCleanResult {
        if (originalText.isNullOrBlank()) {
            return SanitizationCleanResult(
                cleanedText = "",
                isSensitive = false,
                matches = emptyList()
            )
        }

        // 1. Text basierend auf den bestätigten Treffern schwärzen
        val cleanedText = correctText(originalText, approvedMatches)

        // 2. Den geschwärzten Text erneut scannen
        val scanResult = scanAndEvaluate(cleanedText)

        return SanitizationCleanResult(
            cleanedText = cleanedText,
            isSensitive = scanResult.matches.isNotEmpty(),
            matches = scanResult.matches
        )
    }

    // 1. Der Initializer (Ersetzt unübersichtliches Liquibase)
    @PostConstruct
    fun initDefaultPatterns() {
        if (patternRepository.count() == 0L) {
            val defaultPatterns = listOf(
                SensitivePatternEntity(
                    patternValue = "password",
                    patternType = "EXACT",
                    reason = "PASSWORD",
                    weight = 0.8
                ),
                SensitivePatternEntity(
                    patternValue = "(?i)(password|passwort|pwd)[:=]?\\s*(\\S+)",
                    patternType = "REGEX",
                    reason = "PASSWORD",
                    weight = 0.8
                ),
                SensitivePatternEntity(patternValue = "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", patternType = "REGEX", reason = "EMAIL", weight = 0.7),
                SensitivePatternEntity(patternValue = "sk_live_[0-9a-zA-Z]{20,}", patternType = "REGEX", reason = "API_TOKEN", weight = 0.9)
            )
            patternRepository.saveAll(defaultPatterns)
        }
    }

    // 2. Die Scanner-Methode (Die Anwendung mit Gewicht-Klassifizierung)
    fun scanAndEvaluate(description: String?): SanitizationResult {
        if (description.isNullOrBlank()) return SanitizationResult(emptyList(), false)

        val patterns = patternRepository.findAll()
        val matches = mutableListOf<SensitiveMatch>()

        for (patternEntity in patterns) {
            val regex = SanitizationPattern.create(patternEntity.patternValue, PatternTypeEnum.valueOf(patternEntity.patternType)).createRegex()

            for (result in regex.findAll(description)) {
                matches.add(
                    SensitiveMatch(
                        startIndex = result.range.first,
                        endIndex = result.range.last + 1,
                        patternId = patternEntity.id,
                        reason = patternEntity.reason,
                        weight = patternEntity.weight,
                        isApproved = null
                    )
                )
            }
        }
        return SanitizationResult(matches, matches.any { it.isApproved == null })
    }

    // 3. Die Feedback-Methode direkt im Service
    fun processFeedback(feedbackDto: SanitizationFeedbackDto) {
        for (item in feedbackDto.feedbacks) {
            val patternId = item.match.patternId

            val patternEntity = patternRepository.findById(patternId).orElseThrow {
                IllegalArgumentException("Pattern mit ID $patternId wurde nicht gefunden")
            }

            if (item.feedback) {
                patternEntity.weight = (patternEntity.weight + 0.1).coerceIn(0.0, 1.0)
                patternEntity.approvalCount++
            } else {
                patternEntity.weight = (patternEntity.weight - 0.2).coerceIn(0.0, 1.0)
                patternEntity.rejectionCount++
            }

            patternRepository.save(patternEntity)
        }
    }

    // 4. Die Korrektur-Methode
    fun correctText(originalText: String, approvedMatches: List<SensitiveMatch>): String {
        val sortedMatches = approvedMatches.sortedByDescending { it.startIndex }
        val sb = StringBuilder(originalText)

        for (match in sortedMatches) {
            val length = match.endIndex - match.startIndex
            val replacement = "*".repeat(length)
            sb.replace(match.startIndex, match.endIndex, replacement)
        }

        return sb.toString()
    }
}