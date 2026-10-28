package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.RecommendationRoundRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.PlannerType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test


class PlannerCoordinatorMorningTest {

    private val todoRepository = mockk<TodoRepository>()
    private val userRepository = mockk<UserRepository>()
    private val recommendationRoundRepository = mockk<RecommendationRoundRepository>()
    private val todoService = mockk<TodoService>()
    private val plannerInterface = mockk<PlannerInterface>()

    private val coordinator = PlannerCoordinator(
        todoRepository = todoRepository,
        userRepository = userRepository,
        recommendationRoundRepository = recommendationRoundRepository,
        todoService = todoService,
        planners = listOf(plannerInterface)
    )

    @Test
    fun `test getMorningRecommendations uses historical pause todos when available`() {
        val userId = "user-123"

        // Mock: User existiert
        every { userRepository.existsById(userId) } returns true

        // Mock: Historische Todos enthalten ein Pause-Todo (effort == 0) und ein normales Todo
        val historicalTodos = listOf(
            TodoEntity(id = "1", userId = userId, task = "Normales Ticket", effort = 5),
            TodoEntity(id = "2", userId = userId, task = "Kaffee trinken", effort = 0)
        )
        every { todoRepository.findAll() } returns historicalTodos

        // Mock: Planner gibt eine Empfehlung zurück
        val recommendationSlot = slot<List<TodoEntity>>()
        every {
            plannerInterface.calculatePerfectRecommendation(
                userId = userId,
                candidates = capture(recommendationSlot),
                userEnergy = EnergyLevel.LOW,
                workingTimeLeft = 480L
            )
        } answers {
            com.backend.todo_api.model.PlannerRecomendation(
                plannerType = PlannerType.BAYES,
                todoId = "2",
                score = 0.95,
                energyLevel = EnergyLevel.LOW,
                timeUntilDue = 0L,
                workingTimeLeft = 480L,
                effort = 0,
                reason = "TEST_REASON"
            )
        }

        every { recommendationRoundRepository.save(any()) } answers { firstArg() }
        every { todoService.mapToDto(any()) } answers { mockk() }

        // Ausführen
        val response = coordinator.getMorningRecommendations(userId)

        // Verifizieren
        assertNotNull(response)
        // Prüfen, ob an die KI nur die Pause-Kandidaten (effort == 0) übergeben wurden
        val candidatesPassedToPlanner = recommendationSlot.captured
        assertEquals(1, candidatesPassedToPlanner.size)
        assertEquals("2", candidatesPassedToPlanner[0].id)
        assertEquals("Kaffee trinken", candidatesPassedToPlanner[0].task)
    }

    @Test
    fun `test getMorningRecommendations uses fallback when no history exists`() {
        val userId = "user-123"

        every { userRepository.existsById(userId) } returns true
        // Keine Historie vorhanden
        every { todoRepository.findAll() } returns emptyList()

        val recommendationSlot = slot<List<TodoEntity>>()
        every {
            plannerInterface.calculatePerfectRecommendation(
                userId = userId,
                candidates = capture(recommendationSlot),
                userEnergy = EnergyLevel.LOW,
                workingTimeLeft = 480L
            )
        } answers {
            com.backend.todo_api.model.PlannerRecomendation(
                plannerType = PlannerType.BAYES,
                todoId = "morning_coffee",
                score = 0.90,
                energyLevel = EnergyLevel.LOW,
                timeUntilDue = 0L,
                workingTimeLeft = 480L,
                effort = 0,
                reason = "FALLBACK_REASON"
            )
        }

        every { recommendationRoundRepository.save(any()) } answers { firstArg() }
        every { todoService.mapToDto(any()) } answers { mockk() }

        // Ausführen
        val response = coordinator.getMorningRecommendations(userId)

        // Verifizieren
        assertNotNull(response)
        val candidatesPassedToPlanner = recommendationSlot.captured
        // Prüfen, ob die Fallback-Pausen greifen
        assert(candidatesPassedToPlanner.any { it.id == "morning_coffee" })
    }
}