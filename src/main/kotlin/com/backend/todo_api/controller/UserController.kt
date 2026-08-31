package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.dto.OnRegisterOrLogin
import com.backend.todo_api.dto.OnUpdate
import com.backend.todo_api.dto.UserApproveDto
import com.backend.todo_api.dto.UserDto
import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.dto.UserUpdateProfileDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.UserAlreadyExistsException
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = ["http://localhost:4200"])
@Tag(
    name = "User-Controller",
    description = "Endpunkte für Registrierung, Login, Profilverwaltung und Freischaltung von Benutzern"
)
class UserController(private val userService: UserService) {

    @PostMapping("/login")
    @Operation(
        summary = "Benutzer einloggen",
        description = "Prüft die Anmeldedaten eines Benutzers, bestimmt dessen Rolle und baut die Session auf, sofern das Konto freigeschaltet und nicht archiviert ist."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Erfolgreich eingeloggt"),
            ApiResponse(responseCode = "400", description = "Ungültige Eingabe (Name leer)"),
            ApiResponse(
                responseCode = "404",
                description = "Benutzername existiert nicht oder Account wurde archiviert"
            )
        ]
    )
    fun loginUser(
        @Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name darf nicht leer sein!"))
        }

        val userDto = userService.login(dto)
        val isUserAdmin = userService.isAdminDepartment(userDto.department?.id)
        val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"
        val authorities = listOf(SimpleGrantedAuthority(roleName))
        val authentication = UsernamePasswordAuthenticationToken(userDto.id, null, authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        val session = request.getSession(true)
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
        return ResponseEntity.ok(userDto)
    }

    @PostMapping("/register")
    @Operation(
        summary = "Neuen Benutzer registrieren",
        description = "Erstellt einen neuen User, falls der Name noch frei ist."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Erfolgreich registriert"),
            ApiResponse(responseCode = "400", description = "Ungültige Eingabe (Name leer)"),
            ApiResponse(responseCode = "409", description = "Name ist bereits vergeben")
        ]
    )
    fun registerUser(
        @Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name darf nicht leer sein!"))
        }
        val userDto = userService.register(dto)
        val isUserAdmin = userService.isAdminDepartment(userDto.department?.id)
        val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"
        val authorities = listOf(SimpleGrantedAuthority(roleName))
        val authentication = UsernamePasswordAuthenticationToken(userDto.id, null, authorities)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        // 🛠️ FIX: Session für ALLE registrierten User erstellen, damit das Warteraum-Polling funktioniert!
        val session = request.getSession(true)
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
        if (isUserAdmin) {
            println("🚀 [Security] Session für Ur-Admin '${userDto.username}' bei Registrierung erstellt!")
        } else {
            println("⏳ [Security] Session für wartenden User '${userDto.username}' erstellt (Warteraum-Polling aktiv).")
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(userDto)
    }

    @PostMapping
    @Operation(
        summary = "Neuen Benutzer durch Admin/Manager anlegen",
        description = "Erstellt einen neuen User über den Admin-Drawer und prüft Berechtigungen über den PermissionService."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Erfolgreich angelegt"),
            ApiResponse(responseCode = "400", description = "Ungültige Eingabe (Name darf nicht leer sein)"),
            ApiResponse(responseCode = "403", description = "Keine Berechtigung zum Anlegen von Benutzern"),
            ApiResponse(responseCode = "409", description = "Name ist bereits vergeben")
        ]
    )
    fun createUser(
        @Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto,
        principal: Principal?
    ): ResponseEntity<UserDto> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().build()
        }

        val currentUserId = getUserIdFromPrincipal(principal)
        val createdUser = userService.createUser(currentUserId, dto)

        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser)
    }

    @PutMapping("/profile/{id}")
    @Operation(
        summary = "Benutzerprofil aktualisieren",
        description = "Aktualisiert Vorname und Nachname eines bestehenden aktiven Benutzers. Archivierte Benutzer werden blockiert."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Profil erfolgreich aktualisiert"),
            ApiResponse(responseCode = "404", description = "Benutzer-ID nicht gefunden"),
            ApiResponse(responseCode = "409", description = "Änderungen an archivierten Benutzern nicht erlaubt")
        ]
    )
    fun updateProfile(
        @PathVariable id: String,
        @Validated(OnUpdate::class) @RequestBody dto: UserUpdateProfileDto,
        principal: Principal?
    ): ResponseEntity<Any> {
        val currentUserId = getUserIdFromPrincipal(principal)
        val updatedUser = userService.updateUser(currentUserId, id, dto)
        return ResponseEntity.ok(updatedUser)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Benutzer archivieren (Soft-Delete)",
        description = "Löscht den Benutzer nicht physisch, sondern markiert ihn als archiviert. Private Todos werden archiviert, offene Team-Zuweisungen entkoppelt."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Benutzer erfolgreich archiviert"),
            ApiResponse(responseCode = "404", description = "Benutzer-ID nicht gefunden")
        ]
    )
    fun deleteUser(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Any> {
        val currentUserId = getUserIdFromPrincipal(principal)
        println("🗑️ [Backend-Controller] Soft-DELETE-Request erhalten für User-ID: $id")
        userService.deleteUser(currentUserId, id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/unapproved")
    fun getUnapprovedUsers(principal: Principal?): ResponseEntity<List<UserResponseDto>> {
        val currentUserId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.getUnapprovedUsers(currentUserId))
    }

    @GetMapping("/approved")
    fun getApprovedUsers(principal: Principal?): ResponseEntity<List<UserResponseDto>> {
        val currentUserId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.getApprovedUsers(currentUserId))
    }

    @PostMapping("/{userId}/approve")
    fun approveUser(
        @PathVariable userId: String,
        @Validated @RequestBody dto: UserApproveDto,
        principal: Principal?
    ): ResponseEntity<UserDto> {
        val currentUserId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.approveUser(currentUserId, userId, dto))
    }

    @GetMapping("/status/{userId}")
    @PreAuthorize("authentication.name == @userService.getUserById(#userId).username")
    @Operation(
        summary = "Eigenen Freischaltungs-Status pollen",
        description = "Ermöglicht dem wartenden Client zu prüfen, ob der Account freigeschaltet wurde. Aktualisiert die Sitzung bei Erfolg live."
    )
    fun getUserStatus(
        request: HttpServletRequest,
        principal: Principal?
    ): ResponseEntity<UserDto> {
        val currentUserId = getUserIdFromPrincipal(principal)
        val userDto = userService.getUserById(currentUserId, currentUserId)

        if (userDto.isApproved) {
            val isUserAdmin = userService.isAdminDepartment(userDto.department?.id)
            val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"

            val authorities = listOf(SimpleGrantedAuthority(roleName))
            val authentication = UsernamePasswordAuthenticationToken(userDto.id, null, authorities)

            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)

            val session = request.getSession(true)
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
            println("🔄 [Security] Stempel für User '${userDto.username}' live aktualisiert auf: $roleName")
        }

        return ResponseEntity.ok(userDto)
    }

    @PostMapping("/signout")
    @Operation(
        summary = "Benutzer ausloggen",
        description = "Beendet und zerstört die aktuelle Session auf dem Server vollständig."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Erfolgreich abgemeldet")
        ]
    )
    fun logoutUser(request: HttpServletRequest): ResponseEntity<Any> {
        val session = request.getSession(false)
        if (session != null) {
            println("🗑️ [Security] Session ${session.id} wird per Logout vernichtet!")
            session.invalidate()
        }
        SecurityContextHolder.clearContext()
        return ResponseEntity.ok(mapOf("message" to "Erfolgreich abgemeldet"))
    }
}