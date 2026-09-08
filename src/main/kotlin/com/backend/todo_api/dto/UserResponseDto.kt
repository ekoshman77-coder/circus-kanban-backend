package com.backend.todo_api.dto

import com.backend.todo_api.model.RoleType

class UserResponseDto(
    id: String = "",
    username: String = "",
    firstName: String = "",
    lastName: String = "",
    department: DepartmentDto? = null,
    departmentRole: RoleType = RoleType.MEMBER,
    isApproved: Boolean = false,
    var projectIds: List<String> = ArrayList(),

    // 🏢 Die echte Abteilungsrolle (z. B. "ADMIN", "MEMBER")

    // ☕ Das gekapselte Kaffeekonto als eigenes Objekt
    var coffeeAccount: CoffeeAccountDto = CoffeeAccountDto()
) : UserDto(id, username, firstName, lastName, "", department, departmentRole, isApproved)