package com.example.star.aiwork.ai.provider.providers.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import com.example.star.aiwork.ai.core.MessageRole
import com.example.star.aiwork.ai.core.TokenUsage
import com.example.star.aiwork.ai.ui.MessageChunk
import com.example.star.aiwork.ai.ui.UIMessage
import com.example.star.aiwork.ai.ui.UIMessageChoice
import com.example.star.aiwork.ai.ui.UIMessagePart
import java.util.UUID

@Serializable
data class OpenAIChunk(
    val id: String,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: TokenUsage? = null,
) {
    fun toMessageChunk(): MessageChunk {
        return MessageChunk(
            id = id,
            model = model,
            choices = choices.map { it.toUIMessageChoice() },
            usage = usage
        )
    }
}

@Serializable
data class OpenAIChoice(
    val index: Int,
    val delta: OpenAIMessage? = null,
    val message: OpenAIMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
) {
    fun toUIMessageChoice(): UIMessageChoice {
        return UIMessageChoice(
            index = index,
            delta = delta?.toUIMessage(),
            message = message?.toUIMessage(),
            finishReason = finishReason
        )
    }
}

@Serializable
data class OpenAIMessage(
    val role: MessageRole? = null,
    val content: String? = null,
    // tool_calls, etc. can be added later
) {
    fun toUIMessage(): UIMessage {
        val parts = if (content != null) {
            listOf(UIMessagePart.Text(content))
        } else {
            emptyList()
        }
        
        return UIMessage(
            id = UUID.randomUUID().toString(),
            role = role ?: MessageRole.ASSISTANT, // Default to assistant if role is missing in delta
            parts = parts
        )
    }
}
