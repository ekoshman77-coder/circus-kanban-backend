package com.backend.todo_api.controller

import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.SyncResultDto
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.dto.TodoUpdateResponse
import com.backend.todo_api.services.TodoService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TodoControllerTest {

    private val todoService: TodoService = mockk()
    private val todoController = TodoController(todoService)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(todoController).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `POST bulk sollte Offline-Todos synchronisieren und SyncResultDto ausgeben`() {
        val userId = "user-offline-123"

        // 🎯 Sicherer Aufruf dank deiner neuen Standardwerte im TodoDto
        val offlineList = listOf(TodoDto(id = "offline-1", task = "Im Flugzeug gecodet", userId = userId))

        val mockSyncResult = SyncResultDto(
            liste = listOf(TodoDto(id = "offline-1", task = "Im Flugzeug gecodet", done = false, userId = userId)),
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

        every { todoService.syncBulkTodos(userId, any()) } returns mockSyncResult

        mockMvc.perform(
            post("/api/todos/bulk")
                .param("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(offlineList))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.liste[0].id").value("offline-1"))
    }
}