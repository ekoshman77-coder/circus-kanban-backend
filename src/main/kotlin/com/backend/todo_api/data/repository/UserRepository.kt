package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.model.RoleType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<UserEntity, String> {

    fun findByUsernameIgnoreCase(username: String): UserEntity?

    // Nur aktive User (nicht archiviert) für den Warteraum oder das Board holen
    fun findByIsApprovedAndIsArchivedFalse(isApproved: Boolean): List<UserEntity>

    fun findByIsApprovedAndDepartmentIdAndIsArchivedFalse(isApproved: Boolean, departmentId: String?): List<UserEntity>

    //Prüfen, ob ein Benutzername existiert und NICHT archiviert ist (für Login)
    fun findByUsernameIgnoreCaseAndIsArchivedFalse(username: String): UserEntity?

    // 🔍 Holt einfach JEDEN User, der nicht archiviert ist (egal ob approved oder nicht)
    fun findByIsArchivedFalse(): List<UserEntity>

    fun findByIdAndIsArchivedFalse(id: String): UserEntity?

    fun findByIsApprovedAndIsArchivedFalseAndDepartmentRole_Name(bool: Boolean, roleFilter: RoleType): List<UserEntity>

    fun findByIsApprovedAndDepartmentIdAndIsArchivedFalseAndDepartmentRole_Name(
        bool: Boolean,
        departmentId: String?,
        roleFilter: RoleType
    ): List<UserEntity>

    // 1. Wenn KEIN Rollenfilter da ist:
    @Query("""
    SELECT DISTINCT u FROM UserEntity u
    JOIN ProjectMemberEntity pm ON pm.user.id = u.id
    WHERE pm.project.id IN :projectIds 
    AND u.isArchived = false 
    AND u.isApproved = true
    """)
    fun findMembersByProjectIds(@Param("projectIds") projectIds: List<String>): List<UserEntity>

    // 2. Wenn EIN Rollenfilter da ist:
    @Query("""
    SELECT DISTINCT u FROM UserEntity u
    JOIN ProjectMemberEntity pm ON pm.user.id = u.id
    WHERE pm.project.id IN :projectIds 
    AND u.isArchived = false 
    AND u.isApproved = true
    AND u.departmentRole.name = :role
    """)
    fun findMembersByProjectIdsWithRole(
        @Param("projectIds") projectIds: List<String>,
        @Param("role") role: RoleType
    ): List<UserEntity>

    fun findByIsApprovedAndIsArchivedFalseAndDepartmentIdIn(bool: Boolean, departmentIds: List<String>
    ): List<UserEntity>

    @Query("""
    SELECT u FROM UserEntity u 
    WHERE u.isApproved = :isApproved 
      AND u.isArchived = false 
      AND u.departmentId IN :departmentIds 
      AND u.departmentRole.name IN :rolesFilter
""")
    fun findApprovedUsersInDepartmentsWithRoles(
        isApproved: Boolean,
        departmentIds: List<String>,
        rolesFilter: List<RoleType>
    ): List<UserEntity>

    fun findByIsApprovedAndDepartmentIdAndIsArchivedFalseAndDepartmentRole_NameIn(
        bool: Boolean,
        departmentId: String?,
        rolesFilter: List<RoleType>
    ): List<UserEntity>

    @Query("""
    SELECT DISTINCT u FROM UserEntity u
    JOIN ProjectMemberEntity pm ON pm.user.id = u.id
    WHERE pm.project.id IN :projectIds 
    AND u.departmentId IN :departmentIds
    AND u.isArchived = false 
    AND u.isApproved = true
    """)
    fun findMembersByProjectIdsAndDepartmentIdIn(
        @Param("projectIds") projectIds: List<String>,
        @Param("departmentIds") departmentIds: List<String>
    ): List<UserEntity>

    // 2. PROJECT-Scope: Mit Rollenfilter (filtert nach Projekten, Abteilungen UND Rollen)
    @Query("""
    SELECT DISTINCT u FROM UserEntity u
    JOIN ProjectMemberEntity pm ON pm.user.id = u.id
    WHERE pm.project.id IN :projectIds 
    AND u.departmentId IN :departmentIds
    AND u.departmentRole.name IN :rolesFilter
    AND u.isArchived = false 
    AND u.isApproved = true
    """)
    fun findMembersByProjectIdsAndDepartmentIdInWithRoleIn(
        @Param("projectIds") projectIds: List<String>,
        @Param("departmentIds") departmentIds: List<String>,
        @Param("rolesFilter") rolesFilter: List<RoleType>
    ): List<UserEntity>

    fun findByIsApprovedTrueAndIsArchivedFalse(): List<UserEntity>
}