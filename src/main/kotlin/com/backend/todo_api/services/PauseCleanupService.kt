package com.backend.todo_api.services

import com.backend.todo_api.data.repository.TodoRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import org.slf4j.LoggerFactory

@Service
class PauseCleanupService(
    private val todoRepository: TodoRepository // Injiziere dein echtes Repository
) {
    private val logger = LoggerFactory.getLogger(PauseCleanupService::class.java)

    /**
     * SICHERUNG 1: Läuft jeden Tag pünktlich um 00:00:00 Uhr (Mitternacht)
     * "0 0 0 * * ?" ist der Cron-Ausdruck für Sekunde 0, Minute 0, Stunde 0, jeden Tag
     */
    @Scheduled(cron = "0 0 0 * * ?")
    fun cleanupOasisAtMidnight() {
        logger.info("🌴 Mitternacht! Die Oase wird automatisch aufgeräumt...")
        executeCleanup()
    }

    /**
     * SICHERUNG 2: Läuft EINMALIG, sobald der Server komplett hochgefahren und bereit ist
     */
    @EventListener(ApplicationReadyEvent::class)
    fun cleanupOasisOnServerStart() {
        logger.info("🚀 Server gestartet! Prüfe, ob alte Pausetickets gelöscht werden müssen...")
        executeCleanup()
    }

    /**
     * Die eigentliche Lösch-Logik (wird von beiden Sicherungen genutzt)
     */
    private fun executeCleanup() {
        try {
            // Wir holen uns den exakten Start des heutigen Tages (00:00 Uhr) in Millisekunden
            val heuteMitternachtMs = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            // Datenbank-Löschung:
            // Wir löschen alle Todos mit effort == 0, die NICHT erledigt sind
            // UND deren Erstellungsdatum VOR heute Mitternacht liegt.
            val gelöschteAnzahl = todoRepository.deleteOldPauseTickets(
                effort = 0,
                done = false, // 🌟 done statt completed
                todayMidnight = heuteMitternachtMs
            )

            if (gelöschteAnzahl > 0) {
                logger.info("🧹 Erfolgreich $gelöschteAnzahl alte Pausetickets von gestern entfernt.")
            } else {
                logger.info("✨ Keine alten Pausetickets zum Löschen gefunden. Die Oase ist sauber!")
            }
        } catch (e: Exception) {
            logger.error("❌ Fehler beim Aufräumen der Pausetickets: ${e.message}", e)
        }
    }
}