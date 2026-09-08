package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.services.NoteService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.security.Principal

class NoteControllerTest {

    private val noteService: NoteService = mockk()
    private val principal: Principal = mockk()

    private lateinit var noteController: NoteController

    private val mockUserId = "user-abc"
    private val noteId = "note-999"

    @BeforeEach
    fun setUp() {
        noteController = NoteController(noteService)
        every { principal.name } returns mockUserId
    }

    @Test
    fun `getNotes sollte Notizen des Users zurueckgeben`() {
        val notesList = listOf(
            NoteDto(id = noteId, title = "Titel 1", content = "Inhalt 1", userId = mockUserId)
        )

        every { noteService.getNotesByUserId(mockUserId) } returns notesList

        val response = noteController.getNotes(null, principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.size)
        assertEquals("Titel 1", response.body?.get(0)?.title)
        verify(exactly = 1) { noteService.getNotesByUserId(mockUserId) }
    }

    @Test
    fun `getNoteById sollte Notiz anhand ID zurueckgeben`() {
        val note = NoteDto(id = noteId, title = "Einzelne Notiz", userId = mockUserId)

        every { noteService.getNoteById(mockUserId, noteId) } returns note

        val response = noteController.getNoteById(noteId, principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Einzelne Notiz", response.body?.title)
        verify(exactly = 1) { noteService.getNoteById(mockUserId, noteId) }
    }

    @Test
    fun `createNote sollte neue Notiz erstellen und zurückgeben`() {
        val inputDto = CreateNoteDto(
            title = "Controller lernen",
            content = "MockMvc verstehen",
            userId = mockUserId,
            isInCalculation = true
        )

        val mockResponse = NoteDto(
            id = noteId,
            title = "Controller lernen",
            content = "MockMvc verstehen",
            userId = mockUserId,
            isInCalculation = true
        )

        every { noteService.createNote(mockUserId, inputDto) } returns mockResponse

        val response = noteController.createNote(inputDto, principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(noteId, response.body?.id)
        assertEquals("Controller lernen", response.body?.title)
        assertTrue(response.body?.isInCalculation == true)
        verify(exactly = 1) { noteService.createNote(mockUserId, inputDto) }
    }

    @Test
    fun `updateNote sollte aktualisierte Notiz zurueckgeben`() {
        val updateDto = NoteDto(id = noteId, title = "Neuer Titel", content = "Neuer Inhalt", userId = mockUserId)

        every { noteService.updateNote(mockUserId, noteId, updateDto) } returns updateDto

        val response = noteController.updateNote(noteId, updateDto, principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Neuer Titel", response.body?.title)
        verify(exactly = 1) { noteService.updateNote(mockUserId, noteId, updateDto) }
    }

    @Test
    fun `deleteNote sollte HTTP 204 No Content zurueckgeben`() {
        every { noteService.deleteNote(mockUserId, noteId) } just runs

        val response = noteController.deleteNote(noteId, principal)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNull(response.body)
        verify(exactly = 1) { noteService.deleteNote(mockUserId, noteId) }
    }
}