package com.example.star.aiwork.data.local.record

data class DraftRecord(
    val sessionId: String,  // 一条会话一个草稿
    val content: String?,   // 文本草稿
    val updatedAt: Long
)