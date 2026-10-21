package com.example.cuidaleapp.models

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val pinned: Boolean = false,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToName: String? = null,
    val type: String = "text",
    val metadata: Map<String, String> = emptyMap(),
    val readBy: Map<String, Boolean> = emptyMap()
)
