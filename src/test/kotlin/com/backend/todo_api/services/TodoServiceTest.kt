package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.exceptions.TodoNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.verify
import io.mockk.slot
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.mockk

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
    lateinit var milestoneRepository: MilestoneRepository

    @MockK
    lateinit var projectMemberRepository: ProjectMemberRepository

    @InjectMockKs
    lateinit var todoService: TodoService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    // --- TEST 1: DIE GAMIFICATION-KETTE ---

    @Test
    fun `toggleStatusWithGamification sollte den Status updaten und XP verbuchen`() {
        val userId = "user-123"
        val todoId = "todo-777"

        every { userRepository.existsById(userId) } returns true

        val alteEntity = TodoEntity(id = todoId, task = "Lernen", done = false, userId = userId, effort = 5, usedEffort = 0)
        val aktualisierteEntity = TodoEntity(id = todoId, task = "Lernen", done = true, userId = userId, effort = 5, usedEffort = 0)

        // 🎯 ANPASSUNG: Nutzt jetzt die aktive findByIdAndIsArchivedFalse Methode des Services
        every { todoRepository.findByIdAndIsArchivedFalse(todoId) } returns alteEntity
        every { todoRepository.save(any()) } returns aktualisierteEntity

        val erwartetesErgebnis = GamificationResult(
            levelUp = false,
            currentLevel = 2,
            levelTitle = "Code-Anfänger",
            levelIcon = "🤫",
            currentXp = 150,
            currentLevelXpStart = 100,
            nextLevelXpRequired = 200
        )

        every {
            gamificationService.processTodoStatusChange(
                userId = userId,
                effort = 5,
                usedEffort = any(),
                isDone = true
            )
        } returns erwartetesErgebnis

        val ergebnis = todoService.toggleStatusWithGamification(todoId, isDone = true, userId = userId)

        assertFalse(ergebnis.levelUp)
        assertEquals(2, ergebnis.currentLevel)
        assertEquals("Code-Anfänger", ergebnis.levelTitle)
        assertEquals(150, ergebnis.currentXp)

        verify(exactly = 1) { todoRepository.save(any()) }
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

    // --- 🔮 NEUER TEST 3: KI-FELDER SCHÜTZEN BEI UPDATE ---

    @Test
    fun `updateTodo sollte KI-Felder aus der DB beibehalten und effortChangesCount erhoehen`() {
        val todoId = "todo-999"
        val userId = "user-123"

        // Frontend schickt geänderten Aufwand (von 2 auf 5), kennt aber keine KI-Felder
        val frontendDto = TodoDto(
            id = todoId,
            task = "Refactoring",
            userId = userId,
            effort = 5
        )

        // In der DB liegen wertvolle KI-Daten
        val dbEntity = TodoEntity(
            id = todoId,
            task = "Altes Refactoring",
            userId = userId,
            effort = 2,
            focusType = "HIGH_FOCUS",
            cooldownTurns = 3,
            effortChangesCount = 1
        )

        every { userRepository.existsById(userId) } returns true
        every { todoRepository.findByIdAndIsArchivedFalse(todoId) } returns dbEntity

        val savedEntitySlot = slot<TodoEntity>()
        every { todoRepository.save(capture(savedEntitySlot)) } answers { firstArg() }

        // Act
        todoService.updateTodo(frontendDto)

        // Assert
        val saved = savedEntitySlot.captured
        assertEquals("Refactoring", saved.task)
        assertEquals(5, saved.effort)
        assertEquals("HIGH_FOCUS", saved.focusType)   // 🔮 Unangetastet!
        assertEquals(3, saved.cooldownTurns)           // 🔮 Unangetastet!
        assertEquals(2, saved.effortChangesCount)      // 🧠 Von 1 auf 2 hochgezählt!
    }

    // --- 🔮 NEUER TEST 4: KI-FELDER SICHERN BEI BULK SYNC ---

    @Test
    fun `syncBulkTodos sollte KI-Felder bei existierenden Aufgaben schuetzen`() {
        val userId = "user-123"
        val todoId = "bulk-todo"

        val frontendDto = TodoDto(
            id = todoId,
            task = "Sync Task",
            userId = userId,
            effort = 3,
            done = false
        )

        val dbEntity = TodoEntity(
            id = todoId,
            task = "Alter Sync Task",
            userId = userId,
            effort = 3,
            focusType = "LOW_FOCUS",
            cooldownTurns = 2,
            effortChangesCount = 0
        )

        every { userRepository.existsById(userId) } returns true
        every { todoRepository.findByUserId(userId) } returns listOf(dbEntity)
        every { todoRepository.findByUserIdAndIsArchivedFalse(userId) } returns emptyList()
        every { gamificationService.getGamificationState(userId) } returns mockk(relaxed = true)

        val savedEntitySlot = slot<TodoEntity>()
        every { todoRepository.save(capture(savedEntitySlot)) } answers { firstArg() }

        // Act
        todoService.syncBulkTodos(userId, listOf(frontendDto))

        // Assert
        val saved = savedEntitySlot.captured
        assertEquals("Sync Task", saved.task)
        assertEquals("LOW_FOCUS", saved.focusType) // 🔮 Datenverlust im Sync verhindert!
        assertEquals(2, saved.cooldownTurns)       // 🔮 Datenverlust im Sync verhindert!
        assertEquals(0, saved.effortChangesCount)  // Aufwand blieb gleich, Zähler bleibt unberührt
    }
}