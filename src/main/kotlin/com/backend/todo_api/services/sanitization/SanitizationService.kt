package com.backend.todo_api.services.sanitization

import com.backend.todo_api.data.entity.SensitivePatternEntity
import com.backend.todo_api.data.repository.SensitivePatternRepository
import com.backend.todo_api.dto.CreatePatternFromSelectionDto
import com.backend.todo_api.dto.SanitizationFeedbackDto
import com.backend.todo_api.exceptions.InvalidInputException
import com.backend.todo_api.exceptions.ResourceAlreadyExistsException
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.PatternTypeEnum
import com.backend.todo_api.model.SanitizationCleanResult
import com.backend.todo_api.model.SanitizationInput
import com.backend.todo_api.model.SanitizationPattern
import com.backend.todo_api.model.SanitizationResult
import com.backend.todo_api.model.SensitiveMatch
import com.backend.todo_api.providers.AiGlobalDataProvider
import com.backend.todo_api.utils.AiTextUtil
import jakarta.annotation.PostConstruct
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException
import kotlin.collections.isNotEmpty

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
                SensitivePatternEntity(
                    patternValue = "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b",
                    patternType = "REGEX",
                    reason = "EMAIL",
                    weight = 0.7
                ),
                SensitivePatternEntity(
                    patternValue = "sk_live_[0-9a-zA-Z]{20,}",
                    patternType = "REGEX",
                    reason = "API_TOKEN",
                    weight = 0.9
                )
            )
            patternRepository.saveAll(defaultPatterns)
        }
    }

    @Transactional
    fun createPatternFromSelection(dto: CreatePatternFromSelectionDto): SensitivePatternEntity {
        val tokens = AiTextUtil.tokenizeWithIndices(dto.selectedText) { word ->
            AiTextUtil.isStopWord(word)
        }

        val meaningfulTokens = tokens.filter { !it.isStopWord }

        if (meaningfulTokens.isEmpty()) {
            throw InvalidInputException("Der markierte Text enthält keine verwertbaren Ankerwörter.")
        }

        val keyword = when (dto.patternType) {
            PatternTypeEnum.KEYWORD_VALUE_BEFORE -> meaningfulTokens.last().text
            PatternTypeEnum.KEYWORD_VALUE_AFTER -> meaningfulTokens.first().text
            else -> meaningfulTokens.first().text
        }

        // 🚀 Direkter, performanter Datenbank-Check!
        if (patternRepository.existsByPatternValueIgnoreCaseAndPatternTypeIgnoreCase(keyword, dto.patternType.name)) {
            throw ResourceAlreadyExistsException("Ein Muster mit dem Anker '$keyword' (Typ: ${dto.patternType.name}) existiert bereits in der Datenbank!")
        }

        val patternEntity = SensitivePatternEntity(
            patternValue = keyword,
            patternType = dto.patternType.name,
            reason = dto.reason,
            weight = 1.0
        )

        return patternRepository.save(patternEntity)
    }

    // 2. Die Scanner-Methode (Die Anwendung mit Gewicht-Klassifizierung)
    fun scanAndEvaluate(description: String?): SanitizationResult {
        if (description.isNullOrBlank()) return SanitizationResult(emptyList(), false)

        val patterns = patternRepository.findAll()
        val matches = mutableListOf<SensitiveMatch>()

        for (patternEntity in patterns) {
            val regex = SanitizationPattern.Companion.create(patternEntity.patternValue, PatternTypeEnum.valueOf(patternEntity.patternType)).createRegex()

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

    /**
     * 📋 Liefert eine Liste aller bereits verwendeten Kategorien/Gründe (distinct & sortiert)
     */
    fun getUniqueReasons(): List<String> {
        return patternRepository.findAll()
            .map { it.reason }
            .distinct()
            .sorted()
    }

    /**
     * 🧠 Analysiert den markierten Text, extrahiert das Ankerwort und sucht in der Historie
     * nach einem bekannten Grund (Reason). Wenn nichts gefunden wird, dient das Ankerwort als Fallback.
     */
    fun predictReason(selectedText: String, patternType: PatternTypeEnum): String {
        // 1. Stoppwörter über dein AiTextUtil herausfiltern
        val tokens = AiTextUtil.tokenizeWithIndices(selectedText) { word ->
            AiTextUtil.isStopWord(word)
        }
        val meaningfulTokens = tokens.filter { !it.isStopWord }

        if (meaningfulTokens.isEmpty()) {
            return "SENSITIVE_DATA"
        }

        // 3. Keyword je nach Typ extrahieren (genau wie beim Erstellen des Musters)
        val keyword = when (patternType) {
            PatternTypeEnum.KEYWORD_VALUE_BEFORE -> meaningfulTokens.last().text
            PatternTypeEnum.KEYWORD_VALUE_AFTER -> meaningfulTokens.first().text
            else -> meaningfulTokens.first().text
        }

        // 4. In der Historie (Datenbank) nachschauen, ob es dieses Keyword schon mal gab
        val existingPattern = patternRepository.findAll().find {
            it.patternValue.equals(keyword, ignoreCase = true)
        }

        // Wenn bekannt, den historischen Reason zurückgeben, sonst das Keyword in Großbuchstaben
        return existingPattern?.reason ?: keyword.uppercase()
    }
}

@Component
class SanitizationGlobalDataProvider(
    private val patternRepository: SensitivePatternRepository // Dein Repository für Muster
) : AiGlobalDataProvider<SanitizationInput, String> {

    override fun getContextType(): AiContextType {
        return AiContextType.SANITIZATION_REASON
    }

    override fun getGlobalTrainingPairs(): List<Pair<SanitizationInput, String>> {
        // Holt alle gespeicherten Muster aus der DB und mappt sie zu (selectedText -> reason)
        return patternRepository.findAll().map { pattern ->
            Pair(
                SanitizationInput(pattern.patternValue, patternType = PatternTypeEnum.valueOf( pattern.patternType)),
                pattern.reason
            )
        }
    }
}