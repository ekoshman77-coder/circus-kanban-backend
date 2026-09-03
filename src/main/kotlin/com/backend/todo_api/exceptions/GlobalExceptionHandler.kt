package com.backend.todo_api.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserDeletedException::class)
    fun handleUserDeleted(ex: UserDeletedException) =
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

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorCode.USER_NOT_FOUND, ex.message))

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExists(ex: UserAlreadyExistsException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ErrorCode.USER_ALREADY_EXISTS, ex.message))

    @ExceptionHandler(UserNotApprovedException::class)
    fun handleUserNotApproved(ex: UserNotApprovedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN) // 🛡️ 403 Forbidden für den Warteraum!
            .body(ErrorResponse(ErrorCode.USER_NOT_APPROVED, ex.message))

    // 🛡️ 403 Forbidden für fehlende Rechte im Permissionsystem!
    @ExceptionHandler(ActionForbiddenException::class)
    fun handleActionForbidden(ex: ActionForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN) // 🛡️ 403 Forbidden für den Warteraum!
            .body(ErrorResponse(ErrorCode.FORBIDDEN, ex.message))

    // 🛡️ 409 Conflict für doppelte Berechtigungen
    @ExceptionHandler(PermissionAlreadyExistsException::class)
    fun handlePermissionAlreadyExists(ex: PermissionAlreadyExistsException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ErrorCode.INVALID_DATA, ex.message))

    // 🛡️ 404 Not Found für nicht existierende Berechtigungen
    @ExceptionHandler(PermissionNotFoundException::class)
    fun handlePermissionNotFound(ex: PermissionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorCode.INVALID_DATA, ex.message))

    @ExceptionHandler(NoteNotFoundException::class)
    fun handleNoteNotFound(ex: NoteNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorCode.NOTE_NOT_FOUND, ex.message))

}
