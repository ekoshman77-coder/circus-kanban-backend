package com.backend.todo_api.services

import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.NoteRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.ScopeType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class NoteServiceTest {

    private val noteRepository: NoteRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val userContextResolver: UserContextResolver = mockk()
    private val permissionService: PermissionService = mockk()

    private lateinit var noteService: NoteService

    private val userId = "user-profi-123"
    private val noteId = "note-999"

    @BeforeEach
    fun setUp() {
        noteService = NoteService(noteRepository, userRepository, userContextResolver, permissionService)
    }

    // --- 1. CREATE NOTE ---

    @Test
    fun `createNote sollte Notiz erfolgreich erstellen wenn Berechtigung erteilt ist`() {
        val user = UserEntity(id = userId)
        val inputDto = CreateNoteDto(
            title = "Unsere Test-Idee",
            content = "Lernen, wie man mockt",
            colorType = "blue",
            userId = userId,
            isInCalculation = true
        )

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userContextResolver.resolveContexts(user) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns true
        every { noteRepository.save(any<NoteEntity>()) } answers { firstArg() }

        val resultDto = noteService.createNote(userId, inputDto)

        assertEquals("Unsere Test-Idee", resultDto.title)
        assertTrue(resultDto.isInCalculation)

        verify(exactly = 1) { userRepository.findById(userId) }
        verify(exactly = 1) { noteRepository.save(any()) }
    }

    @Test
    fun `createNote sollte SecurityException werfen wenn Rechte fehlen`() {
        val user = UserEntity(id = userId)
        val inputDto = CreateNoteDto(title = "Sicherer Zettel", userId = userId)

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userContextResolver.resolveContexts(user) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns false

        assertThrows<SecurityException> {
            noteService.createNote(userId, inputDto)
        }
    }

    // --- 2. GET NOTES BY USER ID (FILTER-BRILLE) ---

    @Test
    fun `getNotesByUserId sollte Abteilungsnotizen filtern wenn MaxContext DEPARTMENT ist`() {
        val deptScope = ScopeEntity(name = ScopeType.DEPARTMENT)
        val context = UserContext(scope = deptScope, scopeInstanceId = "dept-42", role = mockk())
        val note = NoteEntity(id = noteId, title = "Team Notiz", departmentId = "dept-42")

        every { userContextResolver.resolveContexts(userId) } returns listOf(context)
        every {
            permissionService.getMaxAllowedUserContext(
                userContexts = any(),
                action = ActionType.READ,
                resource = ResourceType.NOTE
            )
        } returns context

        every { noteRepository.findByDepartmentIdAndIsArchivedFalse("dept-42") } returns listOf(note)

        val notes = noteService.getNotesByUserId(userId)

        assertEquals(1, notes.size)
        assertEquals("Team Notiz", notes[0].title)
        verify { noteRepository.findByDepartmentIdAndIsArchivedFalse("dept-42") }
    }

    // --- 3. UPDATE & DELETE ---

    @Test
    fun `updateNote sollte Aktualisierung speichern wenn Berechtigung vorliegt`() {
        val existingNote = NoteEntity(id = noteId, title = "Alt", content = "Alt")
        val updateDto = NoteDto(id = noteId, title = "Neu", content = "Neu", userId = userId)

        every { noteRepository.findById(noteId) } returns Optional.of(existingNote)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true
        every { noteRepository.save(any()) } answers { firstArg() }

        val updated = noteService.updateNote(userId, noteId, updateDto)

        assertEquals("Neu", updated.title)
        assertEquals("Neu", updated.content)
        verify { noteRepository.save(existingNote) }
    }

    @Test
    fun `deleteNote sollte isArchived auf true setzen`() {
        val existingNote = NoteEntity(id = noteId, isArchived = false)

        every { noteRepository.findById(noteId) } returns Optional.of(existingNote)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.DELETE, any()) } returns true
        every { noteRepository.save(any()) } answers { firstArg() }

        noteService.deleteNote(userId, noteId)

        assertTrue(existingNote.isArchived)
        verify { noteRepository.save(existingNote) }
    }
}