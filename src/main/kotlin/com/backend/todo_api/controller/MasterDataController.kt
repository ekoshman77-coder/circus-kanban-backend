package com.backend.todo_api.controller

import com.backend.todo_api.dto.MasterDataResponseDto
import com.backend.todo_api.services.PermissionService
import com.backend.todo_api.services.RolePermissionAdminService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/masterdata")
@CrossOrigin(origins = ["http://localhost:4200"])
@Tag(name = "MasterData-Controller", description = "Liefert Konfigurationsdaten und Enums für Frontend-Dropdowns")
class MasterDataController(
    private val permissionService: RolePermissionAdminService
) {

    @GetMapping
    @Operation(summary = "Alle Masterdata-Enums für Dropdowns abrufen")
    fun getMasterData(): ResponseEntity<MasterDataResponseDto> {
        return ResponseEntity.ok(permissionService.getMasterData())
    }
}