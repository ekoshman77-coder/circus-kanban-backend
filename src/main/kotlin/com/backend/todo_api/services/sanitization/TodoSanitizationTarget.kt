package com.backend.todo_api.services.sanitization

import com.backend.todo_api.providers.SanitizationTargetProvider
import com.backend.todo_api.services.TodoService
import org.springframework.stereotype.Component

@Component
class TodoSanitizationTarget(
    private val todoService: TodoService
) : SanitizationTargetProvider {

    override fun getTargetType(): String = "TODO"

    override fun rescanAll() {
        todoService.rescanAllTodosSensitivity()
    }
}