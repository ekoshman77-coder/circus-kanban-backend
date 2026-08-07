package com.backend.todo_api.services

import com.backend.todo_api.data.entity.MilestoneEntity
import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateProjectDto
import com.backend.todo_api.dto.ProjectDashboardStatsDTO
import com.backend.todo_api.dto.ProjectDto
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ProjectSecurityResource
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.toSecurityResource
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val userContextResolver: UserContextResolver,
    private val permissionService: PermissionService
) {

    fun getProjectsByWithUser(userId: String?): List<ProjectDto> {
        if (userId.isNullOrBlank()) return emptyList()

        // 1. Alle Kontexte des Benutzers auflösen (RESOURCE, DEPARTMENT/COMPANY, PROJECT)
        val userContexts = userContextResolver.resolveContexts(userId)

        // 2. Maximalen zugelassenen Kontext für READ auf PROJECT ermitteln
        val maxContext = permissionService.getMaxAllowedUserContext(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = ResourceType.PROJECT
        ) ?: return emptyList() // Keine Berechtigung -> Leere Liste

        // 3. Entsprechend des ermittelten Max-Contexts dynamisch aus der DB laden:
        return when (maxContext.scope.name) {
            // ADMIN / COMPANY-Scope: maxContext.scopeInstanceId ist null -> Alle Projekte
            ScopeType.COMPANY -> {
                projectRepository.findByStatusNot("Zip")
                    .map { it.toDto() }
            }

            // MEMBER / DEPARTMENT-Scope: Nur Projekte der eigenen Abteilung
            ScopeType.DEPARTMENT -> {
                val deptId = maxContext.scopeInstanceId
                    ?: return emptyList()

                projectRepository.findByDepartmentIdAndStatusNot(deptId, "Zip")
                    .map { it.toDto() }
            }

            // PROJECT_MANAGER / PROJECT-Scope oder RESOURCE-Scope:
            // Nur Projekte, in denen der User als Projektmitglied eingetragen ist
            ScopeType.PROJECT, ScopeType.RESOURCE -> {
                projectRepository.findProjectsByMemberUserIdAndStatusNot(userId, "Zip")
                    .map { it.toDto() }
            }

            else -> emptyList()
        }
    }

    fun getProjectById(userId: String, id: String): ProjectDto {
        val  project = projectRepository.findById(id)
            .orElseThrow { RuntimeException("Projekt mit ID $id nicht gefunden") }

        // 1. Alle Kontexte des Benutzers laden
        val userContexts = userContextResolver.resolveContexts(userId)

        val canRead = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = project.toSecurityResource()
        )

        if (!canRead) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, dieses Projekt zu lesen.")
        }
        return project.toDto()
    }

    @Transactional
    fun createProject(userId: String, dto: CreateProjectDto): ProjectDto {
        // 🏗️ Wir wandeln das DTO um (das Team bleibt dabei komplett LEER)
        val projectEntity = convertToEntity(dto)

        val userContexts = userContextResolver.resolveContexts(userId)

        val canCreate = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.CREATE,
            resource = projectEntity.toSecurityResource()
        )

        if (!canCreate) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, dieses Projekt zu erzeugen.")
        }

        return projectRepository.save(projectEntity).toDto()
    }

    @Transactional
    fun updateProject(userId: String, id: String, dto: ProjectDto): ProjectDto {
        val existingProject = projectRepository.findById(id)
            .orElseThrow { RuntimeException("Projekt mit ID $id nicht gefunden") }

        // 1. Alle Kontexte des Benutzers laden
        val userContexts = userContextResolver.resolveContexts(userId)

        val canUpdate = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE,
            resource = existingProject.toSecurityResource()
        )

        if (!canUpdate) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, dieses Projekt zu bearbeiten.")
        }

        // 3. Update-Logik durchführen...
        val existingMembers = projectMemberRepository.findByProjectId(id)
        existingProject.milestones.clear()

        existingProject.title = dto.title
        existingProject.area = dto.area
        existingProject.content = dto.content
        existingProject.status = dto.status
        existingProject.departmentId = dto.departmentId

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

        existingProject.teamMemberships.clear()
        existingProject.teamMemberships.addAll(existingMembers)

        return projectRepository.save(existingProject).toDto()
    }

    @Transactional
    fun deleteProject(userId: String, id: String) {
        val project = projectRepository.findById(id)
            .orElseThrow { RuntimeException("Projekt mit ID $id wurde nicht gefunden.") }
        val userContexts = userContextResolver.resolveContexts(userId)

        val canDelete = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.DELETE,
            resource = project.toSecurityResource()
        )

        if (!canDelete) {
            throw SecurityException("Zugriff verweigert: Du hast keine Berechtigung, dieses Projekt zu löschen.")
        }
        // 🎯 SOFT DELETE statt hard delete! Die KI behält ihre Meilenstein-Daten!
        project.status = "Zip"
        projectRepository.save(project)
    }

    private fun convertToEntity(dto: CreateProjectDto): ProjectEntity {
        // 🌟 Wir erstellen die nackte Projekt-Entität
        val projectEntity = ProjectEntity(
            userId = dto.userId, // Das Feld merkt sich weiterhin, WER das Projekt erstellt hat (wichtig für Audits!)
            ideaId = dto.ideaId,
            title = dto.title,
            area = dto.area,
            content = dto.content,
            status = dto.status,
            departmentId = dto.departmentId
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

    @Transactional
    fun getDashboardStatistics(userId: String): ProjectDashboardStatsDTO {
        // 1. Das Interface von Spring Data JPA holen
        val projection = projectRepository.getDashboardStatistics(userId)

        // 2. Auslesen über die Properties und ins saubere DTO mappen
        return ProjectDashboardStatsDTO(
            totalProjects = projection.getTotalProjects(),
            totalMilestones = projection.getTotalMilestones(),
            totalTodos = projection.getTotalTodos()
        )
    }
}