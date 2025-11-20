package com.example.star.aiwork.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)
