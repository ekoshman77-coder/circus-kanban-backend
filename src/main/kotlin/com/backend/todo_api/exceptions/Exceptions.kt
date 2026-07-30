package com.backend.todo_api.exceptions

enum class ErrorCode(val value: String) {
    USER_NOT_FOUND("USER_NOT_FOUND"),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS"), // 🚀 NEU
    USER_NOT_APPROVED("USER_NOT_APPROVED"),     // 🚀 NEU
    TODO_NOT_FOUND("TODO_NOT_FOUND"),
    INVALID_DATA("INVALID_DATA"),
    WRONG_ENDPOINT("WRONG_ENDPOINT"),
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND"),
    TEAM_VALIDATION_ERROR("TEAM_VALIDATION_ERROR")
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
class ProjectNotFoundException(message: String) : RuntimeException(message)
class TeamValidationException(message: String) : RuntimeException(message)
class UserAlreadyExistsException(message: String) : RuntimeException(message)
class UserNotFoundException(message: String) : RuntimeException(message)
class UserNotApprovedException(message: String): RuntimeException(message)