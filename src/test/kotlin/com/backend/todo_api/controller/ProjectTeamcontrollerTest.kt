package com.backend.todo_api.controller

import com.backend.todo_api.dto.AssignUserRequestDTO
import com.backend.todo_api.exceptions.GlobalExceptionHandler
import com.backend.todo_api.exceptions.TeamValidationException
import com.backend.todo_api.services.ProjectTeamService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean // 👈 Importieren!

class ProjectTeamControllerTest {

    private val projectTeamService: ProjectTeamService = mockk()
    private val projectTeamController = ProjectTeamController(projectTeamService)

    // 🛡️ HIER PASSIERT DIE MAGIE: Wir bauen den Validator für das standaloneSetup
    private val springValidator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(projectTeamController)
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(springValidator) // 👈 HIER DEN VALIDATOR ÜBERGEBEN!
        .build()

    private val objectMapper = ObjectMapper()

    @Test
    fun `POST -api-teams - sollte 400 Bad Request liefern wenn userId im Body leer ist`() {
        val invalidRequestBody = AssignUserRequestDTO(userId = "   ")

        mockMvc.perform(
            post("/api/teams")
                .param("projectId", "proj-1")
                .param("role", "DEVELOPER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequestBody))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("INVALID_DATA"))
    }

    @Test
    fun `POST -api-teams - sollte Exception vom Service abfangen und als TEAM_VALIDATION_ERROR senden`() {
        val requestBody = AssignUserRequestDTO(userId = "user-1")

        every {
            projectTeamService.assignUserToProject("proj-1", "user-1", "FALSEROLLE")
        } throws TeamValidationException("Ungültige Rolle")

        mockMvc.perform(
            post("/api/teams")
                .param("projectId", "proj-1")
                .param("role", "FALSEROLLE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("TEAM_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Ungültige Rolle"))
    }
}