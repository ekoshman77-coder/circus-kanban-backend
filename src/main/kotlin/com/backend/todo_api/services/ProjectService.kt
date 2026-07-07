package com.backend.todo_api.services

import com.backend.todo_api.data.entity.MilestoneEntity
import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateProjectDto
import com.backend.todo_api.dto.ProjectDto
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectMemberRepository: ProjectMemberRepository
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
        // 🏗️ Wir wandeln das DTO um (das Team bleibt dabei komplett LEER)
        val projectEntity = convertToEntity(dto)

        // ❌ KEIN automatischer OWNER mehr! Der Ersteller (z.B. Admin)
        // wird NICHT ungefragt in das Projektteam gedrückt.

        return projectRepository.save(projectEntity).toDto()
    }

    @Transactional
    fun updateProject(id: String, dto: CreateProjectDto): ProjectDto {
        val existingProject = projectRepository.findById(id)
            .orElseThrow { RuntimeException("Projekt mit ID $id nicht gefunden") }

        // 1. 🛡️ DEINE IDEE: Wir retten die bestehenden Mitglieder aus der Zwischentabelle!
        val existingMembers = projectMemberRepository.findByProjectId(id)

        // 2. Bestehende Meilensteine löschen (das soll so sein, weil das DTO neue liefert)
        existingProject.milestones.clear()

        // 3. Stammdaten aus dem DTO übernehmen
        existingProject.title = dto.title
        existingProject.area = dto.area
        existingProject.content = dto.content
        existingProject.status = dto.status

        // 4. Meilensteine neu mappen...
        dto.milestones.forEach { mDto ->
            val assignedUserEntity = mDto.assignedUserId?.let { userRepository.findById(it).orElse(null) }
            val mEntity = MilestoneEntity(
                title = mDto.title,
                duration = mDto.duration,
                usedDuration = mDto.usedDuration,
                status = mDto.status,
                orderIndex = mDto.orderIndex,
                assignedUser = assignedUserEntity
            )
            existingProject.addMilestone(mEntity)
        }

        // 5. 🛡️ DEINE IDEE TEIL 2: Wir weisen dem Projekt seine geretteten Mitglieder wieder zu!
        existingProject.teamMemberships.clear()
        existingProject.teamMemberships.addAll(existingMembers)

        // 6. Jetzt speichern! Hibernate sieht die vollen Members und löscht absolut GAR NICHTS!
        return projectRepository.save(existingProject).toDto()
    }

    @Transactional
    fun deleteProject(id: String) {
        if (projectRepository.existsById(id)) {
            projectRepository.deleteById(id)
        }
    }

    private fun convertToEntity(dto: CreateProjectDto): ProjectEntity {
        // 🌟 Wir erstellen die nackte Projekt-Entität
        val projectEntity = ProjectEntity(
            userId = dto.userId, // Das Feld merkt sich weiterhin, WER das Projekt erstellt hat (wichtig für Audits!)
            ideaId = dto.ideaId,
            title = dto.title,
            area = dto.area,
            content = dto.content,
            status = dto.status
        )

        // ❌ HIER WAR DIE FEHLERQUELLE: Die gesamte Schleife, die blind "DEVELOPER"
        // eingetragen hat, wird komplett gelöscht. Das Team ist beim Erstellen leer!

        // 🎯 Meilensteine umwandeln (Das bleibt so, falls beim Erstellen direkt Meilensteine mitkommen)
        dto.milestones.forEach { mDto ->
            val assignedUserEntity = mDto.assignedUserId?.let {
                userRepository.findById(it).orElse(null)
            }
            val mEntity = MilestoneEntity(
                title = mDto.title,
                duration = mDto.duration,
                usedDuration = mDto.usedDuration,
                status = mDto.status,
                orderIndex = mDto.orderIndex,
                assignedUser = assignedUserEntity
            )
            projectEntity.addMilestone(mEntity)
        }

        return projectEntity
    }
}