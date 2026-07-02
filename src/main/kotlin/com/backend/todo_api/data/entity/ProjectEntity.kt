package com.backend.todo_api.data.entity

import com.backend.todo_api.dto.ProjectDto
import com.backend.todo_api.dto.UserDto
import jakarta.persistence.*
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

    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var milestones: MutableList<MilestoneEntity> = mutableListOf(),

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_team_members",
        joinColumns = [JoinColumn(name = "project_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var teamMembers: MutableList<UserEntity> = mutableListOf()
) {
    constructor() : this(id = "proj_" + UUID.randomUUID().toString().take(11))

    fun addMilestone(milestone: MilestoneEntity) {
        milestones.add(milestone)
        milestone.project = this
    }

    // ✨ Schicke Konvertierungsmethode -> Macht den Service extrem sauber!
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
        dto.teamMembers = this.teamMembers.map { UserDto(id = it.id, username = it.username, firstName = it.firstName, lastName = it.lastName) }
        return dto
    }

    fun addTeamMember(user: UserEntity) {
        if (!teamMembers.contains(user)) {
            teamMembers.add(user)
            user.projects.add(this) // Wichtig für die beidseitige Verknüpfung!
        }
    }

    fun removeTeamMember(user: UserEntity) {
        if (teamMembers.contains(user)) {
            teamMembers.remove(user)
            user.projects.remove(this)
        }
    }
}