package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.dto.OnRegisterOrLogin
import com.backend.todo_api.dto.OnUpdate
import com.backend.todo_api.dto.UserApproveDto
import com.backend.todo_api.dto.UserDto
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

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = ["http://localhost:4200"])
@Tag(name = "User-Controller", description = "Endpunkte für Registrierung, Login, Profilverwaltung und Freischaltung von Benutzern")
class UserController(private val userService: UserService) {

    @PostMapping("/login")
    @Operation(summary = "Benutzer einloggen", description = "Prüft die Anmeldedaten eines Benutzers, bestimmt dessen Rolle und baut die Session auf, sofern das Konto freigeschaltet und nicht archiviert ist.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Erfolgreich eingeloggt"),
        ApiResponse(responseCode = "400", description = "Ungültige Eingabe (Name leer)"),
        ApiResponse(responseCode = "404", description = "Benutzername existiert nicht oder Account wurde archiviert")
    ])
    fun loginUser(
        @Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name darf nicht leer sein!"))
        }
        return try {
            val userDto = userService.login(dto)
            val isUserAdmin = userService.isAdminDepartment(userDto.departmentId)
            val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"
            val authorities = listOf(SimpleGrantedAuthority(roleName))
            val authentication = UsernamePasswordAuthenticationToken(userDto.username, null, authorities)

            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)

            val session = request.getSession(true)
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
            ResponseEntity.ok(userDto)
        } catch (e: UserNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }
    @PostMapping("/register")
    @Operation(summary = "Neuen Benutzer registrieren", description = "Erstellt einen neuen User, falls der Name noch frei ist.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Erfolgreich registriert"),
        ApiResponse(responseCode = "400", description = "Ungültige Eingabe (Name leer)"),
        ApiResponse(responseCode = "409", description = "Name ist bereits vergeben")
    ])
    fun registerUser(
        @Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name darf nicht leer sein!"))
        }
        return try {
            val userDto = userService.register(dto)
            val isUserAdmin = userService.isAdminDepartment(userDto.departmentId)
            val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"
            val authorities = listOf(SimpleGrantedAuthority(roleName))
            val authentication = UsernamePasswordAuthenticationToken(userDto.username, null, authorities)

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

            ResponseEntity.status(HttpStatus.CREATED).body(userDto)
        } catch (e: UserAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/profile/{id}")
    @Operation(summary = "Benutzerprofil aktualisieren", description = "Aktualisiert Vorname und Nachname eines bestehenden aktiven Benutzers. Archivierte Benutzer werden blockiert.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Profil erfolgreich aktualisiert"),
        ApiResponse(responseCode = "404", description = "Benutzer-ID nicht gefunden"),
        ApiResponse(responseCode = "409", description = "Änderungen an archivierten Benutzern nicht erlaubt")
    ])
    fun updateProfile(
        @PathVariable id: String,
        @Validated(OnUpdate::class) @RequestBody dto: UserDto
    ): ResponseEntity<Any> {
        return try {
            val updatedUser = userService.updateUser(id, dto)
            ResponseEntity.ok(updatedUser)
        } catch (e: UserNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Benutzer archivieren (Soft-Delete)", description = "Löscht den Benutzer nicht physisch, sondern markiert ihn als archiviert. Private Todos werden archiviert, offene Team-Zuweisungen entkoppelt.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Benutzer erfolgreich archiviert"),
        ApiResponse(responseCode = "404", description = "Benutzer-ID nicht gefunden")
    ])
    fun deleteUser(@PathVariable id: String): ResponseEntity<Any> {
        return try {
            println("🗑️ [Backend-Controller] Soft-DELETE-Request erhalten für User-ID: $id")
            userService.deleteUser(id)
            ResponseEntity.noContent().build()
        } catch (e: UserNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/unapproved")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Warteraum abrufen", description = "Liefert eine Liste aller unbestätigten Benutzer zurück, die noch auf ihre Freischaltung warten (archivierte Benutzer ausgeschlossen).")
    fun getUnapprovedUsers(): ResponseEntity<Any> {
        return ResponseEntity.ok(userService.getUnapprovedUsers())
    }

    @GetMapping("/approved")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aktive Belegschaft abrufen", description = "Liefert eine Liste aller freigeschalteten, voll funktionsfähigen Mitarbeiter (archivierte Benutzer ausgeschlossen).")
    fun getApprovedUsers(): ResponseEntity<Any> {
        return ResponseEntity.ok(userService.getApprovedUsers())
    }

    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "User freischalten und Abteilung zuweisen", description = "Bestätigt das Benutzerkonto im Warteraum und ordnet es einer Abteilung im System zu.")
    fun approveUser(
        @PathVariable userId: String,
        @Validated @RequestBody dto: UserApproveDto
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(userService.approveUser(userId, dto))
    }

    @GetMapping("/status/{userId}")
    @PreAuthorize("authentication.name == @userService.getUserById(#userId).username")
    @Operation(summary = "Eigenen Freischaltungs-Status pollen", description = "Ermöglicht dem wartenden Client zu prüfen, ob der Account freigeschaltet wurde. Aktualisiert die Sitzung bei Erfolg live.")
    fun getUserStatus(
        @PathVariable userId: String,
        request: HttpServletRequest
    ): ResponseEntity<UserDto> {
        val userDto = userService.getUserById(userId)

        if (userDto.isApproved) {
            val isUserAdmin = userService.isAdminDepartment(userDto.departmentId)
            val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"

            val authorities = listOf(SimpleGrantedAuthority(roleName))
            val authentication = UsernamePasswordAuthenticationToken(userDto.username, null, authorities)

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
    @Operation(summary = "Benutzer ausloggen", description = "Beendet und zerstört die aktuelle Session auf dem Server vollständig.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Erfolgreich abgemeldet")
    ])
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