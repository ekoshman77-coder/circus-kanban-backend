package com.backend.todo_api.services

import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.repository.NoteRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.dto.toNewEntity
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.NoteNotFoundException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.providers.AiGlobalDataProvider
import jakarta.transaction.Transactional
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.model.NoteSecurityResource
import com.backend.todo_api.model.toEntity
import com.backend.todo_api.model.toSecurityResource

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val userContextResolver: UserContextResolver,
    private val permissionService: PermissionService,
    private val scopeRepository: ScopeRepository
    )
{

    fun createNote(userId: String, noteDto: CreateNoteDto): NoteDto {
        val noteEntity = noteDto.toNewEntity( scopeRepository )
        // Da jeder aktive User eine Abteilung HABEN MUSS, prüfen wir direkt gegen user.departmentId!
        checkPermission(userId, noteEntity, ActionType.CREATE,"Zugriff verweigert: Du darfst keine Notizen erstellen.")

        val created = noteRepository.save(noteEntity).toDto()
        return created
    }

    fun getNoteById(userId: String, noteId: String): NoteDto {
        val note = noteRepository.findByIdAndIsArchivedFalse(noteId)
            ?:throw IllegalArgumentException("Notiz $noteId nicht gefunden.")

        checkPermission(userId, note, ActionType.READ, "Zugriff verweigert: Du hast keine Berechtigung, diese Notiz zu lesen.")

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

        checkPermission(userId, note, ActionType.UPDATE,"Zugriff verweigert: Du hast keine Berechtigung, diese Notiz zu bearbeiten.")

        // 🟢 Alle relevanten Felder aktualisieren
        note.title = updatedDto.title
        note.content = updatedDto.content
        note.colorType = updatedDto.colorType
        note.tag = updatedDto.tag
        note.isInCalculation = updatedDto.isInCalculation

        return noteRepository.save(note).toDto()
    }

    @Transactional
    fun deleteNote(userId: String, noteId: String) {
        val note = noteRepository.findById(noteId).orElseThrow {
            IllegalArgumentException("Notiz $noteId nicht gefunden.")
        }

        checkPermission(userId, note, ActionType.DELETE, "Zugriff verweigert: Du hast keine Berechtigung, diese Notiz zu löschen.")

        note.isArchived = true
        noteRepository.save(note)
    }

    @Transactional
    fun changeNoteScope(userId: String, noteId: String, action: ActionType): NoteDto {
        val note = noteRepository.findByIdAndIsArchivedFalse(noteId)
                    ?: throw(NoteNotFoundException("note existiert nicht oder war gelöscht"))

        checkPermission(userId, note, action, "Du darfst scope der Note nicht ändern")
        note.scope = if (action == ActionType.PROMOTE) {
            ScopeType.COMPANY.toEntity(scopeRepository)
        } else {
            ScopeType.DEPARTMENT.toEntity(scopeRepository)
        }
        val saved = noteRepository.save(note)
        return saved.toDto()
    }

    @Transactional
    fun changeStatus(userId: String, noteId: String, newStatus: Boolean): NoteDto {
        val note = noteRepository.findByIdAndIsArchivedFalse(noteId)
            ?: throw(NoteNotFoundException("note existiert nicht oder war gelöscht"))

        checkPermission(userId, note, ActionType.EXECUTE, "Du hast keine Berechtigung, den Status dieser Note zu ändern")

        note.isInCalculation = newStatus
        val saved = noteRepository.save(note)

        return saved.toDto()
    }

    private fun checkPermission(userId: String, note: NoteEntity, action: ActionType, exceptionMessage: String) {

        val userContexts = userContextResolver.resolveContexts(userId)

        val canAct = permissionService.hasPermission(
            userContexts,
            action = action,
            resource = note.toSecurityResource()
        )

        if (!canAct) {
            throw ActionForbiddenException(exceptionMessage)
        }
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