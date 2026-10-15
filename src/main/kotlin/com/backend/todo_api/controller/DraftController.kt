package com.backend.todo_api.controller

import com.backend.todo_api.dto.DelegateDraftDto
import com.backend.todo_api.dto.DraftChainDto
import com.backend.todo_api.services.DraftBackendService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/drafts")
class DraftController(
    private val draftBackendService: DraftBackendService
) {

    /**
     * 1. GET /api/drafts
     * Liefert alle dem aktuellen User/Team zugewiesenen Entwürfe (REMOTE)
     */
    @GetMapping
    fun getAssignedDrafts(principal: Principal): ResponseEntity<List<DraftChainDto>> {
        val currentUserId = getUserIdFromPrincipal(principal)
        val drafts = draftBackendService.getAssignedDraftsForCurrentUser(currentUserId)
        return ResponseEntity.ok(drafts)
    }

    /**
     * 2. POST /api/drafts/{id}/delegate
     * Delegiert einen Entwurf an ein anderes Team, Projekt oder einen User
     */
    @PostMapping("/{id}/delegate")
    fun delegateDraft(
        @PathVariable("id") chainId: String,
        @Valid @RequestBody dto: DelegateDraftDto,
        principal: Principal // 👈 Der authentifizierte User aus dem Token
    ): ResponseEntity<Unit> {
        val currentUserId =getUserIdFromPrincipal(principal)
        draftBackendService.delegateDraft(chainId, dto, currentUserId)
        return ResponseEntity.noContent().build()
    }

    /**
     * 3. DELETE /api/drafts/{id}
     * Löscht / verwirft einen Entwurf serverseitig
     */
    @DeleteMapping("/{id}")
    fun deleteDraft(
        @PathVariable("id") chainId: String,
        principal: Principal
    ): ResponseEntity<Unit> {
        val userId = getUserIdFromPrincipal(principal)
        draftBackendService.deleteDraft(chainId, userId)
        return ResponseEntity.noContent().build()
    }
}