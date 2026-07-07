package com.backend.todo_api.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserDeletedException::class)
    fun handleUserNotFound(ex: UserDeletedException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ErrorCode.USER_NOT_FOUND, ex.message))

    @ExceptionHandler(TodoNotFoundException::class)
    fun handleTodoNotFound(ex: TodoNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorCode.TODO_NOT_FOUND, ex.message))

    @ExceptionHandler(InvalidTodoException::class)
    fun handleInvalidTodoException(ex: TodoNotFoundException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorCode.INVALID_DATA, ex.message))

    @ExceptionHandler(InvalidStatusRequestException::class)
    fun handlenvalidStatusRequest(ex: TodoNotFoundException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorCode.WRONG_ENDPOINT, ex.message))

    // 1. Wenn ein Projekt nicht existiert (404 Not Found)
    @ExceptionHandler(ProjectNotFoundException::class)
    fun handleProjectNotFound(ex: ProjectNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorCode.PROJECT_NOT_FOUND, ex.message))

    // 2. Wenn z.B. die Rolle im Service leer ist (400 Bad Request)
    @ExceptionHandler(TeamValidationException::class)
    fun handleTeamValidation(ex: TeamValidationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorCode.TEAM_VALIDATION_ERROR, ex.message))

    // 3. Wenn die Jakarta @Valid Validation im DTO fehlschlägt (z.B. leere userId)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleDtoValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        // Holt die Fehlermeldung, die wir im DTO bei @field:NotBlank definiert haben
        val validationMessage = ex.bindingResult.fieldError?.defaultMessage ?: "Eingabevalidierung fehlgeschlagen."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ErrorCode.INVALID_DATA, validationMessage))
    }
}
