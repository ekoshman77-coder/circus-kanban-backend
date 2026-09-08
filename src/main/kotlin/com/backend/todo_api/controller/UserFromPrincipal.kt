package com.backend.todo_api.controller

import com.backend.todo_api.exceptions.ActionForbiddenException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.security.Principal

public fun getUserIdFromPrincipal(principal: Principal?): String {
    return principal?.name
        ?: throw ActionForbiddenException("Nicht authorisiert")
}
