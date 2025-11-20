package com.example.star.aiwork.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import com.example.star.aiwork.ai.provider.ProviderSetting
import com.example.star.aiwork.ai.provider.TextGenerationParams
import com.example.star.aiwork.ai.ui.MessageChunk
import com.example.star.aiwork.ai.ui.UIMessage
import okhttp3.OkHttpClient

class ResponseAPI(private val client: OkHttpClient) {
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        // TODO: Implement if needed, usually ChatCompletionsAPI is enough
        throw NotImplementedError("ResponseAPI for non-streaming not implemented yet")
    }

    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> {
        // TODO: Implement if needed
        throw NotImplementedError("ResponseAPI for streaming not implemented yet")
    }
}
