package com.backend.todo_api.services

import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.repository.NoteRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.dto.toNewEntity
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.providers.AiGlobalDataProvider
import com.backend.todo_api.validation.validateUserExists
import jakarta.transaction.Transactional
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.model.NoteSecurityResource
import com.backend.todo_api.model.toSecurityResource

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val userRepository: UserRepository,
    private val userContextResolver: UserContextResolver,
    private val permissionService: PermissionService
    )
{

    fun createNote(userId: String, noteDto: CreateNoteDto): NoteDto {
        val user = userRepository.findById(userId).orElseThrow {
            UserDeletedException("User $userId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(user)
        val noteEntity = noteDto.toNewEntity()
        // Da jeder aktive User eine Abteilung HABEN MUSS, prüfen wir direkt gegen user.departmentId!
        val canCreate = permissionService.hasPermission(
            userContexts = userContexts,
            resource = noteEntity.toSecurityResource(),
            action = ActionType.CREATE
        )

        if (!canCreate) {
            throw SecurityException("Zugriff verweigert: Du darfst keine Notizen erstellen.")
        }

        val created = noteRepository.save(noteEntity).toDto()
        return created
    }

    fun getNoteById(userId: String, noteId: String): NoteDto {
        val note = noteRepository.findById(noteId).orElseThrow {
            IllegalArgumentException("Notiz $noteId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        // 1 Zeile Clean-Architecture-Prüfung!
        val canRead = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = note.toSecurityResource()
        )

        if (!canRead) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, diese Notiz zu lesen.")
        }

        return note.toDto()
    }

    // Holt nur die relevanten aktiven Notizen für den User
    fun getNotesByUserId(userId: String): List<NoteDto> {
        val userContexts = userContextResolver.resolveContexts(userId)
        val resultNotes = mutableSetOf<NoteEntity>()

        for (context in userContexts) {
            val dummyResource = NoteSecurityResource(
                ownerUserId = userId,
                departmentId = if (context.scope.name == ScopeType.DEPARTMENT) context.scopeInstanceId else null,
                projectId = if (context.scope.name == ScopeType.PROJECT) context.scopeInstanceId else null
            )

            val hasAccess = permissionService.hasPermission(
                userContexts = listOf(context),
                action = ActionType.READ,
                resource = dummyResource
            )

            if (hasAccess) {
                when (context.scope.name) {
                    // 1. COMPANY (Admin): Sieht alle Notizen
                    ScopeType.COMPANY -> {
                        resultNotes.addAll(noteRepository.findByIsArchivedFalse())
                    }

                    // 2. DEPARTMENT: Notizen der eigenen Abteilung
                    ScopeType.DEPARTMENT -> {
                        context.scopeInstanceId?.let { deptId ->
                            resultNotes.addAll(
                                noteRepository.findByDepartmentIdAndIsArchivedFalse(deptId)
                            )
                        }
                    }

                    // 3. PROJECT: Die Zettel/Ideen der Projekte, in denen der User Mitglied ist! 🎯
                    ScopeType.PROJECT -> {
                        context.scopeInstanceId?.let { projectId ->
                            resultNotes.addAll(
                                noteRepository.findNotesByProjectId(projectId)
                            )
                        }
                    }

                    // 4. RESOURCE: Eigene persönliche Notizen des Benutzers
                    ScopeType.RESOURCE -> {
                        resultNotes.addAll(
                            noteRepository.findByUserIdAndIsArchivedFalse(userId)
                        )
                    }
                }
            }
        }

        return resultNotes.map { it.toDto() }
    }

    // 3. Zettel editieren (Sicherheitshalber prüfen wir hier auch die userId!)
    @Transactional
    fun updateNote(userId: String, noteId: String, updatedDto: NoteDto): NoteDto {
        val note = noteRepository.findById(noteId).orElseThrow {
            IllegalArgumentException("Notiz $noteId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        // Prüfen: Darf der User diese Notiz bearbeiten?
        // Wir prüfen gegen den Besitzer der Notiz (note.userId)
        val canUpdate = permissionService.hasPermission(
            userContexts = userContexts,
            resource = note.toSecurityResource(),
            action = ActionType.UPDATE,
        )

        if (!canUpdate) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, diese Notiz zu bearbeiten.")
        }

        // Felder aktualisieren
        note.title = updatedDto.title
        note.content = updatedDto.content

        return noteRepository.save(note).toDto()
    }

    @Transactional
    fun deleteNote(userId: String, noteId: String) {
        val note = noteRepository.findById(noteId).orElseThrow {
            IllegalArgumentException("Notiz $noteId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        val canDelete = permissionService.hasPermission(
            userContexts = userContexts,
            resource = note.toSecurityResource(),
            action = ActionType.DELETE,
        )

        if (!canDelete) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, diese Notiz zu löschen.")
        }

        note.isArchived = true
        noteRepository.save(note)
    }

    fun getGlobalTrainingPairs(): List<Pair<String, String>> {
        val notesFromDb = noteRepository.findAll()
        return notesFromDb.map { Pair(it.title ?: "", it.tag ?: "") }
    }
}

@Configuration
class NoteAiProviderConfig(private val noteService: NoteService) {
    @Bean
    fun noteCategoryProvider() = object : AiGlobalDataProvider<String> {
        override fun getContextType() = AiContextType.NOTE_TAG
        override fun getGlobalTrainingPairs() = noteService.getGlobalTrainingPairs()
    }
}