package com.backend.todo_api.services

import com.backend.todo_api.data.entity.MilestoneEntity
import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateProjectDto
import com.backend.todo_api.dto.ProjectDashboardStatsDTO
import com.backend.todo_api.dto.ProjectDto
import com.backend.todo_api.dto.ProjectMemberDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.toEntity
import com.backend.todo_api.model.toSecurityResource
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val userContextResolver: UserContextResolver,
    private val permissionService: PermissionService,
    private val scopeRepository: ScopeRepository,
    private val userService: UserService
) {

    fun getProjectsByWithUser(userId: String?): List<ProjectDto> {
        if (userId.isNullOrBlank()) return emptyList()

        // 1. Alle Kontexte des Benutzers laden (DEPARTMENT, PROJECT, COMPANY etc.)
        val userContexts = userContextResolver.resolveContexts(userId)

        // Set verhindert doppelte Einträge (z.B. wenn ein Projekt in der Abteilung liegt UND eine direkte Mitgliedschaft existiert)
        val resultProjects = mutableSetOf<ProjectEntity>()

        // 2. Durch ALLE Kontexte des Users iterieren
        for (context in userContexts) {
            when (context.scope.name) {
                // Unternehmensweiter Zugriff (Admin) -> Sofort alle aktiven Projekte laden
                ScopeType.COMPANY -> {
                    return projectRepository.findByStatusNot("Zip")
                        .map { it.toDto() }
                }

                // Abteilungs-Zugriff -> Alle Projekte der jeweiligen Abteilung sammeln
                ScopeType.DEPARTMENT -> {
                    context.scopeInstanceId?.let { deptId ->
                        resultProjects.addAll(
                            projectRepository.findByDepartmentIdAndStatusNot(deptId, "Zip")
                        )
                    }
                }

                // Projekt- & Resource-Zugriff -> Alle Projekte laden, in denen der User persönlich als Mitglied steht
                ScopeType.PROJECT, ScopeType.RESOURCE -> {
                    resultProjects.addAll(
                        projectRepository.findProjectsByMemberUserIdAndStatusNot(userId, "Zip")
                    )
                }

                else -> {}
            }
        }

        return resultProjects.map { it.toDto() }
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
        val projectEntity = convertToEntity(dto, scopeRepository = scopeRepository)

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
            throw ActionForbiddenException("Zugriff verweigert: Du hast keine Berechtigung, dieses Projekt zu löschen.")
        }
        // 🎯 SOFT DELETE statt hard delete! Die KI behält ihre Meilenstein-Daten!
        project.status = "Zip"
        projectRepository.save(project)
    }

    private fun ProjectEntity.toDto(): ProjectDto {
        val dto = ProjectDto()
        dto.id = this.id
        dto.userId = this.userId
        dto.ideaId = this.ideaId
        dto.title = this.title
        dto.area = this.area
        dto.content = this.content
        dto.status = this.status
        dto.departmentId = this.departmentId
        dto.scope = ScopeType.valueOf(this.scope.name.name) // ScopeType Enum

        // ✨ Unified milestones list
        dto.milestones = this.milestones.map { it.toDto() }

        // ✨ Teammitglieder-Mapping mit UserService
        dto.teamMembers = this.teamMemberships.map { membership ->
            ProjectMemberDto(
                user = userService.entityToUserResponseDto(membership.user, null),
                projectRole = membership.role.name // ✨ membership.role.name ist bereits ein RoleType!
            )
        }

        return dto
    }

    private fun convertToEntity(dto: CreateProjectDto, scopeRepository: ScopeRepository): ProjectEntity {
        // 1. Grunddaten der Projekt-Entität erzeugen
        val projectEntity = ProjectEntity(
            userId = dto.userId,
            ideaId = dto.ideaId,
            title = dto.title,
            area = dto.area,
            content = dto.content,
            status = dto.status,
            departmentId = dto.departmentId,
            scope = dto.scope.toEntity(scopeRepository)
        )

        // 2. Falls beim Erstellen schon Meilensteine übergeben wurden (CreateMilestoneDto -> MilestoneEntity)
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
            projectEntity.addMilestone(mEntity) // Verbindet Meilenstein & Projekt bidirektional
        }

        // Hinweis: Das Team bleibt beim Erstellen leer, da Mitglieder erst später hinzugefügt werden.

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