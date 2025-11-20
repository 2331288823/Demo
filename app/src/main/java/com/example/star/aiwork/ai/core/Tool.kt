package com.example.star.aiwork.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.example.star.aiwork.ai.provider.Model
import com.example.star.aiwork.ai.ui.UIMessage

/**
 * 表示 AI 可用的工具 (Tool/Function Calling)。
 * 定义了工具的名称、描述、参数结构以及执行逻辑。
 *
 * @property name 工具名称，唯一标识。
 * @property description 工具的功能描述，模型会根据此描述决定是否调用。
 * @property parameters 返回工具参数的 JSON Schema 结构。
 * @property systemPrompt 可选的系统提示词生成器，根据当前模型和消息上下文动态生成。
 * @property execute 挂起函数，执行具体的工具逻辑。接收 JSON 格式的参数，返回 JSON 格式的结果。
 */
@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: () -> InputSchema? = { null },
    val systemPrompt: (model: Model, messages: List<UIMessage>) -> String = { _, _ -> "" },
    val execute: suspend (JsonElement) -> JsonElement
)

/**
 * 工具输入参数的 Schema 定义 (JSON Schema 的子集)。
 * 用于描述工具接受的参数类型和结构。
 */
@Serializable
sealed class InputSchema {
    /**
     * 表示一个 JSON 对象类型的参数结构。
     *
     * @property properties 属性定义的键值对，值通常也是 JSON 对象描述。
     * @property required 必须包含的属性名称列表。
     */
    @Serializable
    @SerialName("object")
    data class Obj(
        val properties: JsonObject,
        val required: List<String>? = null,
    ) : InputSchema()
}
