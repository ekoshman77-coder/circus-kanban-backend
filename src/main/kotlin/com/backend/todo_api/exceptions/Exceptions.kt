package com.backend.todo_api.exceptions

enum class ErrorCode(val value: String) {
    USER_NOT_FOUND("USER_NOT_FOUND"),
    TODO_NOT_FOUND("TODO_NOT_FOUND"),
    INVALID_DATA("INVALID_DATA"),
    WRONG_ENDPOINT("WRONG_ENDPOINT")
}

data class ErrorResponse(
    val errorCode: ErrorCode,
    val message: String?
)

// Alle deine App-Exceptions kompakt untereinander
class UserDeletedException(message: String) : RuntimeException(message)
class TodoNotFoundException(message: String) : RuntimeException(message)
class InvalidTodoException(message: String) : RuntimeException(message)
class InvalidStatusRequestException(message: String): RuntimeException(message)
class MilestoneNotFoundException(message: String) : RuntimeException(message)