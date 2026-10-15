package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.exceptions.GlobalExceptionHandler
import com.backend.todo_api.exceptions.UserAlreadyExistsException
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class UserControllerTest {

    private val userService: UserService = mockk()
    private val userController = UserController(userService)

    // GlobalExceptionHandler + Validator aktivieren
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(userController)
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(LocalValidatorFactoryBean().apply { afterPropertiesSet() })
        .build()

    private val objectMapper = ObjectMapper()

    @Test
    fun `registerUser sollte bei leerem Username ein HTTP 400 Bad Request werfen`() {
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
            .andExpect(jsonPath("$.errorCode").value("INVALID_DATA"))
            .andExpect(jsonPath("$.message").value("Der Username darf nicht leer sein!"))
    }

    @Test
    fun `registerUser sollte bei bereits existierendem User ein HTTP 409 Conflict werfen`() {
        val dto = CreateUserDto(
            username = "admin",
            firstName = "Admin",
            lastName = "System",
            password = "securePassword123"
        )

        every { userService.register(any()) } throws UserAlreadyExistsException("Username bereits vergeben!")

        mockMvc.perform(
            post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("Username bereits vergeben!"))
    }
}