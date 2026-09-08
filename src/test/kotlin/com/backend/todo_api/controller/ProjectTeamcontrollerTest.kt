package com.backend.todo_api.controller

import com.backend.todo_api.dto.AssignUserRequestDTO
import com.backend.todo_api.dto.CoffeeAccountDto
import com.backend.todo_api.dto.ProjectMemberDto
import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.exceptions.GlobalExceptionHandler
import com.backend.todo_api.exceptions.TeamValidationException
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.services.ProjectTeamService
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.security.Principal

class ProjectTeamControllerTest {

    private val projectTeamService: ProjectTeamService = mockk()
    private val projectTeamController = ProjectTeamController(projectTeamService)

    private val springValidator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(projectTeamController)
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(springValidator)
        .build()

    private val objectMapper = ObjectMapper()

    private val mockPrincipal: Principal = mockk()
    private val mockUserId = "user-admin-123"

    @BeforeEach
    fun setUp() {
        every { mockPrincipal.name } returns mockUserId
    }

    // --- 1. GET /api/teams ---

    @Test
    fun `GET - api-teams - sollte Mitglieder des Projekts zurückgeben wenn projectId vorhanden ist`() {
        val userResponse = UserResponseDto(id = "mem-1", username = "johndoe", firstName = "John", lastName = "Doe")
        val member = ProjectMemberDto(user = userResponse, projectRole = "DEVELOPER")

        every { projectTeamService.getMembersForProject("proj-1", mockUserId) } returns listOf(member)

        mockMvc.perform(
            get("/api/teams")
                .param("projectId", "proj-1")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].user.id").value("mem-1"))
            .andExpect(jsonPath("$[0].projectRole").value("DEVELOPER"))

        verify(exactly = 1) { projectTeamService.getMembersForProject("proj-1", mockUserId) }
    }

    @Test
    fun `GET - api-teams - sollte globalen Abteilungs-Pool zurückgeben wenn keine projectId uebergeben wird`() {
        val userResponse = UserResponseDto(id = "mem-2", username = "janedoe", firstName = "Jane", lastName = "Doe")
        val member = ProjectMemberDto(user = userResponse, projectRole = "MEMBER")

        every { projectTeamService.getAllGlobalUsersWithProjects(mockUserId) } returns listOf(member)

        mockMvc.perform(
            get("/api/teams")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].user.id").value("mem-2"))

        verify(exactly = 1) { projectTeamService.getAllGlobalUsersWithProjects(mockUserId) }
    }

    // --- 2. POST /api/teams ---

    @Test
    fun `POST - api-teams - sollte 400 Bad Request liefern wenn userId im Body blank ist`() {
        val invalidRequestBody = AssignUserRequestDTO(
            projectId = "proj-1",
            userId = "   ",
            projectRole = RoleType.DEVELOPER
        )

        mockMvc.perform(
            post("/api/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequestBody))
                .principal(mockPrincipal)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST - api-teams - sollte Exception vom Service abfangen und als TEAM_VALIDATION_ERROR senden`() {
        val requestBody = AssignUserRequestDTO(
            projectId = "proj-1",
            userId = "user-1",
            projectRole = RoleType.DEVELOPER
        )

        every {
            projectTeamService.assignUserToProject(mockUserId, "proj-1", "user-1", RoleType.DEVELOPER)
        } throws TeamValidationException("Ungültige Rolle")

        mockMvc.perform(
            post("/api/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .principal(mockPrincipal)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("TEAM_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Ungültige Rolle"))

        verify(exactly = 1) {
            projectTeamService.assignUserToProject(mockUserId, "proj-1", "user-1", RoleType.DEVELOPER)
        }
    }

    // --- 3. DELETE /api/teams/{memberId} ---

    @Test
    fun `DELETE - api-teams-{memberId} - sollte 204 No Content liefern`() {
        every {
            projectTeamService.removeUserFromProject("proj-1", "member-1", mockUserId)
        } just runs

        mockMvc.perform(
            delete("/api/teams/{memberId}", "member-1")
                .param("projectId", "proj-1")
                .principal(mockPrincipal)
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) {
            projectTeamService.removeUserFromProject("proj-1", "member-1", mockUserId)
        }
    }

    // --- 4. PUT /api/teams/{id}/coffee-account ---

    @Test
    fun `PUT - api-teams-{id}-coffee-account - sollte Kaffeekonto aktualisieren`() {
        val updatedUser = UserResponseDto(id = "user-1", username = "user1",
            coffeeAccount = CoffeeAccountDto( balance = 10.5f, role = "Barista", emoji = "☕"))

        every {
            projectTeamService.updateCoffeeAccount(mockUserId, "user-1", 10.5f, "Barista", "☕")
        } returns updatedUser

        mockMvc.perform(
            put("/api/teams/{id}/coffee-account", "user-1")
                .param("balance", "10.5")
                .param("role", "Barista")
                .param("emoji", "☕")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("user-1"))

        verify(exactly = 1) {
            projectTeamService.updateCoffeeAccount(mockUserId, "user-1", 10.5f, "Barista", "☕")
        }
    }

    // --- 5. GET /api/teams/all-users ---

    @Test
    fun `GET - api-teams-all-users - sollte alle Admin-Benutzer zurueckgeben`() {
        val userResponse = UserResponseDto(id = "user-1", username = "adminuser", firstName = "Admin", lastName = "User")
        val member = ProjectMemberDto(user = userResponse, projectRole = "ADMIN")

        every { projectTeamService.getAllUsersForAdminBoard(mockUserId) } returns listOf(member)

        mockMvc.perform(
            get("/api/teams/all-users")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].user.id").value("user-1"))

        verify(exactly = 1) { projectTeamService.getAllUsersForAdminBoard(mockUserId) }
    }
}