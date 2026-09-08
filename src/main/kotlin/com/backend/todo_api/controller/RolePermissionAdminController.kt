package com.backend.todo_api.controller

import com.backend.todo_api.dto.*
import com.backend.todo_api.services.RolePermissionAdminService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/admin/permissions")
class RolePermissionAdminController(
    private val adminService: RolePermissionAdminService
) {
    @GetMapping
    fun getAllPermissions(principal: Principal): ResponseEntity<List<RolePermissionResponseDto>> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(adminService.getAllRolePermissions(userId))
    }

    @PutMapping
    fun updatePermissionScope(
        @RequestBody dto: UpdateRolePermissionDto,
        principal: Principal
    ): ResponseEntity<RolePermissionResponseDto> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(adminService.updatePermissionScope(userId, dto))
    }

    @PostMapping
    fun createPermission(
        @RequestBody dto: CreateRolePermissionDto,
        principal: Principal
    ): ResponseEntity<RolePermissionResponseDto> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(adminService.createPermission(userId, dto))
    }

    @DeleteMapping("/{id}")
    fun deletePermission(
        @PathVariable id: String,
        principal: Principal
    ): ResponseEntity<Void> {
        val userId = getUserIdFromPrincipal(principal)
        adminService.deletePermission(userId, id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/batch")
    fun createBatchPermissions(
        @RequestBody dto: BatchCreateRolePermissionDto,
        principal: Principal
    ): ResponseEntity<List<RolePermissionResponseDto>> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(adminService.createBatchPermissions(userId, dto))
    }
}