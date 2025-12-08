package com.example.star.aiwork.domain.usecase.embedding

import android.util.Log
import com.example.star.aiwork.data.repository.AiRepository
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.fold
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 过滤应该被写入长期记忆的消息的用例。
 * 
 * 接收一个消息列表（包含文本），通过 AI 模型判断哪些消息应该被写入长期记忆，
 * 并返回应该被保存的消息索引列表。
 * 
 * 参考 SendMessageUseCase 的实现方式，使用 AiRepository 来发送消息。
 */
class FilterMemoryMessagesUseCase(
    private val aiRepository: AiRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 模型返回的 JSON 格式数据结构
     */
    @Serializable
    private data class MemoryFilterResponse(
        val shouldSave: Boolean = false,
        val messages: List<MemoryItem> = emptyList()
    )

    @Serializable
    private data class MemoryItem(
        val index: Int,
        val content: String,
        val reason: String? = null
    )

    /**
     * 执行消息过滤。
     * 
     * @param messages 要过滤的消息文本列表（只包含文本内容）
     * @param providerSetting AI 提供商设置
     * @param model AI 模型配置
     * @param temperature 生成温度参数，默认 0.3 以获得更稳定的判断
     * @param maxTokens 最大 token 数，默认 1000
     * @return 应该被写入长期记忆的消息索引列表（从 0 开始），如果没有则返回空列表
     */
    suspend operator fun invoke(
        messages: List<String>,
        providerSetting: ProviderSetting,
        model: Model,
        temperature: Float = 0.3f,
        maxTokens: Int = 1000
    ): List<Int> {
        Log.d("FilterMemoryMessages", "=".repeat(80))
        Log.d("FilterMemoryMessages", "🤖 [AI 过滤] 开始使用 AI 模型过滤消息")
        
        if (messages.isEmpty()) {
            Log.w("FilterMemoryMessages", "⚠️ [AI 过滤] 消息列表为空，返回空列表")
            return emptyList()
        }

        Log.d("FilterMemoryMessages", "   └─ 待过滤消息数量: ${messages.size}")
        Log.d("FilterMemoryMessages", "   └─ Provider: ${providerSetting.name}, Model: ${model.modelId}")
        Log.d("FilterMemoryMessages", "   └─ Temperature: $temperature, MaxTokens: $maxTokens")
        
        // 记录所有待过滤的消息
        messages.forEachIndexed { index, text ->
            Log.d("FilterMemoryMessages", "   [$index] ${text.take(60)}${if (text.length > 60) "..." else ""}")
        }

        try {
            // 将消息列表转换为文本格式
            Log.d("FilterMemoryMessages", "📝 [AI 过滤] 构建提示词")
            val messagesText = buildMessagesText(messages)
            Log.d("FilterMemoryMessages", "   └─ 消息文本长度: ${messagesText.length} 字符")
            
            // 构建系统提示词
            val systemPrompt = buildSystemPrompt()
            Log.d("FilterMemoryMessages", "   └─ 系统提示词长度: ${systemPrompt.length} 字符")
            
            // 构建用户提示词
            val userPrompt = buildUserPrompt(messagesText, messages.size)
            Log.d("FilterMemoryMessages", "   └─ 用户提示词长度: ${userPrompt.length} 字符")
            
            // 构建要发送的消息列表（转换为 ChatDataItem 格式，参考 SendMessageUseCase）
            val history = listOf(
                ChatDataItem(
                    role = MessageRole.SYSTEM.name.lowercase(),
                    content = systemPrompt
                ),
                ChatDataItem(
                    role = MessageRole.USER.name.lowercase(),
                    content = userPrompt
                )
            )
            
            // 构建文本生成参数
            val params = TextGenerationParams(
                model = model,
                temperature = temperature,
                maxTokens = maxTokens
            )
            
            // 使用 AiRepository 发送消息（参考 SendMessageUseCase 的方式）
            Log.d("FilterMemoryMessages", "📤 [AI 过滤] 发送请求到 AI 模型")
            val startTime = System.currentTimeMillis()
            val responseText = callAiRepository(history, providerSetting, params)
            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d("FilterMemoryMessages", "📥 [AI 过滤] 收到 AI 模型响应 (耗时: ${elapsedTime}ms)")
            Log.d("FilterMemoryMessages", "   └─ 响应长度: ${responseText.length} 字符")
            Log.d("FilterMemoryMessages", "   └─ 响应预览: ${responseText.take(200)}${if (responseText.length > 200) "..." else ""}")
            
            // 解析响应
            Log.d("FilterMemoryMessages", "🔍 [AI 过滤] 解析 AI 响应")
            val filterResponse = parseResponse(responseText)
            Log.d("FilterMemoryMessages", "   └─ shouldSave: ${filterResponse.shouldSave}")
            Log.d("FilterMemoryMessages", "   └─ 返回的消息数量: ${filterResponse.messages.size}")
            
            if (!filterResponse.shouldSave || filterResponse.messages.isEmpty()) {
                Log.d("FilterMemoryMessages", "⏭️ [AI 过滤] AI 模型判断没有消息需要写入长期记忆")
                Log.d("FilterMemoryMessages", "=".repeat(80))
                return emptyList()
            }
            
            // 记录 AI 返回的原始结果
            filterResponse.messages.forEach { item ->
                Log.d("FilterMemoryMessages", "   └─ AI 返回: 索引=${item.index}, 内容=\"${item.content?.take(50) ?: "无"}\", 原因=\"${item.reason?.take(50) ?: "无"}\"")
            }
            
            // 提取有效的索引列表
            val validIndices = filterResponse.messages
                .mapNotNull { item ->
                    if (item.index >= 0 && item.index < messages.size) {
                        item.index
                    } else {
                        Log.w("FilterMemoryMessages", "   ⚠️ 无效的消息索引: ${item.index}, 消息总数: ${messages.size}")
                        null
                    }
                }
                .distinct()
                .sorted()
            
            Log.d("FilterMemoryMessages", "✅ [AI 过滤] 过滤完成")
            Log.d("FilterMemoryMessages", "   └─ 有效索引数量: ${validIndices.size}")
            Log.d("FilterMemoryMessages", "   └─ 有效索引列表: $validIndices")
            Log.d("FilterMemoryMessages", "=".repeat(80))
            return validIndices
            
        } catch (e: Exception) {
            Log.e("FilterMemoryMessages", "❌ [AI 过滤] 过滤消息失败: ${e.message}", e)
            Log.e("FilterMemoryMessages", "   └─ 异常类型: ${e.javaClass.simpleName}")
            e.printStackTrace()
            Log.d("FilterMemoryMessages", "=".repeat(80))
            // 发生错误时返回空列表，避免影响正常流程
            return emptyList()
        }
    }

    /**
     * 将消息列表转换为文本格式
     */
    private fun buildMessagesText(messages: List<String>): String {
        return messages.mapIndexed { index, text ->
            """
            [消息 $index]
            内容: $text
            """.trimIndent()
        }.joinToString("\n\n")
    }

    /**
     * 构建系统提示词
     */
    private fun buildSystemPrompt(): String {
        return """
        你是一个智能记忆过滤器，负责判断对话中的哪些信息应该被写入长期记忆。

        长期记忆应该包含以下类型的信息：
        1. 用户的个人身份信息（姓名、年龄、职业、居住地等）
        2. 用户的偏好和习惯（喜欢的食物、颜色、活动等）
        3. 用户的重要目标和计划（未来计划、目标等）
        4. 用户的重要关系信息（家人、朋友、同事等）
        5. 用户的重要经历和事件（值得记住的经历）
        6. 用户明确要求记住的信息

        不应该写入长期记忆的信息：
        1. 临时性的对话内容
        2. 已经过时的信息
        3. 无关紧要的闲聊
        4. 系统消息和技术性内容

        请仔细分析每条消息，判断是否应该被写入长期记忆。
        """.trimIndent()
    }

    /**
     * 构建用户提示词
     */
    private fun buildUserPrompt(messagesText: String, messageCount: Int): String {
        return """
        以下是对话中的 $messageCount 条消息：

        $messagesText

        请分析这些消息，判断哪些应该被写入长期记忆。

        请严格按照以下 JSON 格式返回结果：
        {
          "shouldSave": true/false,
          "messages": [
            {
              "index": 0,
              "content": "消息的简要内容",
            }
          ]
        }

        要求：
        1. 如果没有任何消息需要写入长期记忆，返回 {"shouldSave": false, "messages": []}
        2. index 必须是消息在列表中的索引（从 0 开始）
        3. content 应该是消息的简要摘要
        4. 只返回 JSON，不要包含任何其他说明文字
        """.trimIndent()
    }

    /**
     * 使用 AiRepository 发送消息并收集完整响应
     * 参考 SendMessageUseCase 的实现方式
     */
    private suspend fun callAiRepository(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams
    ): String {
        // 使用 AiRepository.streamChat 发送消息
        // 由于我们需要完整的响应来解析 JSON，需要收集所有流式数据
        val taskId = java.util.UUID.randomUUID().toString()
        val stream = aiRepository.streamChat(history, providerSetting, params, taskId)
        
        // 收集所有流式字符串片段并拼接成完整响应
        return stream.fold(StringBuilder()) { acc, chunk ->
            acc.append(chunk)
        }.toString()
    }

    /**
     * 解析模型返回的响应
     */
    private fun parseResponse(responseText: String): MemoryFilterResponse {
        if (responseText.isBlank()) {
            Log.w("FilterMemoryMessages", "模型返回的响应为空")
            return MemoryFilterResponse(shouldSave = false)
        }
        
        // 尝试提取 JSON 部分（可能包含在 markdown 代码块中）
        val jsonText = extractJsonFromText(responseText)
        
        return try {
            json.decodeFromString<MemoryFilterResponse>(jsonText)
        } catch (e: Exception) {
            Log.e("FilterMemoryMessages", "解析 JSON 失败: ${e.message}", e)
            Log.d("FilterMemoryMessages", "原始响应: $responseText")
            // 如果解析失败，尝试简单的文本匹配
            if (responseText.contains("shouldSave") || responseText.contains("true")) {
                // 如果响应中包含相关关键词，尝试手动解析
                Log.w("FilterMemoryMessages", "JSON 解析失败，但响应中包含相关关键词，返回空列表")
            }
            MemoryFilterResponse(shouldSave = false)
        }
    }

    /**
     * 从文本中提取 JSON 部分（可能包含在 markdown 代码块中）
     */
    private fun extractJsonFromText(text: String): String {
        // 尝试提取 JSON 代码块
        val jsonBlockRegex = Regex("```(?:json)?\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val jsonBlockMatch = jsonBlockRegex.find(text)
        if (jsonBlockMatch != null) {
            return jsonBlockMatch.groupValues[1].trim()
        }
        
        // 尝试提取大括号包裹的 JSON
        val braceRegex = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
        val braceMatch = braceRegex.find(text)
        if (braceMatch != null) {
            return braceMatch.value
        }
        
        // 如果都没有，返回原文本（可能是纯 JSON）
        return text.trim()
    }
}

