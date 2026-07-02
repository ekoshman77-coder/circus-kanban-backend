package com.backend.todo_api.services

import com.backend.todo_api.data.entity.MilestoneEntity
import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateProjectDto
import com.backend.todo_api.dto.ProjectDto
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) {

    fun getProjectsByWithUser(userId: String?): List<ProjectDto> {
        return if (userId == null) projectRepository.findAll().map { it.toDto() }
                else projectRepository.findByUserId(userId).map { it.toDto() }
    }

    fun getProjectById(id: String): ProjectDto {
        return projectRepository.findById(id)
            .map { it.toDto() }
            .orElseThrow { RuntimeException("Projekt mit ID $id wurde nicht gefunden.") }
    }

    @Transactional
    fun createProject(dto: CreateProjectDto): ProjectDto {
        // ✨ Hier nutzen wir die saubere Mapping-Methode des Services!
        val projectEntity = convertToEntity(dto)
        return projectRepository.save(projectEntity).toDto()
    }

    @Transactional
    fun updateProject(id: String, dto: CreateProjectDto): ProjectDto {
        val existing = projectRepository.findById(id)
            .orElseThrow { RuntimeException("Projekt mit ID $id nicht gefunden") }

        // Wir mappen das DTO zu einer temporären neuen Entity
        val updatedFields = convertToEntity(dto)

        // Werte übertragen (ID des bestehenden Projekts bleibt erhalten)
        existing.title = updatedFields.title
        existing.area = updatedFields.area
        existing.content = updatedFields.content
        existing.status = updatedFields.status

        // Listen sauber aktualisieren
        existing.teamMembers.clear()
        existing.teamMembers.addAll(updatedFields.teamMembers)

        existing.milestones.clear()
        updatedFields.milestones.forEach { existing.addMilestone(it) }

        return projectRepository.save(existing).toDto()
    }

    @Transactional
    fun deleteProject(id: String) {
        if (projectRepository.existsById(id)) {
            projectRepository.deleteById(id)
        }
    }

    // 🛠️ DEINE GENIALE MAPPING-METHODE:
    // Sie kapselt die Logik perfekt, und nur der Service steuert die Repositories!
    private fun convertToEntity(dto: CreateProjectDto): ProjectEntity {
        val projectEntity = ProjectEntity(
            userId = dto.userId,
            ideaId = dto.ideaId,
            title = dto.title,
            area = dto.area,
            content = dto.content,
            status = dto.status
        )

        // 👤 Teammitglieder über das Repository auflösen (Vollkommen legitim im Service!)
        val members = userRepository.findAllById(dto.teamMemberIds)
        projectEntity.teamMembers = members.toMutableList()

        // 🎯 Meilensteine umwandeln und den neuen orderIndex mitspeichern!
        dto.milestones.forEach { mDto ->
            val assignedUserEntity = mDto.assignedUserId?.let {
                userRepository.findById(it).orElse(null)
            }
            val mEntity = MilestoneEntity(
                title = mDto.title,
                duration = mDto.duration,
                usedDuration = mDto.usedDuration,
                status = mDto.status,
                orderIndex = mDto.orderIndex, // 🆕 Hier wird er ausgelesen!
                assignedUser = assignedUserEntity
            )
            projectEntity.addMilestone(mEntity)
        }

        return projectEntity
    }
}