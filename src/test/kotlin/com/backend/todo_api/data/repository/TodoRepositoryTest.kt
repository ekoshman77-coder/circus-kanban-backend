package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.TodoEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TodoRepositoryTest {

    @Autowired
    private lateinit var todoRepository: TodoRepository

    @Test
    fun `archiveOldPauseTickets should only archive expired uncompleted pause todos`() {
        // Arrange
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000L
        val tomorrow = now + 86400000L

        val oldPause = TodoEntity(
            task = "Alter Kaffee",
            effort = 0,
            done = false,
            dueDate = yesterday,
            isArchived = false,
            userId = "test-user"
        )

        val futurePause = TodoEntity(
            task = "Picknick am Samstag",
            effort = 0,
            done = false,
            dueDate = tomorrow,
            isArchived = false,
            userId = "test-user"
        )

        val donePause = TodoEntity(
            task = "Getrunkener Tee",
            effort = 0,
            done = true,
            dueDate = yesterday,
            isArchived = false,
            userId = "test-user"
        )

        todoRepository.saveAll(listOf(oldPause, futurePause, donePause))

        val todayMidnightMs = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Act
        val archivedCount = todoRepository.archiveOldPauseTickets(
            effort = 0,
            done = false,
            todayMidnight = todayMidnightMs
        )

        // Assert
        assertEquals(1, archivedCount, "Genau ein abgelaufenes Pauseticket sollte archiviert werden")

        val reloadedOld = todoRepository.findById(oldPause.id).get()
        val reloadedFuture = todoRepository.findById(futurePause.id).get()
        val reloadedDone = todoRepository.findById(donePause.id).get()

        assertEquals(true, reloadedOld.isArchived, "Altes Pausenticket muss archiviert sein")
        assertEquals(false, reloadedFuture.isArchived, "Zukünftiges Pausenticket muss aktiv bleiben")
        assertEquals(false, reloadedDone.isArchived, "Erledigtes Ticket muss unangetastet bleiben")
    }
}