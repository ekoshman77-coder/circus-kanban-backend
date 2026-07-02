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
    private val preferenceRepository: UserAiPreferenceRepository
) {
    private fun isUrgent(todo: TodoEntity): Boolean {
        if (todo.dueDate == null) return false
        val fortyEightHoursInMs = 48 * 60 * 60 * 1000
        return (todo.dueDate - System.currentTimeMillis()) <= fortyEightHoursInMs
    }

    @Transactional
    fun processUserFeedback(userId: String, todoId: String, accepted: Boolean, rejectReason: String?, currentEnergy: String) {
        val todo = todoRepository.findById(todoId).orElse(null) ?: return

        if (accepted) {
            // 🎉 BELOHNUNG: Nutzer hat die Aufgabe gestartet!
            // Wir belohnen die Aufwands-Kategorie bei dieser Energie
            val effortCategory = if (todo.effort > 3) "aufwendig" else "leicht"
            updateScore(userId, currentEnergy, "EFFORT", effortCategory, plusPoints = 5)
        } else {
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

    fun calculatePerfectRecommendation(userId: String, userEnergy: String, workingTimeLeft: Double): RecommendedTodoResponse {
        validateUserExists(userId, userRepository)

        val settings = plannerSettingsRepository.findById(userId).orElseGet {
            PlannerSettingsEntity(id = userId, defaultWorkingHours = 8, primeTimeStartHour = 10, primeTimeEndHour = 18)
        }

        // ⚡ FEHLER KORRIGIERT: Wir holen ALLE offenen To-Dos, ohne sie wegen der Zeit hart zu blockieren!
        val matchPool = todoRepository.findByUserIdAndDoneFalse(userId) ?: emptyList()

        if (matchPool.isEmpty()) return RecommendedTodoResponse(null, "", "")

        // --- KI-FILTERUNG (Deine Fokus-Logik bleibt) ---
        var filteredPool = matchPool
        if (userEnergy == "low") {
            filteredPool = matchPool.filter { focusPredictorService.predict(it.task) == FocusType.LOW_FOCUS }
        } else if (userEnergy == "high") {
            val highFocusTasks = matchPool.filter { focusPredictorService.predict(it.task) == FocusType.HIGH_FOCUS }
            if (highFocusTasks.isNotEmpty()) {
                filteredPool = highFocusTasks
            }
        }

        // Falls durch den Fokus-Filter alles leer ist, fallen wir auf den Gesamtpool zurück
        val finalPool = if (filteredPool.isEmpty()) matchPool else filteredPool

        val userPreferences = preferenceRepository.findByUserId(userId)
        val currentHour = LocalTime.now().hour
        val isInsidePrimeTime = currentHour in settings.primeTimeStartHour..settings.primeTimeEndHour

        val scoredTodos = finalPool.map { todo ->
            var score = 0
            val isHeavy = (todo.effort ?: 0) > 3

            // A) Basis-Score durch Dringlichkeit
            if (isUrgent(todo)) {
                score += 100
            }

            // B) 🧠 DEIN NEUER RECHERCHE-MODUS-ALGORITHMUS
            if (userEnergy == "low" && workingTimeLeft <= 2.0 && isHeavy) {
                // Genau dein Szenario: Wenig Kraft, kurz vor Feierabend, großer Brocken!
                // Wir geben einen fetten Bonus für das "Über-Nacht-Sacken-lassen"
                score += 150
                println("🧠 Inkubations-Effekt getriggert für: ${todo.task}. Ab in den Recherche-Modus!")
            } else {
                // Normales biologisches Scoring
                if (isInsidePrimeTime && userEnergy != "low") {
                    if (isHeavy) score += 30 else score += 10
                } else {
                    if (!isHeavy) score += 30 else score += 10
                }

                // Sanfter Zeit-Bonus: Wenn man noch viel Energie hat, belohnen wir Aufgaben, die in die Restzeit passen
                if (userEnergy != "low" && (todo.effort ?: 0) <= workingTimeLeft) {
                    score += 20
                }
            }

            // C) KI-GEWICHTUNG (Feedback-Punkte aufrechnen)
            val effortCategory = if (isHeavy) "aufwendig" else "leicht"
            val timeCategory = if (isHeavy) "lang" else "kurz"
            val todoTag = todo.category ?: "Standard"

            userPreferences.forEach { pref ->
                when (pref.preferenceType) {
                    "EFFORT" -> {
                        if (pref.userEnergy == userEnergy && pref.preferenceValue == effortCategory) {
                            score += pref.score
                        }
                    }
                    "TIME" -> {
                        if (pref.preferenceValue == timeCategory) {
                            score += pref.score
                        }
                    }
                    "MOTIVATION" -> {
                        if (pref.preferenceValue == "Tag:$todoTag") {
                            if (pref.score < -30) {
                                score -= 500 // Frust-Hammer 🔨
                            } else {
                                score += pref.score
                            }
                        }
                    }
                }
            }

            Pair(todo, score)
        }

        val winnerTodo = scoredTodos.maxByOrNull { it.second }?.first

        if (winnerTodo == null) {
            return RecommendedTodoResponse(
                todo = null,
                modeCode = "CLEAN_SLATE",
                reasonCode = "NO_TODOS_LEFT"
            )
        }

// Prüfen, ob dein genialer Inkubations-Effekt (Recherche-Modus) zutrifft
        val isRecherche = userEnergy == "low" && workingTimeLeft <= 2.0 && (winnerTodo.effort ?: 0) > 3

        return if (isRecherche) {
            RecommendedTodoResponse(
                todo = winnerTodo.toDto(),
                modeCode = "RECHERCHE",
                reasonCode = "LOW_ENERGY_SHORT_TIME"
            )
        } else {
            RecommendedTodoResponse(
                todo = winnerTodo.toDto(),
                modeCode = "STANDARD",
                reasonCode = "DEFAULT"
            )
        }
    }
}