package com.backend.todo_api.services

import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.RecommendationRoundRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.PlannerType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional

class PlannerCoordinatorBlueprintTest {

    private val todoRepository = mockk<TodoRepository>()
    private val userRepository = mockk<UserRepository>()
    private val recommendationRoundRepository = mockk<RecommendationRoundRepository>()
    private val plannerSettingsRepository = mockk<PlannerSettingsRepository>()
    private val todoService = mockk<TodoService>()
    private val plannerInterface = mockk<PlannerInterface>()

    // Feste Clock auf 11:00 Uhr (Prime Time) fixieren
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-06-06T11:00:00Z"),
        ZoneId.systemDefault()
    )

    private val coordinator = PlannerCoordinator(
        todoRepository = todoRepository,
        userRepository = userRepository,
        recommendationRoundRepository = recommendationRoundRepository,
        plannerSettingsRepository = plannerSettingsRepository,
        todoService = todoService,
        planners = listOf(plannerInterface),
        clock = fixedClock
    )

    @Test
    fun `test generateDailyBlueprint filters snoozed todos and respects prime time`() {
        val userId = "user-123"
        val currentTime = System.currentTimeMillis()

        every { userRepository.existsById(userId) } returns true

        // Settings: Prime Time von 10 bis 18 Uhr
        every { plannerSettingsRepository.findById(userId) } returns Optional.of(
            PlannerSettingsEntity(id = userId, primeTimeStartHour = 10, primeTimeEndHour = 18)
        )

        // Todos: Eins ist aktiv, eins schläft bis in die Zukunft
        val todos = listOf(
            TodoEntity(id = "todo-active", userId = userId, task = "Wichtige Aufgabe", effort = 3, snoozedUntil = 0L),
            TodoEntity(id = "todo-snoozed", userId = userId, task = "Schlafende Aufgabe", effort = 1, snoozedUntil = currentTime + 3600000L)
        )
        every { todoRepository.findActivePlannerTodosForUser(userId) } returns todos

        val recommendationSlot = slot<List<TodoEntity>>()
        every {
            plannerInterface.calculatePerfectRecommendation(
                userId = userId,
                candidates = capture(recommendationSlot),
                userEnergy = EnergyLevel.HIGH,
                workingTimeLeft = any()
            )
        } answers {
            com.backend.todo_api.model.PlannerRecomendation(
                plannerType = PlannerType.BAYES,
                todoId = "todo-active",
                score = 0.88,
                energyLevel = EnergyLevel.HIGH,
                timeUntilDue = 0L,
                workingTimeLeft = 3L,
                effort = 3,
                reason = "TEST_REASON"
            )
        }

        every { recommendationRoundRepository.save(any()) } answers { firstArg() }

        // Ausführen
        val response = coordinator.generateDailyBlueprint(userId)

        // Verifizieren
        assertNotNull(response)
        assertEquals(userId, response.userId)

        // Prüfen, ob das schlafende Todo aus den Kandidaten herausgefiltert wurde
        val passedCandidates = recommendationSlot.captured
        assert(passedCandidates.none { it.id == "todo-snoozed" })
        assert(passedCandidates.any { it.id == "todo-active" })
    }
}