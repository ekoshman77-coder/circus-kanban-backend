package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.ProjectStreakInfoDto
import com.backend.todo_api.dto.StreakInfoDto
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.toSecurityResource
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Service
class StreakService(
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val milestoneRepository: MilestoneRepository,
    private val permissionService: PermissionService,
    private val userContextResolver: UserContextResolver
) {
    companion object {
        private val DAILY_EFFORT_GOAL = 2.0
        private val MAX_PUFFER_DAYS = 10.0
        private val PROJECT_MAX_PUFFER_DAYS = 2.0
        private val MIN_PUFFER_FOR_REVIEW = 15
    }

    /**
     * Berechnet die Live-Informationen für das Frontend (beim Sync oder Abfragen).
     */
    /**
     * Berechnet die Live-Informationen für das Frontend (beim Sync oder Abfragen).
     */
    fun getCurrentStreakInfo(user: UserEntity): StreakInfoDto {
        val now = LocalDateTime.now()
        val coveredUntil = user.streakCoveredUntil

        // 1. Eigener Ticket-Schild (IN_PROGRESS / REVIEW)
        val hasPersonalShield = checkProjectShield(user.id)

        // 2. 🎯 NEU: Team-Schutzschild prüfen!
        // Brennt die Flamme in mindestens einem Projekt, in dem der User Mitglied ist?
        val hasTeamShield = checkTeamShieldForUser(user.id)

        val isShieldActive = hasPersonalShield || hasTeamShield

        // Falls kein Datum gesetzt ist oder es abgelaufen ist
        if (coveredUntil == null || coveredUntil.isBefore(now)) {
            return if (isShieldActive) {
                // Team oder eigene Arbeit rettet die persönliche Flamme!
                val shieldReason = if (hasPersonalShield) {
                    "Schutzschild aktiv: Du arbeitest an einer Projektaufgabe!"
                } else {
                    "🛡️ Team-Schutzschild aktiv: Dein Team hält die Flamme für dich am Brennen!"
                }

                StreakInfoDto(
                    streakDays = calculateCurrentStreakDays(user).coerceAtLeast(1),
                    batteryPercentage = MIN_PUFFER_FOR_REVIEW, // Optischer Not-Puffer im UI
                    pufferDaysRemaining = 0.0,
                    isShieldActive = true,
                    infoText = shieldReason
                )
            } else {
                // Keiner hilft -> Flamme aus
                StreakInfoDto(
                    streakDays = 0,
                    batteryPercentage = 0,
                    pufferDaysRemaining = 0.0,
                    isShieldActive = false,
                    infoText = "Batterie leer. Schließe eine Aufgabe ab, um sie aufzuladen!"
                )
            }
        }

        // Wenn wir noch Saft haben, berechnen wir die echten verbleibenden Arbeitstage
        //  val startOfToday = now.toLocalDate().atStartOfDay()
        val remainingWorkDays = calculateRemainingWorkDays(now, coveredUntil)

// 1. Regulären Prozentwert berechnen
        val calculatedPercentage = ((remainingWorkDays / MAX_PUFFER_DAYS) * 100).roundToInt().coerceIn(0, 100)

        // 🎯 2. Schild-Sicherung: Wenn du arbeitest (REVIEW/IN_PROGRESS), sinkt die Anzeige nie unter 15%!
        val finalPercentage = if (isShieldActive) {
            calculatedPercentage.coerceAtLeast(MIN_PUFFER_FOR_REVIEW)
        } else {
            calculatedPercentage
        }

        val roundedDaysInt = remainingWorkDays.roundToInt()

        // 3. Info-Text basierend auf der finalen Prozentzahl
        val infoText = if (finalPercentage >= 100) {
            "🌌 OVERDRIVE AKTIV! Deine Batterie strahlt in voller Overtime-Glut!"
        } else if (isShieldActive) {
            "Batterie geschützt durch aktive Arbeit. Reicht noch für ca. ${roundedDaysInt} Tage."
        } else {
            "Dein Akku ist geladen! Puffer reicht für ca. ${roundedDaysInt} Tage."
        }

        return StreakInfoDto(
            streakDays = roundedDaysInt,
            batteryPercentage = finalPercentage, // 👈 HIER "finalPercentage" übergeben!
            pufferDaysRemaining = remainingWorkDays,
            isShieldActive = isShieldActive,
            infoText = infoText
        )
    }

    /**
     * Prüft, ob der User in mindestens einem Projekt ist, dessen Team-Akku noch aktiv brennt.
     */
    private fun checkTeamShieldForUser(userId: String): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val now = LocalDateTime.now()

        // Alle Projekte holen, in denen der User Mitglied ist
        val userProjects = user.projectMemberships.map { it.project }

        // Prüfen, ob mindestens ein Projekt einen aktiven Streak-Puffer hat
        return userProjects.any { project ->
            project.projectStreakCoveredUntil != null && project.projectStreakCoveredUntil!!.isAfter(now)
        }
    }

//    fun updateStreakOnTodoCompleted(todo: TodoEntity, currentUserId: String) {
//        if (todo.milestoneId.isNullOrBlank()) {
//            return
//        }
//
//        // 1. Team-Projekt-Streak aktualisieren
//        updateProjectStreakInfo(todo.milestoneId, todo.effort)
//
//        // 2. Aufwands-Anteile bestimmen
//        val reviewerEffort = todo.reviewerUsedEffort
//        val devEffort = (todo.usedEffort - reviewerEffort).coerceAtLeast(0.0)
//
//        // 3. Entwickler ermitteln & dessen Streak in der DB aufladen
//        val devUserId = determineXpReceiverUserId(todo, currentUserId)
//        if (devEffort > 0.0) {
//            userRepository.findById(devUserId).ifPresent { devUser ->
//                applyStreakEffortToUser(devUser, devEffort)
//            }
//        }
//
//        // 4. Reviewer-Streak in der DB aufladen (falls vorhanden & nicht Dev selbst)
//        if (!todo.reviewerId.isNullOrBlank() && todo.reviewerId != devUserId && reviewerEffort > 0.0) {
//            userRepository.findById(todo.reviewerId!!).ifPresent { reviewerUser ->
//                applyStreakEffortToUser(reviewerUser, reviewerEffort)
//            }
//        }
//    }

    fun applyStreakEffortToUser(user: UserEntity, effortPart: Double): UserEntity {
        val now = LocalDateTime.now()
        val baseTime = if (user.streakCoveredUntil == null || user.streakCoveredUntil!!.isBefore(now)) {
            now
        } else {
            user.streakCoveredUntil!!
        }

        val addedWorkDays = effortPart / DAILY_EFFORT_GOAL
        val newCoveredUntil = addWorkDaysSkippingWeekends(baseTime, addedWorkDays)

        val maxAllowedFuture = addWorkDaysSkippingWeekends(now, MAX_PUFFER_DAYS)
        user.streakCoveredUntil = if (newCoveredUntil.isAfter(maxAllowedFuture)) maxAllowedFuture else newCoveredUntil

        return userRepository.save(user)
    }

    fun updateProjectStreakInfo(milestoneId: String?, effort: Int) {
        if (milestoneId.isNullOrBlank()) {
            return
        }
        val milestone = milestoneRepository.findById(milestoneId!!).orElse(null)
        val project = milestone?.project

        if (project == null) {
            return
        }

        // A. N_aktiv & Q_erforderlich für das Projekt ermitteln
        val milestoneIds = project.milestones.map { it.id }
        val projectTodos = todoRepository.findByMilestoneIdIn(milestoneIds)
        val now = LocalDateTime.now()
        val sevenDaysAgoEpoch = now.minusDays(7).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val activeUserIds = projectTodos.filter { t ->
            val wasActiveRecently = t.completedAt != null && t.completedAt!! >= sevenDaysAgoEpoch
            val isInProgress = t.teamStatus == "IN_PROGRESS" || t.teamStatus == "REVIEW"
            wasActiveRecently || isInProgress
        }.map { it.userId }.toSet()

        val nActive = activeUserIds.size.coerceAtLeast(1)
        val qRequired = kotlin.math.max(1, kotlin.math.ceil(nActive * 0.5).toInt())

        // B. Projekt-Puffer erweitern
        // Formel: (Effort des Todos) / (Tagesziel-Aufwand * Q_erforderlich)
        val dailyGoalPerUser = 2.0
        val teamDailyGoal = dailyGoalPerUser * qRequired

        // Effort-Anteil in Tagen berechnen (z.B. Effort 2 / Teamziel 4 = 0.5 Tage Akku)
        val daysToAdd = effort.toDouble() / teamDailyGoal

        val currentCoveredUntil = project.projectStreakCoveredUntil
        val baseTime = if (currentCoveredUntil != null && currentCoveredUntil.isAfter(now)) {
            currentCoveredUntil
        } else {
            now
        }

        // Neuen Projekt-Akku setzen (maximal z. B. MAX_PUFFER_DAYS im Voraus)
        val newCoveredUntil = addWorkDaysSkippingWeekends(baseTime, daysToAdd)

        // Deckelung auf maximal 2 Arbeitstage in der Zukunft ab JETZT!
        val maxAllowedCoveredUntil = addWorkDaysSkippingWeekends(now, PROJECT_MAX_PUFFER_DAYS)

        project.projectStreakCoveredUntil = if (newCoveredUntil.isAfter(maxAllowedCoveredUntil)) {
            maxAllowedCoveredUntil
        } else {
            newCoveredUntil
        }

        // Projekt-Entität speichern
        projectRepository.save(project)
    }

    /**
     * Rechnet Tage auf ein Datum drauf, überspringt dabei aber Samstage und Sonntage.
     */
    private fun addWorkDaysSkippingWeekends(start: LocalDateTime, daysToAdd: Double): LocalDateTime {
        var result = start
        var remainingDays = daysToAdd

        // Ganze Tage hinzufügen
        while (remainingDays >= 1.0) {
            result = result.plusDays(1)
            if (result.dayOfWeek != DayOfWeek.SATURDAY && result.dayOfWeek != DayOfWeek.SUNDAY) {
                remainingDays -= 1.0
            }
        }

        // Den verbleibenden Bruchteil eines Tages in Stunden umrechnen (z.B. 0.5 Tage = 12 Stunden)
        if (remainingDays > 0.0) {
            val hoursToAdd = (remainingDays * 24).toLong()
            result = result.plusHours(hoursToAdd)

            // Falls wir durch die Stunden im Wochenende gelandet sind, schieben wir es ins ins nächste freie Fenster
            while (result.dayOfWeek == DayOfWeek.SATURDAY || result.dayOfWeek == DayOfWeek.SUNDAY) {
                result = result.plusDays(1)
            }
        }

        return result
    }

    /**
     * Berechnet, wie viele reine Arbeitstage (Mo-Fr) zwischen zwei Zeitpunkten liegen.
     */
    private fun calculateRemainingWorkDays(from: LocalDateTime, to: LocalDateTime): Double {
        if (from.isAfter(to)) return 0.0

        var temp = from
        var totalMinutes = 0.0

        // Wir gehen in Schritten von einzelnen Tagen voran,
        // berechnen aber die Minuten der Arbeitstage exakt
        while (temp.toLocalDate().isBefore(to.toLocalDate())) {
            if (temp.dayOfWeek != DayOfWeek.SATURDAY && temp.dayOfWeek != DayOfWeek.SUNDAY) {
                // Wie viele Minuten hat dieser angebrochene oder volle Tag noch?
                val endOfToday = temp.toLocalDate().atTime(23, 59, 59)
                val minutesToday = ChronoUnit.MINUTES.between(temp, endOfToday) + 1
                totalMinutes += minutesToday

                // Setze temp auf den Beginn des nächsten Tages
                temp = temp.toLocalDate().plusDays(1).atStartOfDay()
            } else {
                // Wochenende einfach überspringen
                temp = temp.toLocalDate().plusDays(1).atStartOfDay()
            }
        }

        // Wenn der Ziel-Tag (to) ein Arbeitstag ist, rechnen wir die verbleibenden Minuten am Ziel-Tag dazu
        if (to.dayOfWeek != DayOfWeek.SATURDAY && to.dayOfWeek != DayOfWeek.SUNDAY) {
            val minutesOnLastDay = ChronoUnit.MINUTES.between(temp, to)
            if (minutesOnLastDay > 0) {
                totalMinutes += minutesOnLastDay
            }
        }

        // Minuten in Tage umrechnen (1 Arbeitstag = 1440 Minuten)
        return totalMinutes / 1440.0
    }

    /**
     * Prüft, ob der User ein Schutzschild hat (In-Progress oder Reviewer-Rolle).
     */
    private fun checkProjectShield(userId: String): Boolean {
        val activeTeamTodos = todoRepository.findActiveTeamTodosForUser(userId)

        return activeTeamTodos.any { todo ->
            todo.teamStatus == "IN_PROGRESS" || todo.teamStatus == "REVIEW"
        }
    }

    /**
     * Bestimmt die aktuelle Streak-Zahl. (Einfachheitshalber gekoppelt an geschaffte Tage
     * oder als ein fiktiver Zähler – kann später nach Wunsch verfeinert werden).
     */
    private fun calculateCurrentStreakDays(user: UserEntity): Int {
        val coveredUntil = user.streakCoveredUntil ?: return 0
        val now = LocalDateTime.now()
        if (coveredUntil.isBefore(now)) return 0

        // Eine einfache, solide Annäherung: Wie viele Arbeitstage hat der User sich vorausgearbeitet?
        val days = calculateRemainingWorkDays(now, coveredUntil).toInt()
        return if (days <= 0) 1 else days
    }

    fun syncAndGetStreakInfo(userId: String): StreakInfoDto {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User nicht gefunden")
        }

        val now = LocalDateTime.now()
        val hasActiveShield = checkProjectShield(user.id)

        // 🎯 INITIALISIERUNG: Wenn der Puffer leer/abgelaufen ist, aber der User arbeitet, tanken wir auf!
        if (hasActiveShield && (user.streakCoveredUntil == null || user.streakCoveredUntil!!.isBefore(now))) {
            user.streakCoveredUntil = now.plusDays(1)
            userRepository.save(user)
        }

        // Danach rufen wir einfach deine bestehende Logik auf, die das Dto baut
        return getCurrentStreakInfo(user)
    }

    /**
     * Berechnet die Team-Streak-Informationen für ein spezifisches Projekt unter Berücksichtigung der Berechtigungen.
     */
    fun getProjectStreakInfo(userId: String, projectId: String): ProjectStreakInfoDto {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Projekt mit ID $projectId nicht gefunden") }

        // 1. Alle Sicherheits-Kontexte des Benutzers laden
        val userContexts = userContextResolver.resolveContexts(userId)

        // 2. Rechteprüfung über den PermissionService
        val canRead = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = project.toSecurityResource()
        )

        if (!canRead) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, dieses Projekt zu lesen.")
        }

        // 3. Wenn keine Meilensteine vorhanden sind -> Leeres DTO zurückgeben
        val milestoneIds = project.milestones.map { it.id }
        if (milestoneIds.isEmpty()) {
            return createEmptyProjectStreakDto(projectId)
        }

        // 4. Todos der Meilensteine laden und In-Memory auswerten
        val projectTodos = todoRepository.findByMilestoneIdIn(milestoneIds)

        val now = LocalDateTime.now()
        val sevenDaysAgoEpoch = now.minusDays(7).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayStartEpoch = now.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 1. User der letzten 7 Tage ermitteln
        val recentUserIds = projectTodos.filter { todo ->
            val wasActiveRecently = todo.completedAt != null && todo.completedAt!! >= sevenDaysAgoEpoch
            val isInProgress = todo.teamStatus == "IN_PROGRESS" || todo.teamStatus == "REVIEW"
            wasActiveRecently || isInProgress
        }.map { it.userId }.toSet()

        // 2. Heutige Beiträge & Schild-Status bestimmen
        val todaysTodos = projectTodos.filter { t ->
            t.done && t.completedAt != null && t.completedAt!! >= todayStartEpoch
        }
        val todaysContributedUserIds = todaysTodos.map { it.userId }.toSet()
        val todaysTotalEffort = todaysTodos.sumOf { it.effort }

        val shieldUserIds = projectTodos.filter { t ->
            !t.done && (t.teamStatus == "IN_PROGRESS" || t.teamStatus == "REVIEW")
        }.map { it.userId }.toSet()

        // 🎯 3. Wer war heute ODER in den letzten 7 Tagen aktiv? (Alle vereinen!)
        val allActiveUserIds = recentUserIds + todaysContributedUserIds + shieldUserIds

        // 4. Team-Größe & Erforderliches Quorum berechnen
        val nActive = allActiveUserIds.size.coerceAtLeast(1)
        val qRequired = kotlin.math.max(1, kotlin.math.ceil(nActive * 0.5).toInt())

        val effectiveActiveMembersCount = (todaysContributedUserIds + shieldUserIds).size
        val isTeamShieldActive = shieldUserIds.isNotEmpty()

        // Verbleibenden Akku berechnen
        val coveredUntil = project.projectStreakCoveredUntil
        val remainingWorkDays = if (coveredUntil != null && coveredUntil.isAfter(now)) {
            calculateRemainingWorkDays(now, coveredUntil)
        } else 0.0

        // Prozentwert berechnet sich jetzt auf Basis von maximal 2 Tagen (100% = 2 Tage Puffer)
        val percentage = ((remainingWorkDays / PROJECT_MAX_PUFFER_DAYS) * 100).roundToInt().coerceIn(0, 100)
        val streakDays = remainingWorkDays.roundToInt()
        return ProjectStreakInfoDto(
            projectId = projectId,
            streakDays = streakDays,
            batteryPercentage = percentage,
            activeMembersCount = nActive,
            requiredMembersCount = qRequired,
            todaysContributedMembers = effectiveActiveMembersCount,
            todaysTotalEffort = todaysTotalEffort.toDouble(),
            activeShieldMembersCount = shieldUserIds.size,
            isShieldActive = isTeamShieldActive
        )
    }

    fun deductPenaltyEffortFromUser(user: UserEntity) {
        val coveredUntil = user.streakCoveredUntil ?: return
        val now = LocalDateTime.now()

        if (coveredUntil.isAfter(now)) {
            val remainingMinutes = ChronoUnit.MINUTES.between(now, coveredUntil)
            val penaltyMinutes = (remainingMinutes * 0.02).toLong().coerceAtLeast(15)
            user.streakCoveredUntil = coveredUntil.minusMinutes(penaltyMinutes)
            userRepository.save(user)
        }
    }

    private fun createEmptyProjectStreakDto(projectId: String): ProjectStreakInfoDto {
        return ProjectStreakInfoDto(
            projectId = projectId,
            streakDays = 0,
            batteryPercentage = 0,
            activeMembersCount = 0,
            requiredMembersCount = 1,
            todaysContributedMembers = 0,
            todaysTotalEffort = 0.0,
            activeShieldMembersCount = 0,
            isShieldActive = false
        )
    }
}