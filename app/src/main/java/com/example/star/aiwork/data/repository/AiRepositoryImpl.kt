package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.remote.RemoteChatDataSource
import com.example.star.aiwork.data.repository.mapper.toAiMessages
import com.example.star.aiwork.data.repository.mapper.toModelConfig
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class AiRepositoryImpl(
    private val remoteChatDataSource: RemoteChatDataSource
) : AiRepository {

    override fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String
    ): Flow<String> {
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

