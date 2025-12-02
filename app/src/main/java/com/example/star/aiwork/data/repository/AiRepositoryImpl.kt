package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.provider.ProviderFactory
import com.example.star.aiwork.data.remote.RemoteChatDataSource
import com.example.star.aiwork.data.repository.mapper.toAiMessages
import com.example.star.aiwork.data.repository.mapper.toModelConfig
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.infra.network.defaultOkHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

class AiRepositoryImpl(
    private val remoteChatDataSource: RemoteChatDataSource
) : AiRepository {

    private val okHttpClient: OkHttpClient = defaultOkHttpClient()

    override fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String
    ): Flow<String> {
        // 检查是否为 Google Provider (或其他未通过 RemoteChatDataSource 处理的类型)
        if (providerSetting is ProviderSetting.Google) {
            return flow {
                val provider = ProviderFactory.getProvider(providerSetting, okHttpClient)
                
                // 将 ChatDataItem 列表转换为 UIMessage 列表以适应 Provider 接口
                // 注意：history 包含了本次用户输入，也就是 userMessage
                val uiMessages = history.map { it.toUIMessage() }
                
                // Provider 接口需要泛型的 providerSetting，这里强转是安全的因为我们已经检查了类型
                // 但为了类型安全，我们在 getProvider 里已经处理了
                
                // 这是一个 hack，因为 Provider 接口是泛型的，但 ProviderFactory 返回通配符
                // 我们知道它是 GoogleProvider，所以直接调用 streamText
                // 但更好的做法是让 Provider 接口更统一。
                // 这里通过反射或者 unchecked cast 调用
                
                @Suppress("UNCHECKED_CAST")
                val flow = (provider as com.example.star.aiwork.domain.Provider<ProviderSetting.Google>)
                    .streamText(providerSetting, uiMessages, params)
                
                flow.collect { chunk ->
                    chunk.choices.firstOrNull()?.delta?.parts?.forEach { part ->
                        if (part is UIMessagePart.Text) {
                            emit(part.text)
                        }
                    }
                }
            }
        }
        
        // 对于 OpenAI 和 Ollama，继续使用旧的 RemoteChatDataSource 逻辑
        // 注意：Ollama 虽然在 ProviderFactory 中有对应 Provider，但目前 RemoteChatDataSource 也支持它
        // 如果想统一迁移到 Provider 体系，可以在这里加更多 else if
        
        val upstream = remoteChatDataSource.streamChat(
            history = history.toAiMessages(),
            config = providerSetting.toModelConfig(params, taskId)
        )

        // 由于不同模型返回的 chunk 大小不一致，这里统一做一次缓冲与重切分，
        // 确保对 domain 层暴露的流的每个 chunk 尺寸是相对稳定的。
        return flow {
            val buffer = StringBuilder()

            upstream.collect { chunk ->
                if (chunk.isEmpty()) return@collect

                buffer.append(chunk)

                // 按固定大小切分并依次发射
                while (buffer.length >= STREAM_CHUNK_SIZE) {
                    val piece = buffer.substring(0, STREAM_CHUNK_SIZE)
                    emit(piece)
                    buffer.delete(0, STREAM_CHUNK_SIZE)
                }
            }

            // 上游结束后，如果还有残余内容，一次性发射出去
            if (buffer.isNotEmpty()) {
                emit(buffer.toString())
            }
        }
    }

    override suspend fun cancelStreaming(taskId: String) {
        remoteChatDataSource.cancelStreaming(taskId)
    }

    // 辅助方法：ChatDataItem -> UIMessage
    private fun ChatDataItem.toUIMessage(): UIMessage {
        val roleEnum = when (role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            "tool" -> MessageRole.TOOL // 假设有这个
            else -> MessageRole.USER
        }
        
        // 简单处理内容，如果有图片标记需要解析，这里暂时简化为纯文本
        // 真正的解析逻辑应该类似 StreamingChatRemoteDataSource 中的 buildStreamRequest
        // 或者复用 UIMessage 的反序列化逻辑
        
        // 这里为了支持 Google 传入图片，我们需要简单解析一下 [image:...] 标记
        val parts = mutableListOf<UIMessagePart>()
        val textContent = content
        val markerStart = "[image:data:image/"
        
        if (textContent.contains(markerStart)) {
            var currentIndex = 0
            while (currentIndex < textContent.length) {
                val startIndex = textContent.indexOf(markerStart, currentIndex)
                if (startIndex == -1) {
                    val remainingText = textContent.substring(currentIndex)
                    if (remainingText.isNotEmpty()) {
                        parts.add(UIMessagePart.Text(remainingText))
                    }
                    break
                }
                
                if (startIndex > currentIndex) {
                    val textSegment = textContent.substring(currentIndex, startIndex)
                    if (textSegment.isNotEmpty()) {
                        parts.add(UIMessagePart.Text(textSegment))
                    }
                }
                
                val endIndex = textContent.indexOf("]", startIndex)
                if (endIndex != -1) {
                    val imageUrl = textContent.substring(startIndex + 7, endIndex) // 去掉 "[image:"
                    parts.add(UIMessagePart.Image(imageUrl))
                    currentIndex = endIndex + 1
                } else {
                    val remainingText = textContent.substring(currentIndex)
                    parts.add(UIMessagePart.Text(remainingText))
                    break
                }
            }
        } else {
            parts.add(UIMessagePart.Text(textContent))
        }

        return UIMessage(
            role = roleEnum,
            parts = parts
        )
    }

    companion object {
        /**
         * 对外暴露的统一 chunk 大小。
         *
         * 注意：这里是逻辑上的“目标尺寸”，真实网络数据仍然可能在句子边界等位置
         * 有细微差异，如需按 token 或按句子切分，可在上层增加更复杂的策略。
         */
        private const val STREAM_CHUNK_SIZE: Int = 32
    }
}
