package com.backend.todo_api.services

import com.backend.todo_api.data.repository.LevelRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.GamificationResult
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class GamificationService(
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository
) {

    @Transactional
    fun processTodoStatusChange(
        userId: String,
        effort: Int,
        usedEffort: Int, // ⏱️ NEU: Der tatsächliche Aufwand aus dem Angular-Popup
        isDone: Boolean
    ): GamificationResult {
        // 1. User aus der DB holen
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User mit der ID $userId wurde nicht gefunden.")
        }

        // 2. Alle Levels aus der DB in den RAM laden
        val allLevels = levelRepository.findAll()

// 3. XP-Veränderung berechnen
        val baseXp = 10 + (effort * 2)
        var bonusXp = 0

        // Den Bonus berechnen wir IMMER, egal ob der User schließt oder öffnet!
        when {
            // Perfekte Punktlandung
            usedEffort == effort -> {
                bonusXp = 5
            }
            // Schneller als geplant
            usedEffort < effort -> {
                val savedTime = effort - usedEffort
                bonusXp = savedTime * 2
            }
            // Länger als geplant
            usedEffort > effort -> {
                bonusXp = 0
            }
        }

        val totalXpPool = baseXp + bonusXp

        if (isDone) {
            // --- BEIM SCHLIESSEN ---
            user.xp += totalXpPool
        } else {
            // --- BEIM WIEDERÖFFNEN ---
            user.xp = (user.xp - totalXpPool).coerceAtLeast(0)
        }

        // 4. 🛡️ RECHNERISCHES Level anhand der aktuellen XP bestimmen
        val calculatedLevel = allLevels
            .filter { user.xp >= it.requiredXp }
            .maxByOrNull { it.level }
            ?: allLevels.find { it.level == 1 }
            ?: throw IllegalStateException("Level 1 fehlt!")

        // 5. 🔥 DER RETTER: Was ist höher? Das errechnete Level oder das bereits bestehende?
        val oldLevel = user.level
        user.level = calculatedLevel.level.coerceAtLeast(oldLevel)

        // User abspeichern
        userRepository.save(user)

        // 6. Für das Frontend brauchen wir das MATCHING Level-Objekt, um den Titel zu lesen
        val matchingLevel = allLevels.find { it.level == user.level } !!

        // 7. Das nächste Level suchen (basiert jetzt sicher auf dem ungesunkenen Level)
        val nextLevelEntity = allLevels.find { it.level == user.level + 1 }

        val currentLevelXpStart = matchingLevel.requiredXp
        val nextLevelXpRequired = nextLevelEntity?.requiredXp ?: matchingLevel.requiredXp

        return GamificationResult(
            levelUp = (isDone && user.level > oldLevel),
            currentLevel = user.level,
            levelTitle = matchingLevel.title,
            currentXp = user.xp,
            currentLevelXpStart = currentLevelXpStart,
            nextLevelXpRequired = nextLevelXpRequired
        )
    }

    fun getGamificationState(userId: String): GamificationResult {
        // 1. User aus der DB holen
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User mit der ID $userId wurde nicht gefunden.")
        }

        // 2. 🛡️ SICHER: Über die fachliche Methode das aktuelle Level laden
        val currentLevelEntity = levelRepository.findByLevel(user.level)
            .orElseGet {
                levelRepository.findByLevel(1)
                    .orElseThrow { IllegalStateException("Level 1 fehlt in der Datenbank!") }
            }

        // 3. 🛡️ SICHER: Das nächste Level laden
        val nextLevelEntity = levelRepository.findByLevel(user.level + 1).orElse(null)

        val currentLevelXpStart = currentLevelEntity.requiredXp
        val nextLevelXpRequired = nextLevelEntity?.requiredXp ?: currentLevelEntity.requiredXp

        return GamificationResult(
            levelUp = false,
            currentLevel = user.level,
            levelTitle = currentLevelEntity.title,
            currentXp = user.xp,
            currentLevelXpStart = currentLevelXpStart,
            nextLevelXpRequired = nextLevelXpRequired
        )
    }
}