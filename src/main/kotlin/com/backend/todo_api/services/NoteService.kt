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

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val userRepository: UserRepository,
    private val userContextResolver: UserContextResolver,
    private val permissionService: PermissionService
    )
{

    private fun getUserIdFromPrincipal(principal: Principal?): String {
        return principal?.name
            ?: throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Nicht authentifiziert: Bitte logge dich zuerst ein."
            )
    }

//    fun createNote(createNoteDto: CreateNoteDto): NoteDto {
//        validateUserExists(createNoteDto.userId, userRepository)
//        val created = noteRepository.save(createNoteDto.toNewEntity()).toDto()
//        return created
//    }

    fun createNote(userId: String, noteDto: CreateNoteDto): NoteDto {
        val user = userRepository.findById(userId).orElseThrow {
            UserDeletedException("User $userId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(user)

        // Da jeder aktive User eine Abteilung HABEN MUSS, prüfen wir direkt gegen user.departmentId!
        val canCreate = permissionService.hasPermission(
            userContexts = userContexts,
            resourceContext = ResourceContext(
                resource = ResourceType.NOTE,
                instanceId = user.departmentId
            ),
            action = ActionType.CREATE
        )

        if (!canCreate) {
            throw SecurityException("Zugriff verweigert: Du darfst in deiner Abteilung keine Notizen erstellen.")
        }

        val created = noteRepository.save(noteDto.toNewEntity()).toDto()
        return created
    }

//    fun getNoteById(id: String): NoteDto {
//        val entity = noteRepository.findById(id)
//            .orElseThrow { IllegalArgumentException("Zettel mit ID $id nicht gefunden!") }
//
//        return entity.toDto()
//    }

    fun getNoteById(userId: String, noteId: String): NoteDto {
        val note = noteRepository.findById(noteId).orElseThrow {
            IllegalArgumentException("Notiz $noteId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        // Prüfen, ob der User diese Notiz lesen darf
        // Wir übergeben note.userId als instanceId für den RESOURCE-Check
        val canRead = permissionService.hasPermission(
            userContexts = userContexts,
            resourceContext = ResourceContext(
                resource = ResourceType.NOTE,
                instanceId = note.userId
            ),
            action = ActionType.READ
        )

        if (!canRead) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, diese Notiz zu lesen.")
        }

        // Falls der User nur Abteilungs-Rechte hat, stellen wir sicher, dass die Notiz auch zu seiner Abteilung gehört
        val maxContext = permissionService.getMaxAllowedUserContext(
            userContexts = userContexts,
            resource = ResourceType.NOTE,
            action = ActionType.READ
        )

        if (maxContext?.scope?.name == ScopeType.DEPARTMENT && note.departmentId != maxContext.scopeInstanceId) {
            throw SecurityException("Zugriff verweigert: Diese Notiz gehört nicht zu deiner Abteilung.")
        }

        return note.toDto()
    }

    // Holt nur die relevanten aktiven Notizen für den User
    fun getNotesByUserId(userId: String): List<NoteDto> {
        val userContexts = userContextResolver.resolveContexts(userId)

        val maxContext = permissionService.getMaxAllowedUserContext(
            userContexts = userContexts,
            resource = ResourceType.NOTE,
            action = ActionType.READ
        ) ?: return emptyList()

        val notes = when (maxContext.scope.name) {
            ScopeType.COMPANY -> {
                noteRepository.findByIsArchivedFalse()
            }
            ScopeType.DEPARTMENT -> {
                val deptId = maxContext.scopeInstanceId
                    ?: throw IllegalStateException("Abteilungs-ID fehlt im Kontext!")
                noteRepository.findByDepartmentIdAndIsArchivedFalse(deptId)
            }
            ScopeType.RESOURCE -> {
                noteRepository.findByUserIdAndIsArchivedFalse(userId)
            }
            else -> emptyList()
        }

        return notes.map { it.toDto() }
    }

    /**
     * Holt ALLE aktiven Ideen des gesamten Teams (für die gemeinsame Kreativ-Basis)
     */
    fun getAllActiveNotes(): List<NoteDto> {
        return noteRepository.findByIsArchivedFalse().map { it.toDto() }
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
            resourceContext = ResourceContext(ResourceType.NOTE, updatedDto.userId),
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
//    fun deleteNote(id: String, userId: String) { // 🎯 userId kommt jetzt mit!
//        val entity = noteRepository.findById(id)
//            .orElseThrow { IllegalArgumentException("Zettel nicht gefunden!") }
//
//        // KONSEQUENZ: Wir schützen das Archiv genau wie das Update!
//        if (entity.userId != userId) {
//            throw IllegalAccessException("Nur der Eigentümer darf diese Idee archivieren!")
//        }
//
//        entity.isArchived = true
//        noteRepository.save(entity)
//    }

    fun deleteNote(userId: String, noteId: String) {
        val note = noteRepository.findById(noteId).orElseThrow {
            IllegalArgumentException("Notiz $noteId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        val canDelete = permissionService.hasPermission(
            userContexts = userContexts,
            resourceContext = ResourceContext(ResourceType.NOTE, note.userId),
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