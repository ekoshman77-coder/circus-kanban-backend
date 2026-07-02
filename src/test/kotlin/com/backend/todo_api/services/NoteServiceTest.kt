package com.backend.todo_api.services

import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.repository.NoteRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateNoteDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteServiceTest {

    // 1. ARRANGE: Wir erstellen die Attrappen (Mocks) für die Abhängigkeiten
    private val noteRepository: NoteRepository = mockk()
    private val userRepository: UserRepository = mockk()

    // 2. Wir erstellen den echten Service und übergeben ihm die Attrappen
    private val noteService = NoteService(noteRepository, userRepository)

    @Test
    fun `should successfully create a note with isInCalculation true`() {
        // 🎯 GIVEN (Vorbereitung der Daten)
        val inputDto = CreateNoteDto(
            title = "Unsere Test-Idee",
            content = "Lernen, wie man mockt",
            colorType = "blue",
            userId = "user-profi-123",
            isInCalculation = true
        )

        every { userRepository.existsById("user-profi-123") } returns true

        every { noteRepository.save(any<NoteEntity>()) } answers {
            val passedEntity = firstArg<NoteEntity>()
            passedEntity
        }

        // WHEN
        val resultDto = noteService.createNote(inputDto)

        // THEN
        // Überprüfen, ob das zurückgekommene DTO die richtigen Werte hat
        assertEquals("Unsere Test-Idee", resultDto.title)
        assertTrue(resultDto.isInCalculation, "Mhm! 'isInCalculation' hätte true sein müssen!")

        // 🕵️‍♂️ Bonus-Sicherheit: Haben die Mocks auch wirklich ihren Job gemacht?
        verify(exactly = 1) { userRepository.existsById("user-profi-123") }
        verify(exactly = 1) { noteRepository.save(any()) }
    }
}