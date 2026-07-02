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

    // 1. Nur noch die Zettel des EINEN Users holen
    fun getNotesByUserId(userId: String?): List<NoteDto> {
        if (userId != null) {
            validateUserExists(userId, userRepository)
            return noteRepository.findByUserId(userId).map { it.toDto() }
        } else {
            return noteRepository.findAll().map { it.toDto() }
        }
    }

    // 3. Zettel editieren (Sicherheitshalber prüfen wir hier auch die userId!)
    @Transactional
    fun updateNote(id: String, dto: NoteDto): NoteDto {
        validateUserExists(dto.userId, userRepository)
        val existingEntity = noteRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Zettel mit ID $id nicht gefunden!") }

        // Kleine Schutzmauer: Ein User darf nicht die Zettel eines anderen manipulieren
        if (existingEntity.userId != dto.userId) {
            throw IllegalAccessException("Keine Berechtigung für diesen Zettel!")
        }

        existingEntity.updateFromDto(dto)
        return noteRepository.save(existingEntity).toDto()
    }

    @Transactional
    fun deleteNote(id: String) {
        if (!noteRepository.existsById(id)) {
            throw IllegalArgumentException("Zettel mit ID $id existiert nicht!")
        }
        noteRepository.deleteById(id)
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