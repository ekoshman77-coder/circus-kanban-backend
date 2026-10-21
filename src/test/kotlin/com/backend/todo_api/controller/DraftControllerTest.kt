package com.backend.todo_api.controller

import com.backend.todo_api.dto.DelegateDraftDto
import com.backend.todo_api.dto.DelegationTargetDto
import com.backend.todo_api.dto.DraftChainDto
import com.backend.todo_api.services.DraftBackendService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.Principal

class DraftControllerTest {

    // 1. Service mit MockK mocken
    private val draftBackendService: DraftBackendService = mockk()

    // 2. Principal für authentifizierten User mocken
    private val mockPrincipal: Principal = mockk {
        every { name } returns "user-123"
    }

    // 3. Controller & Standalone MockMvc aufbauen
    private val draftController = DraftController(draftBackendService = draftBackendService)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(draftController).build()
    private val objectMapper = ObjectMapper()

    @Test
    fun `GET drafts sollte alle dem User zugewiesenen Entwuerfe liefern`() {
        val expectedDrafts = listOf(
            DraftChainDto(
                id = "chain-xyz",
                title = "Department Task",
                target = DelegationTargetDto(type = "USER", id = "user-123"),
                createdAt = 1700000000000L,
                createdByUserId = "user-999",
                lastErrorReason = "Network Timeout",
                lastErrorCode = 408
            )
        )

        every { draftBackendService.getAssignedDraftsForCurrentUser("user-123") } returns expectedDrafts

        mockMvc.perform(
            get("/api/drafts")
                .principal(mockPrincipal)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("chain-xyz"))
            .andExpect(jsonPath("$[0].title").value("Department Task"))
            .andExpect(jsonPath("$[0].target.type").value("USER"))
            .andExpect(jsonPath("$[0].target.id").value("user-123"))

        verify(exactly = 1) { draftBackendService.getAssignedDraftsForCurrentUser("user-123") }
    }

    @Test
    fun `POST delegate sollte Entwurf an den Service uebergeben`() {
        val dto = DelegateDraftDto(
            id = "chain-abc",
            title = "Fehlerhafter Entwurf",
            target = DelegationTargetDto(type = "DEPARTMENT", id = "dept-456"),
            createdAt = 1700000000000L,
            createdByUserId = "user-123",
            lastErrorReason = "HTTP 500 Server Error",
            lastErrorCode = 500
        )

        every { draftBackendService.delegateDraft("chain-abc", any(), "user-123") } just runs

        mockMvc.perform(
            post("/api/drafts/chain-abc/delegate")
                .principal(mockPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { draftBackendService.delegateDraft("chain-abc", any(), "user-123") }
    }

    @Test
    fun `DELETE draft sollte Entwurf ueber Service loeschen`() {
        every { draftBackendService.deleteDraft("chain-to-delete", "user-123") } just runs

        mockMvc.perform(
            delete("/api/drafts/chain-to-delete")
                .principal(mockPrincipal)
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { draftBackendService.deleteDraft("chain-to-delete", "user-123") }
    }
}