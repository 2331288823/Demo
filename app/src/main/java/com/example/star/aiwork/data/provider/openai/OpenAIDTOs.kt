package com.example.star.aiwork.data.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.example.star.aiwork.ui.ai.MessageChunk
import com.example.star.aiwork.ui.ai.UIMessageChoice
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.domain.model.TokenUsage

/**
 * OpenAI API 响应的数据传输对象 (DTO)。
 *
 * 用于解析来自 OpenAI 兼容 API 的 JSON 响应流。
 *
 * @property id 响应块的唯一标识符。
 * @property model 生成响应的模型名称。
 * @property choices 包含生成的文本选项列表。
 * @property usage token 使用统计信息（可选）。
 */
@Serializable
data class OpenAIChunk(
    val id: String,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: TokenUsage? = null
)

/**
 * OpenAI 响应中的单个选项。
 *
 * 在流式传输中，包含增量更新 (delta)；在非流式传输中，可能包含完整消息 (message)。
 *
 * @property index 选项在列表中的索引。
 * @property delta 流式传输时的消息增量更新。
 * @property message 非流式传输时的完整消息内容。
 * @property finishReason 生成结束的原因（例如 "stop", "length"），如果未结束则为 null。
 */
@Serializable
data class OpenAIChoice(
    val index: Int,
    val delta: OpenAIMessage? = null,
    val message: OpenAIMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * OpenAI 消息格式。
 *
 * 代表对话中的一条消息或消息片段。
 *
 * @property role 消息发送者的角色（如 "user", "assistant", "system"）。
 * @property content 消息的文本内容。
 */
@Serializable
data class OpenAIMessage(
    val role: String? = null,
    val content: String? = null
)

/**
 * 将 OpenAIChunk DTO 转换为应用内部使用的 MessageChunk 域对象。
 */
fun OpenAIChunk.toMessageChunk(): MessageChunk {
    return MessageChunk(
        id = this.id,
        model = this.model,
        choices = this.choices.map { it.toUIMessageChoice() },
        usage = this.usage
    )
}

/**
 * 将 OpenAIChoice DTO 转换为应用内部使用的 UIMessageChoice 对象。
 */
fun OpenAIChoice.toUIMessageChoice(): UIMessageChoice {
    return UIMessageChoice(
        index = this.index,
        delta = this.delta?.toUIMessage(),
        message = this.message?.toUIMessage(),
        finishReason = this.finishReason
    )
}

/**
 * 将 OpenAIMessage DTO 转换为应用内部使用的 UIMessage 对象。
 *
 * 处理角色映射和内容包装。
 *
 * @return 转换后的 UIMessage，如果无法转换则可能返回 null（尽管当前实现总是返回对象，除非逻辑改变）。
 */
fun OpenAIMessage.toUIMessage(): UIMessage? {
    // 将字符串类型的 role 映射为 MessageRole 枚举。
    // 如果 role 为 null（常见于流式响应的后续块），默认为 ASSISTANT。
    val messageRole = when(role) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "system" -> MessageRole.SYSTEM
        else -> MessageRole.ASSISTANT 
    }

    // 将 content 包装为 UIMessagePart 列表。
    val parts = if (content != null) {
        listOf(UIMessagePart.Text(content))
    } else {
        emptyList()
    }

    return UIMessage(
        role = messageRole,
        parts = parts
    )
}
