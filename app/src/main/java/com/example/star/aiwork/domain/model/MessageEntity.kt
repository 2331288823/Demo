package com.example.star.aiwork.domain.model

data class MessageEntity(
    val id: String = "",
    val sessionId: String = "",
    val role: MessageRole = MessageRole.USER,
    val type: MessageType = MessageType.TEXT,
    val content: String = "",
    val metadata: MessageMetadata = MessageMetadata(),
    val parentMessageId: String? = null,
    val createdAt: Long = 0L,
    val status: MessageStatus = MessageStatus.SENDING
)
