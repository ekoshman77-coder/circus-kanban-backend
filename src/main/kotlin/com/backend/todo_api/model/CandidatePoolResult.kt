package com.backend.todo_api.model

import com.backend.todo_api.data.entity.TodoEntity

data class CandidatePoolResult(
    val candidates: List<TodoEntity>,
    val selectionMode: CandidateSelectionMode
)