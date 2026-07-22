package com.backend.todo_api.controller

import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.services.GamificationService
import com.backend.todo_api.services.TodoService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TodoStatusControllerTest {

    // 1. Beide Services mocken, die der Controller im Konstruktor verlangt
    private val todoService: TodoService = mockk()
    private val gamificationService: GamificationService = mockk()

    // 2. Controller mit den Mocks füttern
    private val todoStatusController = TodoStatusController(todoService, gamificationService)

    // 3. MockMvc für diesen Controller aufbauen
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(todoStatusController).build()

    @Test
    fun `GET am gamification-Endpoint sollte den aktuellen User-Status als JSON zurueckgeben`() {
        val userId = "user-profi"

        val mockGamification = GamificationResult(
            levelUp = false,
            currentLevel = 4,
            levelTitle = "Kotlin-Ninja",
            levelIcon = "🥹",
            currentXp = 320,
            currentLevelXpStart = 300,
            nextLevelXpRequired = 500
        )

        // Drehbuch für den GamificationService schreiben (wird im Controller aufgerufen)
        every { gamificationService.getGamificationState(userId) } returns mockGamification

        // 🎯 Jetzt feuern wir den GET-Request an die ECHTE URL ab!
        mockMvc.perform(
            get("/api/gamification/$userId")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentLevel").value(4))
            .andExpect(jsonPath("$.levelTitle").value("Kotlin-Ninja"))
            .andExpect(jsonPath("$.currentXp").value(320))
    }
}