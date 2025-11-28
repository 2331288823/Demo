package com.example.star.aiwork.data.local.record

data class MessageRecord(
    val id: String,
    val sessionId: String,
    val role: String,              // user / assistant
    val content: String,
    val createdAt: Long,
    val status: Int,               // 0 sending / 1 success / 2 failed
    val parentMessageId: String?   // for regenerate / rollback
)
