package com.example.star.aiwork.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val presetMessages: List<PresetMessage> = emptyList(),
    val messageTemplate: String = "{{ message }}",
    val isDefault: Boolean = false
)

@Serializable
data class PresetMessage(
    val role: MessageRole,
    val content: String
)
