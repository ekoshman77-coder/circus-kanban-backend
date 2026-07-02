package com.backend.todo_api.validation

import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.exceptions.UserDeletedException

public fun validateUserExists(userId: String, userRepository: UserRepository) {
    if (!userRepository.existsById(userId)) {
        throw UserDeletedException("User mit der ID $userId existiert nicht in der Datenbank.")
    }
}

