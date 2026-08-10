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
): PlannerInterface {

    companion object {
        const val MAX_COOLDOWN_TURNS = 3
    }

    private fun isUrgent(todo: TodoEntity): Boolean {
        if (todo.dueDate == null) return false
        val fortyEightHoursInMs = 48 * 60 * 60 * 1000
        return (todo.dueDate - System.currentTimeMillis()) <= fortyEightHoursInMs
    }

    @Transactional
    override fun processUserFeedback(
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
    override fun calculatePerfectRecommendation(
        userId: String, userEnergy: String, workingTimeLeft: Double
    ): RecommendedTodoResponse {
        validateUserExists(userId, userRepository)


        val settings = plannerSettingsRepository.findById(userId).orElseGet {
            PlannerSettingsEntity(id = userId, defaultWorkingHours = 8, primeTimeStartHour = 10, primeTimeEndHour = 18)
        }

        val matchPool = todoRepository.findActivePlannerTodosForUser(userId)

        if (matchPool.isEmpty()) {
            return RecommendedTodoResponse(null, "", "")
        }

        // ⏱️ SCHRITT 1: Snooze-Filterung & Sofort-Fallback
        val currentTime = System.currentTimeMillis()

        println("🔍 --- EMPFEHLUNG FILTER-CHECK ---")
        println("Jetzt-Zeit beim Filtern UTC: ${java.time.Instant.ofEpochMilli(currentTime)}")

        matchPool.forEach {
            val ticketTime = java.time.Instant.ofEpochMilli(it.snoozedUntil)
            val isStillSnoozed = it.snoozedUntil > currentTime
            println("Task: '${it.task}' | SnoozedUntil UTC: $ticketTime | Ist gesnoozed? $isStillSnoozed")
        }

        val nonSnoozedPool = matchPool.filter { it.snoozedUntil <= currentTime }



        if (nonSnoozedPool.isEmpty()) {
            val earliestWakeupTodo = matchPool.minByOrNull { it.snoozedUntil }
            return RecommendedTodoResponse(
                todo = earliestWakeupTodo?.let { todoService.mapToDto(it) },
                modeCode = "STANDARD",
                reasonCode = "ALL_SNOOZED"
            )
        }

        // 🌬️ SCHRITT 2: Cooldown-Dekrementierung & Filterung
        decrementCooldowns(nonSnoozedPool)
        val coolDownPool = filterPoolByCooldown(nonSnoozedPool)

        // 🎯 SCHRITT 3: KI-Filterung (Fokus & Energie)
        val finalPool = applyAiFocusFiltering(coolDownPool, userEnergy)

        // 📊 SCHRITT 4: Scoring berechnen & Gewinner ermitteln
        val userPreferences = preferenceRepository.findByUserId(userId)
        val winnerTodo = calculateScoresAndGetWinner(finalPool, userEnergy, workingTimeLeft, settings, userPreferences)

        if (winnerTodo == null) {
            return RecommendedTodoResponse(todo = null, modeCode = "CLEAN_SLATE", reasonCode = "NO_TODOS_LEFT")
        }

        // 🚀 SCHRITT 5: Ergebnis-Zuweisung
        val isRecherche = userEnergy == "low" && workingTimeLeft <= 2.0 && (winnerTodo.effort ?: 0) > 3
        val finalReasonCode = when {
            isRecherche -> "LOW_ENERGY_SHORT_TIME"
                isUrgent(winnerTodo) -> "URGENT_DEADLINE"
            else -> "DEFAULT"
        }

        return RecommendedTodoResponse(
            todo = todoService.mapToDto(winnerTodo),
        modeCode = if (isRecherche) "RECHERCHE" else "STANDARD",
        reasonCode = finalReasonCode
        )
    }

    // =============================================================================
    // 🛠️ PRIVATE HILFSMETHODEN FÜR DAS REFACTORING
    // =============================================================================

    private fun decrementCooldowns(pool: List<TodoEntity>) {
        pool.forEach { todo ->
            if (todo.cooldownTurns > 0) {
                todo.cooldownTurns -= 1
                todoRepository.save(todo)
            }
        }
    }

    private fun filterPoolByCooldown(pool: List<TodoEntity>): List<TodoEntity> {
        for (cooldownLevel in 0..MAX_COOLDOWN_TURNS) {
            val candidates = pool.filter { it.cooldownTurns == cooldownLevel }
            if (candidates.isNotEmpty()) {
                return candidates
            }
        }
        return pool
    }

    private fun applyAiFocusFiltering(pool: List<TodoEntity>, userEnergy: String): List<TodoEntity> {
        var filteredPool = pool
        if (userEnergy == "low") {
            filteredPool = pool.filter { focusPredictorService.predict(it.task) == FocusType.LOW_FOCUS }
        } else if (userEnergy == "high") {
            val highFocusTasks = pool.filter { focusPredictorService.predict(it.task) == FocusType.HIGH_FOCUS }
            if (highFocusTasks.isNotEmpty()) {
                filteredPool = highFocusTasks
            }
        }
        // Bugfix im Fallback: Wenn filteredPool leer ist, nimm das originale pool!
        return if (filteredPool.isEmpty()) pool else filteredPool
    }

    private fun calculateScoresAndGetWinner(
        pool: List<TodoEntity>,
        userEnergy: String,
        workingTimeLeft: Double,
        settings: PlannerSettingsEntity,
        userPreferences: List<UserAiPreference>
    ): TodoEntity? {
        val currentHour = LocalTime.now().hour
        val isInsidePrimeTime = currentHour in settings.primeTimeStartHour..settings.primeTimeEndHour

        val scoredTodos = pool.map { todo ->
            var score = 0
            val isHeavy = (todo.effort ?: 0) > 3

            // Dringlichkeit
            if (isUrgent(todo)) {
                score += 100
            }

            // Kombi-Score oder Zeitfenster-Score
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

            // Benutzereinstellungen einrechnen
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

        return scoredTodos.maxByOrNull { it.second }?.first
    }

    @Transactional
    override fun snoozeTodoInBackend(todoId: String, snoozeDurationInMinutes: Int): TodoEntity? {
        val todo = todoRepository.findById(todoId).orElse(null) ?: return null

        // Aktuelle Zeit + X Minuten in Millisekunden rechnen
        val millisInFuture = snoozeDurationInMinutes * 60 * 1000L
        todo.snoozedUntil = System.currentTimeMillis() + millisInFuture

        val currentDateTime = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        val snoozedUntilDateTime = java.time.Instant.ofEpochMilli(todo.snoozedUntil)
        println("⏰ --- SNOOZE ZEITZONEN-CHECK ---")
        println("Aktuelle Serverzeit UTC: $currentDateTime")
        println("Ticket gesnoozed bis UTC: $snoozedUntilDateTime")
        println("Roher Long-Wert in DB: ${todo.snoozedUntil}")
        println("---------------------------------")

        return todoRepository.save(todo)
    }
}
