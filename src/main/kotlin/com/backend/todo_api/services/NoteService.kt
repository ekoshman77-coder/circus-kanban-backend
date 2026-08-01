package com.backend.todo_api.services

import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.repository.NoteRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.dto.toNewEntity
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.providers.AiGlobalDataProvider
import com.backend.todo_api.validation.validateUserExists
import jakarta.transaction.Transactional
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service

@Service
class NoteService(private val noteRepository: NoteRepository,
                        private val userRepository: UserRepository
) {

    fun createNote(createNoteDto: CreateNoteDto): NoteDto {
        validateUserExists(createNoteDto.userId, userRepository)
        val created = noteRepository.save(createNoteDto.toNewEntity()).toDto()
        return created
    }

    fun getNoteById(id: String): NoteDto {
        val entity = noteRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Zettel mit ID $id nicht gefunden!") }

        return entity.toDto()
    }

    // Holt nur die relevanten aktiven Notizen für den User
    fun getNotesByUserId(userId: String?): List<NoteDto> {
        if (userId == null) return emptyList()
        validateUserExists(userId, userRepository)

        // 1. User holen, um die echte Abteilung zu erfahren
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User mit ID $userId nicht gefunden!") }

        val userDeptId = user.departmentId

        // 2. Sicherheits-Check: Ist der User überhaupt schon freigeschaltet/zugewiesen?
        if (userDeptId.isNullOrBlank()) {
            // Wenn er eingeloggt ist, aber keine Abteilung hat, ist er noch nicht freigeschaltet!
            return emptyList()
        }

        // 3. Direkt das Repository feuern (holt Global + seine Abteilung, z.B. "DIRECTION")
        return noteRepository.findActiveGlobalAndDepartmentNotes(userDeptId).map { it.toDto() }
    }

    /**
     * Holt ALLE aktiven Ideen des gesamten Teams (für die gemeinsame Kreativ-Basis)
     */
    fun getAllActiveNotes(): List<NoteDto> {
        return noteRepository.findByIsArchivedFalse().map { it.toDto() }
    }

    // 3. Zettel editieren (Sicherheitshalber prüfen wir hier auch die userId!)
    @Transactional
    fun updateNote(id: String, dto: NoteDto): NoteDto {
        validateUserExists(dto.userId, userRepository)

        val existingEntity = noteRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Zettel mit ID $id nicht gefunden!") }

        // 🎯 SCHUTZ: Archivierte Ideen dürfen nicht mehr bearbeitet werden
        if (existingEntity.isArchived) {
            throw IllegalStateException("Diese Idee ist archiviert und kann nicht mehr geändert werden.")
        }

        // Berechtigungsprüfung
        if (existingEntity.userId != dto.userId) {
            throw IllegalAccessException("Keine Berechtigung für diesen Zettel!")
        }

        existingEntity.updateFromDto(dto)
        return noteRepository.save(existingEntity).toDto()
    }

    @Transactional
    fun deleteNote(id: String, userId: String) { // 🎯 userId kommt jetzt mit!
        val entity = noteRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Zettel nicht gefunden!") }

        // KONSEQUENZ: Wir schützen das Archiv genau wie das Update!
        if (entity.userId != userId) {
            throw IllegalAccessException("Nur der Eigentümer darf diese Idee archivieren!")
        }

        entity.isArchived = true
        noteRepository.save(entity)
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