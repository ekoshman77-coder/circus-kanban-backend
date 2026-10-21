package com.backend.todo_api.controller

import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.dto.InviteRequestDto
import com.backend.todo_api.dto.SearchUserDto
import com.backend.todo_api.services.InvitationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/invitation")
class InvitationController(
    private val invitationService: InvitationService
) {

    // Endpunkt A: Abteilungen für Einladungen abrufen
    @GetMapping("/departments")
    fun getDepartmentsForInviting(principal: Principal): ResponseEntity<List<DepartmentDto>> {
        return ResponseEntity.ok(invitationService.getAvailableDepartments(principal.name))
    }

    // Endpunkt B: User suchen basierend auf Departments & optionaler Rolle
    @PostMapping("/search-users")
    fun searchUsersForInvitation(
        @RequestBody request: InviteRequestDto,
        principal: Principal
    ): ResponseEntity<List<SearchUserDto>> {
        val users = invitationService.searchUsers(
            principal.name,
            departmentIds = request.departmentIds,
            rolesFilter = request.departmentRoles
        )
        return ResponseEntity.ok(users)
    }
}