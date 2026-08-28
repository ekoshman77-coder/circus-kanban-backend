package com.backend.todo_api.services

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.OfflinePomodoroItem
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class GamificationService(
    private val userRepository: UserRepository,
    private val todoRepository: TodoRepository
) {

    // --- MATHEMATISCHE HILFSFUNKTIONEN ---
    private fun getXpRequiredForLevel(level: Int): Int {
        if (level <= 0) return 0
        if (level == 1) return 1
        return (100 * Math.pow((level - 1).toDouble(), 1.8)).toInt()
    }

    private fun calculateLevelFromXp(totalXp: Int): Int {
        if (totalXp == 0) return 0

        var level = 1
        while (totalXp >= getXpRequiredForLevel(level + 1)) {
            level++
        }
        return level
    }

    // --- DIE TITEL KI ---
    private fun generateDynamicKiTitle(userId: String, currentLevel: Int): String {
        if (currentLevel == 0) return ""

        val completedTodos = todoRepository.findAll().filter { it.userId == userId && it.done }

        val topCategory = completedTodos.groupBy { it.category }
            .maxByOrNull { it.value.size }?.key ?: "Allgemein"

        val perfectMatches = completedTodos.count { it.usedEffort == it.effort && it.effort > 0 }

        val role = when {
            currentLevel <= 0 -> ""
            currentLevel <= 3 -> listOf("Lehrling 👶", "Novize 📜", "Entdecker 🧭").random()
            currentLevel <= 7 -> listOf("Häcksler 🪓", "Mönch 🧘", "Jäger 🏹", "Spezialist 🥽").random()
            currentLevel <= 15 -> listOf("Meister 🥋", "Ninja 🥷", "Magier 🧙", "Bezwinger 🐉").random()
            else -> listOf("Legende 🏆", "Titan ⚡", "Guru 👑", "Halbgott 🌟").random()
        }

        if (role.isEmpty()) return ""

        val adjective = when {
            perfectMatches > 5 -> listOf("Chirurgischer", "Präziser", "Makelloser", "Schandfleckfreier").random()
            completedTodos.size > 20 -> listOf("Unermüdlicher", "Rastloser", "Produktiver", "Gigantischer").random()
            else -> listOf("Flinker", "Ehrgeiziger", "Aufstrebender").random()
        }

        val kompetenz = when (topCategory.lowercase()) {
            "datenbank", "sql" -> "Query-"
            "bugfixing", "bugs" -> "Bug-Killer-"
            "refactoring", "clean code" -> "Clean-Architecture-"
            "frontend", "ui", "css" -> "Pixel-"
            else -> "Task-"
        }

        return "$adjective $kompetenz$role"
    }

    // --- POMODORO VERARBEITEN ---
    @Transactional
    fun processCompletedPomodoro(userId: String, todoId: String, count: Int): GamificationResult {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }
        val gainedXp = count * 15
        return applyXpAndCheckLevelUp(user, gainedXp, triggerLevelUpCheck = true)
    }

    @Transactional
    fun processBulkPomodoros(userId: String, sessions: List<OfflinePomodoroItem>): GamificationResult {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }
        val totalGainedXp = sessions.size * 15
        return applyXpAndCheckLevelUp(user, totalGainedXp, triggerLevelUpCheck = true)
    }

    // --- REINE XP-BERECHCHNUNG FÜR EINEN SINGULÄREN USER ---
    @Transactional
    fun processTodoStatusChange(
        userId: String,
        effort: Int,
        usedEffort: Double,
        isDone: Boolean
    ): GamificationResult {
        val baseXp = 10 + (effort * 2)
        var bonusXp = 0
        if (effort > 0) {
            val usedInt = usedEffort.toInt()
            when {
                usedInt == effort -> bonusXp = 5
                usedInt < effort -> bonusXp = ((effort - usedEffort) * 2).toInt()
            }
        }

        val calculatedXp = baseXp + bonusXp
        val xpChange = if (isDone) calculatedXp else -calculatedXp

        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }
        return applyXpAndCheckLevelUp(user, xpChange, triggerLevelUpCheck = isDone)
    }

    // --- STAND ABFRAGEN ---
    fun getGamificationState(userId: String): GamificationResult {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }

        val title = generateDynamicKiTitle(userId, user.level)
        val (cleanTitle, icon) = extractTitleAndIcon(title)

        return GamificationResult(
            levelUp = false,
            currentLevel = user.level,
            levelTitle = cleanTitle,
            levelIcon = icon,
            currentXp = user.xp,
            currentLevelXpStart = getXpRequiredForLevel(user.level),
            nextLevelXpRequired = getXpRequiredForLevel(user.level + 1)
        )
    }

    private fun applyXpAndCheckLevelUp(user: UserEntity, gainedXp: Int, triggerLevelUpCheck: Boolean): GamificationResult {
        val oldLevel = user.level

        if (gainedXp < 0) {
            user.xp = (user.xp + gainedXp).coerceAtLeast(0)
        } else {
            user.xp += gainedXp
        }

        val newLevel = calculateLevelFromXp(user.xp)
        user.level = newLevel.coerceAtLeast(oldLevel)

        val isLevelUp = triggerLevelUpCheck && (user.level > oldLevel)

        if (user.level == 0) {
            user.levelTitle = ""
        } else {
            if (isLevelUp || user.levelTitle.isEmpty() || user.levelTitle == "Unbeschriebenes Blatt 📝") {
                val currentTitleInDb = user.levelTitle
                var newTitle = generateDynamicKiTitle(user.id, user.level)

                var versuche = 0
                while (newTitle == currentTitleInDb && versuche < 10) {
                    newTitle = generateDynamicKiTitle(user.id, user.level)
                    versuche++
                }
                user.levelTitle = newTitle
            }
        }

        userRepository.save(user)

        val (cleanTitle, icon) = extractTitleAndIcon(user.levelTitle)

        return GamificationResult(
            levelUp = isLevelUp,
            currentLevel = user.level,
            levelTitle = cleanTitle,
            levelIcon = icon,
            currentXp = user.xp,
            currentLevelXpStart = getXpRequiredForLevel(user.level),
            nextLevelXpRequired = getXpRequiredForLevel(user.level + 1)
        )
    }

    private fun extractTitleAndIcon(fullTitleFromDb: String): Pair<String, String> {
        if (fullTitleFromDb.isEmpty() || fullTitleFromDb == "Unbeschriebenes Blatt 📝") {
            return Pair("Unbeschriebenes Blatt", "📝")
        }

        val lastSpaceIndex = fullTitleFromDb.lastIndexOf(' ')
        if (lastSpaceIndex == -1) {
            return Pair(fullTitleFromDb, "")
        }

        val title = fullTitleFromDb.substring(0, lastSpaceIndex).trim()
        val icon = fullTitleFromDb.substring(lastSpaceIndex + 1).trim()

        return Pair(title, icon)
    }
}