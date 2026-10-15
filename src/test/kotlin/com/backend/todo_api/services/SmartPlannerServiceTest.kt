package com.backend.todo_api.services

import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserAiPreferenceRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.model.FocusType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

//class SmartPlannerServiceTest {
//
//    private lateinit var todoRepository: TodoRepository
//    private lateinit var userRepository: UserRepository
//    private lateinit var plannerSettingsRepository: PlannerSettingsRepository
//    private lateinit var focusPredictorService: FocusPredictorService
//    private lateinit var bayesPlannerService: BayesPlannerService
//    private lateinit var preferenceRepository: UserAiPreferenceRepository
//    private lateinit var todoService: TodoService
//
//    private val testUserId = "user-123"
//
//    @BeforeEach
//    fun setUp() {
//        todoRepository = mock(TodoRepository::class.java)
//        userRepository = mock(UserRepository::class.java)
//        focusPredictorService = mock(FocusPredictorService::class.java)
//        plannerSettingsRepository = mock(PlannerSettingsRepository::class.java)
//        preferenceRepository = mock(UserAiPreferenceRepository::class.java)
//        todoService = mock(TodoService::class.java)
//        // Wir weisen den Mock an, die echte Mapping-Logik zu benutzen!
////        `when`(todoService.mapToDto(any())).thenCallRealMethod()
//
//        bayesPlannerService = BayesPlannerService(
//            plannerSettingsRepository = plannerSettingsRepository,
//            preferenceRepository = preferenceRepository,
//            focusPredictorService = focusPredictorService,
//
//        )
//
//        `when`(userRepository.existsById(testUserId)).thenReturn(true)
//    }
//
//    @Test
//    fun `low energy test — should filter out high focus tasks`() {
//        // --- GIVEN ---
//        val heavyTask = TodoEntity(id = "1", task = "Steuererklärung machen", effort = 5, userId = testUserId)
//        val easyTask = TodoEntity(id = "2", task = "Kaffee trinken und Mails checken", effort = 1, userId = testUserId)
//
//        // 1. Richtige Repository-Methode mocken!
//        `when`(todoRepository.findActivePlannerTodosForUser(testUserId)).thenReturn(listOf(heavyTask, easyTask))
//
//        // 2. Die fehlenden Repositories mit leeren/Standard-Daten füttern, damit der Service nicht stolpert!
//        `when`(plannerSettingsRepository.findById(testUserId)).thenReturn(java.util.Optional.empty())
//        `when`(preferenceRepository.findByUserId(testUserId)).thenReturn(emptyList())
//
//        // 3. KI-Vorhersage
//        `when`(focusPredictorService.predict("Steuererklärung machen")).thenReturn(FocusType.HIGH_FOCUS)
//        `when`(focusPredictorService.predict("Kaffee trinken und Mails checken")).thenReturn(FocusType.LOW_FOCUS)
//
//        // 4. MOCK FÜR TODO-SERVICE OHNE ANY()! Wir sagen ihm exakt, was er bei der easyTask tun soll
//        val easyDto = com.backend.todo_api.dto.TodoDto(
//            id = "2",
//            task = "Kaffee trinken und Mails checken",
//            effort = 1,
//            userId = testUserId
//        )
//        `when`(todoService.mapToDto(easyTask)).thenReturn(easyDto)
//
//        // --- WHEN ---
//        val recommendation = bayesPlannerService.calculatePerfectRecommendation(
//            userId = testUserId,
//            userEnergy = "low",
//            workingTimeLeft = 120.0
//        )
//
//        // --- THEN ---
//        assertNotNull(recommendation, "Es muss ein To-Do empfohlen werden!")
//        assertEquals("Kaffee trinken und Mails checken", recommendation?.todo?.task,
//            "Bei niedriger Energie hätte kein High-Focus-Todo gefiltert werden müssen!")
//    }
//
//    @Test
//    fun `high energy inside prime time — should recommend the biggest high focus task`() {
//        // --- 1. GIVEN ---
//        val currentHourOnComputer = java.time.LocalTime.now().hour
//
//        val mockSettings = PlannerSettingsEntity(
//            id = testUserId,
//            primeTimeStartHour = currentHourOnComputer - 1,
//            primeTimeEndHour = currentHourOnComputer + 1
//        )
//
//        `when`(plannerSettingsRepository.findById(testUserId)).thenReturn(java.util.Optional.of(mockSettings))
//        `when`(userRepository.existsById(testUserId)).thenReturn(true)
//
//        // 🛡️ Für diesen Test mocken wir auch hier die Präferenzen sauber mit einer leeren Liste
//        `when`(preferenceRepository.findByUserId(testUserId)).thenReturn(emptyList())
//
//        val heavyTaskSmall = TodoEntity(id = "1", task = "Steuererklärung machen", effort = 3, userId = testUserId)
//        val heavyTaskBig = TodoEntity(id = "2", task = "Service refactoring", effort = 5, userId = testUserId)
//        val easyTask = TodoEntity(id = "3", task = "Kaffee trinken und Mails checken", effort = 8, userId = testUserId)
//
//        // 🎯 Auf die neue Repository-Methode umgestellt
//        `when`(todoRepository.findActivePlannerTodosForUser(testUserId)).thenReturn(listOf(heavyTaskSmall, heavyTaskBig, easyTask))
//
//        `when`(focusPredictorService.predict("Steuererklärung machen")).thenReturn(FocusType.HIGH_FOCUS)
//        `when`(focusPredictorService.predict("Service refactoring")).thenReturn(FocusType.HIGH_FOCUS)
//        `when`(focusPredictorService.predict("Kaffee trinken und Mails checken")).thenReturn(FocusType.LOW_FOCUS)
//
//        // 🎯 PRÄZISES MOCKING OHNE ANY(): Wir sagen dem TodoService exakt,
//        // welches DTO er für den erwarteten Gewinner ("Service refactoring") bauen soll!
//        val heavyDtoBig = com.backend.todo_api.dto.TodoDto(
//            id = "2",
//            task = "Service refactoring",
//            effort = 5,
//            userId = testUserId
//        )
//        `when`(todoService.mapToDto(heavyTaskBig)).thenReturn(heavyDtoBig)
//
//        // --- 2. WHEN ---
//        val recommendation = bayesPlannerService.calculatePerfectRecommendation(
//            userId = testUserId,
//            userEnergy = "high",
//            workingTimeLeft = 10.0
//        )
//
//        // --- 3. THEN ---
//        assertNotNull(recommendation)
//        assertEquals("Service refactoring", recommendation?.todo?.task,
//            "Da der User sich in seiner konfigurierten Prime-Time befindet, hätte das große Refactoring gewinnen müssen!")
//    }
//}