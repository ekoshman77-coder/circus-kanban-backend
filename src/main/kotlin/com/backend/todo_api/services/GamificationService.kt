package com.backend.todo_api.services

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.TodoRepository // Neu injizieren!
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.OfflinePomodoroItem
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class GamificationService(
    private val userRepository: UserRepository,
    private val todoRepository: TodoRepository // Brauchen wir, um Statistiken für die KI zu lesen
) {

    // --- MATHEMATISCHE HILFSFUNKTIONEN ---
    private fun getXpRequiredForLevel(level: Int): Int {
        if (level <= 0) return 0
        if (level == 1) return 1 // 🎯 1 XP reicht bereits, um Level 1 freizuschalten!
        return (100 * Math.pow((level - 1).toDouble(), 1.8)).toInt()
    }

    private fun calculateLevelFromXp(totalXp: Int): Int {
        if (totalXp == 0) return 0 // Ganz neuer User bleibt Level 0

        var level = 1
        while (totalXp >= getXpRequiredForLevel(level + 1)) {
            level++
        }
        return level
    }

    // --- DIE TITEL KI ---
    private fun generateDynamicKiTitle(userId: String, currentLevel: Int): String {
        // 🛑 WICHTIG: Wenn das Level 0 ist, darf die KI KEINEN Namen zusammenbauen!
        if (currentLevel == 0) {
            return ""
        }

        // Die KI holt sich alle erledigten Aufgaben des Users aus der DB
        val completedTodos = todoRepository.findAll().filter { it.userId == userId && it.done }

        // Mustersuche (Meistgenutzte Kategorie)
        val topCategory = completedTodos.groupBy { it.category }
            .maxByOrNull { it.value.size }?.key ?: "Allgemein"

        // Präzisions-Messung
        val perfectMatches = completedTodos.count { it.usedEffort == it.effort && it.effort > 0 }

        // 1. Die Rolle (Jetzt mit scharfem Blick auf die Level-Stufen)
        val role = when {
            currentLevel <= 0 -> "" // Zur Sicherheit, falls hier doch etwas durchrutscht
            currentLevel <= 3 -> listOf("Lehrling 👶", "Novize 📜", "Entdecker 🧭").random()
            currentLevel <= 7 -> listOf("Häcksler 🪓", "Mönch 🧘", "Jäger 🏹", "Spezialist 🥽").random()
            currentLevel <= 15 -> listOf("Meister 🥋", "Ninja 🥷", "Magier 🧙", "Bezwinger 🐉").random()
            else -> listOf("Legende 🏆", "Titan ⚡", "Guru 👑", "Halbgott 🌟").random()
        }

        // Falls aus irgendeinem Grund kein Level-Titel generiert werden kann (Level 0)
        if (role.isEmpty()) return ""

        // 2. Das Adjektiv
        val adjective = when {
            perfectMatches > 5 -> listOf("Chirurgischer", "Präziser", "Makelloser", "Schandfleckfreier").random()
            completedTodos.size > 20 -> listOf("Unermüdlicher", "Rastloser", "Produktiver", "Gigantischer").random()
            else -> listOf("Flinker", "Ehrgeiziger", "Aufstrebender").random()
        }

        // 3. Die Fach-Kompetenz
        val kompetenz = when (topCategory.lowercase()) {
            "datenbank", "sql" -> "Query-"
            "bugfixing", "bugs" -> "Bug-Killer-"
            "refactoring", "clean code" -> "Clean-Architecture-"
            "frontend", "ui", "css" -> "Pixel-"
            else -> "Task-"
        }

        // Die KI fusioniert das Wissen
        return "$adjective $kompetenz$role"
    }

    // --- EINZELNES POMODORO VERARBEITEN (ONLINE) ---
    @Transactional
    fun processCompletedPomodoro(userId: String, todoId: String, count: Int): GamificationResult {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }
        val gainedXp = count * 15
        return applyXpAndCheckLevelUp(user, gainedXp, triggerLevelUpCheck = true)
    }

    // --- OFFLINE POMODOROS VERARBEITEN (BULK FROM TRAIN) ---
    @Transactional
    fun processBulkPomodoros(userId: String, sessions: List<OfflinePomodoroItem>): GamificationResult {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }
        val totalGainedXp = sessions.size * 15
        return applyXpAndCheckLevelUp(user, totalGainedXp, triggerLevelUpCheck = true)
    }

    // --- BESTEHENDES UPDATE FÜR TODO-STATUS-CHANGES ANPASSEN ---
    @Transactional
    fun processTodoStatusChange(userId: String, effort: Int, usedEffort: Int, isDone: Boolean): GamificationResult {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }

        // 1. Reinen XP-Pool berechnen
        val baseXp = 10 + (effort * 2)
        var bonusXp = 0
        if (effort > 0) {
            when {
                usedEffort == effort -> bonusXp = 5
                usedEffort < effort -> bonusXp = (effort - usedEffort) * 2
            }
        }
        val totalXpPool = baseXp + bonusXp

        // 2. Bestimmen, ob wir Punkte geben (beim Schließen) oder abziehen (beim Wieder-Öffnen)
        val xpChange = if (isDone) totalXpPool else -totalXpPool

        // 3. Übergabe an die Zentralmethode (Level-Up-Check nur erlauben, wenn das To-Do geschlossen wurde)
        return applyXpAndCheckLevelUp(user, xpChange, triggerLevelUpCheck = isDone)
    }

    // --- STAND ABFRAGEN ---
    fun getGamificationState(userId: String): GamificationResult {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }

        val title = generateDynamicKiTitle(userId, user.level)

        return GamificationResult(
            levelUp = false,
            currentLevel = user.level,
            levelTitle = title,
            currentXp = user.xp,
            currentLevelXpStart = getXpRequiredForLevel(user.level),
            nextLevelXpRequired = getXpRequiredForLevel(user.level + 1)
        )
    }

    private fun applyXpAndCheckLevelUp(user: UserEntity, gainedXp: Int, triggerLevelUpCheck: Boolean): GamificationResult {
        val oldLevel = user.level

        // 1. XP berechnen und anpassen
        if (gainedXp < 0) {
            user.xp = (user.xp + gainedXp).coerceAtLeast(0)
        } else {
            user.xp += gainedXp
        }

        // 2. Neues Level basierend auf der mathematischen Kurve bestimmen
        val newLevel = calculateLevelFromXp(user.xp)
        user.level = newLevel.coerceAtLeast(oldLevel)

        // 3. Ein echtes Level-Up liegt nur vor, wenn das neue Level höher als das alte ist
        val isLevelUp = triggerLevelUpCheck && (user.level > oldLevel)

        // 🛑 DIE SICHERHEITS-BREMSE FÜR LEVEL 0:
        if (user.level == 0) {
            // Wenn der User auf Level 0 ist (oder durch Punktabzug wieder dort landet),
            // wird die KI komplett ignoriert und der Titel gelöscht!
            user.levelTitle = ""
        } else {
            // 🔥 Erst WENN das Level größer als 0 ist, darf die KI überhaupt nachdenken:
            // Wir triggern sie bei einem echten Level-Up ODER wenn der User auf Level 1+ ist, aber noch keinen Titel hat.
            if (isLevelUp || user.levelTitle.isEmpty() || user.levelTitle == "Unbeschriebenes Blatt 📝") {
                val currentTitleInDb = user.levelTitle
                var newTitle = generateDynamicKiTitle(user.id, user.level)

                // Schleife gegen das "Titel-Springen/Wiederholen" bei Level-Ups
                var versuche = 0
                while (newTitle == currentTitleInDb && versuche < 10) {
                    newTitle = generateDynamicKiTitle(user.id, user.level)
                    versuche++
                }
                user.levelTitle = newTitle
            }
        }

        // 4. In der Datenbank speichern
        userRepository.save(user)

        // 5. Ergebnis für das Frontend aufbereiten
        return GamificationResult(
            levelUp = isLevelUp,
            currentLevel = user.level,
            // Wenn das Feld in der DB leer ist (weil Level 0), zeigen wir den schönen Platzhalter
            levelTitle = user.levelTitle.ifEmpty { "Unbeschriebenes Blatt 📝" },
            currentXp = user.xp,
            currentLevelXpStart = getXpRequiredForLevel(user.level),
            nextLevelXpRequired = getXpRequiredForLevel(user.level + 1)
        )
    }
}