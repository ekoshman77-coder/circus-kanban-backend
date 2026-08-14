package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.FeedbackForPlanner
import com.backend.todo_api.model.PlannerType
import com.backend.todo_api.services.neural.NeuralNetwork
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NeuralPlannerServiceTest {

    private lateinit var neuralNetwork: NeuralNetwork
    private lateinit var service: NeuralPlannerService

    @BeforeEach
    fun setUp() {
        // Wir mocken das NeuralNetwork, um die Antworten exakt zu steuern
        neuralNetwork = mockk(relaxed = true)
        service = NeuralPlannerService(neuralNetwork)
    }

    @Test
    fun `calculatePerfectRecommendation - Return null when candidates list is empty`() {
        val result = service.calculatePerfectRecommendation(
            userId = "user_123",
            candidates = emptyList(),
            userEnergy = EnergyLevel.HIGH,
            workingTimeLeft = 8L
        )

        assertNull(result)
        verify(exactly = 0) { neuralNetwork.predict(any()) }
    }

    @Test
    fun `calculatePerfectRecommendation - Selects todo with highest score`() {
        // Arrange: 2 Test-Todos anlegen
        val todo1 = TodoEntity(id = "todo_1", task = "Leichte Aufgabe", effort = 2, dueDate = 0)
        val todo2 = TodoEntity(id = "todo_2", task = "Schwere Aufgabe", effort = 8, dueDate = System.currentTimeMillis() + 100000)

        // Simuliere: Das Netz gibt für Todo 1 einen niedrigeren Score als für Todo 2
        every { neuralNetwork.predict(any()) } returnsMany listOf(0.35, 0.88)

        // Act
        val recommendation = service.calculatePerfectRecommendation(
            userId = "user_123",
            candidates = listOf(todo1, todo2),
            userEnergy = EnergyLevel.MEDIUM,
            workingTimeLeft = 6L
        )

        // Assert
        assertNotNull(recommendation)
        assertEquals("todo_2", recommendation?.todoId)
        assertEquals(0.88, recommendation?.score)
        assertEquals(PlannerType.NEURAL, recommendation?.plannerType)

        // Überprüfe, dass predict genau zweimal aufgerufen wurde (für jedes Todo 1x)
        verify(exactly = 2) { neuralNetwork.predict(any()) }
    }

    @Test
    fun `processUserFeedback - Calls neuralNetwork train with target 1_0 when accepted`() {
        val feedback = FeedbackForPlanner(
            userId = "user_123",
            todoId = "todo_1",
            userEnergy = EnergyLevel.HIGH,
            workingTimeLeft = 5L,
            accepted = true, // 👈 Nutzer hat akzeptiert -> Target 1.0
            rejectReason = null,
            score = 0.85,
            timeUntilDue = 3600000L,
            effort = 4
        )

        service.processUserFeedback(feedback)

        // Überprüfe, dass train mit target = 1.0 aufgerufen wurde
        verify(exactly = 1) {
            neuralNetwork.train(
                inputs = any(),
                target = 1.0
            )
        }
    }

    @Test
    fun `processUserFeedback - Calls neuralNetwork train with target 0_0 when rejected`() {
        val feedback = FeedbackForPlanner(
            userId = "user_123",
            todoId = "todo_1",
            userEnergy = EnergyLevel.LOW,
            workingTimeLeft = 2L,
            accepted = false, // 👈 Nutzer hat abgelehnt -> Target 0.0
            rejectReason = "too_heavy",
            score = 0.40,
            timeUntilDue = 0L,
            effort = 9
        )

        service.processUserFeedback(feedback)

        // Überprüfe, dass train mit target = 0.0 aufgerufen wurde
        verify(exactly = 1) {
            neuralNetwork.train(
                inputs = any(),
                target = 0.0
            )
        }
    }
}