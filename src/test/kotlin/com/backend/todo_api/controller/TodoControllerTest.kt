package com.backend.todo_api.controller

import com.backend.todo_api.dto.*
import com.backend.todo_api.exceptions.GlobalExceptionHandler
import com.backend.todo_api.services.TodoService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.Principal

class TodoControllerTest {

    private val todoService: TodoService = mockk()
    private val todoController = TodoController(todoService)

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(todoController)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    private val objectMapper = ObjectMapper()

    private val mockPrincipal: Principal = mockk()
    private val mockUserId = "user-abc-123"

    @BeforeEach
    fun setUp() {
        every { mockPrincipal.name } returns mockUserId
    }

    // --- 1. GET /api/todos ---

    @Test
    fun `GET - api-todos - sollte alle To-Dos des Users zurueckgeben`() {
        val todo = TodoDto(id = "todo-1", task = "Code aufräumen", userId = mockUserId)
        every { todoService.getTodos(mockUserId) } returns listOf(todo)

        mockMvc.perform(
            get("/api/todos")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("todo-1"))
            .andExpect(jsonPath("$[0].task").value("Code aufräumen"))

        verify(exactly = 1) { todoService.getTodos(mockUserId) }
    }

    // --- 2. POST /api/todos ---

    @Test
    fun `POST - api-todos - sollte neues To-Do erstellen und 201 Created liefern`() {
        val inputDto = CreateTodoDto(task = "Neues Task", userId = mockUserId)
        val createdTodo = TodoDto(id = "todo-new", task = "Neues Task", userId = mockUserId)

        every { todoService.createTodo(mockUserId, any()) } returns createdTodo

        mockMvc.perform(
            post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto))
                .principal(mockPrincipal)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("todo-new"))
            .andExpect(jsonPath("$.task").value("Neues Task"))

        verify(exactly = 1) { todoService.createTodo(mockUserId, any()) }
    }

    // --- 3. PUT /api/todos/{id} ---

    @Test
    fun `PUT - api-todos-{id} - sollte TodoUpdateResponse zurueckgeben`() {
        val updateDto = TodoDto(id = "todo-1", task = "Task aktualisiert", userId = mockUserId)
        val updateResponse = TodoUpdateResponse(
            todo = updateDto,
            gamificationResult = null,
            streakInfo = null
        )

        every { todoService.updateTodo(mockUserId, any()) } returns updateResponse

        mockMvc.perform(
            put("/api/todos/{id}", "todo-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto))
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.todo.id").value("todo-1"))
            .andExpect(jsonPath("$.todo.task").value("Task aktualisiert"))

        verify(exactly = 1) { todoService.updateTodo(mockUserId, any()) }
    }

    // --- 4. POST /api/todos/completed ---

    @Test
    fun `POST - api-todos-completed - sollte erledigte private Aufgaben loeschen`() {
        every { todoService.deleteCompletedPrivateTodos(mockUserId) } just runs

        mockMvc.perform(
            post("/api/todos/completed")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Erledigte private Aufgaben gelöscht."))

        verify(exactly = 1) { todoService.deleteCompletedPrivateTodos(mockUserId) }
    }

    // --- 5. POST /api/todos/all ---

    @Test
    fun `POST - api-todos-all - sollte alle privaten Aufgaben loeschen`() {
        every { todoService.deleteAllPrivateTodos(mockUserId) } just runs

        mockMvc.perform(
            post("/api/todos/all")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Alle privaten Aufgaben gelöscht."))

        verify(exactly = 1) { todoService.deleteAllPrivateTodos(mockUserId) }
    }

    // --- 6. DELETE /api/todos/{id} ---

    @Test
    fun `DELETE - api-todos-{id} - sollte 204 No Content liefern`() {
        every { todoService.deleteTodoById(mockUserId, "todo-1") } just runs

        mockMvc.perform(
            delete("/api/todos/{id}", "todo-1")
                .principal(mockPrincipal)
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { todoService.deleteTodoById(mockUserId, "todo-1") }
    }

    // --- 7. POST /api/todos/bulk ---

    @Test
    fun `POST - api-todos-bulk - sollte Offline-Todos synchronisieren und SyncResultDto ausgeben`() {
        val offlineList = listOf(TodoBulkDto())

        val mockSyncResult = SyncResultDto(
            liste = listOf(TodoDto(id = "offline-1", task = "Im Flugzeug gecodet", done = false, userId = mockUserId)),
            gamificationResult = GamificationResult(
                levelUp = false,
                currentLevel = 2,
                levelTitle = "Code-Anfänger",
                levelIcon = "🥹",
                currentXp = 120,
                currentLevelXpStart = 100,
                nextLevelXpRequired = 200
            )
        )

        every { todoService.syncBulkTodos(mockUserId, any()) } returns mockSyncResult

        mockMvc.perform(
            post("/api/todos/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(offlineList))
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.liste[0].id").value("offline-1"))

        verify(exactly = 1) { todoService.syncBulkTodos(mockUserId, any()) }
    }

    // --- 8. GET /api/todos/milestone/{milestoneId} ---

    @Test
    fun `GET - api-todos-milestone-{id} - sollte Todos nach Milestone filtern`() {
        val todo = TodoDto(id = "todo-ms-1", milestoneId = "ms-100", userId = mockUserId)
        every { todoService.getTodosByMilestone(mockUserId, "ms-100") } returns listOf(todo)

        mockMvc.perform(
            get("/api/todos/milestone/{milestoneId}", "ms-100")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("todo-ms-1"))

        verify(exactly = 1) { todoService.getTodosByMilestone(mockUserId, "ms-100") }
    }

    // --- 9. GET /api/todos/relevant ---

    @Test
    fun `GET - api-todos-relevant - sollte relevante Todos laden`() {
        val todo = TodoDto(id = "todo-rel-1", userId = mockUserId)
        every { todoService.getRelevantTodos(mockUserId, 30) } returns listOf(todo)

        mockMvc.perform(
            get("/api/todos/relevant")
                .param("daysLookback", "30")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))

        verify(exactly = 1) { todoService.getRelevantTodos(mockUserId, 30) }
    }

    // --- 10. GET /api/todos/quick-predictions ---

    @Test
    fun `GET - api-todos-quick-predictions - sollte Vorhersagen zurueckgeben`() {
        val predictions = listOf("Mail schreiben", "Code reviewen")
        every { todoService.getIntelligentQuickTodos(QuickPanelMode.ACTIVE) } returns predictions

        mockMvc.perform(
            get("/api/todos/quick-predictions")
                .param("modus", "ACTIVE")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0]").value("Mail schreiben"))

        verify(exactly = 1) { todoService.getIntelligentQuickTodos(QuickPanelMode.ACTIVE) }
    }
}