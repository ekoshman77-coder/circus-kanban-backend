package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.services.NoteService
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

class NoteControllerTest {

    // 1. ARRANGE: Wir mocken den NoteService, weil der Controller ihn aufruft
    private val noteService: NoteService = mockk()

    // 2. Wir übergeben den gemockten Service an den echten Controller
    private val noteController = NoteController(noteService)

    // 3. Wir bauen uns das MockMvc-Testwerkzeug für diesen Controller zusammen
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(noteController).build()

    // 4. Ein kleiner Helfer, um Kotlin-Objekte in JSON-Strings zu verwandeln
    private val objectMapper = ObjectMapper()

    @Test
    fun `POST api-notes sollte eine neue Notiz erstellen und JSON mit HTTP 200 zurueckgeben`() {
        // GIVEN: Das DTO, welches das Frontend im Body mitschickt
        val inputDto = CreateNoteDto(
            title = "Controller lernen",
            content = "MockMvc verstehen",
            userId = "user-abc",
            isInCalculation = true // Unser wichtiges Feld!
        )

        // GIVEN: Das fiktive Ergebnis, das der NoteService ausspucken würde
        val mockResponse = NoteDto(
            id = "note-999",
            title = "Controller lernen",
            content = "MockMvc verstehen",
            userId = "user-abc",
            colorType = "",
            tag = null,
            isInCalculation = true
        )

        // Das Drehbuch für den Service-Mock schreiben:
        every { noteService.createNote(any()) } returns mockResponse

        // 🎯 WHEN & THEN: Jetzt feuern wir den HTTP-Request ab!
        mockMvc.perform(
            post("/api/notes") // Wir machen ein HTTP POST an diese URL
                .contentType(MediaType.APPLICATION_JSON) // Wir senden JSON
                .content(objectMapper.writeValueAsString(inputDto)) // Das inputDto wird zu "{...}" konvertiert
        )
            // Prüfen, ob der HTTP-Statuscode stimmt
            .andExpect(status().isOk)

            // 🕵️‍♂️ JSON-PATH: Wir schauen direkt in das Antwort-JSON hinein!
            .andExpect(jsonPath("$.id").value("note-999"))
            .andExpect(jsonPath("$.title").value("Controller lernen"))
            // Und hier beweisen wir, dass das Feld auch im JSON ankommt:
            .andExpect(jsonPath("$.isInCalculation").value(true))
    }
}