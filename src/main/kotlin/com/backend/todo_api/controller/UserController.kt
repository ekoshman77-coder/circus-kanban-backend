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
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = ["http://localhost:4200"])
@Tag(name = "User-Controller", description = "Endpunkte für Login und Registrierung von Benutzern")
class UserController(private val userService: UserService) {

    @PostMapping("/login")
    @Operation(summary = "Benutzer einloggen", description = "Prüft, ob der Name existiert und gibt den User samt ID zurück.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Erfolgreich eingeloggt"),
        ApiResponse(responseCode = "400", description = "Ungültige Eingabe (Name leer)"),
        ApiResponse(responseCode = "404", description = "Benutzername existiert nicht")
    ])
    fun loginUser(
        @Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto,
                  request: HttpServletRequest
    ): ResponseEntity<Any> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name darf nicht leer sein!"))
        }
        return try {
            // 1. Der normale Login läuft durch und liefert das UserDto
            val userDto = userService.login(dto)

            // 2. Wir nutzen unsere neue Methode im Service, um die Rolle zu bestimmen
            val isUserAdmin = userService.isAdminDepartment(userDto.departmentId)
            val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"

            // 3. Wir packen die Rolle in eine Liste für Spring Security
            val authorities = listOf(SimpleGrantedAuthority(roleName))

            // 4. Wir erstellen das Spring-Security-Ticket
            val authentication = UsernamePasswordAuthenticationToken(userDto.username, null, authorities)

            // 5. Wir drücken den Stempel in den SecurityContext
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)

            val session = request.getSession(true)
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
            // 6. Wir geben das UserDto wie gewohnt an das Frontend zurück
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
            // 1. Der normale Registrierungsprozess läuft im Service
            val userDto = userService.register(dto)

            // 🚀 NEU: HIER KOMMT DER SICHERHEITSSTEMPEL AUCH BEI DER REGISTRIERUNG HIN!
            // 2. Wir prüfen, ob der frisch registrierte User Admin ist (trifft nur auf den Ur-Admin zu!)
            val isUserAdmin = userService.isAdminDepartment(userDto.departmentId)
            val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"

            // 3. Rolle verpacken
            val authorities = listOf(SimpleGrantedAuthority(roleName))

            // 4. Spring-Security-Ticket erstellen
            val authentication = UsernamePasswordAuthenticationToken(userDto.username, null, authorities)

            // 5. Stempel in den SecurityContext drücken
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)

            // 🎯 HIER WIRD DIE SESSION FÜR DEN UR-ADMIN AKTIVIERT!
            // Da normale User zu diesem Zeitpunkt noch 'isApproved = false' haben und in keiner
            // Admin-Abteilung sind, schadet es nicht. Aber für den Ur-Admin erstellen wir das Schließfach!
            if (isUserAdmin) {
                val session = request.getSession(true)
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
                println("🚀 [Security] Session für Ur-Admin '${userDto.username}' bei Registrierung erstellt!")
            }

            // 6. Antwort zurückgeben
            ResponseEntity.status(HttpStatus.CREATED).body(userDto)
        } catch (e: UserAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/profile/{id}")
    @Operation(summary = "Benutzerprofil aktualisieren", description = "Aktualisiert Vorname und Nachname eines bestehenden Users unter einem separaten Pfad.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Profil erfolgreich aktualisiert"),
        ApiResponse(responseCode = "404", description = "Benutzer-ID nicht gefunden")
    ])
    fun updateProfile(
        @PathVariable id: String,
        @Validated(OnUpdate::class) @RequestBody dto: UserDto
    ): ResponseEntity<Any> {
        return try {
            // Der Service ist zum Glück noch da und unversehrt!
            val updatedUser = userService.updateUser(id, dto)
            ResponseEntity.ok(updatedUser)
        } catch (e: UserNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Benutzer global löschen", description = "Löscht den Benutzer global aus dem System und bereinigt alle Abhängigkeiten (Todos, Meilensteine, Projekte).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Benutzer erfolgreich gelöscht"),
        ApiResponse(responseCode = "404", description = "Benutzer-ID nicht gefunden")
    ])
    fun deleteUser(@PathVariable id: String): ResponseEntity<Any> {
        return try {
            println("🗑️ [Backend-Controller] DELETE-Request erhalten für User-ID: $id")

            // 🚀 Ruft die fleißige Methode im Service auf
            userService.deleteUser(id)

            // 204 No Content ist perfekt für erfolgreiche Löschanfragen
            ResponseEntity.noContent().build()
        } catch (e: UserNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/unapproved")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Warteraum abrufen")
    fun getUnapprovedUsers(): ResponseEntity<Any> {
        return ResponseEntity.ok(userService.getUnapprovedUsers())
    }

    @GetMapping("/approved")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aktive Belegschaft abrufen")
    fun getApprovedUsers(): ResponseEntity<Any> {
        return ResponseEntity.ok(userService.getApprovedUsers())
    }

    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')") // 🛡️ Garantiert, dass NUR der echte Admin hier reinkommt!
    @Operation(summary = "User freischalten und Abteilung zuweisen")
    fun approveUser(
        @PathVariable userId: String,
        @Validated @RequestBody dto: UserApproveDto
    ): ResponseEntity<Any> {
        // Da der Admin-Zettel dank unseres getUserStatus-Fixes fest auf dem Klemmbrett bleibt,
        // geht dieser Aufruf jetzt für JEDE Abteilung (auch IT, Vertrieb etc.) fehlerfrei durch!
        return ResponseEntity.ok(userService.approveUser(userId, dto))
    }

    @GetMapping("/status/{userId}")
    // RADIKALER FIX: NUR der betroffene User selbst darf diesen Endpunkt aufrufen! Kein Admin!
    @PreAuthorize("authentication.name == @userService.getUserById(#userId).username")
    fun getUserStatus(
        @PathVariable userId: String,
        request: HttpServletRequest
        ): ResponseEntity<UserDto> {
        val userDto = userService.getUserById(userId)

        // Wenn der User freigeschaltet wurde, aktualisieren wir SEINEN EIGENEN Stempel.
        // Da wir oben sichergestellt haben, dass hier NUR der User selbst anfragt,
        // kann absolut kein fremder Zettel mehr überschrieben werden!
        if (userDto.isApproved) {
            val isUserAdmin = userService.isAdminDepartment(userDto.departmentId)
            val roleName = if (isUserAdmin) "ROLE_ADMIN" else "ROLE_USER"

            val authorities = listOf(SimpleGrantedAuthority(roleName))
            val authentication = UsernamePasswordAuthenticationToken(userDto.username, null, authorities)

            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)

            // DER SESSION-KLEBER BEIM POLLING!
            // Sobald isApproved = true ist, bekommt der User hier sein echtes Schließfach auf dem Server.
            // Damit ist er ab JETZT dauerhaft eingeloggt und kann ins Dashboard springen!
            val session = request.getSession(true)
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
            println("🔄 [Security] Stempel für User '${userDto.username}' live aktualisiert auf: $roleName")
        }

        return ResponseEntity.ok(userDto)
    }

    @PostMapping("/signout")
    @Operation(summary = "Benutzer ausloggen", description = "Vernichtet die aktuelle Session auf dem Server komplett.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Erfolgreich abgemeldet")
    ])
    fun logoutUser(request: HttpServletRequest): ResponseEntity<Any> {
        // 1. Wir holen die aktuelle Session, falls eine existiert (false = erstelle keine neue)
        val session = request.getSession(false)

        if (session != null) {
            println("🗑️ [Security] Session ${session.id} wird per Logout vernichtet!")
            // 🎯 2. DAS SCHLIESSFACH WIRD SPRENGT: Löscht alle Daten und die Session auf dem Server!
            session.invalidate()
        }

        // 3. Wir wischen das aktuelle Klemmbrett im Thread sauber
        SecurityContextHolder.clearContext()

        // 4. Wir sagen dem Browser, dass alles geklappt hat
        return ResponseEntity.ok(mapOf("message" to "Erfolgreich abgemeldet"))
    }
}