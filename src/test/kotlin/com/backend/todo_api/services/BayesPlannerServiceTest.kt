package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserAiPreferenceRepository
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.FeedbackForPlanner
import com.backend.todo_api.model.PreferenceType
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class BayesPlannerServiceTest {

    private val plannerSettingsRepository: PlannerSettingsRepository = mockk()
    private val focusPredictorService: FocusPredictorService = mockk()
    private val preferenceRepository: UserAiPreferenceRepository = mockk()
    private val todoRepository: TodoRepository = mockk()

    private lateinit var bayesPlannerService: BayesPlannerService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        bayesPlannerService = BayesPlannerService(
            plannerSettingsRepository,
            focusPredictorService,
            preferenceRepository,
            todoRepository
        )
    }

    @Test
    fun `processUserFeedback sollte Score positiv aktualisieren wenn Motivation gewaehlt wurde`() {
        // GIVEN
        val todoId = "todo-123"
        val userId = "user-999"

        val todo = TodoEntity().apply {
            id = todoId
            category = "Arbeit"
        }

        val feedback = FeedbackForPlanner(
            userId = userId,
            todoId = todoId,
            userEnergy = EnergyLevel.HIGH,
            workingTimeLeft = 4L,
            accepted = true,
            reason = "motivation",
            score = 1.0,
            timeUntilDue = 1000L,
            effort = 3,
            isOwnRecommendation = true,
            category = "Allgemein"
        )

        every { todoRepository.findById(todoId) } returns Optional.of(todo)
        every {
            preferenceRepository.findByUserIdAndUserEnergyAndPreferenceTypeAndPreferenceValue(
                userId, EnergyLevel.ANY, PreferenceType.MOTIVATION, "Tag:Arbeit"
            )
        } returns null
        every { preferenceRepository.save(any()) } answers { firstArg() }

        // WHEN
        bayesPlannerService.processUserFeedback(feedback)

        // THEN
        verify {
            preferenceRepository.save(match {
                it.userId == userId &&
                        it.preferenceType == PreferenceType.MOTIVATION &&
                        it.preferenceValue == "Tag:Arbeit" &&
                        it.score == 5
            })
        }
    }

    @Test
    fun `processUserFeedback sollte keine Score-Aenderung vornehmen wenn Grund null ist`() {
        // GIVEN
        val todoId = "todo-123"
        val todo = TodoEntity().apply { id = todoId }

        val feedback = FeedbackForPlanner(
            userId = "user-999",
            todoId = todoId,
            userEnergy = EnergyLevel.HIGH,
            workingTimeLeft = 4L,
            accepted = true,
            reason = null, // "Einfach so" / Kein Grund
            score = 1.0,
            timeUntilDue = 1000L,
            effort = 3,
            isOwnRecommendation = true,
            category = "Allgemein"
        )

        every { todoRepository.findById(todoId) } returns Optional.of(todo)

        // WHEN
        bayesPlannerService.processUserFeedback(feedback)

        // THEN
        verify(exactly = 0) { preferenceRepository.save(any()) }
    }

    @Test
    fun `processUserFeedback sollte bei Ablehnung fremder Vorschlaege keine Bestrafung verteilen`() {
        // GIVEN
        val todoId = "todo-123"
        val todo = TodoEntity().apply { id = todoId }

        val feedback = FeedbackForPlanner(
            userId = "user-999",
            todoId = todoId,
            userEnergy = EnergyLevel.HIGH,
            workingTimeLeft = 4L,
            accepted = false,
            reason = "too_heavy",
            score = 1.0,
            timeUntilDue = 1000L,
            effort = 3,
            isOwnRecommendation = false, // Fremder Vorschlag!
            category = "Allgemein"
        )

        // Da der Service das Todo zuerst lädt, müssen wir das hier mocken:
        every { todoRepository.findById(todoId) } returns Optional.of(todo)

        // WHEN
        bayesPlannerService.processUserFeedback(feedback)

        // THEN
        // Es wird das Todo geladen, aber wegen isOwnRecommendation = false wird nichts gespeichert
        verify(exactly = 1) { todoRepository.findById(todoId) }
        verify(exactly = 0) { preferenceRepository.save(any()) }
    }
}