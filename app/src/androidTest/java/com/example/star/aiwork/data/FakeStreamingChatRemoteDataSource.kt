package com.example.star.aiwork.data

import com.example.star.aiwork.data.remote.RemoteChatDataSource
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 一个用于测试的 Fake 实现，可以手动控制流式数据。
 */
class FakeStreamingChatRemoteDataSource : RemoteChatDataSource {

    // 简单地记录测试中 emit 的所有文本，构建一个会在收集时“跑完即结束”的冷 Flow。
    private val chunks = mutableListOf<String>()

    override fun streamChat(
        history: List<ChatDataItem>,
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        taskId: String
    ): Flow<String> = flow {
        for (chunk in chunks) {
            emit(chunk)
        }
    }

    override suspend fun cancelStreaming(taskId: String) {
        // 测试环境下不需要真正取消，保持为空实现即可
    }

    /**
     * 在测试中手动往流里推送一段文本。
     */
    fun emit(text: String) {
        chunks += text
    }
}


