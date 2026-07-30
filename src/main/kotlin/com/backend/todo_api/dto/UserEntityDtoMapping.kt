package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.CoffeeAccountEntity
import com.backend.todo_api.data.entity.UserEntity

// 🚀 KEINE KLASSE MEHR! Die Methoden stehen direkt als "Top-Level" in der Datei.

fun UserEntity.toDto(): UserDto {
    return copyToUserDto(this, UserDto())
}

fun copyToUserDto(userEntity: UserEntity, userDto: UserDto): UserDto {
    userDto.id = userEntity.id
    userDto.username = userEntity.username
    userDto.firstName = userEntity.firstName
    userDto.lastName = userEntity.lastName
    userDto.departmentId = userEntity.departmentId
    userDto.isApproved = userEntity.isApproved
    return userDto
}

fun entityToUserResponseDto(userEntity: UserEntity, coffeeAccountEntity: CoffeeAccountEntity?): UserResponseDto {
    val dto = UserResponseDto()
    val dtoWithUser = copyToUserDto(userEntity, dto) as UserResponseDto
    dtoWithUser.projectIds = userEntity.projectMemberships.map { it.project.id }
    return copyCoffeeAccountToResponseDto(coffeeAccountEntity, dtoWithUser)
}

fun copyCoffeeAccountToResponseDto(coffeeAccountEntity: CoffeeAccountEntity?, dto: UserResponseDto): UserResponseDto {
    dto.coffeeBalance = coffeeAccountEntity?.balance ?: 0f
    dto.emoji = coffeeAccountEntity?.emoji ?: ""
    dto.role = coffeeAccountEntity?.role ?: ""
    return dto
}