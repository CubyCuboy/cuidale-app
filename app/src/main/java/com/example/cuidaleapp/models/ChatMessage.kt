package com.example.cuidaleapp.models

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val readBy: Map<String, Boolean> = emptyMap() // uid -> true
)
