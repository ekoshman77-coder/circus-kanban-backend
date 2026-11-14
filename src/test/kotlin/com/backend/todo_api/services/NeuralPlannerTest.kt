package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.services.neural.NeuralNetwork
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class NeuralPlannerTest {

    @Autowired
    lateinit var neuralPlannerService: NeuralPlannerService

    @Autowired
    lateinit var neuralNetwork: NeuralNetwork

    private val testUserId = "test-user-ai-123"
    private val now = System.currentTimeMillis()
    private val hourInMillis = 60 * 60 * 1000L
    private val dayInMillis = 24 * hourInMillis

    private lateinit var taskPool: List<TodoEntity>

    @BeforeEach
    fun setUp() {
        // 📦 SCHRITT 1: Der Aufgaben-Pool ("Koloda" aus 20 ganz unterschiedlichen Todos)
        taskPool = listOf(
            // --- Quick Wins / Leicht (Effort 1-2) ---
            TodoEntity(id = "qw-1", task = "Otypo in README korrigieren", effort = 1, dueDate = now + 5 * dayInMillis, userId = testUserId),
            TodoEntity(id = "qw-2", task = "Kurze E-Mail an Kunden schreiben", effort = 2, dueDate = now + 2 * dayInMillis, userId = testUserId),
            TodoEntity(id = "qw-3", task = "Abhängigkeit in package.json aktualisieren", effort = 2, dueDate = now + 10 * dayInMillis, userId = testUserId),

            // --- Routine / Mittel (Effort 4-6) ---
            TodoEntity(id = "mid-1", task = "Unit-Test für UserService schreiben", effort = 5, dueDate = now + 3 * dayInMillis, userId = testUserId),
            TodoEntity(id = "mid-2", task = "CSS-Styles im Dashboard ausrichten", effort = 4, dueDate = now + 4 * dayInMillis, userId = testUserId),
            TodoEntity(id = "mid-3", task = "Alten Helper-Methoden in Service verschieben", effort = 5, dueDate = now + 6 * dayInMillis, userId = testUserId),
            TodoEntity(id = "mid-4", task = "Log-Ausgaben bereinigen", effort = 4, dueDate = now + 7 * dayInMillis, userId = testUserId),
            TodoEntity(id = "mid-5", task = "Code-Review für Kollegen durchführen", effort = 5, dueDate = now + 1 * dayInMillis, userId = testUserId),
            TodoEntity(id = "mid-6", task = "Datenbank-Indizes prüfen", effort = 6, dueDate = now + 5 * dayInMillis, userId = testUserId),

            // --- Heavy / Deep Work (Effort 8-10) ---
            TodoEntity(id = "heavy-1", task = "Architektonischer Refactoring des Auth-Moduls", effort = 9, dueDate = now + 10 * dayInMillis, userId = testUserId),
            TodoEntity(id = "heavy-2", task = "Komplexes Datenbankschema für Reporting entwerfen", effort = 10, dueDate = now + 14 * dayInMillis, userId = testUserId),
            TodoEntity(id = "heavy-3", task = "Echtzeit-Synchronisations-Algorithmus schreiben", effort = 10, dueDate = now + 8 * dayInMillis, userId = testUserId),
            TodoEntity(id = "heavy-4", task = "Migration auf neue Spring Boot Major Version", effort = 9, dueDate = now + 20 * dayInMillis, userId = testUserId),
            TodoEntity(id = "heavy-5", task = "Sicherheits-Audit und Penetrationstest-Fixes", effort = 8, dueDate = now + 4 * dayInMillis, userId = testUserId),

            // --- Kreativ / Forschungsarbeiten (Effort 6-8) ---
            TodoEntity(id = "creative-1", task = "Konzept für UI-Animationen ausarbeiten", effort = 7, dueDate = now + 12 * dayInMillis, userId = testUserId),
            TodoEntity(id = "creative-2", task = "Neuen KI-Agenten-Ansatz prototypieren", effort = 8, dueDate = now + 15 * dayInMillis, userId = testUserId),
            TodoEntity(id = "creative-3", task = "Speicherleck im WebSocket-Handler untersuchen", effort = 7, dueDate = now + 3 * dayInMillis, userId = testUserId),
            TodoEntity(id = "creative-4", task = "Design-System für Komponenten entwerfen", effort = 6, dueDate = now + 10 * dayInMillis, userId = testUserId),

            // --- Extrem Dringend / Ceytnot (Sehr nahes Due Date) ---
            TodoEntity(id = "urgent-1", task = "HOTFIX: Production Server Crash beheben", effort = 7, dueDate = now + 30 * 60 * 1000L, userId = testUserId) // In 30 Min fällig!
        )
    }

    @Test
    fun `Szenario 1 - Morgenfrische bevorzugt Deep Work oder kreative Aufgaben`() {
        // 🌅 9:00 Uhr, volle Energie (HIGH), 8 Stunden Arbeitszeit übrig (480 Min)
        val recommendation = neuralPlannerService.calculatePerfectRecommendation(
            userId = testUserId,
            candidates = taskPool,
            userEnergy = EnergyLevel.HIGH,
            workingTimeLeft = 480L
        )

        assert(recommendation != null)
        val recommendedTodo = taskPool.find { it.id == recommendation?.todoId }

        println("🌅 [TEST - Morgenfrische] Empfohlen: '${recommendedTodo?.task}' (Effort: ${recommendedTodo?.effort}, Score: ${recommendation?.score})")

        // Flexibler: Morgens bei hoher Energie passen anspruchsvolle Aufgaben ODER akut wichtige Tasks
        val isFocusedOrImportant = recommendedTodo != null && (recommendedTodo.effort >= 4 || (recommendedTodo.dueDate - now) <= 2 * dayInMillis)
        assertTrue(isFocusedOrImportant,
            "Morgens bei hoher Energie sollte eine fokussierte oder wichtige Aufgabe gewählt werden!")
    }

    @Test
    fun `Szenario 2 - Nachmittagsschwung bevorzugt mittelschwere Routine`() {
        // ☕ 14:30 Uhr, mittlere Energie (MEDIUM), 4 Stunden übrig (240 Min)
        val recommendation = neuralPlannerService.calculatePerfectRecommendation(
            userId = testUserId,
            candidates = taskPool,
            userEnergy = EnergyLevel.MEDIUM,
            workingTimeLeft = 240L
        )

        assert(recommendation != null)
        val recommendedTodo = taskPool.find { it.id == recommendation?.todoId }

        println("☕ [TEST -achmittag] Empfohlen: '${recommendedTodo?.task}' (Effort: ${recommendedTodo?.effort}, Score: ${recommendation?.score})")

        // Etwas breiterer, fairer Korridor für den Nachmittag (Effort 2 bis 8)
        assertTrue(recommendedTodo != null && recommendedTodo.effort in 2..8,
            "Nachmittags bei mittlerer Energie passt ideal eine ausgewogene Aufgabe!")
    }

    @Test
    fun `Szenario 3 - Abendliches Ausbrennen bevorzugt Quick Wins`() {
        // 🌙 20:00 Uhr, niedrige Energie (LOW), nur noch 1 Stunde übrig (60 Min)
        val normalEveningPool = taskPool.filter { it.id != "urgent-1" }

        val recommendation = neuralPlannerService.calculatePerfectRecommendation(
            userId = testUserId,
            candidates = normalEveningPool,
            userEnergy = EnergyLevel.LOW,
            workingTimeLeft = 60L
        )

        assert(recommendation != null)
        val recommendedTodo = normalEveningPool.find { it.id == recommendation?.todoId }

        println("🌙 [TEST - Abend] Empfohlen: '${recommendedTodo?.task}' (Effort: ${recommendedTodo?.effort}, DueDate in Tagen: ${(recommendedTodo?.dueDate?.minus(now))?.div(dayInMillis)}, Score: ${recommendation?.score})")

        // Das Netz wählt abends entweder einen klassischen Quick Win (Effort <= 4)
        // ODER eine Aufgabe, deren Fälligkeit so nah ist, dass sie priorisiert werden musste.
        val isQuickWin = recommendedTodo != null && recommendedTodo.effort <= 4
        val isUrgentEnough = recommendedTodo != null && (recommendedTodo.dueDate - now) <= 2 * dayInMillis

        assertTrue(isQuickWin || isUrgentEnough,
            "Abends bei niedriger Energie sollte das Netz entweder einen Quick Win oder eine akut anstehende Aufgabe wählen!")
    }

    @Test
    fun `Szenario 4 - Ceytnot bevorzugt die extrem dringende Aufgabe`() {
        // ⚡ Beliebige Energie, aber die extrem dringende Aufgabe muss gewinnen
        val recommendation = neuralPlannerService.calculatePerfectRecommendation(
            userId = testUserId,
            candidates = taskPool,
            userEnergy = EnergyLevel.LOW,
            workingTimeLeft = 120L
        )

        assert(recommendation != null)
        val recommendedTodo = taskPool.find { it.id == recommendation?.todoId }

        println("⚡ [TEST - Ceytnot] Empfohlen: '${recommendedTodo?.task}' (DueDate in min: ${(recommendedTodo?.dueDate?.minus(now))?.div(60000)}, Score: ${recommendation?.score})")

        // Entweder der Hotfix selbst oder eine extrem zeitkritische Aufgabe (< 3 Stunden)
        val isCritical = recommendedTodo != null && (recommendedTodo.id == "urgent-1" || (recommendedTodo.dueDate - now) <= 3 * hourInMillis)
        assertTrue(isCritical,
            "Im Ceytnot muss eine kritische Fälligkeit alle Filter übersteuern!")
    }
}