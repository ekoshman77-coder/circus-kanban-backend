package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.UserEntity
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

interface OnRegisterOrLogin
interface OnUpdate

open class CreateUserDto (
    @field:NotBlank(message = "Der Username darf nicht leer sein!")
    @field:Size(min = 3, max = 20, message = "Der Username muss zwischen 3 und 20 Zeichen lang sein!")
    var username: String = "",

    @field:NotBlank(message = "Der Vorname darf nicht leer sein!")
    var firstName: String = "",

    @field:NotBlank(message = "Der Nachname darf nicht leer sein!")
    var lastName: String = "",

    @field:NotBlank(message = "Das Passwort darf nicht leer sein!", groups = [OnRegisterOrLogin::class])
    @field:Size(min = 6, message = "Das Passwort muss mindestens 6 Zeichen lang sein!", groups = [OnRegisterOrLogin::class])
    var password: String = "",


)

open class UserDto(
    var id: String = "",
    username: String = "",
    firstName: String = "",
    lastName: String = "",
    password: String = "",
    var departmentId: String? = null,
    var isApproved: Boolean = false
) : CreateUserDto (username, firstName, lastName, password)

//// UNSER ZENTRALER MAPPER (Erweiterungsfunktion)
//// Jede UserEntity im gesamten Projekt kann jetzt blitzschnell in ein sicheres UserDto umgewandelt werden!
//fun UserEntity.toDto(): UserDto {
//    return UserDto(
//        id = this.id,
//        username = this.username,
//        firstName = this.firstName,
//        lastName = this.lastName,
//        // password wird bewusst ignoriert -> Standardwert "" greift automatisch!
//        departmentId = this.departmentId,
//        isApproved = this.isApproved
//    )
//}
