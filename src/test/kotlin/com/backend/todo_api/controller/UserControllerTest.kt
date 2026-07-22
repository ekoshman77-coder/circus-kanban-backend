package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.services.UserAlreadyExistsException
import com.backend.todo_api.services.UserService
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

class UserControllerTest {

    private val userService: UserService = mockk()
    private val userController = UserController(userService)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(userController).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `registerUser sollte bei leerem Username ein HTTP 400 Bad Request werfen`() {
        // 🎯 FIX: Wir übergeben ein gültiges Passwort (min. 6 Zeichen für OnRegisterOrLogin Gruppe)!
        val dto = CreateUserDto(
            username = "   ",
            firstName = "Max",
            lastName = "Mustermann",
            password = "validPassword123"
        )

        mockMvc.perform(
            post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Name darf nicht leer sein!"))
    }

    @Test
    fun `registerUser sollte bei bereits existierendem User ein HTTP 409 Conflict werfen`() {
        // 🎯 FIX: Wir füttern das DTO mit allen Pflichtfeldern, inklusive Passwort!
        val dto = CreateUserDto(
            username = "admin",
            firstName = "Admin",
            lastName = "System",
            password = "securePassword123"
        )

        // Wir zwingen den Service, deine benutzerdefinierte Exception zu werfen
        every { userService.register(any()) } throws UserAlreadyExistsException("Username bereits vergeben!")

        mockMvc.perform(
            post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("Username bereits vergeben!"))
    }
}