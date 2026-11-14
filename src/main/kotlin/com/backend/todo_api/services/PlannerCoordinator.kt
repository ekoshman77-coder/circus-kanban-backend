package com.backend.todo_api.services

import com.backend.todo_api.data.entity.PlannerRecommendationEntity
import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.RecommendationRoundEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.RecommendationRoundRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.BlueprintSlotDto
import com.backend.todo_api.dto.DailyBlueprintResponse
import com.backend.todo_api.dto.MorningRecommendationItem
import com.backend.todo_api.dto.MorningRecommendationsResponse
import com.backend.todo_api.dto.PlannerFeedbackRequest
import com.backend.todo_api.dto.PlannerRecommendationDetail
import com.backend.todo_api.dto.PlannerRecommendationsResponse
import com.backend.todo_api.dto.RecommendedTodoResponse
import com.backend.todo_api.dto.SlotRecommendationDto
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.FeedbackForPlanner
import com.backend.todo_api.model.PlannerType
import com.backend.todo_api.model.enums.TodoGroupChoice
import com.backend.todo_api.validation.validateUserExists
import jakarta.transaction.Transactional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class PlannerCoordinator(
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val recommendationRoundRepository: RecommendationRoundRepository,
    private val planners: List<PlannerInterface>,
    private val plannerSettingsRepository: PlannerSettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val lock = ReentrantLock()

    companion object {
        private const val MAX_COOLDOWN_TURNS = 3
    }

    @Transactional
    fun getRecommendations(
        userId: String,
        userEnergy: EnergyLevel,
        workingTimeLeft: Long
    ): PlannerRecommendationsResponse = lock.withLock {

        validateUserExists(userId, userRepository)

        // 1. Alle aktiven Todos holen
        val matchPool = todoRepository.findOpenProjectTodosForUser(userId)

        // 2. Snooze-Filter
        val nonSnoozedPool = filterAvailableTodos(matchPool)

        // 3. Alle Todos gesnoozed → direktes Fallback.
        //    Keine Planner und keine Recommendation-Round.
        if (nonSnoozedPool.isEmpty()) {
            val earliestWakeupTodo = matchPool.minByOrNull { it.snoozedUntil }

            if (earliestWakeupTodo == null) {
                return PlannerRecommendationsResponse(
                    roundId = "",
                    recommendations = emptyList()
                )
            }

            return PlannerRecommendationsResponse(
                roundId = "",
                recommendations = listOf(
                    RecommendedTodoResponse(
                        todoId = earliestWakeupTodo.id ,
                        plannerDetails = emptyList(), // Keine KI beteiligt!
                        modeCode = "ALL_SNOOZED"      // 🎯 Jetzt glasklar!
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
            createdAt = clock.millis()
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
        val groupedRecommendations = plannerRecommendations.groupBy { it.todoId }

        val recommendations = groupedRecommendations.map { (todoId, plannerRecs) ->

            val details = plannerRecs.map { rec ->
                PlannerRecommendationDetail(
                    plannerType = rec.plannerType,
                    score = rec.score,
                    reasonCode = rec.reason
                )
            }

            RecommendedTodoResponse(
                todoId = todoId,
                plannerDetails = details,
                modeCode = "STANDARD"
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
    ): List<TodoEntity> = lock.withLock {

        decrementCooldowns(availableTodos)

        return filterPoolByCooldown(availableTodos)
    }

    private fun decrementCooldowns(pool: List<TodoEntity>) = lock.withLock {
        pool.forEach { todo ->
            if (todo.cooldownTurns > 0) {
                todo.cooldownTurns -= 1
                println("cooldown for todo ${todo.task} after decrement = ${todo.cooldownTurns}")
                todoRepository.save(todo)
            }
        }
    }

    private fun filterPoolByCooldown (
        pool: List<TodoEntity>
    ): List<TodoEntity>  = lock.withLock {

        for (cooldownLevel in 0..MAX_COOLDOWN_TURNS) {
            println("check cooldownlevel = ${cooldownLevel}")
            val candidates = pool.filter {
                it.cooldownTurns == cooldownLevel
            }

            if (candidates.isNotEmpty()) {
                return candidates
            }
            println("no candidates found")
        }

        return pool
    }

    /**
     * Liefert nur Todos, deren Snooze-Zeit bereits abgelaufen ist.
     */
    private fun filterAvailableTodos(
        todos: List<TodoEntity>
    ): List<TodoEntity>  = lock.withLock {

        if (todos.isEmpty()) {
            return emptyList()
        }

        val currentTime = clock.millis()

        return todos.filter {
            it.snoozedUntil <= currentTime
        }
    }

    @Transactional
    fun processUserFeedback(request: PlannerFeedbackRequest) = lock.withLock {
        request.rejectedTodos.forEach { r ->
            println("   -> Rejected TodoId: ${r.todoId} | Reason: ${r.reason}")
        }
        println("--------------------------------------------------")

        // 1. RecommendationRound anhand der roundId laden
        val round = recommendationRoundRepository.findById(request.roundId).orElse(null)
        if (round == null) {
            println("❌ [COORDINATOR ERROR] RecommendationRound mit ID '${request.roundId}' NICHT in DB gefunden!")
            println("==================================================")
            return
        }

        println("🔍 [COORDINATOR] DB Round gefunden mit ID: '${round.id}'")
        println("🔍 [COORDINATOR] Anzahl gespeicherter Recommendations in dieser Round: ${round.recommendations.size}")
        round.recommendations.forEach { rec ->
            println("   -> SavedRec in DB: TodoId=${rec.todoId} | PlannerType=${rec.plannerType} | Score=${rec.score}")
        }
        println("--------------------------------------------------")

        // 2. Akzeptiertes Todo verarbeiten (falls eins gewählt wurde)
// 2. Akzeptiertes Todo verarbeiten (falls eins gewählt wurde)
        request.acceptedTodo?.let { accepted ->
            val acceptedId = accepted.todoId
            println("🚀 [COORDINATOR] Verarbeite ACCEPTED TodoId: $acceptedId mit Grund: ${accepted.reason}")

            val todo = todoRepository.findById(acceptedId).orElse(null)
            if (todo != null) {
                todo.cooldownTurns = 0
                todoRepository.save(todo)
            }

            val matchingRecs = round.recommendations.filter { it.todoId == acceptedId }
            println("🔎 [COORDINATOR] Matching Recs für Accepted Todo ($acceptedId): ${matchingRecs.size}")

            if (matchingRecs.isEmpty()) {
                println("⚠️ [COORDINATOR WARN] Keine gespeicherte Rec in DB für Accepted Todo $acceptedId gefunden!")
            }

            matchingRecs.forEach { rec ->
                rec.isAccepted = true
                rec.feedbackReason = accepted.reason // 🎯 Hier speichern wir den Akzeptanz-Grund (z.B. "motivation", "effort", etc.)
            }
        }

        // 3. Abgelehnte Todos verarbeiten
        request.rejectedTodos.forEach { rejected ->
            println("❌ [COORDINATOR] Verarbeite REJECTED TodoId: ${rejected.todoId}")
            val todo = todoRepository.findById(rejected.todoId).orElse(null)
            if (todo != null) {
                todo.cooldownTurns = MAX_COOLDOWN_TURNS + 1
                todoRepository.save(todo)
            }

            val matchingRecs = round.recommendations.filter { it.todoId == rejected.todoId }
            println("🔎 [COORDINATOR] Matching Recs für Rejected Todo (${rejected.todoId}): ${matchingRecs.size}")

            if (matchingRecs.isEmpty()) {
                println("⚠️ [COORDINATOR WARN] Keine gespeicherte Rec in DB für Rejected Todo ${rejected.todoId} gefunden!")
            }

            matchingRecs.forEach { savedRec ->
                savedRec.isAccepted = false
                savedRec.feedbackReason = rejected.reason
            }
        }
        println("==================================================")
    }

    /**
     * Reines Datenbank-Snooze – schickt KEIN Feedback an die Planner!
     */
    @Transactional
    fun snoozeTodoInBackend(todoId: String, durationInMin: Int): TodoEntity? = lock.withLock {
        val todo = todoRepository.findById(todoId).orElse(null) ?: return null

        val millisInFuture = durationInMin * 60 * 1000L
        todo.snoozedUntil = clock.millis() + millisInFuture

        return todoRepository.save(todo)
    }

    @Transactional
    fun getMorningRecommendations(userId: String): MorningRecommendationsResponse = lock.withLock {
        validateUserExists(userId, userRepository)

        // 1. Pause-Todos aus der DB holen oder Fallbacks generieren
        val pauseCandidates = todoRepository.findByUserIdOrBlankAndEffort(userId, 0)

        val candidatePool = if (pauseCandidates.isNotEmpty()) {
            pauseCandidates
        } else {
            listOf(
                TodoEntity(id = "morning_coffee", task = "Kaffee oder Tee kochen", effort = 0, userId = userId, description = "Ein warmer Start in den Tag."),
                TodoEntity(id = "morning_mail", task = "Posteingang kurz überfliegen", effort = 0, userId = userId, description = "Wichtige Nachrichten checken."),
                TodoEntity(id = "morning_fresh", task = "Kurz lüften und strecken", effort = 0, userId = userId, description = "Frische Luft für den Kopf.")
            )
        }

        // 2. Planner (NEURAL + BAYES) im Morgen-Modus ausführen
        val plannerRecommendations = planners.mapNotNull { planner ->
            planner.calculatePerfectRecommendation(
                userId = userId,
                candidates = candidatePool,
                userEnergy = EnergyLevel.LOW,
                workingTimeLeft = 480L
            )
        }

        if (plannerRecommendations.isEmpty()) {
            return MorningRecommendationsResponse(roundId = "", recommendations = emptyList())
        }

        // 3. Recommendation-Round in der DB speichern
        val round = RecommendationRoundEntity(
            userId = userId,
            createdAt = clock.millis()
        )

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
                reason = "MORNING_PAUSE_" + recommendation.reason
            )
        }

        val savedRound = recommendationRoundRepository.save(round)

        // 4. Response mit vollständigen Todo-Objekten (TodoDto) zusammenbauen
        val groupedRecommendations = plannerRecommendations.groupBy { it.todoId }
        val recommendations = groupedRecommendations.map { (todoId, plannerRecs) ->
            val todoEntity = candidatePool.firstOrNull { it.id == todoId }
                ?: TodoEntity(id = todoId, task = "Kaffee-Pause", effort = 0, userId = userId)

            val todoDto = TodoDto(
                id = todoEntity.id,
                task = todoEntity.task,
                description = todoEntity.description,
                effort = todoEntity.effort,
                completedAt = todoEntity.completedAt
            )

            val details = plannerRecs.map { rec ->
                PlannerRecommendationDetail(
                    plannerType = rec.plannerType,
                    score = rec.score,
                    reasonCode = rec.reason
                )
            }

            MorningRecommendationItem(
                todo = todoDto,
                plannerDetails = details,
                modeCode = "MORNING_MODE"
            )
        }

        return MorningRecommendationsResponse(
            roundId = savedRound.id,
            recommendations = recommendations
        )
    }

    @Transactional
    fun getRecommendationsByGroup(
        userId: String,
        desiredGroup: TodoGroupChoice
    ): PlannerRecommendationsResponse = lock.withLock {
        validateUserExists(userId, userRepository)

        val matchPool = todoRepository.findOpenProjectTodosForUser(userId)
        val nonSnoozedPool = filterAvailableTodos(matchPool)

        if (nonSnoozedPool.isEmpty()) {
            return PlannerRecommendationsResponse(roundId = "", recommendations = emptyList())
        }

        // 🎯 Filtern des Kandidatenpools exakt nach Wunsch des Users:
        val candidatePool = when (desiredGroup) {
            TodoGroupChoice.BREAK -> {
                // Suchen nach effort == 0 oder Fallback-Pausen-Todos
                val pauses = nonSnoozedPool.filter { it.effort == 0 }
                if (pauses.isNotEmpty()) pauses else listOf(
                    TodoEntity(id = "pause_coffee", task = "Kaffee kochen & durchatmen", effort = 0, userId = userId),
                    TodoEntity(id = "pause_fresh", task = "Fenster öffnen & kurz strecken", effort = 0, userId = userId)
                )
            }
            TodoGroupChoice.LIGHT -> {
                // Kleine Aufgaben (effort == 1)
                val lightTasks = nonSnoozedPool.filter { it.effort == 1 }
                if (lightTasks.isNotEmpty()) lightTasks else nonSnoozedPool.filter { it.effort <= 2 }
            }
            TodoGroupChoice.HEAVY -> {
                // Die großen Brocken (hoher Effort)
                val heavyTasks = nonSnoozedPool.filter { it.effort >= 3 }
                if (heavyTasks.isNotEmpty()) heavyTasks else nonSnoozedPool
            }
        }

        // Energie-Level entsprechend der Gruppe anpassen für den Planner
        val targetEnergy = when (desiredGroup) {
            TodoGroupChoice.BREAK -> EnergyLevel.LOW
            TodoGroupChoice.LIGHT -> EnergyLevel.MEDIUM
            TodoGroupChoice.HEAVY -> EnergyLevel.HIGH
        }

        // Planner (Neural + Bayes) auf diesen spezifischen Pool loslassen
        val plannerRecommendations = planners.mapNotNull { planner ->
            planner.calculatePerfectRecommendation(
                userId,
                candidates = candidatePool,
                userEnergy = targetEnergy,
                workingTimeLeft = 240L
            )
        }

        if (plannerRecommendations.isEmpty()) {
            return PlannerRecommendationsResponse(roundId = "", recommendations = emptyList())
        }

        // Recommendation-Round speichern
        val round = RecommendationRoundEntity(
            userId = userId,
            createdAt = clock.millis()
        )

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
                reason = "GROUP_${desiredGroup}_" + recommendation.reason
            )
        }

        val savedRound = recommendationRoundRepository.save(round)

        // Response fürs Frontend bauen
        val groupedRecommendations = plannerRecommendations.groupBy { it.todoId }
        val recommendations = groupedRecommendations.map { (todoId, plannerRecs) ->
            val details = plannerRecs.map { rec ->
                PlannerRecommendationDetail(
                    plannerType = rec.plannerType,
                    score = rec.score,
                    reasonCode = rec.reason
                )
            }
            RecommendedTodoResponse(
                todoId = todoId,
                plannerDetails = details,
                modeCode = "GROUP_CHOICE_$desiredGroup"
            )
        }

        return PlannerRecommendationsResponse(
            roundId = savedRound.id,
            recommendations = recommendations
        )
    }

    private fun generateSlot(
        userId: String,
        startTime: String,
        endTime: String,
        energyLevel: EnergyLevel,
        workingTimeLeft: Long,
        reasonText: String,
        candidatePool: List<TodoEntity>,
    assignedTodoIds: MutableSet<String>,
    currentTimeMillis: Long
    ): BlueprintSlotDto? = lock.withLock {
        // 1. Zuerst die regulär verfügbaren (nicht gesnoozedten) Kandidaten suchen
        var availableCandidates = candidatePool.filter { todo ->
            !assignedTodoIds.contains(todo.id) && todo.snoozedUntil <= currentTimeMillis
        }

        // 2. 🟢 UNSER NEUER FALLBACK: Wenn alle aktiven schlafen, nimm das, das am frühesten aufwacht!
        if (availableCandidates.isEmpty()) {
            val remainingCandidates = candidatePool.filter { todo ->
                !assignedTodoIds.contains(todo.id)
            }

            if (remainingCandidates.isNotEmpty()) {
                val earliestWakeupTodo = remainingCandidates.minByOrNull { it.snoozedUntil }
                if (earliestWakeupTodo != null) {
                    availableCandidates = listOf(earliestWakeupTodo)
                }
            }
        }

        if (availableCandidates.isEmpty()) return null

        // Planner berechnen wie gewohnt ihre Empfehlungen...
        val recs = planners.mapNotNull { planner ->
            planner.calculatePerfectRecommendation(userId, availableCandidates, energyLevel, workingTimeLeft)
        }

        if (recs.isEmpty()) return null

        val groupedRecs = recs.groupBy { it.todoId }

        val slotRecommendations = groupedRecs.map { (todoId, todoRecs) ->
            assignedTodoIds.add(todoId)
            SlotRecommendationDto(
                todoId = todoId,
                plannerDetails = todoRecs.map { rec ->
                    PlannerRecommendationDetail(rec.plannerType, rec.score, rec.reason)
                }
            )
        }

        return BlueprintSlotDto(
            startTime = startTime,
            endTime = endTime,
            energyLevel = energyLevel,
            recommendations = slotRecommendations,
            reason = reasonText
        )
    }

    @Transactional
    fun generateDailyBlueprint(userId: String): DailyBlueprintResponse = lock.withLock {
        validateUserExists(userId, userRepository)

        val currentHour = LocalTime.now(clock).hour
        val currentTimeMillis = clock.millis()
        val matchPool = todoRepository.findOpenProjectTodosForUser(userId)

        if (matchPool.isEmpty()) {
            return DailyBlueprintResponse(roundId = UUID.randomUUID().toString(), userId = userId, generatedAtHour = currentHour, slots = emptyList())
        }

        val settings = plannerSettingsRepository.findById(userId).orElseGet {
            PlannerSettingsEntity(id = userId, defaultWorkingHours = 8, primeTimeStartHour = 10, primeTimeEndHour = 18)
        }

        val slots = mutableListOf<BlueprintSlotDto>()
        val assignedTodoIds = mutableSetOf<String>()

        // 1. Früh-Slot (Vor der Prime Time)
        if (currentHour < settings.primeTimeStartHour) {
            generateSlot(
                userId = userId,
                startTime = "$currentHour:00",
                endTime = "${settings.primeTimeStartHour}:00",
                energyLevel = EnergyLevel.LOW,
                workingTimeLeft = 2L,
                reasonText = "Sanfter Start: Perfekt zum Ankommen und für kleinere Aufgaben vor der Prime Time.",
                candidatePool = matchPool,
                assignedTodoIds = assignedTodoIds,
                currentTimeMillis = currentTimeMillis
            )?.let { slots.add(it) }
        }

        // 2. Prime-Time-Slot (High Focus)
        val primeStart = maxOf(currentHour, settings.primeTimeStartHour)
        if (primeStart < settings.primeTimeEndHour) {
            generateSlot(
                userId = userId,
                startTime = "$primeStart:00",
                endTime = "${settings.primeTimeEndHour}:00",
                energyLevel = EnergyLevel.HIGH,
                workingTimeLeft = 3L,
                reasonText = "Prime-Time-Peak: Deine biologische Hochphase für schwere Aufgaben & High Focus.",
                candidatePool = matchPool,
                assignedTodoIds = assignedTodoIds,
                currentTimeMillis = currentTimeMillis
            )?.let { slots.add(it) }
        }

        // 3. Nachmittags-Slot (Ausklang / Low Focus)
// 3. Nachmittags-Slot (Ausklang / medium Focus)
        val afternoonStart = settings.primeTimeEndHour
        val afternoonEnd = minOf(afternoonStart + 3, 20)
        if (currentHour < afternoonEnd) {
            generateSlot(
                userId = userId,
                startTime = "$afternoonStart:00",
                endTime = "$afternoonEnd:00",
                energyLevel = EnergyLevel.MEDIUM,
                workingTimeLeft = 2L,
                reasonText = "Energie-Ausklang: Ideal für Routine, E-Mails und leichtere Aufgaben zum Feierabend.",
                candidatePool = matchPool,
                assignedTodoIds = assignedTodoIds,
                currentTimeMillis = currentTimeMillis
            )?.let { slots.add(it) }
        }

        // 4. 🌙 NEU: Spätabend- / Nachtschicht-Slot (für späte Planer nach 21:00 Uhr)
        val nightStart = maxOf(currentHour, afternoonEnd)
        if (nightStart < 24) {
            generateSlot(
                userId = userId,
                startTime = "$nightStart:00",
                endTime = "24:00",
                energyLevel = EnergyLevel.LOW,
                workingTimeLeft = 1L,
                reasonText = "Nachtschicht / Spät-Focus: Ruhige Abendstunden für die letzten offenen Dinge oder Reflexion.",
                candidatePool = matchPool,
                assignedTodoIds = assignedTodoIds,
                currentTimeMillis = currentTimeMillis
            )?.let { slots.add(it) }
        }

        // Feedback-Runde in DB speichern
        val round = RecommendationRoundEntity(userId = userId, createdAt = currentTimeMillis)
        slots.forEach { slot ->
            // Wir iterieren über die Liste der Empfehlungen im Slot
            slot.recommendations.forEach { recDto ->
                val todoEntity = matchPool.firstOrNull { it.id == recDto.todoId }
                val todoEffort = todoEntity?.effort ?: 0

                // Für jede Empfehlung speichern wir die Details der beteiligten Planner (Bayes, Neural, etc.)
                recDto.plannerDetails.forEach { det ->
                    round.recommendations += PlannerRecommendationEntity(
                        round = round,
                        plannerType = det.plannerType,
                        todoId = recDto.todoId,
                        score = det.score,
                        energyLevel = slot.energyLevel,
                        timeUntilDue = 0L,
                        workingTimeLeft = 3L,
                        effort = todoEffort,
                        reason = det.reasonCode
                    )
                }
            }
        }

        val savedRound = recommendationRoundRepository.save(round)

        return DailyBlueprintResponse(
            roundId = savedRound.id,
            userId = userId,
            generatedAtHour = currentHour,
            slots = slots
        )
    }

    @Transactional
    fun retrainAI(userId: String) = lock.withLock {

        validateUserExists(userId, userRepository)
        planners.forEach { it.runReTraining(userId = userId) }
        // Für Gemini: hier müssen wir noch besprechen, ob wir bei Bayes nur die Daten für diesen User löschen müssen
        recommendationRoundRepository.deleteAll()
    }

    @Scheduled(cron = "0 0 0 * * *") // Jeden Tag um Mitternacht
    @Transactional
    fun runMidnightTraining() {
        println("🌙 [MIDNIGHT AI TRAINING] Starte nächtliches Batch-Training mit Cross-Learning...")

        val zone = ZoneId.systemDefault()

        val todayMidnight = LocalDate.now()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val yesterdayMidnight = LocalDate.now()
            .minusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        // 1. Alle Runden des gestrigen Tages holen
        val pendingRounds = recommendationRoundRepository.findAllByCreatedAtBetween(yesterdayMidnight, todayMidnight)

        if (pendingRounds.isEmpty()) {
            println("🌙 [MIDNIGHT AI TRAINING] Keine Runden für den Zeitraum gefunden. Überspringe Training.")
            return
        }

        pendingRounds.forEach { round ->
            // Nur unverarbeitete Recommendations mit einer bewussten User-Entscheidung
            val pendingRecommendations = round.recommendations.filter { !it.isProcessed && it.isAccepted != null }

            pendingRecommendations.forEach { rec ->
                val isOwn = (rec.plannerType == planners.firstOrNull()?.plannerType) // oder passend ermittelt

                val feedbackForPlanner = FeedbackForPlanner(
                    userId = round.userId,
                    todoId = rec.todoId,
                    userEnergy = rec.energyLevel,
                    workingTimeLeft = rec.workingTimeLeft,
                    accepted = rec.isAccepted!!,
                    reason = rec.feedbackReason,
                    score = rec.score,
                    timeUntilDue = rec.timeUntilDue,
                    effort = rec.effort,
                    category = rec.category,
                    isOwnRecommendation = true // oder ob es der eigene Vorschlag war
                )

                // 🚀 Beide Planner lernen aus dem Feedback (Cross-Learning)
                planners.forEach { planner ->
                    planner.processUserFeedback(feedbackForPlanner)
                }

                rec.isProcessed = true
            }
            recommendationRoundRepository.save(round)
        }

        println("🌙 [MIDNIGHT AI TRAINING] Erfolgreich abgeschlossen! Beide KIs im Batch trainiert.")
    }
}
