package com.backend.todo_api.services

import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
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

class SmartPlannerServiceTest {

    // 1. Hier deklarieren wir unsere gemockten Abhängigkeiten
    private lateinit var todoRepository: TodoRepository
    private lateinit var userRepository: UserRepository
    private lateinit var plannerSettingsRepository: PlannerSettingsRepository
    private lateinit var focusPredictorService: FocusPredictorService
    private lateinit var smartPlannerService: SmartPlannerService
    private lateinit var preferenceRepository: UserAiPreferenceRepository

    private val testUserId = "user-123" // Deine ID ist ein String!

    @BeforeEach
    fun setUp() {
        // 2. Wir erstellen die Mocks vor JEDEM Test frisch
        todoRepository = mock(TodoRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        focusPredictorService = mock(FocusPredictorService::class.java)
        plannerSettingsRepository = mock(PlannerSettingsRepository::class.java)
        preferenceRepository = mock(UserAiPreferenceRepository::class.java)

        // 3. Wir injizieren die Mocks manuell in den Service
        smartPlannerService = SmartPlannerService(
            todoRepository, userRepository,
            plannerSettingsRepository = plannerSettingsRepository,
            preferenceRepository = preferenceRepository,
            focusPredictorService= focusPredictorService)

        // 4. GLOBALER MOCK: Der User existiert im Test immer!
        `when`(userRepository.existsById(testUserId)).thenReturn(true)
    }

    @Test
    fun `low energy test — should filter out high focus tasks`() {
        // --- GIVEN (Vorbereitung der Test-To-Dos) ---

        val heavyTask = TodoEntity(
            id = "1",
            task = "Steuererklärung machen",
            effort = 5,
            userId = testUserId
        )

        val easyTask = TodoEntity(
            id = "2",
            task = "Kaffee trinken und Mails checken",
            effort = 1,
            userId = testUserId
        )

        // Wir sagen dem TodoRepository, dass es diese beiden To-Dos ausspuckt
        `when`(todoRepository.findByUserIdAndDoneFalse(testUserId)).thenReturn(listOf(heavyTask, easyTask))

        // Wir bringen der KI bei, wie sie die Tasks im RAM bewerten soll
        `when`(focusPredictorService.predict("Steuererklärung machen")).thenReturn(FocusType.HIGH_FOCUS)
        `when`(focusPredictorService.predict("Kaffee trinken und Mails checken")).thenReturn(FocusType.LOW_FOCUS)

        // --- WHEN (Ausführung der Methode) ---
        val recommendation = smartPlannerService.calculatePerfectRecommendation(
            userId = testUserId,
            userEnergy = "low",      // Nutzer ist müde!
            workingTimeLeft = 120.0  // Zeit wäre für beides da
        )

        // --- THEN (Überprüfung) ---
        assertNotNull(recommendation, "Es muss ein To-Do empfohlen werden!")
        assertEquals("Kaffee trinken und Mails checken", recommendation?.todo?.task,
            "Bei niedriger Energie hätte kein High-Focus-Todo gefiltert werden müssen!")
    }

    @Test
    fun `high energy inside prime time — should recommend the biggest high focus task`() {
        // --- 1. GIVEN ---
        val currentHourOnComputer = java.time.LocalTime.now().hour

        // Wir erstellen künstliche Einstellungen und stellen die Slider virtuell so ein,
        // dass JETZT gerade die absolute Prime-Time des Users ist!
        val mockSettings = PlannerSettingsEntity(
            id = testUserId,
            primeTimeStartHour = currentHourOnComputer - 1, // Startete vor einer Stunde
            primeTimeEndHour = currentHourOnComputer + 1    // Geht noch eine Stunde
        )

        // Dem neuen Repository beibringen, diese Einstellungen für den User zurückzugeben
        `when`(plannerSettingsRepository.findById(testUserId)).thenReturn(java.util.Optional.of(mockSettings))

        // Der User selbst muss natürlich auch existieren für die Validierung
        `when`(userRepository.existsById(testUserId)).thenReturn(true)

        // Deine To-Dos (wie du sie perfekt gebaut hast!)
        val heavyTaskSmall = TodoEntity(id = "1", task = "Steuererklärung machen", effort = 3, userId = testUserId)
        val heavyTaskBig = TodoEntity(id = "2", task = "Service refactoring", effort = 5, userId = testUserId)
        val easyTask = TodoEntity(id = "3", task = "Kaffee trinken und Mails checken", effort = 8, userId = testUserId)

        `when`(todoRepository.findByUserIdAndDoneFalse(testUserId)).thenReturn(listOf(heavyTaskSmall, heavyTaskBig, easyTask))
        `when`(focusPredictorService.predict("Steuererklärung machen")).thenReturn(FocusType.HIGH_FOCUS)
        `when`(focusPredictorService.predict("Service refactoring")).thenReturn(FocusType.HIGH_FOCUS)
        `when`(focusPredictorService.predict("Kaffee trinken und Mails checken")).thenReturn(FocusType.LOW_FOCUS)

        // --- 2. WHEN ---
        val recommendation = smartPlannerService.calculatePerfectRecommendation(
            userId = testUserId,
            userEnergy = "high",
            workingTimeLeft = 10.0
        )

        // --- 3. THEN ---
        assertNotNull(recommendation)
        // Da wir die Slider-Einstellung passend gemockt haben, MUSS das große Refactoring gewinnen!
        assertEquals("Service refactoring", recommendation?.todo?.task,
            "Da der User sich in seiner konfigurierten Prime-Time befindet, hätte das große Refactoring gewinnen müssen!")
    }
}