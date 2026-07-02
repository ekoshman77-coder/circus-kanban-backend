package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.UserEntity

open class CreateUserDto (
    var username: String = "",
    var firstName: String = "",
    var lastName: String = ""
)

class UserDto(
    var id: String = "",
    username: String = "",
    firstName: String = "",
    lastName: String = "",
) : CreateUserDto (username, firstName, lastName)


