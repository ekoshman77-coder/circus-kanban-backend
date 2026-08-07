package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateTodoDto
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.TodoBulkDto
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.model.ActionType
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

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

    @MockK
    lateinit var streakService: StreakService

    @MockK
    lateinit var permissionService: PermissionService

    @MockK
    lateinit var userContextResolver: UserContextResolver

    @InjectMockKs
    lateinit var todoService: TodoService

    private val userId = "user-123"
    private val todoId = "todo-999"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        // Standard-Mocking für UserContextResolver & PermissionService
        every { userContextResolver.resolveContexts(any<String>()) } returns emptyList()
        every { userContextResolver.resolveContexts(any<String>()) } returns emptyList()
    }

    // --- 1. ERSTELLEN & BERECHTIGUNG ---

    @Test
    fun `createTodo sollte Todo erfolgreich anlegen wenn Berechtigung vorliegt`() {
        val createDto = CreateTodoDto(task = "Code Review", userId = userId, effort = 3)

        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns true
        every { todoRepository.save(any()) } answers { firstArg() }

        val result = todoService.createTodo(userId, createDto)

        assertEquals("Code Review", result.task)
        verify { todoRepository.save(any()) }
    }

    @Test
    fun `createTodo sollte ActionForbiddenException werfen wenn Rechte fehlen`() {
        val createDto = CreateTodoDto(task = "Code Review", userId = userId)

        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns false

        assertThrows<ActionForbiddenException> {
            todoService.createTodo(userId, createDto)
        }
    }

    // --- 2. UPDATE & KI-FELDER SCHÜTZEN ---

    @Test
    fun `updateTodo sollte KI-Felder beibehalten und effortChangesCount erhoehen`() {
        val frontendDto = TodoDto(
            id = todoId,
            task = "Refactoring",
            userId = userId,
            effort = 5 // Aufwand wurde geändert (von 2 auf 5)
        )

        val dbEntity = TodoEntity(
            id = todoId,
            task = "Altes Refactoring",
            userId = userId,
            effort = 2,
            focusType = "HIGH_FOCUS",
            cooldownTurns = 3,
            effortChangesCount = 1
        )

        every { todoRepository.findByIdAndIsArchivedFalse(todoId) } returns dbEntity
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true
        every { todoRepository.save(any()) } answers { firstArg() }
        every { gamificationService.determineXpReceiverUserId(any(), any()) } returns userId
        every { userRepository.findById(userId) } returns Optional.of(mockk(relaxed = true))
        every { streakService.getCurrentStreakInfo(any()) } returns mockk(relaxed = true)

        val response = todoService.updateTodo(userId, frontendDto)

        val saved = response.todo
        assertEquals("Refactoring", saved.task)
        assertEquals(5, saved.effort)
        assertEquals(2, saved.effortChangesCount) // Zähler von 1 auf 2 hochgezählt!
        verify { todoRepository.save(any()) }
    }

    // --- 3. BULK SYNC ---

    @Test
    fun `syncBulkTodos sollte Aktionen für Bulk-Dtos verarbeiten`() {
        val bulkDto = TodoBulkDto(
            id = todoId,
            task = "Sync Task",
            syncAction = "CREATED",
            effort = 3
        )

        val freshUser = mockk<com.backend.todo_api.data.entity.UserEntity>(relaxed = true)

        every { userRepository.findById(userId) } returns Optional.of(freshUser)
        every { userRepository.existsById(userId) } returns true
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns true
        every { todoRepository.save(any()) } answers { firstArg() }
        every { todoRepository.findAll(any<org.springframework.data.jpa.domain.Specification<TodoEntity>>()) } returns emptyList()
        every { gamificationService.getGamificationState(userId) } returns mockk(relaxed = true)
        every { streakService.getCurrentStreakInfo(any()) } returns mockk(relaxed = true)
        every { todoRepository.flush() } just Runs

        val result = todoService.syncBulkTodos(userId, listOf(bulkDto))

        assertNotNull(result)
        verify { todoRepository.save(any()) }
    }

    // --- 4. DELETED USER SAFETY ---

    @Test
    fun `getRelevantTodos sollte UserDeletedException werfen wenn der User nicht existiert`() {
        val geisterUserId = "geister-user"

        // 🎯 mocken von existsById anstelle von findById
        every { userRepository.existsById(geisterUserId) } returns false

        assertThrows<UserDeletedException> {
            todoService.getRelevantTodos(geisterUserId)
        }

        verify(exactly = 1) { userRepository.existsById(geisterUserId) }
    }
}