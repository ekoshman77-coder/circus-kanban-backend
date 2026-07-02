package com.backend.todo_api.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

}