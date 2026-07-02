package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.exceptions.UserDeletedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import org.springframework.data.repository.findByIdOrNull

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockkStatic

class TodoServiceTest {

    @MockK
    lateinit var todoRepository: TodoRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var gamificationService: GamificationService

    @MockK
    lateinit var milestoneService: MilestoneService

    @MockK
    lateinit var categoryPredictorService: CategoryPredictorService

    @InjectMockKs
    lateinit var todoService: TodoService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic("org.springframework.data.repository.CrudRepositoryExtensionsKt")

        // Da .train() entfernt wurde, mocken wir hier die neue zustaendslose Methode
        every { categoryPredictorService.predictStateless(any(), any()) } returns "Allgemein"
    }

    // --- TEST 1: DIE GAMIFICATION-KETTE ---

    @Test
    fun `toggleStatusWithGamification sollte den Status updaten und XP verbuchen`() {
        val userId = "user-123"
        val todoId = "todo-777"

        every { userRepository.existsById(userId) } returns true

        val alteEntity = TodoEntity(id = todoId, task = "Lernen", done = false, userId = userId, effort = 5, usedEffort = 0)
        val aktualisierteEntity = TodoEntity(id = todoId, task = "Lernen", done = true, userId = userId, effort = 5, usedEffort = 0)

        every { todoRepository.findByIdOrNull(todoId) } returns alteEntity
        every { todoRepository.save(any()) } returns aktualisierteEntity

        val erwartetesErgebnis = GamificationResult(
            levelUp = false,
            currentLevel = 2,
            levelTitle = "Code-Anfänger",
            currentXp = 150,
            currentLevelXpStart = 100,
            nextLevelXpRequired = 200
        )

        // FIX: Wir sagen any(), damit es egal ist, ob der Service 0 oder 5 mitschickt!
        every {
            gamificationService.processTodoStatusChange(
                userId = userId,
                effort = 5,
                usedEffort = any(), // 🚀 Flexibel für jeden Integer-Wert!
                isDone = true
            )
        } returns erwartetesErgebnis

        val ergebnis = todoService.toggleStatusWithGamification(todoId, isDone = true, userId = userId)

        assertFalse(ergebnis.levelUp)
        assertEquals(2, ergebnis.currentLevel)
        assertEquals("Code-Anfänger", ergebnis.levelTitle)
        assertEquals(150, ergebnis.currentXp)

        verify(exactly = 1) { todoRepository.save(any()) }
        // 🎯 FIX: Auch hier beim Überprüfen any() eintragen
        verify(exactly = 1) { gamificationService.processTodoStatusChange(userId, 5, any(), true) }
    }

    // --- TEST 2: ABSICHERUNG GELÖSCHTER USER ---

    @Test
    fun `getTodosForUser sollte UserDeletedException werfen wenn der User nicht existiert`() {
        every { userRepository.existsById("geister-user") } returns false

        assertThrows<UserDeletedException> {
            todoService.getTodos("geister-user")
        }

        verify(exactly = 0) { todoRepository.findByUserId(any()) }
    }
}