package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.dto.OnRegisterOrLogin
import com.backend.todo_api.dto.OnUpdate
import com.backend.todo_api.services.UserAlreadyExistsException
import com.backend.todo_api.services.UserNotFoundException
import com.backend.todo_api.services.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    fun loginUser(@Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto): ResponseEntity<Any> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name darf nicht leer sein!"))
        }
        return try {
            ResponseEntity.ok(userService.login(dto))
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
    fun registerUser(@Validated(OnRegisterOrLogin::class) @RequestBody dto: CreateUserDto): ResponseEntity<Any> {
        if (dto.username.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Name darf nicht leer sein!"))
        }
        return try {
            ResponseEntity.status(HttpStatus.CREATED).body(userService.register(dto))
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
        @Validated(OnUpdate::class) @RequestBody dto: CreateUserDto
    ): ResponseEntity<Any> {
        return try {
            // Der Service ist zum Glück noch da und unversehrt!
            val updatedUser = userService.updateUser(id, dto)
            ResponseEntity.ok(updatedUser)
        } catch (e: UserNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }
}