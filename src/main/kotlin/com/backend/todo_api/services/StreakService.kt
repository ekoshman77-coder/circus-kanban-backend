package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.StreakInfoDto
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Service
class StreakService(
    private val todoRepository: TodoRepository,
    private val userRepository: UserRepository
) {
    companion object {
        private val DAILY_EFFORT_GOAL = 2.0
        private val MAX_PUFFER_DAYS = 10.0
    }

    /**
     * Berechnet die Live-Informationen für das Frontend (beim Sync oder Abfragen).
     */
    fun getCurrentStreakInfo(user: UserEntity): StreakInfoDto {
        val now = LocalDateTime.now()
        val coveredUntil = user.streakCoveredUntil

        // Prüfen, ob wir aktiven Schutz durch laufende Projektaufgaben haben
        val hasActiveShield = checkProjectShield(user.id)

        // Falls kein Datum gesetzt ist oder es abgelaufen ist
        if (coveredUntil == null || coveredUntil.isBefore(now)) {
            return if (hasActiveShield) {
                // Schutzschild rettet die Flamme vor dem Ausgehen!
                StreakInfoDto(
                    streakDays = calculateCurrentStreakDays(user),
                    batteryPercentage = 15, // Künstlicher Mindestwert im UI
                    pufferDaysRemaining = 0.0,
                    isShieldActive = true,
                    infoText = "Schutzschild aktiv: Du arbeitest an einer Projektaufgabe!"
                )
            } else {
                // Akku komplett leer, Flamme aus
                StreakInfoDto(
                    streakDays = 0,
                    batteryPercentage = 0,
                    pufferDaysRemaining = 0.0,
                    isShieldActive = false,
                    infoText = "Batterie leer. Schließe eine Projektaufgabe ab, um sie aufzuladen!"
                )
            }
        }

        // Wenn wir noch Saft haben, berechnen wir die echten verbleibenden Arbeitstage
      //  val startOfToday = now.toLocalDate().atStartOfDay()
        val remainingWorkDays = calculateRemainingWorkDays(now, coveredUntil)

        // Prozentwert berechnen (Abstand ist jetzt >= 10 Tage, d.h. wir treffen die 100%)
        val percentage = ((remainingWorkDays / MAX_PUFFER_DAYS) * 100).roundToInt().coerceIn(0, 100)
        //val streakDays = calculateCurrentStreakDays(user)

        val roundedDaysInt = remainingWorkDays.roundToInt()

        // Wenn die Batterie 100% hat, passen wir den Info-Text an oder nutzen ein Flag
        val infoText = if (percentage >= 100) {
            "🌌 OVERDRIVE AKTIV! Deine Batterie strahlt in voller Overtime-Glut!"
        } else if (hasActiveShield) {
            "Batterie geschützt durch aktive Arbeit. Reicht noch für ca. ${roundedDaysInt} Tage."
        } else {
            "Dein Akku ist geladen! Puffer reicht für ca. ${roundedDaysInt} Tage."
        }
        return StreakInfoDto(
            streakDays = roundedDaysInt,
            batteryPercentage = percentage,
            pufferDaysRemaining = remainingWorkDays,
            isShieldActive = hasActiveShield,
            infoText = infoText
        )
    }

    /**
     * Wird aufgerufen, wenn ein Todo auf DONE gesetzt wird. Erhöht das Ablaufdatum.
     */
    fun updateStreakOnTodoCompleted(user: UserEntity, todo: TodoEntity): UserEntity {
        // Regel: Nur Projektaufgaben (milestoneId nicht leer/null) zählen für die Team-Batterie!
        if (todo.milestoneId.isNullOrBlank()) return user

        val now = LocalDateTime.now()
        // Wenn die Batterie schon leer war, starten wir bei 'now', sonst bauen wir auf dem alten Puffer auf
        val baseTime = if (user.streakCoveredUntil == null || user.streakCoveredUntil!!.isBefore(now)) {
            now
        } else {
            user.streakCoveredUntil!!
        }

        val addedWorkDays = todo.effort / DAILY_EFFORT_GOAL
        val newCoveredUntil = addWorkDaysSkippingWeekends(baseTime, addedWorkDays)

        // Deckelung auf maximal 7 Arbeitstage in der Zukunft ab JETZT
        val maxAllowedFuture = addWorkDaysSkippingWeekends(now, MAX_PUFFER_DAYS)
        if (newCoveredUntil.isAfter(maxAllowedFuture)) {
            user.streakCoveredUntil = maxAllowedFuture
        } else {
            user.streakCoveredUntil = newCoveredUntil
        }

        return userRepository.save(user)
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
}