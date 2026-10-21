package com.backend.todo_api.services

import com.backend.todo_api.data.entity.SensitivePatternEntity
import com.backend.todo_api.data.repository.SensitivePatternRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.dto.SanitizationFeedbackDto
import com.backend.todo_api.dto.SanitizationFeedbackItemDto
import com.backend.todo_api.dto.SensitiveMatchDto
import com.backend.todo_api.model.PatternTypeEnum
import com.backend.todo_api.model.SanitizationPattern
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.mockk.verify

class SanitizationServiceTest {

    private val patternRepository: SensitivePatternRepository = mockk()

    // Instanz unseres Services mit gemockten Repositories
    private val sanitizationService = SanitizationService(
        patternRepository,
    )

    @Test
    fun `scanAndEvaluate should detect sensitive words and keep isApproved null for admin review`() {
        // Arrange: Wir simulieren ein in der DB gespeichertes Pattern für "password"
        val mockPattern = SensitivePatternEntity(
            id = "pattern-123",
            patternValue = "password",
            patternType = "EXACT",
            reason = "PASSWORD",
            weight = 0.8
        )
        every { patternRepository.findAll() } returns listOf(mockPattern)

        val textToScan = "Mein neues password ist super sicher."

        // Act: Den Scanner laufen lassen
        val result = sanitizationService.scanAndEvaluate(textToScan)

        // Assert: Prüfen, ob die Erkennung greift und isApproved unberührt (null) bleibt
        assertTrue(result.found, "Der Text sollte als sensibel erkannt werden")
        assertEquals(1, result.matches.size, "Es sollte genau 1 Treffer gefunden werden")

        val match = result.matches[0]
        assertEquals("PASSWORD", match.reason)

        // 🎯 Unser Kern-Prinzip: Das System entscheidet NICHT, isApproved muss null sein!
        assertNull(match.isApproved, "isApproved muss vom System aus null sein, damit der Admin entscheidet")
    }

    @Test
    fun `processFeedback should increase weight and approvalCount when feedback is positive`() {
        // Arrange: Ein existierendes Pattern in der DB mocken
        val patternEntity = SensitivePatternEntity(
            id = "pattern-123",
            patternValue = "password",
            patternType = "EXACT",
            reason = "PASSWORD",
            weight = 0.5,
            approvalCount = 0,
            rejectionCount = 0
        )

        every { patternRepository.findById("pattern-123") } returns java.util.Optional.of(patternEntity)
        every { patternRepository.save(any()) } answers { firstArg() }

        // Wir simulieren das DTO vom Frontend, in dem der Admin den Treffer bestätigt (feedback = true)
        val feedbackDto = SanitizationFeedbackDto(
            todoId = "todo-999",
            feedbacks = listOf(
                SanitizationFeedbackItemDto(
                        match = SensitiveMatchDto(
                        startIndex = 10,
                        endIndex = 18,
                        patternId = "pattern-123",
                        reason = "PASSWORD",
                        isApproved = null
                    ),
                    feedback = true // Admin sagt: Ja, das ist sensibel!
                )
            )
        )

        // Act: Feedback verarbeiten
        sanitizationService.processFeedback(feedbackDto)

        // Assert: Prüfen, ob das Gewicht steigt und der Zähler erhöht wurde
        assertEquals(0.6, patternEntity.weight, 0.0001, "Gewicht sollte um 0.1 steigen")
        assertEquals(1, patternEntity.approvalCount, "Approval-Zähler muss 1 sein")
        assertEquals(0, patternEntity.rejectionCount, "Rejection-Zähler muss 0 bleiben")
        verify { patternRepository.save(patternEntity) }
    }

    @Test
    fun `processFeedback should decrease weight and increment rejectionCount when feedback is negative`() {
        // Arrange: Pattern mit Startgewicht 0.7
        val patternEntity = SensitivePatternEntity(
            id = "pattern-123",
            patternValue = "password",
            patternType = "EXACT",
            reason = "PASSWORD",
            weight = 0.7,
            approvalCount = 0,
            rejectionCount = 0
        )

        every { patternRepository.findById("pattern-123") } returns java.util.Optional.of(patternEntity)
        every { patternRepository.save(any()) } answers { firstArg() }

        val feedbackDto = SanitizationFeedbackDto(
            todoId = "todo-999",
            feedbacks = listOf(
                SanitizationFeedbackItemDto(
                        match = SensitiveMatchDto(
                        startIndex = 10,
                        endIndex = 18,
                        patternId = "pattern-123",
                        reason = "PASSWORD",
                        isApproved = null
                    ),
                    feedback = false // Admin sagt: Falschalarm!
                )
            )
        )

        // Act
        sanitizationService.processFeedback(feedbackDto)

        // Assert: Prüfen, ob das Gewicht sinkt (gemäß der Logik minus 0.2)
        assertEquals(0.5, patternEntity.weight, 0.0001, "Gewicht sollte um 0.2 sinken")
        assertEquals(0, patternEntity.approvalCount, "Approval-Zähler muss 0 bleiben")
        assertEquals(1, patternEntity.rejectionCount, "Rejection-Zähler muss 1 sein")
        verify { patternRepository.save(patternEntity) }
    }

    @Test
//    groups = "Testet die Regex-Erkennung und Indizes"
    fun testRegexIndices() {
        // Wir testen ein einfaches Regex für "login" mit optionalem Wert dahinter
        // Das Fragezeichen nach dem Block macht den Wert optional!
        val patternValue = "(?i)(login|signin)(\\s*[:=]?\\s*\\S+)?"
        val regex = SanitizationPattern.create(patternValue, PatternTypeEnum.REGEX).createRegex()

        val text = "Bitte hier einloggen: login: mySecretUser123"

        val matches = regex.findAll(text).toList()

        println("Anzahl Treffer: ${matches.size}")
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            val extracted = text.substring(start, end)
            println("Gefunden von Index $start bis $end: '$extracted'")
        }
    }
}