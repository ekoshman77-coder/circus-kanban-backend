package com.backend.todo_api.services

import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserAiPreference
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.UserAiPreferenceRepository
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.FeedbackForPlanner
import com.backend.todo_api.model.FocusType
import com.backend.todo_api.model.PlannerRecomendation
import com.backend.todo_api.model.PlannerType
import com.backend.todo_api.model.PreferenceType
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalTime

@Service
class BayesPlannerService(
    private val plannerSettingsRepository: PlannerSettingsRepository, // ⚡ NEU: Hier kommen die Regler-Werte her!
    private val focusPredictorService: FocusPredictorService,
    private val preferenceRepository: UserAiPreferenceRepository,
): PlannerInterface {
    override val plannerType: PlannerType
        get() = PlannerType.BAYES

    companion object {
        const val MAX_COOLDOWN_TURNS = 3
    }

    private fun isUrgent(todo: TodoEntity): Boolean {
        if (todo.dueDate == null) return false
        val fortyEightHoursInMs = 48 * 60 * 60 * 1000
        return (todo.dueDate - System.currentTimeMillis()) <= fortyEightHoursInMs
    }


    @Transactional
    override fun processUserFeedback(feedback: FeedbackForPlanner) {
//        val todo = todoRepository.findById(todoId).orElse(null) ?: return
//
//        if (accepted) {
//            // 🎉 BELOHNUNG: Nutzer hat die Aufgabe gestartet!
//            // Wir belohnen die Aufwands-Kategorie bei dieser Energie
//            val effortCategory = if (todo.effort > 3) "aufwendig" else "leicht"
//            updateScore(userId, currentEnergy, PreferenceType.EFFORT, effortCategory, plusPoints = 5)
//
//            todo.cooldownTurns = 0
//            todoRepository.save(todo)
//        } else {
//            todo.cooldownTurns = MAX_COOLDOWN_TURNS + 1
//            todoRepository.save(todo)
//            // 👎 BESTRAFUNG: Nutzer hat abgelehnt. Jetzt schauen wir, WARUM:
//            when (rejectReason) {
//                "too_heavy" -> {
//                    // Es war zu schwer für die aktuelle Energie
//                    val effortCategory = if (todo.effort > 3) "aufwendig" else "leicht"
//                    updateScore(userId, currentEnergy, PreferenceType.EFFORT,", effortCategory, plusPoints = -10)
//                }
//
//                "too_long" -> {
//                    // Es dauert zu lange (unabhängig von der Energie -> "any")
//                    val timeCategory = if (todo.effort > 3) "lang" else "kurz"
//                    updateScore(userId, EnergyLevel.ANY, PreferenceType.TIME, timeCategory, plusPoints = -8)
//                }
//
//                "no_motivation" -> {
//                    // Keine Lust auf diesen spezifischen Typ (z.B. Tag/Kategorie des To-Dos)
//                    // Angenommen dein Todo hat ein Feld 'category' oder 'tag' (z.B. "Doku")
//                    val todoTag = todo.category ?: "Standard"
//                    updateScore(userId, EnergyLevel.ANY, PreferenceType.MOTIVATION, "Tag:$todoTag", plusPoints = -15)
//                }
//            }
//        }
    }

    // Hilfsmethode: Sucht den Eintrag oder legt ihn neu an und verändert den Score
    private fun updateScore(userId: String, energy: EnergyLevel, type: PreferenceType, value: String, plusPoints: Int) {
        val preference = preferenceRepository.findByUserIdAndUserEnergyAndPreferenceTypeAndPreferenceValue(
            userId, energy, type, value
        ) ?: UserAiPreference(userId = userId, userEnergy = energy, preferenceType = type, preferenceValue = value)

        preference.score += plusPoints
        preferenceRepository.save(preference)
    }

    @Transactional
    override fun calculatePerfectRecommendation(
        userId: String,
        candidates: List<TodoEntity>,
        userEnergy: EnergyLevel,
        workingTimeLeft: Long): PlannerRecomendation? {

        // 🎯 SCHRITT 3: KI-Filterung (Fokus & Energie)
        val finalPool = applyAiFocusFiltering(candidates, userEnergy)

        val settings = plannerSettingsRepository.findById(userId).orElseGet {
            PlannerSettingsEntity(id = userId, defaultWorkingHours = 8, primeTimeStartHour = 10, primeTimeEndHour = 18)
        }
        // 📊 SCHRITT 4: Scoring berechnen & Gewinner ermitteln
        val userPreferences = preferenceRepository.findByUserId(userId)
        val winnerTodoWithScore = calculateScoresAndGetWinner(finalPool, userEnergy, workingTimeLeft, settings, userPreferences)

        if (winnerTodoWithScore == null) {
            return null
        }

        val todo = winnerTodoWithScore.first
        val score = winnerTodoWithScore.second

        // 🚀 SCHRITT 5: Ergebnis-Zuweisung
        val isRecherche = userEnergy == EnergyLevel.LOW && workingTimeLeft <= 2.0 && (todo.effort ?: 0) > 3
        val finalReasonCode = when {
            isRecherche -> "LOW_ENERGY_SHORT_TIME"
                isUrgent(todo) -> "URGENT_DEADLINE"
            else -> "DEFAULT"
        }
         return PlannerRecomendation(
            PlannerType.BAYES,
            todoId = todo.id,
            score = score.toDouble(),
            energyLevel = userEnergy,
            if (todo.dueDate == null) 0
                          else {
                               val currentTime = System.currentTimeMillis()
                               todo.dueDate - currentTime
                          },
            workingTimeLeft,
            effort = todo.effort,
             reason = finalReasonCode
        )
     }

    // =============================================================================
    // 🛠️ PRIVATE HILFSMETHODEN FÜR DAS REFACTORING
    // =============================================================================


    private fun applyAiFocusFiltering(pool: List<TodoEntity>, userEnergy: EnergyLevel): List<TodoEntity> {
        var filteredPool = pool
        if (userEnergy == EnergyLevel.LOW) {
            filteredPool = pool.filter { focusPredictorService.predict(it.task) == FocusType.LOW_FOCUS }
        } else if (userEnergy == EnergyLevel.HIGH) {
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
        userEnergy: EnergyLevel,
        workingTimeLeft: Long,
        settings: PlannerSettingsEntity,
        userPreferences: List<UserAiPreference>
    ): Pair<TodoEntity, Int>? {
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
            if (userEnergy == EnergyLevel.LOW && workingTimeLeft <= 2.0 && isHeavy) {
                score += 150
            } else {
                if (isInsidePrimeTime && userEnergy != EnergyLevel.LOW) {
                    if (isHeavy) score += 30 else score += 10
                } else {
                    if (!isHeavy) score += 30 else score += 10
                }
                if (userEnergy != EnergyLevel.LOW && (todo.effort ?: 0) <= workingTimeLeft) {
                    score += 20
                }
            }

            // Benutzereinstellungen einrechnen
            val effortCategory = if (isHeavy) "aufwendig" else "leicht"
            val timeCategory = if (isHeavy) "lang" else "kurz"
            val todoTag = todo.category ?: "Standard"

            userPreferences.forEach { pref ->
                when (pref.preferenceType) {
                    PreferenceType.EFFORT -> {
                        if (pref.userEnergy == userEnergy && pref.preferenceValue == effortCategory) score += pref.score
                    }
                    PreferenceType.TIME -> {
                        if (pref.preferenceValue == timeCategory) score += pref.score
                    }

                    PreferenceType.MOTIVATION -> {
                        if (pref.preferenceValue == "Tag:$todoTag") {
                            if (pref.score < -30) score -= 500 else score += pref.score
                        }
                    }
                }
            }

            Pair(todo, score)
        }

        return scoredTodos.maxByOrNull { it.second }
    }

//    @Transactional
//    override fun snoozeTodoInBackend(todoId: String, snoozeDurationInMinutes: Int): TodoEntity? {
//        val todo = todoRepository.findById(todoId).orElse(null) ?: return null
//
//        // Aktuelle Zeit + X Minuten in Millisekunden rechnen
//        val millisInFuture = snoozeDurationInMinutes * 60 * 1000L
//        todo.snoozedUntil = System.currentTimeMillis() + millisInFuture
//
//        val currentDateTime = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
//        val snoozedUntilDateTime = java.time.Instant.ofEpochMilli(todo.snoozedUntil)
//        println("⏰ --- SNOOZE ZEITZONEN-CHECK ---")
//        println("Aktuelle Serverzeit UTC: $currentDateTime")
//        println("Ticket gesnoozed bis UTC: $snoozedUntilDateTime")
//        println("Roher Long-Wert in DB: ${todo.snoozedUntil}")
//        println("---------------------------------")
//
//        return todoRepository.save(todo)
//    }
}
