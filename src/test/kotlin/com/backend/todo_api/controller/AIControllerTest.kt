package com.backend.todo_api.controller

import com.backend.todo_api.dto.PredictionRequest
import com.backend.todo_api.model.AiContextType // 👑 Import für das Enum!
import com.backend.todo_api.services.SmartPlannerService // 👑 Import für den neuen Service!
import com.backend.todo_api.services.TrainManager
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AIControllerTest {

    // 1. Wir mocken beide Services, die der Controller braucht!
    private val trainManager: TrainManager = mockk()
    private val smartPlannerService: SmartPlannerService = mockk() // 🚀 Neu dazu-gemockt!

    // 2. Wir übergeben beide Mocks brav an den Controller-Konstruktor
    private val aiController = AIController(trainManager = trainManager, smartPlannerService = smartPlannerService)

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(aiController).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `POST predict sollte einen KATEGORIE Vorschlag basierend auf globalen Daten liefern`() {
        val request = PredictionRequest(
            text = "Flyway-Skript schreiben"
        )

        // ✨ REPARIERT: Wir nutzen jetzt das echte Enum AiContextType.TODO_CATEGORY, genau wie der Controller!
        every { trainManager.trainAndPredictGlobal("Flyway-Skript schreiben", AiContextType.TODO_CATEGORY) } returns "Datenbank"

        mockMvc.perform(
            post("/api/ai/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.suggestedCategory").value("Datenbank"))
    }

    @Test
    fun `POST predict-effort sollte den AUFWAND schaetzen und den Benchmark-Text erzeugen`() {
        val request = PredictionRequest(
            text = "Pipeline in Jenkins refaktoren"
        )

        every { trainManager.predictGlobalEffort("Pipeline in Jenkins refaktoren") } returns 4

        mockMvc.perform(
            post("/api/ai/predict-effort")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.suggestedEffort").value(4))
    }

    @Test
    fun `GET categories sollte die GLOBALEN Kategorien ohne User-Einschraenkung laden`() {
        val mockGlobalCategories = listOf("Arbeit", "Datenbank", "Infrastruktur")

        every { trainManager.getAllGlobalCategories(AiContextType.TODO_CATEGORY) } returns mockGlobalCategories

        mockMvc.perform(
            get("/api/ai/categories")
                .param("contextType", "todo")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value("Arbeit"))
            .andExpect(jsonPath("$[1]").value("Datenbank"))
            .andExpect(jsonPath("$[2]").value("Infrastruktur"))
    }
}