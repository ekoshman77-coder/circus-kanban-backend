package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.FeedbackForPlanner
import com.backend.todo_api.model.PlannerRecomendation
import com.backend.todo_api.model.PlannerType
import com.backend.todo_api.services.neural.NeuralNetwork
import org.springframework.stereotype.Service

@Service
class NeuralPlannerService(
    private val neuralNetwork: NeuralNetwork
) : PlannerInterface {

    override val plannerType: PlannerType
        get() = PlannerType.NEURAL

    override fun calculatePerfectRecommendation(
        userId: String,
        candidates: List<TodoEntity>,
        userEnergy: EnergyLevel,
        workingTimeLeft: Long
    ): PlannerRecomendation? {

        if (candidates.isEmpty()) return null

        // 1. Alle Candidates durch das Neuronale Netz bewerten lassen
        val scoredCandidates = candidates.map { todo ->
            val inputs = extractAndNormalizeInputs(todo, userEnergy, workingTimeLeft)
            val score = neuralNetwork.predict(inputs)
            println("🤖 [NEURAL] Todo: '${todo.task}' (ID: ${todo.id}) -> Score: $score")
            Pair(todo, score)
        }

        // 2. Das Todo mit dem höchsten KI-Score finden
        val winner = scoredCandidates.maxByOrNull { it.second } ?: return null
        val bestTodo = winner.first
        val bestScore = winner.second

        val currentTime = System.currentTimeMillis()
        val timeUntilDue = if (bestTodo.dueDate > 0) bestTodo.dueDate - currentTime else 0L

        // 3. Ergebnis als PlannerRecomendation an den Coordinator zurückgeben
        return PlannerRecomendation(
            plannerType = PlannerType.NEURAL,
            todoId = bestTodo.id,
            score = bestScore,
            energyLevel = userEnergy,
            timeUntilDue = timeUntilDue,
            workingTimeLeft = workingTimeLeft,
            effort = bestTodo.effort,
            reason = "NEURAL_NET_BEST_MATCH"
        )
    }

    override fun processUserFeedback(feedback: FeedbackForPlanner) {
        // Feedback verarbeiten: Backpropagation/Training auslösen
        val target = if (feedback.accepted) 1.0 else 0.0

        val inputs = doubleArrayOf(
            normalizeDueDate(feedback.timeUntilDue),
            normalizeEffort(feedback.effort),
            normalizeEnergy(feedback.userEnergy),
            normalizeWorkingTime(feedback.workingTimeLeft)
        )
        println("🏋️ [TRAIN] Feedback verarbeitet für Todo-ID ${feedback.todoId} | Target: $target")
        // Netz mit dem Feedback des Nutzers trainieren
        neuralNetwork.train(inputs, target)
    }

    // =============================================================================
    // 🛠️ HILFSMETHODEN FÜR DIE NORMALISIERUNG (0.0 bis 1.0)
    // =============================================================================

    private fun extractAndNormalizeInputs(
        todo: TodoEntity,
        userEnergy: EnergyLevel,
        workingTimeLeft: Long
    ): DoubleArray {
        val currentTime = System.currentTimeMillis()
        val timeUntilDue = if (todo.dueDate > 0) todo.dueDate - currentTime else Long.MAX_VALUE

        val normDueDate = normalizeDueDate(timeUntilDue)
        val normEffort = normalizeEffort(todo.effort)
        val normEnergy = normalizeEnergy(userEnergy)

        // 💡 NEU & DYNAMISCH: Passt der Aufwand zur aktuellen Energie? (1.0 = perfektes Match)
        val energyEffortMatch = 1.0 - kotlin.math.abs(normEnergy - normEffort)

        return doubleArrayOf(
            normDueDate,                                    // Input 1: Fälligkeit (0.0 bis 1.0)
            normEffort,                                     // Input 2: Aufwand (0.0 bis 1.0)
            energyEffortMatch,                              // Input 3: Energy-Match (Jedes Todo hat einen ANDEREN Wert!)
            normalizeWorkingTime(workingTimeLeft)     // Input 4: Passt Todo in verbleibende Zeit?
        )
    }

    // 1. Due Date (Je näher an 0 ms, desto näher an 1.0; skaliert auf z.B. 48 Stunden)
    private fun normalizeDueDate(timeUntilDueMillis: Long): Double {
        if (timeUntilDueMillis <= 0) return 1.0 // Bereits fällig/überfällig
        val maxHorizon = 48 * 60 * 60 * 1000L // 48 Stunden in Ms
        val clamped = timeUntilDueMillis.coerceAtMost(maxHorizon)
        return (1.0 - (clamped.toDouble() / maxHorizon)).coerceIn(0.0, 1.0)
    }

    // 2. Effort (Skaliert von 0 bis max. 10 Punkte)
    private fun normalizeEffort(effort: Int): Double {
        val maxEffort = 10.0
        return (effort.toDouble() / maxEffort).coerceIn(0.0, 1.0)
    }

    // 3. User Energy Enum in Zahl umwandeln
    private fun normalizeEnergy(energy: EnergyLevel): Double {
        return when (energy) {
            EnergyLevel.HIGH -> 1.0
            EnergyLevel.MEDIUM -> 0.66
            EnergyLevel.LOW -> 0.33
            else -> 0.5
        }
    }

    // Skaliert z. B. verbleibende Arbeitszeit in Minuten (max. 8 Stunden = 480 Min)
    private fun normalizeWorkingTime(workingTimeLeftMinutes: Long): Double {
        val maxMinutes = 480.0 // 8 Stunden
        val normalizedTimeLeft = (workingTimeLeftMinutes.toDouble() / maxMinutes).coerceIn(0.0, 1.0)

        // Gibt einen Wert zurück, wie gut die Restzeit für den Effort noch reicht
        return normalizedTimeLeft
    }
}