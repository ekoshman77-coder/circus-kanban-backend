package com.backend.todo_api.services

import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserAiPreference
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserAiPreferenceRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.RecommendedTodoResponse
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.validation.validateUserExists
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalTime

@Service
class SmartPlannerService(
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val plannerSettingsRepository: PlannerSettingsRepository, // ⚡ NEU: Hier kommen die Regler-Werte her!
    private val focusPredictorService: FocusPredictorService,
    private val preferenceRepository: UserAiPreferenceRepository,
    private val todoService: TodoService
) {

    companion object {
        const val MAX_COOLDOWN_TURNS = 3
    }

    private fun isUrgent(todo: TodoEntity): Boolean {
        if (todo.dueDate == null) return false
        val fortyEightHoursInMs = 48 * 60 * 60 * 1000
        return (todo.dueDate - System.currentTimeMillis()) <= fortyEightHoursInMs
    }

    @Transactional
    fun processUserFeedback(
        userId: String, todoId: String, accepted: Boolean, rejectReason: String?, currentEnergy: String
    ) {
        val todo = todoRepository.findById(todoId).orElse(null) ?: return

        if (accepted) {
            // 🎉 BELOHNUNG: Nutzer hat die Aufgabe gestartet!
            // Wir belohnen die Aufwands-Kategorie bei dieser Energie
            val effortCategory = if (todo.effort > 3) "aufwendig" else "leicht"
            updateScore(userId, currentEnergy, "EFFORT", effortCategory, plusPoints = 5)

            todo.cooldownTurns = 0
            todoRepository.save(todo)
        } else {
            todo.cooldownTurns = MAX_COOLDOWN_TURNS + 1
            todoRepository.save(todo)
            // 👎 BESTRAFUNG: Nutzer hat abgelehnt. Jetzt schauen wir, WARUM:
            when (rejectReason) {
                "too_heavy" -> {
                    // Es war zu schwer für die aktuelle Energie
                    val effortCategory = if (todo.effort > 3) "aufwendig" else "leicht"
                    updateScore(userId, currentEnergy, "EFFORT", effortCategory, plusPoints = -10)
                }

                "too_long" -> {
                    // Es dauert zu lange (unabhängig von der Energie -> "any")
                    val timeCategory = if (todo.effort > 3) "lang" else "kurz"
                    updateScore(userId, "any", "TIME", timeCategory, plusPoints = -8)
                }

                "no_motivation" -> {
                    // Keine Lust auf diesen spezifischen Typ (z.B. Tag/Kategorie des To-Dos)
                    // Angenommen dein Todo hat ein Feld 'category' oder 'tag' (z.B. "Doku")
                    val todoTag = todo.category ?: "Standard"
                    updateScore(userId, "any", "MOTIVATION", "Tag:$todoTag", plusPoints = -15)
                }
            }
        }
    }

    // Hilfsmethode: Sucht den Eintrag oder legt ihn neu an und verändert den Score
    private fun updateScore(userId: String, energy: String, type: String, value: String, plusPoints: Int) {
        val preference = preferenceRepository.findByUserIdAndUserEnergyAndPreferenceTypeAndPreferenceValue(
            userId, energy, type, value
        ) ?: UserAiPreference(userId = userId, userEnergy = energy, preferenceType = type, preferenceValue = value)

        preference.score += plusPoints
        preferenceRepository.save(preference)
    }

    @Transactional
    fun calculatePerfectRecommendation(
        userId: String, userEnergy: String, workingTimeLeft: Double
    ): RecommendedTodoResponse {
        validateUserExists(userId, userRepository)

        val settings = plannerSettingsRepository.findById(userId).orElseGet {
            PlannerSettingsEntity(id = userId, defaultWorkingHours = 8, primeTimeStartHour = 10, primeTimeEndHour = 18)
        }

        // 1. Punktgenau nur DEINE offenen Aufgaben aus der DB holen
        val matchPool = todoRepository.findActivePlannerTodosForUser(userId)
        if (matchPool.isEmpty()) {
            return RecommendedTodoResponse(null, "", "")
        }

        matchPool.forEach { todo ->
            if (todo.cooldownTurns > 0) {
                todo.cooldownTurns -= 1
                todoRepository.save(todo) // In der DB aktualisieren
            }
        }

        val coolDownPool = filterPoolByCooldown(matchPool)

// 🎯 SCHRITT B: ERST DANACH KI-FILTERUNG (Energie & Fokus) auf der bereinigten Liste
        var filteredPool = coolDownPool
        if (userEnergy == "low") {
            filteredPool = coolDownPool.filter { focusPredictorService.predict(it.task) == FocusType.LOW_FOCUS }
        } else if (userEnergy == "high") {
            val highFocusTasks = coolDownPool.filter { focusPredictorService.predict(it.task) == FocusType.HIGH_FOCUS }
            if (highFocusTasks.isNotEmpty()) {
                filteredPool = highFocusTasks
            }
        }

      // Wenn die KI-Filterung den finalPool komplett leeren würde (z.B. nur High-Focus da bei Low-Energy),
      // nutzen wir den finalPool als Fallback, damit der User irgendwas bekommt.
        val finalPool = if (filteredPool.isEmpty()) filteredPool else filteredPool
        // 5. SCORING BERECHNEN

        val userPreferences = preferenceRepository.findByUserId(userId)
        val currentHour = LocalTime.now().hour
        val isInsidePrimeTime = currentHour in settings.primeTimeStartHour..settings.primeTimeEndHour

        val scoredTodos = finalPool.map { todo ->
            var score = 0
            val isHeavy = (todo.effort ?: 0) > 3

            if (isUrgent(todo)) {
                score += 100
            }

            if (userEnergy == "low" && workingTimeLeft <= 2.0 && isHeavy) {
                score += 150
            } else {
                if (isInsidePrimeTime && userEnergy != "low") {
                    if (isHeavy) score += 30 else score += 10
                } else {
                    if (!isHeavy) score += 30 else score += 10
                }
                if (userEnergy != "low" && (todo.effort ?: 0) <= workingTimeLeft) {
                    score += 20
                }
            }

            val effortCategory = if (isHeavy) "aufwendig" else "leicht"
            val timeCategory = if (isHeavy) "lang" else "kurz"
            val todoTag = todo.category ?: "Standard"

            userPreferences.forEach { pref ->
                when (pref.preferenceType) {
                    "EFFORT" -> {
                        if (pref.userEnergy == userEnergy && pref.preferenceValue == effortCategory) score += pref.score
                    }

                    "TIME" -> {
                        if (pref.preferenceValue == timeCategory) score += pref.score
                    }

                    "MOTIVATION" -> {
                        if (pref.preferenceValue == "Tag:$todoTag") {
                            if (pref.score < -30) score -= 500 else score += pref.score
                        }
                    }
                }
            }

            Pair(todo, score)
        }

        val winnerTodo = scoredTodos.maxByOrNull { it.second }?.first

        if (winnerTodo == null) {
            return RecommendedTodoResponse(todo = null, modeCode = "CLEAN_SLATE", reasonCode = "NO_TODOS_LEFT")
        }

        val isRecherche = userEnergy == "low" && workingTimeLeft <= 2.0 && (winnerTodo.effort ?: 0) > 3

        // 2. 🌀 DER DECAY-LOOP: Zähler für alle deine Zettel runterfahren
        matchPool.forEach { todo ->
            if (todo.cooldownTurns > 0) {
                todo.cooldownTurns -= 1
                todoRepository.save(todo)
            }
        }

        return if (isRecherche) {
            RecommendedTodoResponse(
                todo = todoService.mapToDto(winnerTodo), modeCode = "RECHERCHE", reasonCode = "LOW_ENERGY_SHORT_TIME"
            )
        } else {
            RecommendedTodoResponse(
                todo = todoService.mapToDto(winnerTodo), modeCode = "STANDARD", reasonCode = "DEFAULT"
            )
        }
    }

    /**
     * Hilfsmethode: Filtert stufenweise nach dem Cooldown-Level.
     */
    private fun filterPoolByCooldown(pool: List<TodoEntity>): List<TodoEntity> {
        println("🔍 [PLANNER DEBUG] Starte Filterung. Pool-Größe: ${pool.size}")
        for (cooldownLevel in 0..MAX_COOLDOWN_TURNS) {
            val candidates = pool.filter { it.cooldownTurns == cooldownLevel }
            if (candidates.isNotEmpty()) {
                println("   🎯 Gewähltes Cooldown-Level: $cooldownLevel (Anzahl: ${candidates.size})")
                return candidates
            }
        }
        return pool
    }
}
