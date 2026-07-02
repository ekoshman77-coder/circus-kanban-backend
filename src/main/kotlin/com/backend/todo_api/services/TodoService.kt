package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateTodoDto
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.SyncResultDto
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.dto.TodoUpdateResponse
import com.backend.todo_api.dto.toNewEntity  // 👈 Unsere neuen Extensions importieren!
import com.backend.todo_api.dto.toEntity
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.exceptions.TodoNotFoundException
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.providers.AiGlobalDataProvider
import org.springframework.stereotype.Service
import org.springframework.data.repository.findByIdOrNull
import com.backend.todo_api.validation.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Service
class TodoService (
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val gamificationService: GamificationService,
    private val milestoneService: MilestoneService
) {

    fun getTodos(userId: String?): List<TodoDto> {
        if (userId != null) {
            validateUserExists(userId, userRepository)
            // 🔄 Vorher: map { mapToDto(it) } -> Jetzt nutzen wir die schicke Extension!
            return todoRepository.findByUserId(userId).map { it.toDto() }
        } else {
            return todoRepository.findAll().map { it.toDto() }
        }

    }

    fun createTodo(dto: CreateTodoDto): TodoDto {
        validateUserExists(dto.userId, userRepository)

        // 🚀 Vererbung & Extension im Einsatz: Setzt autom. usedEffort & Zeitstempel, falls nötig!
        val entity = dto.toNewEntity()
        val savedEntity = todoRepository.save(entity)

        if (dto.done && dto.milestoneId != null) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = dto.milestoneId,
                effort = dto.effort,
                usedEffort = dto.usedEffort,
                isDone = true
            )
        }

        return savedEntity.toDto()
    }

    fun updateTodo(dto: TodoDto): TodoUpdateResponse {
        validateUserExists(dto.userId, userRepository)

        // 1. Wir holen uns den AKTUELLEN Zustand aus der DB anhand der ID aus dem DTO
        val oldTodo = todoRepository.findByIdOrNull(dto.id)
            ?: throw TodoNotFoundException("To-Do mit ID ${dto.id} nicht gefunden")

        // Wir merken uns die alten Werte für unseren MilestoneService
        val oldMilestoneId = oldTodo.milestoneId
        val oldEffort = oldTodo.effort
        val oldDone = oldTodo.done

        // 2. Jetzt nutzen wir unser sauberes Mapping OHNE Parameter!
        val updatedEntity = dto.toEntity()

        // 3. MILESTONE-BERECHNUNG:

        // Fall A: War es vorher erledigt? Dann alten Aufwand abziehen.
        if (oldDone && !oldMilestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = oldMilestoneId,
                effort = oldEffort,
                usedEffort = oldTodo.usedEffort,
                isDone = false // false zieht ab!
            )
        }

        // Jetzt speichern wir die aktualisierte Entity ab
        val savedEntity = todoRepository.save(updatedEntity)

        // Fall B: Ist es JETZT erledigt? Dann neuen Aufwand auf den Meilenstein rechnen.
        if (savedEntity.done && !savedEntity.milestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = savedEntity.milestoneId,
                effort = savedEntity.effort,
                usedEffort = savedEntity.usedEffort,
                isDone = true // true addiert drauf!
            )
        }

        var gamificationResult: GamificationResult? = null
        // 4. Unsere bewährte Gamification-Logik triggern
        if (oldDone != savedEntity.done) {
            gamificationResult = gamificationService.processTodoStatusChange(
                savedEntity.userId,
                savedEntity.effort,
                usedEffort = savedEntity.usedEffort,
                isDone = savedEntity.done
            )
        }
        return TodoUpdateResponse(
            todo = savedEntity.toDto(),
            gamificationResult = gamificationResult
        )
    }

    fun deleteCompleted(userId: String): Int {
        validateUserExists(userId, userRepository)
        return todoRepository.deleteByDoneTrueAndUserId(userId)
    }

    fun deleteBulk(ids: List<String>) {
        todoRepository.deleteAllById(ids)
    }

    fun deleteAll(userId: String): Int {
        validateUserExists(userId, userRepository)
        return todoRepository.deleteByUserId(userId)
    }

    fun deleteTodoById(id: String) {
        // 1. Wir holen uns das To-Do aus der Datenbank, BEVOR wir es löschen
        val todo = todoRepository.findByIdOrNull(id)

        // 2. MILESTONE-UPDATE: Wenn die Aufgabe erledigt war und zu einem Meilenstein gehörte,
        // müssen wir die Punkte jetzt rückwirkend wieder abziehen!
        if (todo != null && todo.done && !todo.milestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = todo.milestoneId,
                effort = todo.effort,
                usedEffort = todo.usedEffort,
                isDone = false // 'false' signalisiert dem Service, dass abgezogen werden soll!
            )
        }

        // 3. Jetzt löschen wir das To-Do endgültig aus der Datenbank
        todoRepository.deleteById(id)
    }

    fun updateStatus(id: String, done: Boolean, userId: String): TodoDto {
        validateUserExists(userId, userRepository)
        var todo = todoRepository.findByIdOrNull(id)
            ?: throw TodoNotFoundException("Todo not found")
        println("todo. done = " + todo.done)
        println("done = " + done)
//        if (todo.done == done) {
//            throw InvalidStatusRequestException("todo had status ${if (done) "closed" else "open"}")
//        }
        todo.done = done
        val dto = todoRepository.save(todo).toDto()
        return dto
    }

    @org.springframework.transaction.annotation.Transactional
    fun toggleStatusWithGamification(id: String, isDone: Boolean, userId: String): GamificationResult {
        // 1. Status in der DB updaten (wirft Exception, falls nicht vorhanden)
        val updatedTodoDto = this.updateStatus(id, isDone, userId)

        // 2. XP und Level berechnen lassen und zurückgeben
        return gamificationService.processTodoStatusChange(
            userId = userId,
            effort = updatedTodoDto.effort,
            usedEffort = updatedTodoDto.usedEffort,
            isDone = isDone
        )
    }

    private fun updateEffortChange(oldTodo: TodoEntity, newTodo: TodoEntity): TodoEntity {
        if (oldTodo.effort != newTodo.effort) {
           newTodo.effortChangesCount = oldTodo.effortChangesCount + 1
        }
        return newTodo
    }

    @org.springframework.transaction.annotation.Transactional
    fun syncBulkTodos(userId: String, bulkDtos: List<TodoDto>): SyncResultDto { // 🌟 Rückgabetyp geändert!
        validateUserExists(userId, userRepository)

        val oldTodos = todoRepository.findByUserId(userId).associateBy { it.id }
        var isChanged = false

        for (dto in bulkDtos) {
            val oldTodo = oldTodos[dto.id]

            if (oldTodo != null) {
                if (oldTodo.done != dto.done) {
                    toggleStatusWithGamification(dto.id, dto.done, userId)
                    isChanged = true
                }
                val entityToUpdate = dto.toEntity()
                todoRepository.save(updateEffortChange(oldTodo, entityToUpdate))
            } else {
                val newDto = CreateTodoDto(
                    task = dto.task,
                    description = dto.description,
                    effort = dto.effort,
                    userId = userId
                )
                val entity = newDto.toNewEntity().apply { this.id = dto.id; this.done = dto.done }
                todoRepository.save(entity)

                if (dto.done) {
                    gamificationService.processTodoStatusChange(userId, dto.effort, usedEffort = dto.usedEffort, isDone = true)
                    isChanged = true
                }
            }
        }

        // 🌟 JETZT HOLEN WIR DEN FINALE STATUS:
        // Wir holen uns die frisch aktualisierte Liste...
        val aktuelleListe = getTodos(userId)
        // ...und den aktuellen Gamification-Stand (inkl. aller soeben verbuchten XP!)
        val finalerGamificationStand = gamificationService.getGamificationState(userId)

        // Wenn sich während des Syncs Levels oder Punkte geändert haben, können wir optional
        // prüfen, ob ein "levelUp" stattgefunden hat, indem wir den alten Stand mit dem neuen vergleichen.
        // Fürs Erste liefern wir den absolut exakten, aktuellen Zustand aus der DB.
        return SyncResultDto(
            liste = aktuelleListe,
            gamificationResult = finalerGamificationStand
        )
    }

    /**
     * Reicht den Gamification-State einfach nur durch, damit der Controller
     * den GamificationService nicht kennen muss.
     */
    fun getGamificationState(userId: String): GamificationResult {
        return gamificationService.getGamificationState(userId)
    }

    fun getTodosByMilestone(userId: String?, milestoneId: String): List<TodoDto> {
        // Holt gezielt nur die Aufgaben für diesen Meilenstein aus der DB
        val entities = if (userId != null) todoRepository.findByUserIdAndMilestoneId(userId, milestoneId)
                        else todoRepository.findByMilestoneId(milestoneId)

        return entities.map { it.toDto() }
    }

    fun getCategoryTrainingPairs(): List<Pair<String, String>> {
        return todoRepository.findAll().map { it.task to it.category }
    }

    fun getEffortTrainingPairs(): List<Pair<String, Int>> {
        return todoRepository.findAll().map { it.task to it.effort }
    }

    // Liefert die Daten für den Fokus (Deine geniale Titel + Beschreibung Idee!)
    fun getFocusTrainingPairs(): List<Pair<String, FocusType>> {
        return todoRepository.findAll().map { todo ->
            val text = if (!todo.description.isNullOrBlank()) "${todo.task} ${todo.description}" else todo.task
            val focus = try { FocusType.valueOf(todo.focusType) } catch(e: Exception) { FocusType.LOW_FOCUS }
            text to focus
        }
    }
}

// =============================================================================
// KI-PROVIDER VERDRAHTUNG (Direkt im selben File, damit nichts "zu weit weg" ist)
// =============================================================================
@Configuration
class TodoAiProviderConfig(private val todoService: TodoService) {

    @Bean
    fun todoCategoryProvider() = object : AiGlobalDataProvider<String> {
        override fun getContextType() = AiContextType.TODO_CATEGORY
        override fun getGlobalTrainingPairs() = todoService.getCategoryTrainingPairs()
    }

    @Bean
    fun todoFocusProvider() = object : AiGlobalDataProvider<FocusType> {
        override fun getContextType() = AiContextType.TODO_FOCUS
        override fun getGlobalTrainingPairs() = todoService.getFocusTrainingPairs()
    }

    @Bean
    fun todoEffortProvider() = object : AiGlobalDataProvider<Int> {
        override fun getContextType() = AiContextType.TODO_EFFORT
        override fun getGlobalTrainingPairs() = todoService.getEffortTrainingPairs()
    }
}