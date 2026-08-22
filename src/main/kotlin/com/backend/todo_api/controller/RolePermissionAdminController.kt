package com.backend.todo_api.controller

import com.backend.todo_api.dto.*
import com.backend.todo_api.services.RolePermissionAdminService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/permissions")
class RolePermissionAdminController(
    private val adminService: RolePermissionAdminService
) {
    @GetMapping
    fun getAllPermissions(): ResponseEntity<List<RolePermissionResponseDto>> {
        return ResponseEntity.ok(adminService.getAllRolePermissions())
    }

    @PutMapping
    fun updatePermissionScope(@RequestBody dto: UpdateRolePermissionDto): ResponseEntity<RolePermissionResponseDto> {
        return ResponseEntity.ok(adminService.updatePermissionScope(dto))
    }

    @PostMapping
    fun createPermission(@RequestBody dto: CreateRolePermissionDto): ResponseEntity<RolePermissionResponseDto> {
        return ResponseEntity.ok(adminService.createPermission(dto))
    }

    @DeleteMapping("/{id}")
    fun deletePermission(@PathVariable id: String): ResponseEntity<Void> {
        adminService.deletePermission(id)
        return ResponseEntity.noContent().build()
    }
}