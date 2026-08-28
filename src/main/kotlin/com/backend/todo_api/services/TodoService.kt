package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.specifications.TodoSpecifications
import com.backend.todo_api.dto.CreateTodoDto
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.QuickPanelMode
import com.backend.todo_api.dto.SyncResultDto
import com.backend.todo_api.dto.TodoBulkDto
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.dto.TodoUpdateResponse
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.TodoNotFoundException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.TodoSecurityResource
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
    private val todoRewardOrchestrator: TodoRewardOrchestrator,
    private val milestoneService: MilestoneService,
    private val milestoneRepository: MilestoneRepository,
    private val permissionService: PermissionService,
    private val userContextResolver: UserContextResolver,

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
            reviewerId = dto.reviewerId,
            reviewerUsedEffort = dto.reviewerUsedEffort ?: 0.0,

            // UNSERE VERSTECKTEN KI-/GAMIFICATION-FELDER (Sicher initialisiert!)
            effortChangesCount = 0,
            cooldownTurns = 0,
            focusType = "LOW_FOCUS",
            snoozedUntil = 0L,
            streakAlreadyRewarded = false,
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
        existingEntity.milestoneId = dto.milestoneId?.ifBlank { null }
        existingEntity.assignedUserId = dto.assignedUserId
        existingEntity.isStarted = dto.isStarted
        existingEntity.teamStatus = dto.teamStatus
        existingEntity.lastDeveloperId = dto.lastDeveloperId
        existingEntity.reviewerId = dto.reviewerId
        existingEntity.reviewerUsedEffort = dto.reviewerUsedEffort ?: 0.0

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
            lastDeveloperId = entity.lastDeveloperId,
            reviewerId = entity.reviewerId,
            reviewerUsedEffort = entity.reviewerUsedEffort
        )
    }

    fun getArchiveStats(): String {
        val totalCount = todoRepository.count()
        val archivedCount = todoRepository.findAll().count { it.isArchived }
        return "KI-Archiv-Status: $archivedCount archivierte Todos von insgesamt $totalCount Datensätzen sind für die KI bereit."
    }

    // TODO: Prüfen, ob getTodos noch vom Frontend aufgerufen wird
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
        validateUserExists(userId, userRepository)

        // 1. Cutoff-Date berechnen (z. B. JETZT minus 30 Tage in Millisekunden)
        val cutoffDate = System.currentTimeMillis() - (daysLookback.toLong() * 24 * 60 * 60 * 1000)

        // 2. User-Kontexte laden
        val contexts = userContextResolver.resolveContexts(userId)

        // 3. Aus allen Kontexten Specifications bauen und per OR verknüpfen
        val combinedScopeSpec = contexts.mapNotNull { context ->
            when (context.scope.name) {
                ScopeType.COMPANY ->
                    TodoSpecifications.isCompanyScope()

                ScopeType.DEPARTMENT ->
                    context.scopeInstanceId?.let { TodoSpecifications.isDepartmentScope(it) }

                ScopeType.PROJECT ->
                    context.scopeInstanceId?.let { projectId ->
                        TodoSpecifications.isProjectScope(projectId)
                    }

                ScopeType.RESOURCE ->
                    TodoSpecifications.isPrivateResourceScope(userId)

                else -> null
            }
        }.reduceOrNull { accSpec, currentSpec -> accSpec.or(currentSpec) }

        // Falls gar keine Scopes vorliegen, leere Liste zurückgeben
        if (combinedScopeSpec == null) return emptyList()

        // 🎯 4. HIER wird cutoffDate JETZT SAUBER ÜBERGEBEN:
        val finalSpec = combinedScopeSpec.and(
            TodoSpecifications.isNotArchivedAndWithinCutoff(cutoffDate)
        )

        // 5. Ein einziger DB-Aufruf via Executor
        return todoRepository.findAll(finalSpec).map { mapToDto(it) }
    }

    // 2. GET BY ID (Frontend-Sicht): Nutzt deine neue, effiziente DB-Methode
    fun getTodoById(userId: String, id: String): TodoDto {
        val todo = checkPermission(userId, id, ActionType.READ, "du kannst das todo nicht lesen")
        return mapToDto(todo)
    }

    fun checkPermission(userId: String, todoId: String, action: ActionType, message: String): TodoEntity {
        val todo = todoRepository.findByIdAndIsArchivedFalse(todoId)
            ?: throw TodoNotFoundException("Todo nicht gefunden oder archiviert")

        val userContexts = userContextResolver.resolveContexts(userId)

        val canAct = permissionService.hasPermission(
            userContexts,
            action = action,
            resource = buildSecurityResource(todo)
        )

        if (!canAct) {
            throw ActionForbiddenException(message)
        }
        return todo
    }

    fun createTodo(userId: String, dto: CreateTodoDto): TodoDto {
        dto.userId = userId
        val entity = mapToNewEntity(dto)
        val contexts = userContextResolver.resolveContexts(userId)

        val canCreate = permissionService.hasPermission(
            contexts,
            action = ActionType.CREATE,
            resource = buildSecurityResource(entity)
        )

        if (!canCreate) {
            throw ActionForbiddenException("Du kannst dieses Todo nicht erzeugen")
        }

        val savedEntity = todoRepository.save(entity)

        if (dto.done && dto.milestoneId != null) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = dto.milestoneId!!,
                effort = dto.effort,
                usedEffort = dto.usedEffort,
                isDone = true
            )
        }

        return mapToDto(savedEntity)
    }

    fun updateTodo(userId: String, dto: TodoDto): TodoUpdateResponse {
        val oldTodo = checkPermission(userId, dto.id, ActionType.UPDATE, "Du kannst das Todo nicht ändern")

        // Altzustände für Meilenstein-Berechnung merken
        val oldMilestoneId = oldTodo.milestoneId
        val oldEffort = oldTodo.effort
        val oldDone = oldTodo.done

        // KI-Zähler hochschrauben, falls sich der Effort geändert hat
        if (oldTodo.effort != dto.effort) {
            oldTodo.effortChangesCount += 1
        }

        // Frontend-Felder auf die Entity übertragen
        val updatedEntity = mergeDtoIntoEntity(dto, oldTodo)

        // Meilenstein-Fortschritt korrigieren, falls das Todo VORHER erledigt war
        if (oldDone && !oldMilestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = oldMilestoneId,
                effort = oldEffort,
                usedEffort = updatedEntity.usedEffort,
                isDone = false
            )
        }

        // Modifizierte Entity in der DB speichern
        val savedEntity = todoRepository.save(updatedEntity)

        // Meilenstein-Fortschritt aufrechnen, falls das Todo JETZT erledigt ist
        if (savedEntity.done && !savedEntity.milestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = savedEntity.milestoneId,
                effort = savedEntity.effort,
                usedEffort = savedEntity.usedEffort,
                isDone = true
            )
        }

        // 🎯 Belohnungen verarbeiten ODER falls keine Statusänderung vorlag, reinen Ist-Zustand holen
        val rewardResult =
            if (oldDone != savedEntity.done) {
                todoRewardOrchestrator.processTodoCompletionRewards(
                todo = savedEntity,
                currentUserId = userId,
                isDone = savedEntity.done
              )
            } else {
                todoRewardOrchestrator.getCombinedRewardState(userId)
            }

        return TodoUpdateResponse(
            todo = mapToDto(savedEntity),
            gamificationResult = rewardResult.gamificationResult,
            streakInfo = rewardResult.streakInfo
        )
    }

    /**
     * Erledigte private Aufgaben des Users gesammelt löschen (wird archiviert).
     * Filtert in der Query Projekt-Aufgaben (mit milestoneId) automatisch heraus.
     */
    fun deleteCompletedPrivateTodos(userId: String) {
        val userContexts = userContextResolver.resolveContexts(userId)
        val hasPrivateOwnerScope = userContexts.any { context ->
            context.scope.name == ScopeType.RESOURCE && context.role.name == RoleType.OWNER
        }

        if (!hasPrivateOwnerScope) {
            throw ActionForbiddenException("Du hast keine Berechtigung, deine privaten Todos zu löschen")
        }
        todoRepository.archiveCompletedPrivateTodos(userId)
    }

    /**
     * 🗑️ Alle privaten Aufgaben des Users gesammelt löschen (wird archiviert).
     * Filtert in der Query Projekt-Aufgaben (mit milestoneId) automatisch heraus.
     */
    fun deleteAllPrivateTodos(userId: String) {
        val userContexts = userContextResolver.resolveContexts(userId)
        val hasPrivateOwnerScope = userContexts.any { context ->
            context.scope.name == ScopeType.RESOURCE && context.role.name == RoleType.OWNER
        }

        if (!hasPrivateOwnerScope) {
            throw ActionForbiddenException("Du hast keine Berechtigung, deine privaten Todos zu löschen")
        }

        todoRepository.archiveAllPrivateTodos(userId)
    }

    /**
     * 🗑️ Einzelne Aufgabe über den Mülleimer löschen (wird im Hintergrund archiviert).
     * Wenn das Todo nicht existiert (updatedRows == 0), fliegt eine Exception,
     * damit das Frontend über den Datenkonflikt informiert wird!
     */
    fun deleteTodoById(userId: String, id: String) {

        val todo = checkPermission(userId, id, ActionType.DELETE, "Du hast keine Berechtigung, dieses Todo zu löschen")
        // 🎯 Falls das Todo erledigt war und zu einem Meilenstein gehörte: Meilenstein-Fortschritt anpassen
        if (todo.done && !todo.milestoneId.isNullOrBlank()) {
            milestoneService.recalculateMilestoneProgress(
                milestoneId = todo.milestoneId!!,
                effort = todo.effort,
                usedEffort = todo.usedEffort,
                isDone = false // Fortschritt wieder abziehen
            )
        }

        val updatedRows = todoRepository.archiveById(id)
        if (updatedRows == 0) {
            throw TodoNotFoundException("Das To-Do konnte nicht gelöscht werden")
        }
    }

    @Transactional
    fun syncBulkTodos(userId: String, bulkDtos: List<TodoBulkDto>): SyncResultDto {
        validateUserExists(userId, userRepository)

        for (dto in bulkDtos) {
            when (dto.syncAction) {
                "CREATED" -> {
                    val createDto = CreateTodoDto(
                        task = dto.task,
                        description = dto.description,
                        effort = dto.effort,
                        userId = userId,
                        done = dto.done,
                        usedEffort = dto.usedEffort,
                        milestoneId = dto.milestoneId
                    )
                    // 🎯 Nutzt komplett deine Create-Logik inkl. Rechte & Meilensteine
                    createTodo(userId, createDto)
                }

                "UPDATED" -> {
                    val todoDto = TodoDto(
                        id = dto.id,
                        task = dto.task,
                        description = dto.description,
                        done = dto.done,
                        effort = dto.effort,
                        usedEffort = dto.usedEffort,
                        dueDate = dto.dueDate,
                        completedAt = dto.completedAt,
                        createdAt = dto.createdAt,
                        userId = userId,
                        category = dto.category,
                        milestoneId = dto.milestoneId,
                        assignedUserId = dto.assignedUserId,
                        isStarted = dto.isStarted,
                        teamStatus = dto.teamStatus,
                        lastDeveloperId = dto.lastDeveloperId
                    )
                    // 🎯 Nutzt deine perfekte Update-Logik (Rechte, Gamification, KI-Zähler, Streaks, Meilensteine)
                    updateTodo(userId, todoDto)
                }

                "DELETED" -> {
                    // 🎯 Prüft Rechte & zieht bei Bedarf Meilenstein-Fortschritt ab
                    deleteTodoById(userId, dto.id)
                }

                "DIRTY_AND_DELETED" -> {
                    // Erst Updaten (damit Gamification/Status abgehandelt wird), dann Löschen
                    val todoDto = TodoDto(
                        id = dto.id, task = dto.task, description = dto.description,
                        done = dto.done, effort = dto.effort, usedEffort = dto.usedEffort,
                        dueDate = dto.dueDate, completedAt = dto.completedAt, createdAt = dto.createdAt,
                        userId = userId, category = dto.category, milestoneId = dto.milestoneId,
                        assignedUserId = dto.assignedUserId, isStarted = dto.isStarted,
                        teamStatus = dto.teamStatus, lastDeveloperId = dto.lastDeveloperId
                    )
                    updateTodo(userId, todoDto)
                    deleteTodoById(userId, dto.id)
                }

                "CREATED_AND_DELETED" -> {
                    // War offline neu, wurde aber offline auch gleich wieder gelöscht -> Gar nichts tun oder 1x blind ins Archiv
                    val newDto = CreateTodoDto(
                        task = dto.task, description = dto.description, effort = dto.effort,
                        userId = userId, done = dto.done, usedEffort = dto.usedEffort, milestoneId = dto.milestoneId
                    )
                    val entity = mapToNewEntity(newDto).apply {
                        this.id = dto.id
                        this.done = dto.done
                        this.isArchived = true
                    }
                    todoRepository.save(entity)
                }

                "BULK_DELETE_COMPLETED" -> {
                    deleteCompletedPrivateTodos(userId)
                }

                "BULK_DELETE_ALL" -> {
                    deleteAllPrivateTodos(userId)
                }
            }
        }

        todoRepository.flush()

        // 🎯 Frischer Gesamtstand direkt aus dem Orchestrator
        val rewardState = todoRewardOrchestrator.getCombinedRewardState(userId)
        val aktuelleListe = getRelevantTodos(userId)

        return SyncResultDto(
            liste = aktuelleListe,
            gamificationResult = rewardState.gamificationResult,
            streakInfo = rewardState.streakInfo
        )
    }

    fun getTodosByMilestone(userId: String, milestoneId: String): List<TodoDto> {
        validateUserExists(userId, userRepository)

        // 1. Alle Todos für diesen Meilenstein aus der DB holen (nicht archiviert)
        val todos = todoRepository.findByMilestoneIdAndIsArchivedFalse(milestoneId)

        if (todos.isEmpty()) {
            return emptyList()
        }

        // 2. Sicherheits-Check: Hat der User READ-Rechte auf diese Todos/das Projekt?
        val userContexts = userContextResolver.resolveContexts(userId)

        val authorizedTodos = todos.filter { entity ->
            permissionService.hasPermission(
                userContexts = userContexts,
                action = ActionType.READ,
                resource = buildSecurityResource(entity)
            )
        }

        return authorizedTodos.map { mapToDto(it) }
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

    private fun buildSecurityResource(todo: TodoEntity): TodoSecurityResource {
        var projectId: String? = null
        var departmentId: String? = null

        if (!todo.milestoneId.isNullOrBlank()) {
            val milestone = milestoneRepository.findById(todo.milestoneId!!).orElse(null)
            projectId = milestone?.project?.id
            departmentId = milestone?.project?.departmentId
        }

        return TodoSecurityResource(
            id = todo.id.ifBlank { null },
            ownerUserId = todo.userId,
            projectId = projectId,
            departmentId = departmentId
        )
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