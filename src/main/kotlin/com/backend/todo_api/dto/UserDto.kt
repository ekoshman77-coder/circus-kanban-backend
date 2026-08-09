package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.model.RoleType
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
    var department: DepartmentDto? = null,
    var departmentRole: RoleType? = null, // 👈 NEU: Hier gehört sie hin!
    var isApproved: Boolean = false
) : CreateUserDto(username, firstName, lastName, password)