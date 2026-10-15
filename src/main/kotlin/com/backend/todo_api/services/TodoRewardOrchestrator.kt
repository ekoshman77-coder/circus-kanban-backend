package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.model.RewardUpdateResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TodoRewardOrchestrator(
    private val gamificationService: GamificationService,
    private val streakService: StreakService,
    private val userRepository: UserRepository,
    private val todoRepository: TodoRepository
) {
    /**
     * Ermittelt den primären Entwickler/Empfänger für das Todo.
     */
    fun determineXpReceiverUserId(todo: TodoEntity, currentUserId: String): String {
        return todo.lastDeveloperId?.takeIf { it.isNotBlank() }
            ?: todo.assignedUserId?.takeIf { it.isNotBlank() }
            ?: todo.userId.takeIf { it.isNotBlank() }
            ?: currentUserId
    }

    @Transactional
    fun processTodoCompletionRewards(
        todo: TodoEntity,
        currentUserId: String,
        isDone: Boolean
    ): RewardUpdateResult {
        // 1. Aufwände aufteilen
        val reviewerEffort = todo.reviewerUsedEffort
        val devEffort = (todo.usedEffort - reviewerEffort).coerceAtLeast(0.0)
        val totalUsed = if (todo.usedEffort > 0) todo.usedEffort.toDouble() else 1.0

        // 2. Ziel-Entwickler bestimmen
        val devUserId = determineXpReceiverUserId(todo, currentUserId)

        // 3. STREAKS VERARBEITEN
        if (isDone) {
            // 🔒 STREAK-PUNKTE NUR BEIM ERSTEN SCHLIESSEN VERTEILEN
            if (!todo.streakAlreadyRewarded) {
                if (!todo.milestoneId.isNullOrBlank()) {
                    streakService.updateProjectStreakInfo(todo.milestoneId, todo.effort)
                }
                if (devEffort > 0.0) {
                    userRepository.findById(devUserId).ifPresent { devUser ->
                        streakService.applyStreakEffortToUser(devUser, devEffort)
                    }
                }
                if (!todo.reviewerId.isNullOrBlank() && todo.reviewerId != devUserId && reviewerEffort > 0.0) {
                    userRepository.findById(todo.reviewerId!!).ifPresent { reviewerUser ->
                        streakService.applyStreakEffortToUser(reviewerUser, reviewerEffort)
                    }
                }
                // Flag setzen, damit beim 2. Schließen keine Punkte fließen
                todo.streakAlreadyRewarded = true
                todoRepository.save(todo)
            }
        } else {
            // 🔄 WIEDERÖFFNEN AUS DONE (Korrektur/Bulgarisch-Fall): 2% Malus abziehen
            if (todo.streakAlreadyRewarded) {
                userRepository.findById(devUserId).ifPresent { devUser ->
                    streakService.deductPenaltyEffortFromUser(devUser)
                }
            }
        }
        // 4. GAMIFICATION (XP) VERARBEITEN
        var devResult: GamificationResult? = null
        if (devEffort > 0.0) {
            val devPlanned = (todo.effort * (devEffort / totalUsed)).toInt()
            devResult = gamificationService.processTodoStatusChange(devUserId, devPlanned, devEffort, isDone)
        }

        var reviewerResult: GamificationResult? = null
        if (!todo.reviewerId.isNullOrBlank() && todo.reviewerId != devUserId && reviewerEffort > 0.0) {
            val reviewerPlanned = (todo.effort * (reviewerEffort / totalUsed)).toInt()
            reviewerResult = gamificationService.processTodoStatusChange(todo.reviewerId!!, reviewerPlanned, reviewerEffort, isDone)
        }

        // 5. Passendes GamificationResult ermitteln
        val finalGamificationResult = when (currentUserId) {
            devUserId -> devResult ?: gamificationService.getGamificationState(currentUserId)
            todo.reviewerId -> reviewerResult ?: gamificationService.getGamificationState(currentUserId)
            else -> gamificationService.getGamificationState(currentUserId)
        }

        // 6. 🎯 Frische Streak-Info für den AUFRUFER laden
        val currentUser = userRepository.findById(currentUserId).orElseThrow {
            IllegalArgumentException("User $currentUserId nicht gefunden.")
        }
        val freshStreakInfo = streakService.getCurrentStreakInfo(currentUser)

        return RewardUpdateResult(
            gamificationResult = finalGamificationResult,
            streakInfo = freshStreakInfo
        )
    }

    @Transactional(readOnly = true)
    fun getCombinedRewardState(userId: String): RewardUpdateResult {
        val gamificationState = gamificationService.getGamificationState(userId)

        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User $userId nicht gefunden.")
        }
        val streakInfo = streakService.getCurrentStreakInfo(user)

        return RewardUpdateResult(
            gamificationResult = gamificationState,
            streakInfo = streakInfo
        )
    }
}