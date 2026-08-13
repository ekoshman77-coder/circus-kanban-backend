package com.backend.todo_api.services

import com.backend.todo_api.data.entity.PlannerRecommendationEntity
import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.RecommendationRoundEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.RecommendationRoundRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.PlannerFeedbackRequest
import com.backend.todo_api.dto.PlannerRecommendationsResponse
import com.backend.todo_api.dto.RecommendedTodoResponse
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.FeedbackForPlanner
import com.backend.todo_api.model.PlannerType
import com.backend.todo_api.validation.validateUserExists
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class PlannerCoordinator(
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val recommendationRoundRepository: RecommendationRoundRepository,
    private val todoService: TodoService,
    private val planners: List<PlannerInterface>
) {

    companion object {
        private const val MAX_COOLDOWN_TURNS = 3
    }

    @Transactional
    fun getRecommendations(
        userId: String,
        userEnergy: EnergyLevel,
        workingTimeLeft: Long
    ): PlannerRecommendationsResponse {

        validateUserExists(userId, userRepository)

        // 1. Alle aktiven Todos holen
        val matchPool = todoRepository.findActivePlannerTodosForUser(userId)

        // 2. Snooze-Filter
        val nonSnoozedPool = filterAvailableTodos(matchPool)

        // 3. Alle Todos gesnoozed → direktes Fallback.
        //    Keine Planner und keine Recommendation-Round.
        if (nonSnoozedPool.isEmpty()) {
            val earliestWakeupTodo =
                matchPool.minByOrNull { it.snoozedUntil }

            return PlannerRecommendationsResponse(
                roundId = "",
                recommendations = listOf(
                    RecommendedTodoResponse(
                        todo = earliestWakeupTodo?.let { todoService.mapToDto(it) },
                        plannerType = null,
                        modeCode = "STANDARD",
                        reasonCode = "NO_TODOS_LEFT"
                    )
                )
            )
        }

        // 4. Gemeinsamen Candidate Pool für alle Planner bauen.
        val candidatePool = getCandidatePool(nonSnoozedPool)

        // 5. Alle Planner bekommen denselben Candidate Pool.
        val plannerRecommendations = planners.mapNotNull { planner ->
            planner.calculatePerfectRecommendation(
                userId,
                candidates = candidatePool,
                userEnergy = userEnergy,
                workingTimeLeft = workingTimeLeft
            )
        }

        // 6. Kein Planner konnte eine Recommendation erzeugen.
        //    Deshalb auch keine Round speichern.
        if (plannerRecommendations.isEmpty()) {
            return PlannerRecommendationsResponse(
                roundId = "",
                recommendations = emptyList()
            )
        }

        // 7. Neue Recommendation-Round erzeugen.
        val round = RecommendationRoundEntity(
            userId = userId,
            createdAt = System.currentTimeMillis()
        )

        // 8. Planner-Ergebnisse als Entities an die Round hängen.
        plannerRecommendations.forEach { recommendation ->
            round.recommendations += PlannerRecommendationEntity(
                round = round,
                plannerType = recommendation.plannerType,
                todoId = recommendation.todoId,
                score = recommendation.score,
                energyLevel = recommendation.energyLevel,
                timeUntilDue = recommendation.timeUntilDue,
                workingTimeLeft = recommendation.workingTimeLeft,
                effort = recommendation.effort,
                reason = recommendation.reason
            )
        }

        // 9. Round + Recommendations gemeinsam speichern.
        val savedRound = recommendationRoundRepository.save(round)

        // 10. Interne Planner-Ergebnisse in API-Responses umwandeln.
        val recommendations = plannerRecommendations.map { recommendation ->
            val todo = candidatePool
                .firstOrNull { it.id == recommendation.todoId }

            RecommendedTodoResponse(
                todo = todo?.let { todoService.mapToDto(it) },
                plannerType = recommendation.plannerType,
                modeCode = "STANDARD",
                reasonCode = recommendation.reason
            )
        }

        return PlannerRecommendationsResponse(
            roundId = savedRound.id,
            recommendations = recommendations
        )
    }

    /**
     * Gemeinsame Vorauswahl für alle Planner.
     *
     * Snooze wurde bereits vorher behandelt.
     * Hier wird nur noch der Cooldown verarbeitet.
     */
    private fun getCandidatePool(
        availableTodos: List<TodoEntity>
    ): List<TodoEntity> {

        decrementCooldowns(availableTodos)

        return filterPoolByCooldown(availableTodos)
    }

    private fun decrementCooldowns(pool: List<TodoEntity>) {
        pool.forEach { todo ->
            if (todo.cooldownTurns > 0) {
                todo.cooldownTurns -= 1
                todoRepository.save(todo)
            }
        }
    }

    private fun filterPoolByCooldown(
        pool: List<TodoEntity>
    ): List<TodoEntity> {

        for (cooldownLevel in 0..MAX_COOLDOWN_TURNS) {
            val candidates = pool.filter {
                it.cooldownTurns == cooldownLevel
            }

            if (candidates.isNotEmpty()) {
                return candidates
            }
        }

        return pool
    }

    /**
     * Liefert nur Todos, deren Snooze-Zeit bereits abgelaufen ist.
     */
    private fun filterAvailableTodos(
        todos: List<TodoEntity>
    ): List<TodoEntity> {

        if (todos.isEmpty()) {
            return emptyList()
        }

        val currentTime = System.currentTimeMillis()

        return todos.filter {
            it.snoozedUntil <= currentTime
        }
    }

    @Transactional
    fun processUserFeedback(request: PlannerFeedbackRequest) {
        // 1. RecommendationRound anhand der roundId laden
        val round = recommendationRoundRepository.findById(request.roundId).orElse(null) ?: return

        // Map für schnellen Zugriff auf die gespeicherten Empfehlungen dieser Runde
        val recommendationMap = round.recommendations.associateBy { it.todoId }

        // 2. Akzeptiertes Todo verarbeiten (falls eins gewählt wurde)
        request.acceptedTodoId?.let { acceptedId ->
            val todo = todoRepository.findById(acceptedId).orElse(null)
            if (todo != null) {
                todo.cooldownTurns = 0
                todoRepository.save(todo)
            }

            val savedRec = recommendationMap[acceptedId]
            if (savedRec != null) {
                val feedback = FeedbackForPlanner(
                    userId = request.userId,
                    todoId = acceptedId,
                    userEnergy = savedRec.energyLevel,
                    workingTimeLeft = savedRec.workingTimeLeft,
                    accepted = true,
                    rejectReason = null,
                    score = savedRec.score,
                    timeUntilDue = savedRec.timeUntilDue,
                    effort = savedRec.effort
                )
                // Nur den Planner ansprechen, der dieses Todo vorgeschlagen hat
                planners.firstOrNull { it.plannerType == savedRec.plannerType }
                    ?.processUserFeedback(feedback)
            }
        }

        // 3. Abgelehnte Todos verarbeiten
        request.rejectedTodos.forEach { rejected ->
            val todo = todoRepository.findById(rejected.todoId).orElse(null)
            if (todo != null) {
                todo.cooldownTurns = MAX_COOLDOWN_TURNS + 1
                todoRepository.save(todo)
            }

            val savedRec = recommendationMap[rejected.todoId]
            if (savedRec != null) {
                val feedback = FeedbackForPlanner(
                    userId = request.userId,
                    todoId = rejected.todoId,
                    userEnergy = savedRec.energyLevel,
                    workingTimeLeft = savedRec.workingTimeLeft,
                    accepted = false,
                    rejectReason = rejected.rejectReason,
                    score = savedRec.score,
                    timeUntilDue = savedRec.timeUntilDue,
                    effort = savedRec.effort
                )
                // Nur den jeweiligen Planner ansprechen
                planners.firstOrNull { it.plannerType == savedRec.plannerType }
                    ?.processUserFeedback(feedback)
            }
        }
    }

    /**
     * Reines Datenbank-Snooze – schickt KEIN Feedback an die Planner!
     */
    @Transactional
    fun snoozeTodoInBackend(todoId: String, durationInMin: Int): TodoEntity? {
        val todo = todoRepository.findById(todoId).orElse(null) ?: return null

        val millisInFuture = durationInMin * 60 * 1000L
        todo.snoozedUntil = System.currentTimeMillis() + millisInFuture

        return todoRepository.save(todo)
    }
}

