package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateTodoDto
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.QuickPanelMode
import com.backend.todo_api.dto.SyncResultDto
import com.backend.todo_api.dto.TodoBulkDto
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.dto.TodoUpdateResponse
import com.backend.todo_api.exceptions.TodoNotFoundException
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.providers.AiGlobalDataProvider
import com.backend.todo_api.utils.AiTextUtil
import org.springframework.stereotype.Service
import com.backend.todo_api.validation.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.Transactional

@Service
class TodoService (
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val gamificationService: GamificationService,
    private val milestoneService: MilestoneService,
    private val streakService: StreakService
) {
    // 1. NEUERSTELLUNG: Wandelt CreateTodoDto in eine neue Entity um und initialisiert versteckte Felder
    private fun mapToNewEntity(dto: CreateTodoDto): TodoEntity {
        return TodoEntity(
            id = java.util.UUID.randomUUID().toString(),
            task = dto.task,
            description = dto.description,
            done = dto.done,
            effort = dto.effort,
            usedEffort = dto.usedEffort,
            dueDate = dto.dueDate,
            completedAt = dto.completedAt,
            createdAt = if (dto.createdAt == 0L) System.currentTimeMillis() else dto.createdAt,
            userId = dto.userId,
            category = dto.category,
            milestoneId = dto.milestoneId,
            assignedUserId = dto.assignedUserId,
            isStarted = dto.isStarted,
            teamStatus = dto.teamStatus,
            lastDeveloperId = dto.lastDeveloperId,

            // 🔮 UNSERE VERSTECKTEN KI-/GAMIFICATION-FELDER (Sicher initialisiert!)
            effortChangesCount = 0,
            cooldownTurns = 0,
            focusType = "LOW_FOCUS",
            snoozedUntil = 0L
        )
    }

    // 2. UPDATE: Überträgt NUR Frontend-Felder auf eine bereits existierende DB-Entity
    private fun mergeDtoIntoEntity(dto: TodoDto, existingEntity: TodoEntity): TodoEntity {
        existingEntity.task = dto.task
        existingEntity.description = dto.description
        existingEntity.done = dto.done
        existingEntity.effort = dto.effort
        existingEntity.usedEffort = dto.usedEffort
        existingEntity.dueDate = dto.dueDate
        existingEntity.completedAt = dto.completedAt
        existingEntity.category = dto.category
        existingEntity.milestoneId = dto.milestoneId
        existingEntity.assignedUserId = dto.assignedUserId
        existingEntity.isStarted = dto.isStarted
        existingEntity.teamStatus = dto.teamStatus
        existingEntity.lastDeveloperId = dto.lastDeveloperId

        // 🛡️ HIER PASSIERT NICHTS: cooldownTurns und focusType bleiben auf existingEntity unberührt!
        return existingEntity
    }

    // 3. ANTWORT: Wandelt eine DB-Entity in ein fressbares DTO fürs Frontend um
    fun mapToDto(entity: TodoEntity): TodoDto {
        return TodoDto(
            id = entity.id ?: "",
            task = entity.task,
            description = entity.description,
            done = entity.done,
            effort = entity.effort,
            usedEffort = entity.usedEffort,
            dueDate = entity.dueDate,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            userId = entity.userId,
            category = entity.category,
            effortChangesCount = entity.effortChangesCount,
            milestoneId = entity.milestoneId,
            assignedUserId = entity.assignedUserId,
            isStarted = entity.isStarted,
            teamStatus = entity.teamStatus,
            lastDeveloperId = entity.lastDeveloperId
        )
    }

    fun getArchiveStats(): String {
        val totalCount = todoRepository.count()
        val archivedCount = todoRepository.findAll().count { it.isArchived }
        return "KI-Archiv-Status: $archivedCount archivierte Todos von insgesamt $totalCount Datensätzen sind für die KI bereit."
    }

    // 1. GET (Frontend-Sicht): Nutzt deine neue, effiziente DB-Methode
    fun getTodos(userId: String?): List<TodoDto> {
        if (userId != null) {
            validateUserExists(userId, userRepository)
            // 🎯 Hier nutzen wir deine neue Repository-Methode!
            return todoRepository.findByUserIdAndIsArchivedFalse(userId)
                .map { mapToDto(it) }
        }
        return todoRepository.findByIsArchivedFalse().map { mapToDto(it) }
    }

    /**
     * 📋 Holt alle für den User relevanten To-Dos (inkl. Projekt- & Privat-Tickets).
     * Validiert zuerst den User, um verwaiste Sessions sofort zu kicken.
     */
    fun getRelevantTodos(userId: String, daysLookback: Int = 30): List<TodoDto> {
        // 🛡️ SICHERHEITS-CHECK: Existiert der User noch in der Datenbank?
        validateUserExists(userId, userRepository)

        // 1. Zeitstempel für das Ausblenden alter, erledigter Aufgaben berechnen
        val cutoffDate = System.currentTimeMillis() - (daysLookback.toLong() * 24 * 60 * 60 * 1000)

        // 2. Hochperformanter Datenbank-Aufruf über unsere gemeinsame Team-Query
        val relevantEntities = todoRepository.findRelevantTodosForUser(userId, cutoffDate)

        // 3. Konvertieren in DTOs und ab ans Frontend
        return relevantEntities.map { mapToDto(it) }
    }

    // 2. GET BY ID (Frontend-Sicht): Nutzt deine neue, effiziente DB-Methode
    fun getTodoById(id: String): TodoDto {
        // 🎯 Hier nutzen wir ebenfalls deine neue Methode
        val todo = todoRepository.findByIdAndIsArchivedFalse(id)
            ?: throw TodoNotFoundException("Todo nicht gefunden oder archiviert")
        return mapToDto(todo)
    }

    fun createTodo(dto: CreateTodoDto): TodoDto {
        validateUserExists(dto.userId, userRepository)

        // 🚀 Vererbung & Extension im Einsatz: Setzt autom. usedEffort & Zeitstempel, falls nötig!
        val entity = mapToNewEntity(dto)
        val savedEntity = todoRepository.save(entity)

        if (dto.done && dto.milestoneId != null) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = dto.milestoneId,
                effort = dto.effort,
                usedEffort = dto.usedEffort,
                isDone = true
            )
        }

        return mapToDto(savedEntity)
    }

    fun updateTodo(dto: TodoDto): TodoUpdateResponse {
        validateUserExists(dto.userId, userRepository)

        // 1. Aktuellen Zustand inklusive ALLER versteckten KI-Felder aus der DB holen
        val oldTodo = todoRepository.findByIdAndIsArchivedFalse(dto.id)
            ?: throw TodoNotFoundException("To-Do mit ID ${dto.id} nicht gefunden")

        // Werte für den MilestoneService sichern, bevor wir das Objekt modifizieren
        val oldMilestoneId = oldTodo.milestoneId
        val oldEffort = oldTodo.effort
        val oldDone = oldTodo.done

        // 🧠 KI-LOGIK RETTEN: Zähler hochschrauben, wenn sich der Aufwand im Frontend geändert hat
        if (oldTodo.effort != dto.effort) {
            oldTodo.effortChangesCount += 1
        }

        // 🔄 SICHERES MERGE: Wir übertragen NUR die Frontend-Felder auf unsere geladene DB-Entity
        val updatedEntity = mergeDtoIntoEntity(dto, oldTodo)

        // 2. MILESTONE-BERECHNUNG: Fall A (War es vorher fertig? Aufwand abziehen)
        if (oldDone && !oldMilestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = oldMilestoneId,
                effort = oldEffort,
                usedEffort = updatedEntity.usedEffort,
                isDone = false
            )
        }

        val user = userRepository.findById(dto.userId).orElseThrow()
        if (!oldDone && updatedEntity.done && !updatedEntity.milestoneId.isNullOrBlank()) {
            streakService.updateStreakOnTodoCompleted(user, updatedEntity)
        }

        // Jetzt speichern die modifizierte Entity ab (cooldownTurns und focusType sind absolut sicher!)
        val savedEntity = todoRepository.save(updatedEntity)

        // Fall B: Ist es JETZT erledigt? Dann neuen Aufwand auf den Meilenstein rechnen.
        if (savedEntity.done && !savedEntity.milestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = savedEntity.milestoneId,
                effort = savedEntity.effort,
                usedEffort = savedEntity.usedEffort,
                isDone = true
            )
        }

        // 3. Gamification-Logik triggern bei Statuswechsel
        var gamificationResult: GamificationResult? = null
        if (oldDone != savedEntity.done) {
            gamificationResult = gamificationService.processTodoStatusChange(
                savedEntity.userId,
                savedEntity.effort,
                usedEffort = savedEntity.usedEffort,
                isDone = savedEntity.done
            )
        }

        val freshUser = userRepository.findById(dto.userId).orElseThrow()
        val streakInfo = streakService.getCurrentStreakInfo(freshUser)

        // 4. Antwort via mapToDto sauber konvertieren
        return TodoUpdateResponse(
            todo = mapToDto(savedEntity),
            gamificationResult = gamificationResult,
            streakInfo = streakInfo
        )
    }

    /**
     * Erledigte private Aufgaben des Users gesammelt löschen (wird archiviert).
     * Filtert in der Query Projekt-Aufgaben (mit milestoneId) automatisch heraus.
     */
    fun deleteCompletedPrivateTodos(userId: String) {
        // 🛡️ SICHERHEITS-CHECK: Frontend kickt den User, wenn er aus der DB gelöscht wurde
        validateUserExists(userId, userRepository)

        // Nutzt die sichere Update-Query aus dem Repository
        todoRepository.archiveCompletedPrivateTodos(userId)
    }

    /**
     * 🗑️ Alle privaten Aufgaben des Users gesammelt löschen (wird archiviert).
     * Filtert in der Query Projekt-Aufgaben (mit milestoneId) automatisch heraus.
     */
    fun deleteAllPrivateTodos(userId: String) {
        // 🛡️ SICHERHEITS-CHECK: Frontend kickt den User, wenn er aus der DB gelöscht wurde
        validateUserExists(userId, userRepository)

        // Nutzt die sichere Update-Query aus dem Repository
        todoRepository.archiveAllPrivateTodos(userId)
    }

    /**
     * 🗑️ Einzelne Aufgabe über den Mülleimer löschen (wird im Hintergrund archiviert).
     * Wenn das Todo nicht existiert (updatedRows == 0), fliegt eine Exception,
     * damit das Frontend über den Datenkonflikt informiert wird!
     */
    fun deleteTodoById(id: String) {
        val updatedRows = todoRepository.archiveById(id)
        if (updatedRows == 0) {
            throw TodoNotFoundException("todo not found")
        }
    }

    // 3. STATUS UPDATE (Sicherheit erhöhen)
    fun updateStatus(id: String, done: Boolean, userId: String): TodoDto {
        validateUserExists(userId, userRepository)

        // 🎯 Nur noch aktive Todos können ihren Status ändern
        val todo = todoRepository.findByIdAndIsArchivedFalse(id)
            ?: throw TodoNotFoundException("Todo nicht gefunden oder archiviert")

        todo.done = done
        val saved = todoRepository.save(todo)
        return mapToDto(saved)
    }

    @Transactional
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
        // 🧠 KI-LOGIK: Wenn sich der Aufwand geändert hat, Zähler basierend auf der DB hochzählen
        if (oldTodo.effort != newTodo.effort) {
            newTodo.effortChangesCount = oldTodo.effortChangesCount + 1
        } else {
            // Falls er gleich blieb, Zählerstand aus der DB übernehmen (damit dort keine 0 überschrieben wird)
            newTodo.effortChangesCount = oldTodo.effortChangesCount
        }

        // DIE RETTUNG DER VERSTECKTEN KI-FELDER:
        // Wir impfen die neue Entity mit den unberührten Werten aus der DB
        newTodo.focusType = oldTodo.focusType
        newTodo.cooldownTurns = oldTodo.cooldownTurns
        newTodo.isArchived = oldTodo.isArchived
        newTodo.createdAt = oldTodo.createdAt // Auch das originale Erstellungsdatum bleibt so sicher!
        newTodo.snoozedUntil = oldTodo.snoozedUntil

        return newTodo
    }

    @Transactional
    fun syncBulkTodos(userId: String, bulkDtos: List<TodoBulkDto>): SyncResultDto {
        validateUserExists(userId, userRepository)

        val oldTodos = todoRepository.findByUserId(userId).associateBy { it.id }
        val user = userRepository.findById(userId).orElseThrow()

        for (dto in bulkDtos) {
            val oldTodo = oldTodos[dto.id]

            when (dto.syncAction) {
                "CREATED" -> {
                if (oldTodo == null) {
                    val newDto = CreateTodoDto(
                    task = dto.task, description = dto.description, effort = dto.effort,
                    userId = userId, done = dto.done, usedEffort = dto.usedEffort, milestoneId = dto.milestoneId
                    )
                    val entity = mapToNewEntity(newDto).apply { this.id = dto.id; this.done = dto.done }
                    todoRepository.save(entity)

                    if (dto.done) {
                        gamificationService.processTodoStatusChange(userId, dto.effort, usedEffort = dto.usedEffort, isDone = true)
                        if (!entity.milestoneId.isNullOrBlank()) {
                            streakService.updateStreakOnTodoCompleted(user, entity)
                        }
                    }
                }
            }
                "UPDATED" -> {
                    if (oldTodo != null) {
                        if (oldTodo.done != dto.done) {
                            toggleStatusWithGamification(dto.id, dto.done, userId)
                            if (dto.done && !oldTodo.milestoneId.isNullOrBlank()) {
                                streakService.updateStreakOnTodoCompleted(user, oldTodo)
                            }
                        }
                        val entityToUpdate = mergeDtoIntoEntity(dto, oldTodo)
                        todoRepository.save(updateEffortChange(oldTodo, entityToUpdate))
                    }
                }
                "DELETED" -> {
                    if (oldTodo != null) {
                        if (!oldTodo.done && dto.done) {
                            gamificationService.processTodoStatusChange(userId, oldTodo.effort, usedEffort = dto.usedEffort, isDone = true)
                            if (!oldTodo.milestoneId.isNullOrBlank()) {
                                streakService.updateStreakOnTodoCompleted(user, oldTodo)
                            }
                        }
                        todoRepository.archiveById(dto.id) // 📦 Sicher archivieren!
                    }
                }
                "DIRTY_AND_DELETED" -> {
                    if (oldTodo != null) {
                        if (oldTodo.done != dto.done) {
                            gamificationService.processTodoStatusChange(userId, dto.effort, usedEffort = dto.usedEffort, isDone = dto.done)
                            if (dto.done && !oldTodo.milestoneId.isNullOrBlank()) {
                                streakService.updateStreakOnTodoCompleted(user, oldTodo)
                            }
                        }
                        val entityToUpdate = mergeDtoIntoEntity(dto, oldTodo)
                        todoRepository.save(updateEffortChange(oldTodo, entityToUpdate))
                        todoRepository.archiveById(dto.id)
                    }
                }
                "CREATED_AND_DELETED" -> {
                    if (oldTodo == null) {
                        val newDto = CreateTodoDto(
                        task = dto.task, description = dto.description, effort = dto.effort,
                        userId = userId, done = dto.done, usedEffort = dto.usedEffort, milestoneId = dto.milestoneId
                        )
                        val entity = mapToNewEntity(newDto).apply {
                            this.id = dto.id
                            this.done = dto.done
                            this.isArchived = true // 📦 Wandert sofort blind ins Archiv!
                        }
                        todoRepository.save(entity)

                        if (dto.done) {
                            gamificationService.processTodoStatusChange(userId, dto.effort, usedEffort = dto.usedEffort, isDone = true)
                            if (!entity.milestoneId.isNullOrBlank()) {
                                streakService.updateStreakOnTodoCompleted(user, entity)
                            }
                        }
                    }
                }
                "BULK_DELETE_COMPLETED" -> {
                    this.deleteCompletedPrivateTodos(userId)
                }
                "BULK_DELETE_ALL" -> {
                    this.deleteAllPrivateTodos(userId)
                }
            }
        }

        todoRepository.flush()
        val aktuelleListe = getRelevantTodos(userId)
        val finalerGamificationStand = gamificationService.getGamificationState(userId)

        // 👈 4. STREAK-LOGIK TEIL C: Den finalen Stand nach dem Massen-Sync berechnen
        val freshUser = userRepository.findById(userId).orElseThrow()
        val streakInfo = streakService.getCurrentStreakInfo(freshUser)

        return SyncResultDto(
            liste = aktuelleListe,
        gamificationResult = finalerGamificationStand,
        streakInfo = streakInfo // 🔥 Mitgeben!
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

        return entities.map { mapToDto(it) }
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

    fun getQuickTodoTrainingPairs(): List<Pair<String, String>> {
        // Hole alle aktiven Todos, die wenig Aufwand benötigen (effort < 3)
        return todoRepository.findAll()
            .filter { it.effort < 3 }
            .map { it.task to (it.category ?: "Sonstiges") }
    }

    fun getIntelligentQuickTodos(modus: QuickPanelMode): List<String> {
        // 1. Alle Todos holen (wir schauen in die gesamte Historie)
        val allTodos = todoRepository.findAll()
        val isPause = (modus == QuickPanelMode.PAUSE)

        // 2. Filtern nach Modus (Effort 0 für Pause, >0 für Aktiv)
        // 3. Gruppieren nach dem "Bedeutungskern" mittels AiTextUtil
        return allTodos
            .filter { (it.effort == 0) == isPause }
            .groupBy { getMeaningfulCore(it.task) }
            .entries
            .filter { it.key.isNotEmpty() } // Nur Gruppen mit echtem Inhalt
            .sortedByDescending { it.value.size } // Die häufigsten Themen gewinnen
            .take(6)
            .map { group ->
                // Wir nehmen den Namen des letzten Todos aus dieser Gruppe,
                // damit der Vorschlag aktuell bleibt.
                group.value.last().task
            }
    }

    private fun getMeaningfulCore(task: String): String {
        // 🧠 Nutzung der zentralen Utility!
        val tokens = AiTextUtil.tokenizeAndClean(task)

        // Wir nehmen das erste relevante Wort als "Kern-Kategorie" (z.B. "Refactoring")
        // Falls das Todo nur aus einem Wort besteht, nehmen wir das.
        return tokens.firstOrNull() ?: ""
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

    @Bean
    fun quickTodoProvider() = object : AiGlobalDataProvider<String> {
        override fun getContextType() = AiContextType.QUICK_TODO_CATEGORY
        // Hier ziehen wir nur die Aufgaben, die "schnell" waren (z.B. wenig effort)
        override fun getGlobalTrainingPairs() = todoService.getQuickTodoTrainingPairs()
    }
}