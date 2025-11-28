package com.example.star.aiwork.domain.model

data class SessionEntity(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pinned: Boolean = false,               // 是否置顶
    val archived: Boolean = false,             // 是否归档
    val metadata: SessionMetadata = SessionMetadata()
)
