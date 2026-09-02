package com.backend.todo_api.data.entity

import com.backend.todo_api.dto.ProjectDto
import com.backend.todo_api.dto.UserDto
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "projects")
class ProjectEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: String = "proj_" + UUID.randomUUID().toString().take(11),

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "idea_id", nullable = false)
    var ideaId: String = "",

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    var area: String = "",

    @Column(columnDefinition = "TEXT", nullable = true)
    var content: String? = null,

    @Column(nullable = false)
    var status: String = "Calculation",

    @Column(name = "department_id", nullable = false)
    var departmentId: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_id", nullable = false)
    var scope: ScopeEntity = ScopeEntity(),

    @Column(name = "project_streak_covered_until")
    var projectStreakCoveredUntil: LocalDateTime? = null,

    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var milestones: MutableList<MilestoneEntity> = mutableListOf(),

    // 🚀 HIER WAR DER FEHLER: Wir tauschen das alte @ManyToMany gegen das neue @OneToMany aus!
    @OneToMany(mappedBy = "project") // 🛡️ Keinerlei Kaskadierung mehr!
    var teamMemberships: MutableList<ProjectMemberEntity> = mutableListOf()
) {
    constructor() : this(id = "proj_" + UUID.randomUUID().toString().take(11))

    fun addMilestone(milestone: MilestoneEntity) {
        milestones.add(milestone)
        milestone.project = this
    }

    // ✨ Schicke Konvertierungsmethode -> Jetzt angepasst an teamMemberships!
    fun toDto(): ProjectDto {
        val dto = ProjectDto()
        dto.id = this.id
        dto.userId = this.userId
        dto.ideaId = this.ideaId
        dto.title = this.title
        dto.area = this.area
        dto.content = this.content
        dto.status = this.status
        dto.fullMilestones = this.milestones.map { it.toDto() }
        dto.departmentId = this.departmentId
        dto.scope = this.scope.name

        // 🗑️ ENTFARNT: Keine Zuweisung mehr an ein nicht-existierendes DTO-Feld!

        return dto
    }

    fun addTeamMember(user: UserEntity, roleEntity: RoleEntity) {
        val alreadyMember = teamMemberships.any { it.user.id == user.id }
        if (!alreadyMember) {
            val newMembership = ProjectMemberEntity(user = user, project = this, role = roleEntity)
            teamMemberships.add(newMembership)
            user.projectMemberships.add(newMembership)
        }
    }

    // 🛑 Mitglied entfernen
    fun removeTeamMember(user: UserEntity) {
        teamMemberships.removeIf { it.user.id == user.id }
        user.projectMemberships.removeIf { it.project.id == this.id }
    }
}