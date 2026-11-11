package com.backend.todo_api.services

import com.backend.todo_api.data.repository.TodoRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class PauseCleanupServiceTest {

    private lateinit var todoRepository: TodoRepository
    private lateinit var pauseCleanupService: PauseCleanupService

    @BeforeEach
    fun setUp() {
        todoRepository = mock(TodoRepository::class.java)
        pauseCleanupService = PauseCleanupService(todoRepository)
    }

    @Test
    fun `should archive old pause tickets correctly at midnight`() {
        // Arrange
        val expectedMidnight = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Wir simulieren, dass 3 Tickets archiviert wurden
        `when`(todoRepository.archiveOldPauseTickets(eq(0), eq(false), anyLong()))
            .thenReturn(3)

        // Act (wir triggern die private Logik über den Server-Start-Event oder machen eine testbare Methode)
        // Angenommen, executeCleanup ist internal oder wir rufen cleanupOasisOnServerStart auf:
        pauseCleanupService.cleanupOasisOnServerStart()

        // Assert
        verify(todoRepository, times(1)).archiveOldPauseTickets(
            effort = eq(0),
            done = eq(false),
            todayMidnight = eq(expectedMidnight)
        )
    }
}