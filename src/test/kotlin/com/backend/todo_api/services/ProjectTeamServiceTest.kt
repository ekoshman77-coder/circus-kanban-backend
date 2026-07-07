package com.backend.todo_api.services

import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.CoffeeAccountRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.exceptions.ProjectNotFoundException
import com.backend.todo_api.exceptions.TeamValidationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import java.util.*

class ProjectTeamServiceTest {

    // Wir mocken alle Repositories, die der Service braucht
    private val projectRepository = mock(ProjectRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val coffeeAccountRepository = mock(CoffeeAccountRepository::class.java)
    private val projectMemberRepository = mock(ProjectMemberRepository::class.java)

    // Unser Test-Objekt (das Gehirn)
    private val projectTeamService = ProjectTeamService(
        projectRepository, userRepository, coffeeAccountRepository, projectMemberRepository
    )

    @Test
    fun `assignUserToProject - sollte Exception werfen wenn Rolle blank ist`() {
        // Act & Assert
        val exception = assertThrows<TeamValidationException> {
            projectTeamService.assignUserToProject("proj-1", "user-1", "   ")
        }
        assertEquals("Es muss zwingend eine Projekt-Rolle übergeben werden!", exception.message)
    }

    @Test
    fun `assignUserToProject - sollte Exception werfen wenn Projekt nicht existiert`() {
        // Arrange
        `when`(projectRepository.findById("invalid-proj")).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<ProjectNotFoundException> {
            projectTeamService.assignUserToProject("invalid-proj", "user-1", "DEVELOPER")
        }
    }

    @Test
    fun `assignUserToProject - sollte Exception werfen wenn User nicht existiert`() {
        // Arrange
        val mockProject = ProjectEntity(id = "proj-1")
        `when`(projectRepository.findById("proj-1")).thenReturn(Optional.of(mockProject))
        `when`(userRepository.findById("invalid-user")).thenReturn(Optional.empty())

        // Act & Assert
        assertThrows<UserNotFoundException> {
            projectTeamService.assignUserToProject("proj-1", "invalid-user", "DEVELOPER")
        }
    }

    @Test
    fun `assignUserToProject - Happy Path - sollte User erfolgreich zuweisen`() {
        // Arrange
        val mockProject = ProjectEntity(id = "proj-1")
        val mockUser = UserEntity(id = "user-1", firstName = "Max", lastName = "Mustermann", username = "max")

        `when`(projectRepository.findById("proj-1")).thenReturn(Optional.of(mockProject))
        `when`(userRepository.findById("user-1")).thenReturn(Optional.of(mockUser))

        // 🎯 HIER ANPASSEN: Mock auf die neue ID-basierte Methode umstellen!
        `when`(projectMemberRepository.findByUserIdAndProjectId("user-1", "proj-1")).thenReturn(null)

        `when`(coffeeAccountRepository.findById("user-1")).thenReturn(Optional.empty())

        // Act
        val result = projectTeamService.assignUserToProject("proj-1", "user-1", "DEVELOPER")

        // Assert
        assertNotNull(result)
        assertEquals("DEVELOPER", result.projectRole)
        verify(projectMemberRepository, times(1)).save(any())
    }
}