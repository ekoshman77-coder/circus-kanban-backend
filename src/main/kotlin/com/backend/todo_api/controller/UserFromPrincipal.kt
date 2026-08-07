package com.backend.todo_api.controller

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.security.Principal

public fun getUserIdFromPrincipal(principal: Principal?): String {
    return principal?.name
        ?: throw ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Nicht authentifiziert: Bitte logge dich zuerst ein."
        )
}
