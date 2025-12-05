package com.example.star.aiwork.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 消息角色枚举，定义了聊天中不同消息发送者的身份。
 *
 * 遵循标准 LLM API 的角色定义。
 */
@Serializable
enum class MessageRole(val value: String) {
    @SerialName("system")
    SYSTEM("system"),     // 系统消息，通常用于设置助手的行为

    @SerialName("user")
    USER("user"),       // 用户消息

    @SerialName("assistant")
    ASSISTANT("assistant"),  // 助手 (AI) 消息

    @SerialName("tool")
    TOOL("tool");       // 工具执行结果消息
}
