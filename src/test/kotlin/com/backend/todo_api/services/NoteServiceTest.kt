package com.backend.todo_api.services

import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.repository.NoteRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.UserContext
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
    private val scopeRepository: ScopeRepository = mockk()
    private val userContextResolver: UserContextResolver = mockk()
    private val permissionService: PermissionService = mockk()

    private lateinit var noteService: NoteService

    private val userId = "user-profi-123"
    private val noteId = "note-999"

    @BeforeEach
    fun setUp() {
        // Richtige Reihenfolge und Typen passend zu NoteService
        noteService = NoteService(
            noteRepository,
            userContextResolver,
            permissionService,
            scopeRepository
        )
    }

    // --- 1. CREATE NOTE ---

    @Test
    fun `createNote sollte Notiz erfolgreich erstellen wenn Berechtigung erteilt ist`() {
        val inputDto = CreateNoteDto(
            title = "Unsere Test-Idee",
            content = "Lernen, wie man mockt",
            colorType = "blue",
            userId = userId,
            isInCalculation = true,
            scope = ScopeType.DEPARTMENT
        )

        val scopeEntity = ScopeEntity(name = ScopeType.DEPARTMENT)

        every { scopeRepository.findByName(ScopeType.DEPARTMENT) } returns scopeEntity
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns true
        every { noteRepository.save(any()) } answers { firstArg() }
        every { noteRepository.existsByTitleIgnoreCaseAndIsArchivedFalse(any()) } answers { false }

        val resultDto = noteService.createNote(userId, inputDto)

        assertEquals("Unsere Test-Idee", resultDto.title)
        assertTrue(resultDto.isInCalculation)

        verify(exactly = 1) { noteRepository.save(any()) }
    }

    @Test
    fun `createNote sollte ActionForbiddenException werfen wenn Rechte fehlen`() {
        val inputDto = CreateNoteDto(
            title = "Sicherer Zettel",
            userId = userId,
            scope = ScopeType.DEPARTMENT
        )

        val scopeEntity = ScopeEntity(name = ScopeType.DEPARTMENT)

        every { scopeRepository.findByName(ScopeType.DEPARTMENT) } returns scopeEntity
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns false
        every { noteRepository.existsByTitleIgnoreCaseAndIsArchivedFalse(any()) } answers { false }

        assertThrows< ActionForbiddenException> {
            noteService.createNote(userId, inputDto)
        }
    }

    // --- 2. GET NOTES BY USER ID ---

    @Test
    fun `getNotesByUserId - liefert Notizen aus RESOURCE, DEPARTMENT und PROJECT Scopes ohne Duplikate`() {
        // GIVEN
        val userId = "user-123"
        val deptId = "dept-1"
        val projId = "proj-1"

        val scopeResource = ScopeEntity(name = ScopeType.RESOURCE)
        val scopeDept = ScopeEntity(name = ScopeType.DEPARTMENT)
        val scopeProj = ScopeEntity(name = ScopeType.PROJECT)

        val roleOwner = RoleEntity(name = RoleType.OWNER)
        val roleMember = RoleEntity(name = RoleType.MEMBER)
        val roleDeveloper = RoleEntity(name = RoleType.DEVELOPER)

        val contextResource = UserContext(scope = scopeResource, scopeInstanceId = userId, role = roleOwner)
        val contextDept = UserContext(scope = scopeDept, scopeInstanceId = deptId, role = roleMember)
        val contextProj = UserContext(scope = scopeProj, scopeInstanceId = projId, role = roleDeveloper)

        every { userContextResolver.resolveContexts(userId) } returns listOf(contextResource, contextDept, contextProj)
        every { permissionService.hasPermission(any(), ActionType.READ, any()) } returns true

        val ownNote = NoteEntity(id = "n1", userId = userId, title = "Eigene Notiz", scope = scopeResource)
        val deptNote = NoteEntity(id = "n2", userId = "other-user", departmentId = deptId, title = "Abteilungs-Notiz", scope = scopeDept)
        val projNote = NoteEntity(id = "n3", userId = "other-user-2", title = "Projekt-Idee", scope = scopeProj)

        every { noteRepository.findByUserIdAndIsArchivedFalse(userId) } returns listOf(ownNote)
        every { noteRepository.findByDepartmentIdAndIsArchivedFalse(deptId) } returns listOf(deptNote)
        every { noteRepository.findNotesByProjectId(projId) } returns listOf(projNote)

        // WHEN
        val result = noteService.getNotesByUserId(userId)

        // THEN
        assertEquals(3, result.size)
        assertTrue(result.any { it.id == "n1" })
        assertTrue(result.any { it.id == "n2" })
        assertTrue(result.any { it.id == "n3" })

        verify(exactly = 1) { noteRepository.findByUserIdAndIsArchivedFalse(userId) }
        verify(exactly = 1) { noteRepository.findByDepartmentIdAndIsArchivedFalse(deptId) }
        verify(exactly = 1) { noteRepository.findNotesByProjectId(projId) }
    }

    @Test
    fun `getNoteById - wirft ActionForbiddenException wenn keine Berechtigung vorhanden`() {
        // GIVEN
        val userId = "user-123"
        val noteId = "note-999"
        val noteEntity = NoteEntity(id = noteId, userId = "owner-456", title = "Geheim")

        every { noteRepository.findByIdAndIsArchivedFalse(noteId) } returns noteEntity
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.READ, any()) } returns false

        // WHEN & THEN
        val exception = assertThrows<ActionForbiddenException> {
            noteService.getNoteById(userId, noteId)
        }

        assertTrue(exception.message!!.contains("Zugriff verweigert"))
        verify(exactly = 1) { permissionService.hasPermission(any(), ActionType.READ, any()) }
    }

    // --- 3. UPDATE & DELETE ---

    @Test
    fun `updateNote sollte Aktualisierung speichern wenn Berechtigung vorliegt`() {
        val existingNote = NoteEntity(id = noteId, title = "Alt", content = "Alt")
        val updateDto = NoteDto(id = noteId, title = "Neu", content = "Neu", userId = userId, scope = ScopeType.DEPARTMENT)

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