package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.StreakInfoDto
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class TodoRewardOrchestratorTest {

    private val gamificationService: GamificationService = mockk()
    private val streakService: StreakService = mockk()
    private val userRepository: UserRepository = mockk()
    private val todoRepository: TodoRepository = mockk()

    private lateinit var orchestrator: TodoRewardOrchestrator

    private val devId = "dev-123"
    private val reviewerId = "rev-456"
    private val dummyStreakInfo = StreakInfoDto(
        streakDays = 3,
        batteryPercentage = 80,
        pufferDaysRemaining = 2.0,
        isShieldActive = false,
        infoText = "Akku geladen"
    )
    private val dummyGamificationResult = GamificationResult(
        levelUp = false,
        currentLevel = 2,
        levelTitle = "Novize",
        levelIcon = "📜",
        currentXp = 100,
        currentLevelXpStart = 50,
        nextLevelXpRequired = 200
    )

    @BeforeEach
    fun setUp() {
        orchestrator = TodoRewardOrchestrator(
            gamificationService,
            streakService,
            userRepository,
            todoRepository
        )
    }

    @Test
    fun `processTodoCompletionRewards sollte beim ERSTEN Schliessen Dev und Reviewer belohnen`() {
        // GIVEN: Todo ist 4.0 effort (2.0 Dev, 2.0 Reviewer), noch nicht belohnt
        val todo = TodoEntity(
            id = "todo-chinesisch",
            task = "Chinesische Übersetzung 🇨🇳",
            effort = 4,
            usedEffort = 4,
            userId = devId,
            lastDeveloperId = devId,
            reviewerId = reviewerId,
            reviewerUsedEffort = 2.0,
            milestoneId = "ms-1",
            done = true,
            streakAlreadyRewarded = false
        )

        val devUser = UserEntity(id = devId, username = "Developer")
        val reviewerUser = UserEntity(id = reviewerId, username = "Reviewer")

        every { userRepository.findById(devId) } returns Optional.of(devUser)
        every { userRepository.findById(reviewerId) } returns Optional.of(reviewerUser)
        every { todoRepository.save(any()) } answers { firstArg() }

        // Mocks für Streaks & XP
        every { streakService.updateProjectStreakInfo("ms-1", 4) } just Runs
        every { streakService.applyStreakEffortToUser(devUser, 2.0) } returns devUser
        every { streakService.applyStreakEffortToUser(reviewerUser, 2.0) } returns reviewerUser
        every { gamificationService.processTodoStatusChange(any(), any(), any(), true) } returns dummyGamificationResult
        every { streakService.getCurrentStreakInfo(devUser) } returns dummyStreakInfo

        // WHEN
        val result = orchestrator.processTodoCompletionRewards(todo, devId, isDone = true)

        // THEN
        // 1. Entität wurde als belohnt markiert & gespeichert
        assertTrue(todo.streakAlreadyRewarded, "Flag streakAlreadyRewarded muss true werden!")
        verify(exactly = 1) { todoRepository.save(todo) }

        // 2. Streaks für beide Parteien & Projekt verteilt
        verify(exactly = 1) { streakService.updateProjectStreakInfo("ms-1", 4) }
        verify(exactly = 1) { streakService.applyStreakEffortToUser(devUser, 2.0) }
        verify(exactly = 1) { streakService.applyStreakEffortToUser(reviewerUser, 2.0) }

        // 3. XP für beide verteilt (Dev: effort 2.0, Reviewer: effort 2.0)
        verify(exactly = 1) { gamificationService.processTodoStatusChange(devId, 2, 2.0, true) }
        verify(exactly = 1) { gamificationService.processTodoStatusChange(reviewerId, 2, 2.0, true) }
    }

    @Test
    fun `processTodoCompletionRewards sollte beim ZWEITEN Schliessen KEINE Streaks vergeben aber XP neu verarbeiten`() {
        // GIVEN: Todo war bereits belohnt (streakAlreadyRewarded = true)
        val todo = TodoEntity(
            id = "todo-chinesisch",
            task = "Chinesische Übersetzung 🇨🇳",
            effort = 4,
            usedEffort = 4,
            userId = devId,
            lastDeveloperId = devId,
            reviewerId = reviewerId,
            reviewerUsedEffort = 2.0,
            milestoneId = "ms-1",
            done = true,
            streakAlreadyRewarded = true
        )

        val devUser = UserEntity(id = devId)

        every { userRepository.findById(devId) } returns Optional.of(devUser)
        every { gamificationService.processTodoStatusChange(any(), any(), any(), true) } returns dummyGamificationResult
        every { streakService.getCurrentStreakInfo(devUser) } returns dummyStreakInfo

        // WHEN
        orchestrator.processTodoCompletionRewards(todo, devId, isDone = true)

        // THEN
        // 1. KEINE Streak-Aufrufe und kein DB-Save fürs Flag!
        verify(exactly = 0) { streakService.updateProjectStreakInfo(any(), any()) }
        verify(exactly = 0) { streakService.applyStreakEffortToUser(any(), any()) }
        verify(exactly = 0) { todoRepository.save(any()) }

        // 2. 🎯 XP wird jedoch für Dev UND Reviewer verarbeitet (Gamification erlaubt Re-Score)
        verify(exactly = 1) { gamificationService.processTodoStatusChange(devId, 2, 2.0, true) }
        verify(exactly = 1) { gamificationService.processTodoStatusChange(reviewerId, 2, 2.0, true) }
    }

    @Test
    fun `processTodoCompletionRewards sollte beim Wiederoeffnen Malus an Dev vergeben UND XP fuer Dev und Reviewer abziehen`() {
        // GIVEN: Task wird wiedergeöffnet (isDone = false)
        val todo = TodoEntity(
            id = "todo-chinesisch",
            task = "Chinesische Übersetzung 🇨🇳",
            effort = 4,
            usedEffort = 4,
            userId = devId,
            lastDeveloperId = devId,
            reviewerId = reviewerId,
            reviewerUsedEffort = 2.0,
            done = false,
            streakAlreadyRewarded = true
        )

        val devUser = UserEntity(id = devId)

        every { userRepository.findById(devId) } returns Optional.of(devUser)
        every { streakService.deductPenaltyEffortFromUser(devUser) } just Runs
        every { gamificationService.processTodoStatusChange(any(), any(), any(), false) } returns dummyGamificationResult
        every { streakService.getCurrentStreakInfo(devUser) } returns dummyStreakInfo

        // WHEN
        orchestrator.processTodoCompletionRewards(todo, devId, isDone = false)

        // THEN
        // 1. 2% Streak-Malus exakt einmal für den Dev
        verify(exactly = 1) { streakService.deductPenaltyEffortFromUser(devUser) }

        // 2. 🎯 XP-Abzug (isDone = false) für Dev UND Reviewer!
        verify(exactly = 1) { gamificationService.processTodoStatusChange(devId, 2, 2.0, false) }
        verify(exactly = 1) { gamificationService.processTodoStatusChange(reviewerId, 2, 2.0, false) }
    }

    @Test
    fun `processTodoCompletionRewards sollte XP korrekt anteilig berechnen wenn Effort ungleich aufgeteilt ist`() {
        // GIVEN: Total Effort 6.0 (Dev = 4.0, Reviewer = 2.0)
        val todo = TodoEntity(
            id = "todo-custom-effort",
            task = "Großes Refactoring",
            effort = 6,
            usedEffort = 6,
            userId = devId,
            lastDeveloperId = devId,
            reviewerId = reviewerId,
            reviewerUsedEffort = 2.0,
            done = true,
            streakAlreadyRewarded = false
        )

        val devUser = UserEntity(id = devId)
        val reviewerUser = UserEntity(id = reviewerId)

        every { userRepository.findById(devId) } returns Optional.of(devUser)
        every { userRepository.findById(reviewerId) } returns Optional.of(reviewerUser)
        every { todoRepository.save(any()) } answers { firstArg() }
        every { streakService.applyStreakEffortToUser(any(), any()) } returns devUser
        every { gamificationService.processTodoStatusChange(any(), any(), any(), true) } returns dummyGamificationResult
        every { streakService.getCurrentStreakInfo(devUser) } returns dummyStreakInfo

        // WHEN
        orchestrator.processTodoCompletionRewards(todo, devId, isDone = true)

        // THEN:
        // Dev bekommt 4/6 des Geplanten -> plannedEffort = 4
        verify(exactly = 1) { gamificationService.processTodoStatusChange(devId, 4, 4.0, true) }
        // Reviewer bekommt 2/6 des Geplanten -> plannedEffort = 2
        verify(exactly = 1) { gamificationService.processTodoStatusChange(reviewerId, 2, 2.0, true) }
    }
}